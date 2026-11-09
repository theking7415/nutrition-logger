package com.nutritionlogger.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.nutritionlogger.health.HealthConnectManager

/**
 * Runs when the widget is tapped. This is the whole point of using Glance
 * over a classic RemoteViews widget: this suspend callback can call straight
 * into Health Connect with no activity launch and no toast, then redraw the
 * widget in place with the result. The success path flashes the widget (see
 * WaterWidgetState.logged) so every tap, including a second one within the
 * same minute, visibly registers.
 */
class LogWaterAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val ml = HealthConnectManager.DEFAULT_GLASS_ML
        runCatching { HealthConnectManager(context).logHydration(ml) }
            .onSuccess { WaterWidgetState.logged(context, ml) }
            .onFailure { e -> WaterWidgetState.failed(context, e.message ?: "perm?") }
    }
}
