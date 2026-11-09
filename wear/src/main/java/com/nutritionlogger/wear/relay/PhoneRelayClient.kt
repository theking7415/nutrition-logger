package com.nutritionlogger.wear.relay

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import java.util.UUID

private const val TAG = "NutritionLogger.relay"

/** Prefix shared with the phone's PhoneRelayListenerService -- keep these in sync. */
const val LOG_PREFIX = "/nutrilogger/log"

/**
 * Queues a water log for the phone as a Data Layer item. Unlike a message,
 * a data item is stored on the watch and synced whenever the phone is
 * reachable, so a log made out of range is delivered later, with its
 * original timestamp, with no queue or retry code of our own. The phone
 * deletes the item after writing it to Health Connect (that is the ack).
 * See CLAUDE.md, "The wear/watch integration", for why the watch relays
 * instead of writing to Health Connect directly.
 *
 * Succeeds as soon as the item is stored locally; it does not mean the phone
 * has processed it yet.
 */
suspend fun relayLogWater(context: Context, volumeMl: Double) {
    val id = UUID.randomUUID().toString()
    val request = PutDataMapRequest.create("$LOG_PREFIX/$id").apply {
        dataMap.putDouble("ml", volumeMl)
        dataMap.putLong("ts", System.currentTimeMillis())
    }.asPutDataRequest().setUrgent()
    Wearable.getDataClient(context).putDataItem(request).await()
    Log.e(TAG, "queued ${volumeMl}ml as $id")
}
