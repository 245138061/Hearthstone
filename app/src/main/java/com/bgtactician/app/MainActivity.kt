package com.bgtactician.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgtactician.app.overlay.OverlayPermissionHelper
import com.bgtactician.app.overlay.OverlayService
import com.bgtactician.app.ui.screen.HomeScreen
import com.bgtactician.app.ui.theme.BGTacticianTheme
import com.bgtactician.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BGTacticianTheme {
                MainRoute(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun MainRoute(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val overlayRunning by OverlayService.isRunning.collectAsStateWithLifecycle()

    var overlayPermissionGranted by remember {
        mutableStateOf(OverlayPermissionHelper.canDrawOverlays(context))
    }
    var notificationGranted by remember {
        mutableStateOf(OverlayPermissionHelper.hasNotificationPermission(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationGranted = granted
    }

    DisposableEffect(context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayPermissionGranted = OverlayPermissionHelper.canDrawOverlays(context)
                notificationGranted = OverlayPermissionHelper.hasNotificationPermission(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.load(context.applicationContext)
    }

    HomeScreen(
        uiState = uiState,
        overlayPermissionGranted = overlayPermissionGranted,
        notificationGranted = notificationGranted,
        overlayRunning = overlayRunning,
        onRequestOverlayPermission = {
            OverlayPermissionHelper.openOverlaySettings(context)
        },
        onRequestNotificationPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onToggleOverlay = {
            if (!overlayPermissionGranted) {
                OverlayPermissionHelper.openOverlaySettings(context)
            } else if (overlayRunning) {
                OverlayService.stop(context)
            } else {
                OverlayService.start(context)
            }
        },
        onToggleTribe = viewModel::toggleTribe,
        onSelectAnomaly = viewModel::selectAnomaly,
        onSetDuos = viewModel::setDuosMode,
        onSelectStrategy = viewModel::selectStrategy,
        onManifestUrlOverrideChange = viewModel::updateManifestUrlOverride,
        onRefreshCatalog = { viewModel.refreshCatalog() },
        onSetOverlayInteractionEnabled = { enabled ->
            viewModel.setOverlayInteractionEnabled(enabled)
            if (overlayRunning) {
                OverlayService.refreshSettings(context)
            }
        },
        onSetBubbleOpacityPercent = { value ->
            viewModel.setBubbleOpacityPercent(value)
            if (overlayRunning) {
                OverlayService.refreshSettings(context)
            }
        }
    )
}
