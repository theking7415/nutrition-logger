package com.nutritionlogger.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.concurrent.futures.CallbackToFutureAdapter
import com.google.common.util.concurrent.ListenableFuture
import com.nutritionlogger.wear.TrampolineActivity

private const val RESOURCES_VERSION = "1"

/**
 * Single-button tile: tap logs 250ml. A Tile's Clickable can only carry a
 * LaunchAction (open an intent) or a LoadAction (re-render the tile) -- there
 * is no "run this code directly" primitive -- so the click launches
 * TrampolineActivity, which does the actual relay-to-phone call and finishes
 * immediately. See CLAUDE.md, "The wear/watch integration", for why this
 * relays to the phone rather than writing to Health Connect directly.
 *
 * NOTE: this hasn't been built/run on-device yet (see CLAUDE.md -- nothing
 * gets claimed as verified without a real compile). `ListenableFuture` here
 * comes from the lightweight `com.google.guava:listenablefuture` stub that
 * `androidx.wear.tiles:tiles` actually depends on (just the interface, no
 * `Futures` utility class). Use `CallbackToFutureAdapter.getFuture { ... }`
 * to build one -- NOT `ResolvableFuture.create()`, even though that's what
 * TileService's own internal code uses: ResolvableFuture is
 * `@RestrictTo(LIBRARY_GROUP_PREFIX)`, meaning only code inside the
 * `androidx` group is allowed to call it. TileService can; this app can't --
 * the build fails with "can only be called from within the same library
 * group prefix". CallbackToFutureAdapter is the actual public-facing API for
 * exactly this. Confirmed by reading concurrent-futures' own source after
 * this restriction surfaced at compile time.
 */
class WaterTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("log_water")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(TrampolineActivity::class.java.name)
                            .build()
                    )
                    .build()
            )
            .build()

        val layout = LayoutElementBuilders.Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("💧 Log 250ml")
                    .build()
            )
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(layout).build())
                            .build()
                    )
                    .build()
            )
            .build()

        return CallbackToFutureAdapter.getFuture { completer ->
            completer.set(tile)
            "onTileRequest"
        }
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        return CallbackToFutureAdapter.getFuture { completer ->
            completer.set(resources)
            "onTileResourcesRequest"
        }
    }
}
