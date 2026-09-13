package com.nutritionlogger.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * All Health Connect interaction lives here.
 *
 * The important design decision is the client record ID. Each day gets a stable,
 * derived ID -- "nutrilogger-nutrition-2026-09-02" -- so re-syncing a day you have
 * already synced *replaces* that day's record instead of adding a second one.
 * Without this, screenshotting at lunch and again at dinner would double-count
 * the whole day. clientRecordVersion is the wall clock at write time, so a later
 * write always wins over an earlier one.
 *
 * Hydration does not follow that pattern. OCR sync never writes hydration at
 * all -- water is logged exclusively via logHydration(), called once per tap
 * from the in-app buttons, the home screen widget, or the watch tile. Each of
 * those calls is a genuine insert with a fresh UUID client ID, so repeated
 * taps accumulate instead of overwriting each other; Health Connect's own
 * daily aggregation produces the running total, not this app.
 */
class HealthConnectManager(private val context: Context) {

    val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class),
    )

    /** Default quick-log amount, used by the in-app button, the widget, and the watch tile. */
    companion object {
        const val DEFAULT_GLASS_ML = 250.0
    }

    /** One of HealthConnectClient.SDK_AVAILABLE / SDK_UNAVAILABLE / ..._PROVIDER_UPDATE_REQUIRED. */
    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    val isAvailable: Boolean
        get() = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun grantedPermissions(): Set<String> =
        client.permissionController.getGrantedPermissions()

    suspend fun hasAllPermissions(): Boolean =
        grantedPermissions().containsAll(permissions)

    /**
     * Upsert one day of totals. Returns a short human-readable summary of what
     * was written, for display back to the user.
     */
    suspend fun writeDay(totals: DailyTotals): String {
        if (totals.isEmpty) return "Nothing to write — every field was blank."

        val zone = ZoneId.systemDefault()
        val start = totals.date.atStartOfDay(zone).toInstant()

        // Health Connect rejects records that end in the future, so a mid-day
        // sync covers midnight..now and a later one extends the window.
        val dayEnd = totals.date.plusDays(1).atStartOfDay(zone).toInstant()
        val now = Instant.now()
        var end = if (dayEnd.isAfter(now)) now else dayEnd
        if (!end.isAfter(start)) end = start.plusSeconds(60)

        val startOffset = zone.rules.getOffset(start)
        val endOffset = zone.rules.getOffset(end)
        val version = System.currentTimeMillis()
        val key = totals.date.toString()

        val records = mutableListOf<Record>()

        if (totals.hasNutrition) {
            records += NutritionRecord(
                startTime = start,
                startZoneOffset = startOffset,
                endTime = end,
                endZoneOffset = endOffset,
                energy = totals.energyKcal?.let { Energy.kilocalories(it) },
                protein = totals.proteinG?.let { Mass.grams(it) },
                totalCarbohydrate = totals.carbsG?.let { Mass.grams(it) },
                totalFat = totals.fatG?.let { Mass.grams(it) },
                dietaryFiber = totals.fiberG?.let { Mass.grams(it) },
                name = "HealthifyMe daily total",
                mealType = MealType.MEAL_TYPE_UNKNOWN,
                metadata = metadataFor("nutrilogger-nutrition-$key", version),
            )
        }

        client.insertRecords(records)

        return "Wrote nutrition for $key."
    }

    /**
     * Logs one water-drinking event as its own HydrationRecord. Unlike writeDay(),
     * this is intentionally additive, not an upsert: every call gets a fresh
     * clientRecordId, so Health Connect keeps all of them and its own daily
     * aggregation produces the day total. Call this once per tap -- from the
     * in-app buttons, the home screen widget, or the watch tile.
     */
    suspend fun logHydration(volumeMl: Double, at: Instant = Instant.now()) {
        val zone = ZoneId.systemDefault()
        val start = at.minusSeconds(1)
        val id = "nutrilogger-hydration-${UUID.randomUUID()}"
        client.insertRecords(listOf(
            HydrationRecord(
                startTime = start,
                startZoneOffset = zone.rules.getOffset(start),
                endTime = at,
                endZoneOffset = zone.rules.getOffset(at),
                volume = Volume.milliliters(volumeMl),
                metadata = metadataFor(id, System.currentTimeMillis()),
            )
        ))
    }

    /** Read back what Health Connect holds for a day, from all source apps. */
    suspend fun readDay(date: LocalDate): DaySnapshot {
        val zone = ZoneId.systemDefault()
        val filter = TimeRangeFilter.between(
            date.atStartOfDay(zone).toInstant(),
            date.plusDays(1).atStartOfDay(zone).toInstant(),
        )

        val nutrition = client
            .readRecords(ReadRecordsRequest(recordType = NutritionRecord::class, timeRangeFilter = filter))
            .records
        val hydration = client
            .readRecords(ReadRecordsRequest(recordType = HydrationRecord::class, timeRangeFilter = filter))
            .records

        fun sum(select: (NutritionRecord) -> Double?): Double? {
            val values = nutrition.mapNotNull(select)
            return if (values.isEmpty()) null else values.sum()
        }

        return DaySnapshot(
            date = date,
            energyKcal = sum { it.energy?.inKilocalories },
            proteinG = sum { it.protein?.inGrams },
            carbsG = sum { it.totalCarbohydrate?.inGrams },
            fatG = sum { it.totalFat?.inGrams },
            fiberG = sum { it.dietaryFiber?.inGrams },
            waterMl = hydration.takeIf { it.isNotEmpty() }?.sumOf { it.volume.inMilliliters },
            nutritionRecords = nutrition.size,
            hydrationRecords = hydration.size,
        )
    }

    /**
     * Single place that builds Metadata, because this is the one API that moved
     * between Health Connect versions. On connect-client 1.1.0-alpha07 the
     * constructor is public. If you bump to 1.1.0 stable or later, this becomes:
     *
     *     Metadata.manualEntry(clientRecordId = clientId, clientRecordVersion = version)
     *
     * NOT Metadata.manualEntryWithId(...) — that overload takes an existing
     * Health-Connect-assigned UUID for updating a known record, not a fresh
     * client-supplied ID for a new insert; confirmed against 1.1.0's actual
     * Metadata.kt source after :wear's build first tried the wrong one.
     * Nothing else in the project needs to change.
     */
    private fun metadataFor(clientId: String, version: Long) = Metadata(
        clientRecordId = clientId,
        clientRecordVersion = version,
    )
}
