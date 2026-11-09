package com.nutritionlogger.relay

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.nutritionlogger.health.HealthConnectManager
import com.nutritionlogger.widget.WaterWidgetState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val TAG = "NutritionLogger.relay"

/** Prefix shared with the watch's PhoneRelayClient -- keep these in sync. */
private const val LOG_PREFIX = "/nutrilogger/log"

/**
 * Receives water logs queued by the watch. The watch stores each log as a
 * Data Layer item, which the Play Services layer keeps on the watch while the
 * phone is out of reach and delivers once it reconnects. We write each one to
 * Health Connect at its original timestamp, then delete the item: deletion is
 * the ack, so a failed write (e.g. permission revoked) stays queued and is
 * retried on the next drain. See CLAUDE.md, "The wear/watch integration".
 */
class PhoneRelayListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        Log.e(TAG, "onDataChanged: ${events.count} events")
        // The events themselves are ignored; draining reads everything pending,
        // which also picks up earlier items whose write previously failed.
        events.release()
        runBlocking { PendingWatchLogs.drain(applicationContext) }
    }
}

object PendingWatchLogs {
    private val lock = Mutex()

    /** Writes every pending watch log to Health Connect and deletes the ones that succeed. */
    suspend fun drain(context: Context) = lock.withLock {
        val client = Wearable.getDataClient(context)
        val buffer = client.getDataItems(Uri.parse("wear://*$LOG_PREFIX"), DataClient.FILTER_PREFIX).await()
        // Copy out before releasing; DataItems are only valid while the buffer is open.
        val pending = try {
            buffer.map { item ->
                val map = DataMapItem.fromDataItem(item).dataMap
                Triple(item.uri, item.uri.lastPathSegment ?: "", map.getDouble("ml") to map.getLong("ts"))
            }
        } finally {
            buffer.release()
        }
        Log.e(TAG, "drain: ${pending.size} pending")

        val health = HealthConnectManager(context.applicationContext)
        val zone = ZoneId.systemDefault()
        for ((uri, id, payload) in pending) {
            val (ml, ts) = payload
            // Clamp: a watch clock slightly ahead of the phone would otherwise
            // produce a future endTime, which Health Connect rejects.
            val at = Instant.ofEpochMilli(ts).coerceAtMost(Instant.now())
            runCatching { health.logHydration(ml, at, clientId = "watch-$id") }
                .onSuccess {
                    Log.e(TAG, "logged ${ml}ml from $at (id $id)")
                    client.deleteDataItems(uri).await()
                    if (at.atZone(zone).toLocalDate() == LocalDate.now()) {
                        WaterWidgetState.logged(context.applicationContext, ml)
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "logHydration failed for $id; leaving queued", e)
                    WaterWidgetState.failed(context.applicationContext, e.message ?: "watch log failed")
                }
        }
    }

    private fun Instant.coerceAtMost(max: Instant) = if (isAfter(max)) max else this
}
