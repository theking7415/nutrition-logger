package com.nutritionlogger.relay

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.nutritionlogger.health.HealthConnectManager
import com.nutritionlogger.widget.WaterQuickLogWidget
import com.nutritionlogger.widget.lastLoggedKey
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "NutritionLogger.relay"

/** Path shared with the watch's PhoneRelayClient -- keep these in sync. */
private const val LOG_WATER_PATH = "/nutrilogger/log-water"

/**
 * Receives a water amount relayed from the watch tile and does the actual
 * Health Connect write. This exists because on-watch direct writes were
 * tried and abandoned -- see CLAUDE.md, "The wear/watch integration".
 * Requires this app to be installed and already holding WRITE_HYDRATION;
 * if the write fails (e.g. permission revoked), it is silently dropped from
 * the watch's perspective -- there is no ack path back, which is an accepted
 * limitation for a personal single-user setup.
 */
class PhoneRelayListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        Log.e(TAG, "onMessageReceived: path=${event.path} sourceNodeId=${event.sourceNodeId}")

        if (event.path != LOG_WATER_PATH) {
            Log.e(TAG, "ignoring: path \"${event.path}\" does not match \"$LOG_WATER_PATH\"")
            return
        }
        val raw = String(event.data, Charsets.UTF_8)
        val ml = raw.toDoubleOrNull()
        if (ml == null) {
            Log.e(TAG, "could not parse volume from payload: \"$raw\"")
            return
        }

        runBlocking {
            runCatching { HealthConnectManager(applicationContext).logHydration(ml) }
                .onSuccess {
                    Log.e(TAG, "logHydration($ml) succeeded")
                    updateWidgetState(applicationContext, ml)
                }
                .onFailure { e -> Log.e(TAG, "logHydration($ml) failed", e) }
        }
    }

    private suspend fun updateWidgetState(context: Context, ml: Double) {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val message = "${ml.toInt()}ml @ $time (watch)"

        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(WaterQuickLogWidget::class.java)
        for (id in ids) {
            updateAppWidgetState(context, id) { prefs ->
                prefs[lastLoggedKey] = message
            }
            WaterQuickLogWidget().update(context, id)
        }
    }
}
