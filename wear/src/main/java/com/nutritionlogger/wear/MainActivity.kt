package com.nutritionlogger.wear

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.nutritionlogger.wear.relay.relayLogWater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Main watch interaction: a single full-screen button to log water.
 * Relays the call to the phone app (see relay/PhoneRelayClient.kt).
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by viewModel.state.collectAsState()
                MainScreen(state = state, onLogWater = viewModel::logWater)
            }
        }
    }
}

data class MainState(val status: String = "Log 250ml")

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    fun logWater() {
        viewModelScope.launch {
            _state.update { it.copy(status = "Relaying...") }
            runCatching { relayLogWater(getApplication(), 250.0) }
                .onSuccess { _state.update { it.copy(status = "Relayed OK") } }
                .onFailure { e -> _state.update { it.copy(status = "Failed: ${e.message?.take(10)}") } }
        }
    }
}

@Composable
private fun MainScreen(state: MainState, onLogWater: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Full-screen large circular button
        Button(
            onClick = onLogWater,
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = MaterialTheme.colors.surface
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "💧",
                    fontSize = 48.sp
                )
            }
        }
        
        // Status text overlay at the bottom
        Text(
            text = state.status,
            style = MaterialTheme.typography.caption2,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
        )
    }
}
