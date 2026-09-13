package com.nutritionlogger.wear.relay

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

private const val TAG = "NutritionLogger.relay"

/** Path shared with the phone's PhoneRelayListenerService -- keep these in sync. */
const val LOG_WATER_PATH = "/nutrilogger/log-water"

/**
 * Sends a water amount to every connected phone over the Wearable Data Layer
 * API. This is the whole write path for the watch now -- see CLAUDE.md, "The
 * wear/watch integration", for why direct on-watch Health Connect writes were
 * tried (status 3, then status 1 on a newer library) and abandoned in favor
 * of this. Requires the phone app to be installed, permitted, and reachable;
 * that's an accepted limitation for a personal single-user setup, not
 * something to build delivery guarantees around.
 *
 * Throws if there are no connected nodes, rather than silently returning --
 * a bare for-loop over an empty list looks identical to success to the
 * caller otherwise, which is exactly what happened the first time this ran:
 * the UI reported "relayed OK" with zero nodes actually reachable.
 */
suspend fun relayLogWater(context: Context, volumeMl: Double) {
    Log.e(TAG, "relayLogWater: starting for ${volumeMl}ml")

    // Use CapabilityClient to find the phone app specifically.
    val capabilityInfo = runCatching {
        Wearable.getCapabilityClient(context)
            .getCapability("nutrition_logger_phone", CapabilityClient.FILTER_REACHABLE)
            .await()
    }.getOrNull()

    val nodes = capabilityInfo?.nodes ?: emptySet()
    Log.e(TAG, "relayLogWater: found ${nodes.size} nodes with capability")

    if (nodes.isEmpty()) {
        // Fallback to all connected nodes if capability search failed/returned empty
        Log.e(TAG, "relayLogWater: falling back to all connected nodes")
        val allNodes = Wearable.getNodeClient(context).connectedNodes.await()
        Log.e(TAG, "relayLogWater: found ${allNodes.size} total connected nodes")
        if (allNodes.isEmpty()) {
            throw IllegalStateException("No connected phone found")
        }

        val payload = volumeMl.toString().toByteArray(Charsets.UTF_8)
        for (node in allNodes) {
            Log.e(TAG, "relayLogWater: sending to all-node ${node.displayName} (${node.id})")
            Wearable.getMessageClient(context).sendMessage(node.id, LOG_WATER_PATH, payload).await()
            Log.e(TAG, "relayLogWater: send to all-node successful")
        }
    } else {
        val payload = volumeMl.toString().toByteArray(Charsets.UTF_8)
        for (node in nodes) {
            Log.e(TAG, "relayLogWater: sending to capability-node ${node.displayName} (${node.id})")
            Wearable.getMessageClient(context).sendMessage(node.id, LOG_WATER_PATH, payload).await()
            Log.e(TAG, "relayLogWater: send to capability-node successful")
        }
    }
}
