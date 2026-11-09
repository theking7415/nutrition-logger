package com.nutritionlogger.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.concurrent.futures.CallbackToFutureAdapter
import com.google.common.util.concurrent.ListenableFuture
import android.net.Uri
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import com.nutritionlogger.wear.TrampolineActivity
import com.nutritionlogger.wear.relay.LOG_PREFIX

private const val RESOURCES_VERSION = "2"

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

    /** Counts logs still queued on the watch (stored but not yet written by the phone). */
    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = CallbackToFutureAdapter.getFuture { completer ->
        Wearable.getDataClient(this)
            .getDataItems(Uri.parse("wear://*$LOG_PREFIX"), DataClient.FILTER_PREFIX)
            .addOnSuccessListener { buffer ->
                val pending = buffer.count
                buffer.release()
                completer.set(buildTile(pending))
            }
            .addOnFailureListener { completer.set(buildTile(0)) }
        "onTileRequest"
    }

    private fun buildTile(pending: Int): TileBuilders.Tile {
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

        val white = ColorBuilders.argb(0xFFFFFFFF.toInt())

        // One big circular button, nearly the full tile, instead of a text
        // band. The whole circle is the click target.
        val button = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.dp(150f))
            .setHeight(DimensionBuilders.dp(150f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(0xFF1B8A68.toInt()))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(DimensionBuilders.dp(75f))
                                    .build()
                            )
                            .build()
                    )
                    .setClickable(clickable)
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText("\uD83D\uDCA7")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(DimensionBuilders.sp(48f))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText("250 ml")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(DimensionBuilders.sp(20f))
                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                    .setColor(white)
                                    .build()
                            )
                            .build()
                    )
                    .also { column ->
                        if (pending > 0) {
                            column.addContent(
                                LayoutElementBuilders.Text.Builder()
                                    .setText("$pending waiting")
                                    .setFontStyle(
                                        LayoutElementBuilders.FontStyle.Builder()
                                            .setSize(DimensionBuilders.sp(13f))
                                            .setColor(ColorBuilders.argb(0xFFFFD27A.toInt()))
                                            .build()
                                    )
                                    .build()
                            )
                        }
                    }
                    .build()
            )
            .build()

        val layout = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(button)
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
            // While logs are waiting, re-render every minute so the count clears
            // once the phone has taken them.
            .setFreshnessIntervalMillis(if (pending > 0) 60_000L else 0L)
            .build()
        return tile
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
