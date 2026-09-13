package com.nutritionlogger.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.nutritionlogger.health.DailyTotals
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import kotlin.math.abs

data class ParseResult(
    /** Reconstructed rows, shown in the UI so you can see what the recogniser saw. */
    val lines: List<String>,
    val totals: DailyTotals,
)

/**
 * Pulls daily totals off a HealthifyMe "Your Calorie Budget" screenshot.
 *
 * Two facts about that screen drive the whole design:
 *
 *  1. Every value is a **consumed / goal pair** — "1,674 / 2,000 Cal",
 *     "90.7 g/150.0 g". We always want the left-hand number. Matching the pair
 *     as a unit is far safer than grabbing the first number on a line, because
 *     the row also carries a percentage ("60%") that would otherwise win.
 *
 *  2. It is a **column layout**. ML Kit groups text by proximity, so the labels
 *     (Proteins / Fats / Carbs / Fibre) usually come back as one block and the
 *     values as a separate block — the label and its number are not adjacent in
 *     the raw line list at all. [reconstructRows] fixes this by throwing away
 *     ML Kit's blocks and regrouping every line by its vertical position, which
 *     rebuilds "Proteins  90.7 g/150.0 g  60%" as a single row.
 *
 * Output is still only a suggestion. Everything lands in an editable field.
 */
object ScreenshotParser {

    private const val NUM = """\d[\d,]*(?:\.\d+)?"""

    /**
     * A consumed/goal pair, capturing the consumed side. The optional unit
     * between the number and the slash covers "90.7 g/150.0 g"; its absence
     * covers "1,674 / 2,000 Cal".
     */
    private val PAIR = Regex("""($NUM)\s*(?:g|ml|kcal|cal)?\s*/\s*$NUM""", RegexOption.IGNORE_CASE)

    /** Strictly a gram pair, used by the positional fallback. */
    private val GRAM_PAIR = Regex("""($NUM)\s*g\s*/\s*$NUM\s*g""", RegexOption.IGNORE_CASE)

    /** A bare value with a unit, for screens that show no goal alongside. */
    private val SINGLE = Regex("""($NUM)\s*(g|ml|l|kcal|cal)\b""", RegexOption.IGNORE_CASE)

    /** Percentages are noise on every row here — removed before any fallback. */
    private val PERCENT = Regex("""$NUM\s*%""")

    private val ENERGY = Regex("""\b(cal|cals|calorie|calories|kcal|energy)\b""", RegexOption.IGNORE_CASE)
    private val PROTEIN = Regex("""\bproteins?\b""", RegexOption.IGNORE_CASE)
    private val FAT = Regex("""\bfats?\b""", RegexOption.IGNORE_CASE)
    private val CARBS = Regex("""\bcarb(s|ohydrates?)?\b""", RegexOption.IGNORE_CASE)
    private val FIBER = Regex("""\bfib(re|er)s?\b""", RegexOption.IGNORE_CASE)

    /**
     * Rows that talk about the target rather than the intake. "Your Calorie
     * Budget" is the heading directly above the number we want, so without this
     * the heading would match first and swallow the search.
     */
    private val EXCLUDE = Regex(
        "goal|target|remaining|budget|allowance|recommended|burnt?\\b",
        RegexOption.IGNORE_CASE,
    )

    suspend fun parse(context: Context, uri: Uri, date: LocalDate): ParseResult {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val text = try {
            recognizer.process(image).await()
        } finally {
            recognizer.close()
        }
        val rows = reconstructRows(text)
        return ParseResult(rows, extract(rows, date))
    }

    /**
     * Pure extraction, split out from [parse] so it can be unit tested against
     * literal rows without an image or an Android device.
     */
    fun extract(rows: List<String>, date: LocalDate): DailyTotals {
        var protein = valueFor(rows, PROTEIN)
        var fat = valueFor(rows, FAT)
        var carbs = valueFor(rows, CARBS)
        var fiber = valueFor(rows, FIBER)

        // Positional fallback. If the labels were unreadable but four gram pairs
        // are present, they are the macro rows in HealthifyMe's fixed on-screen
        // order. Only applied when *nothing* matched by label, so a partial
        // label match can never be silently overwritten by a positional guess.
        if (protein == null && fat == null && carbs == null && fiber == null) {
            val pairs = rows.flatMap { row -> GRAM_PAIR.findAll(row).map { it.groupValues[1] } }
                .mapNotNull { it.toNumber() }
            if (pairs.size == 4) {
                protein = pairs[0]
                fat = pairs[1]
                carbs = pairs[2]
                fiber = pairs[3]
            }
        }

        return DailyTotals(
            date = date,
            energyKcal = valueFor(rows, ENERGY),
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
            fiberG = fiber,
        )
    }

    /** First number on the first non-excluded row mentioning [label]. */
    private fun valueFor(rows: List<String>, label: Regex): Double? =
        rows.firstNotNullOfOrNull { row ->
            if (!label.containsMatchIn(row) || EXCLUDE.containsMatchIn(row)) null else numberIn(row)
        }

    /**
     * Preference order matters: a consumed/goal pair beats a single value, and
     * both beat whatever is left after percentages are stripped out.
     */
    private fun numberIn(row: String): Double? {
        PAIR.find(row)?.let { return it.groupValues[1].toNumber() }
        val withoutPercent = PERCENT.replace(row, " ")
        SINGLE.find(withoutPercent)?.let { return it.groupValues[1].toNumber() }
        return Regex("($NUM)").find(withoutPercent)?.groupValues?.get(1)?.toNumber()
    }

    private fun String.toNumber(): Double? = replace(",", "").toDoubleOrNull()

    /**
     * Regroups every recognised line by vertical position, discarding ML Kit's
     * own block grouping.
     *
     * This is what makes the macro table readable: ML Kit sees the label column
     * and the value column as separate blocks, so in its own ordering "Proteins"
     * and "90.7 g/150.0 g" can be many entries apart. Bucketing by row puts them
     * back together, left to right, the way they appear on screen.
     */
    private fun reconstructRows(text: Text): List<String> {
        val items = text.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                val content = line.text.trim()
                if (content.isEmpty()) null else Positioned(content, box.top, box.bottom, box.left)
            }

        // No bounding boxes means no positional information to work with; fall
        // back to ML Kit's own ordering rather than returning nothing.
        if (items.isEmpty()) {
            return text.textBlocks.flatMap { it.lines }.map { it.text.trim() }.filter { it.isNotEmpty() }
        }

        // Half the median glyph height: tall enough to absorb baseline jitter
        // across a row, short enough not to merge two stacked rows.
        val heights = items.map { it.height }.sorted()
        val tolerance = (heights[heights.size / 2] * 0.5).toInt().coerceAtLeast(8)

        val rows = mutableListOf<MutableList<Positioned>>()
        for (item in items.sortedBy { it.centerY }) {
            val current = rows.lastOrNull()
            if (current != null && abs(item.centerY - current.first().centerY) <= tolerance) {
                current += item
            } else {
                rows += mutableListOf(item)
            }
        }

        return rows.map { row -> row.sortedBy { it.left }.joinToString("  ") { it.text } }
    }

    private data class Positioned(val text: String, val top: Int, val bottom: Int, val left: Int) {
        val centerY: Int get() = (top + bottom) / 2
        val height: Int get() = bottom - top
    }
}
