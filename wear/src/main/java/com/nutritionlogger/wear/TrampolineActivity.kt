package com.nutritionlogger.wear

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.Wearable
import com.nutritionlogger.wear.relay.relayLogWater
import com.nutritionlogger.wear.tile.WaterTileService
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay

/**
 * The only way a Wear Tile's Clickable can trigger real work: Tiles support
 * LaunchAction (open an intent) or LoadAction (re-render the tile) and
 * nothing else, so WaterTileService points its button here instead of
 * running the relay call itself.
 *
 * Rather than hiding, this now doubles as the tap feedback: the circle pops
 * in with a bouncy spring, a ring pulses outward, the watch gives a haptic
 * click, and the circle turns into a check mark (or a red cross if the phone
 * was unreachable, which was previously completely silent). It finishes
 * itself after about a second.
 */
class TrampolineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var result by remember { mutableStateOf<Outcome?>(null) }
                LaunchedEffect(Unit) {
                    val stored = runCatching { relayLogWater(applicationContext, DEFAULT_LOG_ML) }.isSuccess
                    // The log is safe on the watch either way; this only decides
                    // whether to say it already reached the phone or is waiting.
                    val phoneNear = stored && runCatching {
                        Wearable.getNodeClient(applicationContext).connectedNodes.await().isNotEmpty()
                    }.getOrDefault(false)
                    val outcome = when {
                        !stored -> Outcome.FAILED
                        phoneNear -> Outcome.SENT
                        else -> Outcome.QUEUED
                    }
                    result = outcome
                    haptic(outcome != Outcome.FAILED)
                    // Refresh the tile so its "waiting" count is current.
                    runCatching {
                        TileService.getUpdater(applicationContext).requestUpdate(WaterTileService::class.java)
                    }
                    delay(if (outcome == Outcome.QUEUED) 1500 else 1000)
                    finish()
                }
                Feedback(result, DEFAULT_LOG_ML.toInt())
            }
        }
    }

    private fun haptic(ok: Boolean) {
        val effect = if (ok) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_DOUBLE_CLICK
        getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createPredefined(effect))
    }

    companion object {
        private const val DEFAULT_LOG_ML = 250.0
    }
}

private enum class Outcome { SENT, QUEUED, FAILED }

@Composable
private fun Feedback(result: Outcome?, ml: Int) {
    val color by animateColorAsState(
        when (result) {
            null -> Color(0xFF1B8A68)
            Outcome.SENT -> Color(0xFF2EBD85)
            Outcome.QUEUED -> Color(0xFFD99A1E)
            Outcome.FAILED -> Color(0xFFC0392B)
        },
        tween(150),
        label = "color",
    )
    val scale by animateFloatAsState(
        if (result == null) 0.9f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "scale",
    )
    // Ring that expands and fades once the result lands.
    val ring by animateFloatAsState(if (result == null) 1f else 1.4f, tween(600), label = "ring")

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(150.dp).scale(ring).alpha(if (result == null) 0f else 0.5f - ringFade(ring)).background(color, CircleShape),
        )
        Box(Modifier.size(150.dp).scale(scale).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (result) {
                        Outcome.FAILED -> "✕"
                        Outcome.SENT, Outcome.QUEUED -> "✓"
                        null -> "💧"
                    },
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    when (result) {
                        Outcome.FAILED -> "Couldn't\nsave"
                        Outcome.QUEUED -> "+$ml ml\nSaved, will\nsync later"
                        else -> "+$ml ml"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

/** Maps the ring's 1.0..1.4 scale onto a 0..0.5 alpha drop so it fades as it grows. */
private fun ringFade(scale: Float): Float = ((scale - 1f) / 0.4f).coerceIn(0f, 1f) * 0.5f
