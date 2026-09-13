package com.nutritionlogger.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Pinned against a real "Your Calorie Budget" screenshot:
 *
 *     1,674 / 2,000 Cal
 *     Proteins  90.7 g/150.0 g   60%
 *     Fats      66.9 g/66.7 g   100%
 *     Carbs    184.0 g/200.0 g   92%
 *     Fibre     21.9 g/30.0 g    73%
 *
 * The cases below are the ways OCR can plausibly mangle that card. All of them
 * must still yield the consumed values, never the goals and never the
 * percentages. Runs on the JVM — no device needed.
 */
class ScreenshotParserTest {

    private val date: LocalDate = LocalDate.of(2026, 9, 2)

    private fun assertExpected(rows: List<String>) {
        val t = ScreenshotParser.extract(rows, date)
        assertEquals(1674.0, t.energyKcal!!, 0.001)
        assertEquals(90.7, t.proteinG!!, 0.001)
        assertEquals(66.9, t.fatG!!, 0.001)
        assertEquals(184.0, t.carbsG!!, 0.001)
        assertEquals(21.9, t.fiberG!!, 0.001)
    }

    @Test
    fun `reads the card when rows are reconstructed cleanly`() = assertExpected(
        listOf(
            "Your Calorie Budget",
            "84%",
            "1,674 / 2,000 Cal",
            "Keep it up! Your calorie intake is perfectly",
            "balanced!",
            "Macronutrients Breakup",
            "Proteins  90.7 g/150.0 g  60%",
            "Fats  66.9 g/66.7 g  100%",
            "Carbs  184.0 g/200.0 g  92%",
            "Fibre  21.9 g/30.0 g  73%",
        )
    )

    /** The big "84%" sits close enough to the calorie line to be merged into it. */
    @Test
    fun `ignores the percentage when it lands on the calorie row`() = assertExpected(
        listOf(
            "Your Calorie Budget",
            "1,674 / 2,000 Cal  84%",
            "Macronutrients Breakup",
            "Proteins  90.7 g/150.0 g  60%",
            "Fats  66.9 g/66.7 g  100%",
            "Carbs  184.0 g/200.0 g  92%",
            "Fibre  21.9 g/30.0 g  73%",
        )
    )

    /** Worst case: labels and values stay in separate columns, never regrouped. */
    @Test
    fun `falls back to on-screen order when labels are detached from values`() = assertExpected(
        listOf(
            "Your Calorie Budget", "84%", "1,674 / 2,000 Cal", "Macronutrients Breakup",
            "Proteins", "Fats", "Carbs", "Fibre",
            "90.7 g/150.0 g", "66.9 g/66.7 g", "184.0 g/200.0 g", "21.9 g/30.0 g",
            "60%", "100%", "92%", "73%",
        )
    )

    @Test
    fun `handles missing spaces around the slash`() = assertExpected(
        listOf(
            "1,674/2,000 Cal",
            "Proteins 90.7g/150.0g 60%",
            "Fats 66.9g/66.7g 100%",
            "Carbs 184.0g/200.0g 92%",
            "Fibre 21.9g/30.0g 73%",
        )
    )

    /** The heading sits directly above the number and must not swallow the search. */
    @Test
    fun `does not read the goal from the Your Calorie Budget heading`() {
        val t = ScreenshotParser.extract(listOf("Your Calorie Budget", "1,674 / 2,000 Cal"), date)
        assertEquals(1674.0, t.energyKcal!!, 0.001)
    }

    /** A value that is absent must stay absent — never be written as zero. */
    @Test
    fun `leaves unmatched fields null`() {
        val t = ScreenshotParser.extract(listOf("Macronutrients Breakup"), date)
        assertNull(t.energyKcal)
        assertNull(t.proteinG)
        assertEquals(true, t.isEmpty)
    }
}
