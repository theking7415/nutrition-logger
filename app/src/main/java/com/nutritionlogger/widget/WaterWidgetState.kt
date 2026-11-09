package com.nutritionlogger.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

val totalMlKey = intPreferencesKey("total_ml")
val lastMlKey = intPreferencesKey("last_ml")
val lastTimeKey = stringPreferencesKey("last_time")
val countKey = intPreferencesKey("count")
val flashKey = booleanPreferencesKey("flash")
val errorKey = stringPreferencesKey("error")
/** Bumped on every push so the Glance state always changes, even for an identical log. */
val seqKey = intPreferencesKey("seq")

/**
 * Single source of truth for what the widget shows, shared by every path that
 * logs water (widget tap, watch relay, in-app buttons). The widget's own Glance
 * state is just a per-id mirror of this, pushed by [push]; Glance only
 * recomposes on a state change, so reading this directly from provideGlance
 * would go stale while a session is alive.
 */
object WaterWidgetState {
    private const val PREFS = "water_widget"
    private const val FLASH_MS = 1000L

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Rolls the running total over at midnight. */
    private fun rollDay(c: Context) {
        val today = LocalDate.now().toString()
        val p = prefs(c)
        if (p.getString("day", null) != today) {
            p.edit().putString("day", today).putInt("total", 0).putInt("count", 0).commit()
        }
    }

    /** Records a successful log and flashes the widget; call after the Health Connect write succeeds. */
    suspend fun logged(c: Context, ml: Double, flash: Boolean = true) {
        rollDay(c)
        val p = prefs(c)
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        p.edit()
            .putInt("total", p.getInt("total", 0) + ml.toInt())
            .putInt("count", p.getInt("count", 0) + 1)
            .putInt("last", ml.toInt())
            .putString("time", time)
            .remove("error")
            .putBoolean("flash", flash)
            .commit()
        push(c)
        if (flash) {
            delay(FLASH_MS)
            prefs(c).edit().putBoolean("flash", false).commit()
            push(c)
        }
    }

    suspend fun failed(c: Context, message: String) {
        prefs(c).edit().putString("error", message.take(24)).putBoolean("flash", false).commit()
        push(c)
    }

    /** Re-anchors the total to Health Connect's own figure (all sources) when the app reads it. */
    suspend fun syncTotal(c: Context, todayMl: Double) {
        rollDay(c)
        prefs(c).edit().putInt("total", todayMl.toInt()).commit()
        push(c)
    }

    suspend fun push(c: Context) {
        val p = prefs(c)
        rollDay(c)
        val seq = p.getInt("seq", 0) + 1
        p.edit().putInt("seq", seq).commit()
        val ids = GlanceAppWidgetManager(c).getGlanceIds(WaterQuickLogWidget::class.java)
        for (id in ids) {
            updateAppWidgetState(c, id) { s ->
                s[totalMlKey] = p.getInt("total", 0)
                s[lastMlKey] = p.getInt("last", 0)
                s[lastTimeKey] = p.getString("time", "") ?: ""
                s[countKey] = p.getInt("count", 0)
                s[flashKey] = p.getBoolean("flash", false)
                s[errorKey] = p.getString("error", "") ?: ""
                s[seqKey] = seq
            }
            WaterQuickLogWidget().update(c, id)
        }
    }
}
