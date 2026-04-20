package com.bgtactician.app

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
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
import com.bgtactician.app.autodetect.ScreenCapturePermissionStore
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
    val screenCaptureGranted by ScreenCapturePermissionStore.isGranted.collectAsStateWithLifecycle()

    var overlayPermissionGranted by remember {
        mutableStateOf(OverlayPermissionHelper.canDrawOverlays(context))
    }
    val mediaProjectionManager = remember(context) {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenCapturePermissionStore.update(result.resultCode, data)
            if (overlayRunning) {
                OverlayService.updateCapturePermission(context, result.resultCode, data)
            }
        } else {
            ScreenCapturePermissionStore.clear()
            if (overlayRunning) {
                OverlayService.clearCapturePermission(context)
            }
        }
    }

    DisposableEffect(context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayPermissionGranted = OverlayPermissionHelper.canDrawOverlays(context)
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
        screenCaptureGranted = screenCaptureGranted,
        overlayRunning = overlayRunning,
        onRequestOverlayPermission = {
            OverlayPermissionHelper.openOverlaySettings(context)
        },
        onRequestScreenCapturePermission = {
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
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
        onRefreshData = {
            viewModel.refreshCatalog(silent = false)
        }
    )
}
