package com.nutritionlogger.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.nutritionlogger.health.HealthConnectManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Runs when the widget is tapped. This is the whole point of using Glance
 * over a classic RemoteViews widget: this suspend callback can call straight
 * into Health Connect with no activity launch and no toast, then redraw the
 * widget in place with the result.
 */
class LogWaterAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val message = runCatching {
            HealthConnectManager(context).logHydration(HealthConnectManager.DEFAULT_GLASS_ML)
        }.fold(
            onSuccess = { "250ml @ $time" },
            // Shortened to ensure it fits in 1x1
            onFailure = { error -> "Err: ${error.message?.take(10) ?: "perm?"}" },
        )
        updateAppWidgetState(context, glanceId) { prefs -> prefs[lastLoggedKey] = message }
        WaterQuickLogWidget().update(context, glanceId)
    }
}
