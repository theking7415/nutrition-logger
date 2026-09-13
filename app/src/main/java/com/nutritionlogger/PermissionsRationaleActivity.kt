package com.nutritionlogger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nutritionlogger.ui.NutritionLoggerTheme

/**
 * Health Connect requires every app that asks for health permissions to offer a
 * screen explaining why. It is reachable from the Health Connect settings UI.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NutritionLoggerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("How Nutrition Logger uses your data", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "HealthifyMe does not write anything to Health Connect. " +
                                "Nutrition Logger copies the daily totals you have already logged " +
                                "there — energy, protein, carbs, fat and fibre — into " +
                                "Health Connect so they sit alongside the rest of your fitness data."
                        )
                        Text(
                            "Write access to Nutrition is used to record those daily totals. " +
                                "Write access to Hydration is used only for water you log " +
                                "directly in this app — via the quick-log buttons, the home " +
                                "screen widget, or the watch tile — never from HealthifyMe. " +
                                "Read access is used only to show you what a day already " +
                                "contains, so you can tell whether it has been synced."
                        )
                        Text(
                            "Everything happens on this device. The text recognition model is " +
                                "bundled into the app, so screenshots are never uploaded and " +
                                "nothing you log leaves the phone."
                        )
                    }
                }
            }
        }
    }
}
