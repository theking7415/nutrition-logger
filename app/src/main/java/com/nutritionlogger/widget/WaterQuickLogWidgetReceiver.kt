package com.nutritionlogger.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** The manifest-facing AppWidgetProvider shim Glance requires. No logic lives here. */
class WaterQuickLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaterQuickLogWidget()
}
