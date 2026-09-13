package com.nutritionlogger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.health.connect.client.HealthConnectClient
import com.nutritionlogger.health.HealthConnectManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    state: UiState,
    onPickImage: () -> Unit,
    onField: (Field, String) -> Unit,
    onShiftDate: (Long) -> Unit,
    onSetDate: (LocalDate) -> Unit,
    onWrite: () -> Unit,
    onClear: () -> Unit,
    onLogWater: (Double) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenHealthConnect: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Nutrition Logger") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(state, onRequestPermissions, onOpenHealthConnect)

            WaterQuickLog(date = state.date, existingMl = state.existing?.waterMl, onLogWater = onLogWater)

            DateRow(state.date, onShiftDate, onSetDate)

            ExistingCard(state)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickImage, modifier = Modifier.weight(1f)) {
                    Text("Pick screenshot")
                }
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Text("Clear")
                }
            }

            Text(
                "Leave a field blank to skip it. A blank field is left untouched in " +
                    "Health Connect rather than written as zero.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            NumberField("Energy", "kcal", state.energy) { onField(Field.ENERGY, it) }
            NumberField("Protein", "g", state.protein) { onField(Field.PROTEIN, it) }
            NumberField("Carbs", "g", state.carbs) { onField(Field.CARBS, it) }
            NumberField("Fat", "g", state.fat) { onField(Field.FAT, it) }
            NumberField("Fibre", "g", state.fiber) { onField(Field.FIBER, it) }

            Button(
                onClick = onWrite,
                enabled = !state.busy && state.permissionsGranted && state.hasAnything,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Write to Health Connect")
            }

            if (state.busy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.message?.let { message ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(message, modifier = Modifier.padding(12.dp))
                }
            }

            if (state.ocrLines.isNotEmpty()) {
                OcrDump(state.ocrLines)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusCard(
    state: UiState,
    onRequestPermissions: () -> Unit,
    onOpenHealthConnect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state.sdkStatus) {
                HealthConnectClient.SDK_UNAVAILABLE -> {
                    Text("Health Connect is not available on this device.")
                    Text(
                        "On Android 13 and below it installs from the Play Store; " +
                            "on Android 14+ it is part of the system.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                    Text("Health Connect needs updating before this app can write to it.")
                    TextButton(onClick = onOpenHealthConnect) { Text("Open Health Connect") }
                }
                else -> {
                    if (state.permissionsGranted) {
                        Text("Connected. Nutrition and hydration permissions granted.")
                        TextButton(onClick = onOpenHealthConnect) { Text("Open Health Connect") }
                    } else {
                        Text("Health Connect is available but has not granted permissions yet.")
                        Button(onClick = onRequestPermissions) { Text("Grant permissions") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(date: LocalDate, onShiftDate: (Long) -> Unit, onSetDate: (LocalDate) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onShiftDate(-1) }) { Text("‹ Prev") }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { showPicker = true },
        ) {
            Text(date.format(DATE_FORMAT), style = MaterialTheme.typography.titleMedium)
            if (date == LocalDate.now()) {
                Text("today", style = MaterialTheme.typography.labelSmall)
            } else {
                Text("tap to change", style = MaterialTheme.typography.labelSmall)
            }
        }
        TextButton(
            onClick = { onShiftDate(1) },
            enabled = date.isBefore(LocalDate.now()),
        ) { Text("Next ›") }
    }

    if (showPicker) {
        // Photos are shared without a date attached, and are not always
        // uploaded same-day — this lets you target the right day directly
        // instead of stepping Prev/Next one day at a time.
        val zone = ZoneId.systemDefault()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onSetDate(Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun WaterQuickLog(date: LocalDate, existingMl: Double?, onLogWater: (Double) -> Unit) {
    var customAmount by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Log water", style = MaterialTheme.typography.titleSmall)
            Text(
                "Always logs right now — independent of the date selected below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(HealthConnectManager.DEFAULT_GLASS_ML, 500.0, 750.0).forEach { ml ->
                    FilterChip(
                        selected = false,
                        onClick = { onLogWater(ml) },
                        label = { Text("${ml.toInt()} ml") },
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = customAmount,
                    onValueChange = { customAmount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Custom") },
                    suffix = { Text("ml") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        customAmount.toDoubleOrNull()?.let { onLogWater(it) }
                        customAmount = ""
                    },
                    enabled = customAmount.toDoubleOrNull() != null,
                ) { Text("Log") }
            }
            val dayLabel = if (date == LocalDate.now()) "today" else "on ${date.format(DATE_FORMAT)}"
            Text(
                existingMl?.let { "So far $dayLabel: ${it.toInt()} ml" } ?: "Nothing logged $dayLabel yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExistingCard(state: UiState) {
    val existing = state.existing ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Already in Health Connect", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            if (existing.isEmpty) {
                Text("Nothing recorded for this day yet.")
            } else {
                val summary = listOfNotNull(
                    existing.energyKcal?.let { "${it.toInt()} kcal" },
                    existing.proteinG?.let { "P ${it.toInt()}g" },
                    existing.carbsG?.let { "C ${it.toInt()}g" },
                    existing.fatG?.let { "F ${it.toInt()}g" },
                    existing.fiberG?.let { "Fib ${it.toInt()}g" },
                    existing.waterMl?.let { "${it.toInt()} ml water" },
                ).joinToString(" · ")
                Text(summary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${existing.nutritionRecords} nutrition and " +
                        "${existing.hydrationRecords} water record(s), across all apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NumberField(label: String, unit: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        suffix = { Text(unit) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The raw OCR text. This is the debugging surface: when a field comes out wrong,
 * this shows exactly what the recogniser saw, which tells you what to add to the
 * keyword lists in ScreenshotParser.
 */
@Composable
private fun OcrDump(lines: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide OCR text" else "Show OCR text (${lines.size} lines)")
            }
            if (expanded) {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                lines.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
