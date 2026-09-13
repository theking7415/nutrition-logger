package com.nutritionlogger.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.nutritionlogger.wear.relay.relayLogWater
import kotlinx.coroutines.launch

/**
 * The only way a Wear Tile's Clickable can trigger real work: Tiles support
 * LaunchAction (open an intent) or LoadAction (re-render the tile) and
 * nothing else, so WaterTileService points its button here instead of
 * running the relay call itself. Theme.Translucent.NoTitleBar plus
 * excludeFromRecents/noHistory in the manifest keep this from reading as
 * "an app opened" -- the tap should feel like it hit the tile directly.
 *
 * Unverified on-device whether this is fully imperceptible on this specific
 * Wear/Android build (per CLAUDE.md, treat that watch's exact behavior as
 * unconfirmed until seen) -- but this is the standard, well-established
 * pattern for action tiles (flashlight, DND toggles), so it's the committed
 * approach regardless of how it looks on first run.
 */
class TrampolineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            runCatching { relayLogWater(applicationContext, DEFAULT_LOG_ML) }
            finish()
        }
    }

    companion object {
        private const val DEFAULT_LOG_ML = 250.0
    }
}
