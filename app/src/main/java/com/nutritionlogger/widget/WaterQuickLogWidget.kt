package com.nutritionlogger.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
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

val lastLoggedKey = stringPreferencesKey("last_logged")

/**
 * 1x1 tap-to-log widget. The tap itself (LogWaterAction) does the Health
 * Connect write directly -- this composable only ever renders whatever state
 * that action last left behind, it never opens an activity.
 *
 * NOTE: the exact package for Glance's ColorProvider/TextStyle has moved
 * between glance-appwidget releases; this hasn't been compiled yet (no
 * Android SDK in this shell -- see CLAUDE.md). If Android Studio flags an
 * import here, it is almost certainly just a package-path drift, not a
 * design problem -- fix the import, not the approach.
 */
class WaterQuickLogWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val lastLogged = prefs[lastLoggedKey] ?: "Tap to log 250 ml"

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF1B5E4A))
                    .padding(4.dp)
                    .clickable(actionRunCallback<LogWaterAction>()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("💧", style = TextStyle(fontSize = 18.sp))
                Text(
                    lastLogged,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}
