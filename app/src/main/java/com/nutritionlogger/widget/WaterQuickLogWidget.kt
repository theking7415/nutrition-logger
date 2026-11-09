package com.nutritionlogger.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

private val IDLE_GREEN = Color(0xFF1B5E4A)
private val FLASH_GREEN = Color(0xFF2EBD85)
private val ERROR_RED = Color(0xFF8E2A2A)

/**
 * 1x1 tap-to-log widget. The tap itself (LogWaterAction) does the Health
 * Connect write directly -- this composable only ever renders the state
 * WaterWidgetState last pushed, it never opens an activity. Glance can't
 * animate, so a tap "plays" as a brief bright-green flash with a check mark
 * (flashKey), then settles back to the running total and last-log line.
 *
 * NOTE: the exact package for Glance's ColorProvider/TextStyle has moved
 * between glance-appwidget releases; if Android Studio flags an import here
 * it is almost certainly just package-path drift -- fix the import, not the
 * approach.
 */
class WaterQuickLogWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val total = prefs[totalMlKey] ?: 0
            val lastMl = prefs[lastMlKey] ?: 0
            val time = prefs[lastTimeKey].orEmpty()
            val count = prefs[countKey] ?: 0
            val flash = prefs[flashKey] ?: false
            val error = prefs[errorKey].orEmpty()

            val bg = when {
                error.isNotEmpty() -> ERROR_RED
                flash -> FLASH_GREEN
                else -> IDLE_GREEN
            }
            val line = when {
                error.isNotEmpty() -> error
                flash -> "+$lastMl ml"
                count > 0 -> "+$lastMl · $time"
                else -> "Tap to log"
            }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(bg)
                    .cornerRadius(20.dp)
                    .padding(4.dp)
                    .clickable(actionRunCallback<LogWaterAction>()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (flash) "✓" else "💧",
                    style = TextStyle(
                        fontSize = if (flash) 26.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color.White),
                    ),
                )
                if (!flash && error.isEmpty() && total > 0) {
                    Text(
                        "$total ml",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
                Text(
                    line,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFD9F2E8)),
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}
