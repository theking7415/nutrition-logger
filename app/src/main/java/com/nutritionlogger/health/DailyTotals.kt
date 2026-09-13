package com.nutritionlogger.health

import java.time.LocalDate

/**
 * One day's worth of intake, as read off a HealthifyMe daily-summary screenshot.
 *
 * Every field is nullable on purpose: OCR routinely misses one, and a missing
 * value must stay missing rather than being written to Health Connect as zero.
 * A zero would be a lie — it says "you ate no fibre today", which then drags
 * down every weekly average that reads it.
 */
data class DailyTotals(
    val date: LocalDate,
    val energyKcal: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val fiberG: Double? = null,
) {
    val hasNutrition: Boolean
        get() = listOfNotNull(energyKcal, proteinG, carbsG, fatG, fiberG).isNotEmpty()

    val isEmpty: Boolean
        get() = !hasNutrition
}

/** What Health Connect currently holds for a day, for the "already synced?" line. */
data class DaySnapshot(
    val date: LocalDate,
    val energyKcal: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fiberG: Double?,
    val waterMl: Double?,
    val nutritionRecords: Int,
    val hydrationRecords: Int,
) {
    val isEmpty: Boolean get() = nutritionRecords == 0 && hydrationRecords == 0
}
