package com.nutritionlogger.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.health.connect.client.HealthConnectClient
import com.nutritionlogger.health.DailyTotals
import com.nutritionlogger.health.DaySnapshot
import com.nutritionlogger.health.HealthConnectManager
import com.nutritionlogger.ocr.ScreenshotParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

data class UiState(
    val sdkStatus: Int = HealthConnectClient.SDK_UNAVAILABLE,
    val permissionsGranted: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val energy: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val fiber: String = "",
    val imageUri: Uri? = null,
    val ocrLines: List<String> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val existing: DaySnapshot? = null,
) {
    val hasAnything: Boolean
        get() = listOf(energy, protein, carbs, fat, fiber).any { it.isNotBlank() }
}

enum class Field { ENERGY, PROTEIN, CARBS, FAT, FIBER }

class SyncViewModel(app: Application) : AndroidViewModel(app) {

    private val health = HealthConnectManager(app)

    private val _state = MutableStateFlow(UiState(sdkStatus = health.sdkStatus()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    val permissions: Set<String> get() = health.permissions

    fun refresh() {
        val status = health.sdkStatus()
        _state.update { it.copy(sdkStatus = status) }
        if (status != HealthConnectClient.SDK_AVAILABLE) return
        viewModelScope.launch {
            val granted = runCatching { health.hasAllPermissions() }.getOrDefault(false)
            _state.update { it.copy(permissionsGranted = granted) }
            if (granted) loadExisting()
        }
    }

    fun setField(field: Field, value: String) {
        // Keep the field permissive while typing; parsing happens on write.
        val cleaned = value.filter { it.isDigit() || it == '.' }
        _state.update {
            when (field) {
                Field.ENERGY -> it.copy(energy = cleaned)
                Field.PROTEIN -> it.copy(protein = cleaned)
                Field.CARBS -> it.copy(carbs = cleaned)
                Field.FAT -> it.copy(fat = cleaned)
                Field.FIBER -> it.copy(fiber = cleaned)
            }
        }
    }

    fun shiftDate(days: Long) {
        setDate(_state.value.date.plusDays(days))
    }

    /** Jump straight to a date, e.g. from a date picker, rather than stepping day by day. */
    fun setDate(date: LocalDate) {
        _state.update { it.copy(date = date, message = null) }
        loadExisting()
        // Re-run OCR against the new date so the parsed record targets it.
        _state.value.imageUri?.let { loadImage(it) }
    }

    fun clearForm() {
        _state.update {
            it.copy(
                energy = "", protein = "", carbs = "", fat = "", fiber = "",
                imageUri = null, ocrLines = emptyList(), message = null,
            )
        }
    }

    /** Logs one water event immediately; independent of the OCR form/date above. */
    fun logWater(ml: Double) {
        viewModelScope.launch {
            runCatching { health.logHydration(ml) }
                .onSuccess {
                    _state.update { it.copy(message = "Logged ${ml.toInt()} ml of water.") }
                    loadExisting()
                }
                .onFailure { error ->
                    _state.update { it.copy(message = "Water log failed: ${error.message}") }
                }
        }
    }

    fun loadImage(uri: Uri) {
        _state.update { it.copy(busy = true, imageUri = uri, message = null) }
        viewModelScope.launch {
            val result = runCatching {
                ScreenshotParser.parse(getApplication(), uri, _state.value.date)
            }
            result.onSuccess { parsed ->
                val t = parsed.totals
                _state.update {
                    it.copy(
                        busy = false,
                        ocrLines = parsed.lines,
                        energy = t.energyKcal.toField(),
                        protein = t.proteinG.toField(),
                        carbs = t.carbsG.toField(),
                        fat = t.fatG.toField(),
                        fiber = t.fiberG.toField(),
                        message = if (t.isEmpty) {
                            "Read the image but matched no values — check the OCR text below and fix the fields by hand."
                        } else {
                            "Read the screenshot. Check every value before writing."
                        },
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(busy = false, message = "Could not read that image: ${error.message}")
                }
            }
        }
    }

    fun write() {
        val s = _state.value
        val totals = DailyTotals(
            date = s.date,
            energyKcal = s.energy.toValue(),
            proteinG = s.protein.toValue(),
            carbsG = s.carbs.toValue(),
            fatG = s.fat.toValue(),
            fiberG = s.fiber.toValue(),
        )
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            runCatching { health.writeDay(totals) }
                .onSuccess { summary ->
                    _state.update { it.copy(busy = false, message = summary) }
                    loadExisting()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(busy = false, message = "Write failed: ${error.message}")
                    }
                }
        }
    }

    fun onPermissionsResult(granted: Set<String>) {
        val ok = granted.containsAll(health.permissions)
        _state.update { it.copy(permissionsGranted = ok) }
        if (ok) loadExisting()
    }

    private fun loadExisting() {
        val date = _state.value.date
        viewModelScope.launch {
            runCatching { health.readDay(date) }
                .onSuccess { snapshot -> _state.update { it.copy(existing = snapshot) } }
                .onFailure { _state.update { it.copy(existing = null) } }
        }
    }

    /** Blank stays blank — a missing value must never be written as zero. */
    private fun String.toValue(): Double? = trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    private fun Double?.toField(): String {
        val value = this ?: return ""
        return if (value == Math.floor(value)) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }
}
