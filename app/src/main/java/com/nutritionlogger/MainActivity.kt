package com.nutritionlogger

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.health.connect.client.PermissionController
import com.nutritionlogger.ui.NutritionLoggerTheme
import com.nutritionlogger.ui.SyncScreen
import com.nutritionlogger.ui.SyncViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SyncViewModel by viewModels()

    private val requestPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            viewModel.onPermissionsResult(granted)
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { viewModel.loadImage(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NutritionLoggerTheme {
                val state by viewModel.state.collectAsState()
                SyncScreen(
                    state = state,
                    onPickImage = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onField = viewModel::setField,
                    onShiftDate = viewModel::shiftDate,
                    onSetDate = viewModel::setDate,
                    onWrite = viewModel::write,
                    onClear = viewModel::clearForm,
                    onLogWater = viewModel::logWater,
                    onRequestPermissions = { requestPermissions.launch(viewModel.permissions) },
                    onOpenHealthConnect = ::openHealthConnect,
                )
            }
        }

        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    override fun onResume() {
        super.onResume()
        // Permissions can be revoked from the Health Connect settings screen at
        // any time, so re-check on every return to the app rather than caching.
        viewModel.refresh()
    }

    /** Entry point for "share a HealthifyMe screenshot to Nutrition Logger". */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("image/") != true) return

        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        uri?.let { viewModel.loadImage(it) }
    }

    private fun openHealthConnect() {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            "android.health.connect.action.HEALTH_HOME_SETTINGS"
        } else {
            "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
        }
        runCatching { startActivity(Intent(action)) }
    }
}
