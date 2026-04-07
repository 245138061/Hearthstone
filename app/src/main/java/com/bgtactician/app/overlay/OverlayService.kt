package com.bgtactician.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.bgtactician.app.R
import com.bgtactician.app.data.local.AppPreferences
import com.bgtactician.app.data.local.OverlaySettings
import com.bgtactician.app.data.model.AnomalyPreset
import com.bgtactician.app.data.model.Tribe
import com.bgtactician.app.data.repository.StrategyEngine
import com.bgtactician.app.data.repository.StrategyRepository
import com.bgtactician.app.ui.components.MiniOverlayBubble
import com.bgtactician.app.ui.screen.TacticianDashboard
import com.bgtactician.app.ui.theme.BGTacticianTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OverlayService : LifecycleService(), SavedStateRegistryOwner {

    companion object {
        private const val ACTION_START = "com.bgtactician.app.overlay.START"
        private const val ACTION_STOP = "com.bgtactician.app.overlay.STOP"
        private const val ACTION_REFRESH_SETTINGS = "com.bgtactician.app.overlay.REFRESH_SETTINGS"
        private const val ACTION_TOGGLE_INTERACTION = "com.bgtactician.app.overlay.TOGGLE_INTERACTION"
        private const val CHANNEL_ID = "bgtactician_overlay"
        private const val NOTIFICATION_ID = 1001

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }

        fun refreshSettings(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_REFRESH_SETTINGS)
            ContextCompat.startForegroundService(context, intent)
        }

        fun toggleInteraction(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_TOGGLE_INTERACTION)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = StrategyRepository()
    private val preferences by lazy { AppPreferences(applicationContext) }

    private lateinit var windowManager: WindowManager
    private var bubbleView: ComposeView? = null
    private var panelView: ComposeView? = null
    private var bubbleLayoutParams: WindowManager.LayoutParams? = null

    private var catalogVersion by mutableStateOf("")
    private var hasLoadedCatalog by mutableStateOf(false)
    private var strategies by mutableStateOf(emptyList<com.bgtactician.app.data.model.StrategyComp>())
    private var selectedTribes by mutableStateOf(
        setOf(Tribe.MECH, Tribe.DEMON, Tribe.UNDEAD, Tribe.PIRATE, Tribe.ELEMENTAL)
    )
    private var selectedAnomaly by mutableStateOf(AnomalyPreset.NONE)
    private var duosMode by mutableStateOf(false)
    private var selectedStrategyId by mutableStateOf<String?>(null)
    private var overlaySettings by mutableStateOf(OverlaySettings())

    override fun onCreate() {
        savedStateController.performRestore(null)
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        loadPreferences()
        createNotificationChannel()
        startOverlayForeground()
        addBubble()
        loadCatalog()
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_TOGGLE_INTERACTION -> {
                val next = !overlaySettings.interactionEnabled
                preferences.saveOverlaySettings(next, overlaySettings.bubbleOpacityPercent)
                syncOverlaySettings()
            }
            ACTION_REFRESH_SETTINGS -> syncOverlaySettings()
        }
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    override fun onDestroy() {
        removePanel()
        removeBubble()
        serviceScope.cancel()
        _isRunning.value = false
        super.onDestroy()
    }

    private fun loadCatalog(forceFresh: Boolean = false) {
        serviceScope.launch {
            val snapshot = repository.loadCatalog(
                context = applicationContext,
                ignoreMemoryCache = forceFresh
            )
            catalogVersion = snapshot.catalog.version
            strategies = snapshot.catalog.comps
            hasLoadedCatalog = true
            val filtered = filteredStrategies()
            selectedStrategyId = filtered.firstOrNull()?.id
        }
    }

    private fun filteredStrategies() = StrategyEngine.filter(
        allStrategies = strategies,
        selectedTribes = selectedTribes,
        selectedAnomaly = selectedAnomaly,
        isDuos = duosMode
    )

    private fun loadPreferences() {
        val dashboardPreferences = preferences.loadDashboardPreferences()
        selectedTribes = dashboardPreferences.selectedTribes
        selectedAnomaly = dashboardPreferences.selectedAnomaly
        duosMode = dashboardPreferences.isDuos
        overlaySettings = preferences.loadOverlaySettings()
    }

    private fun syncOverlaySettings() {
        overlaySettings = preferences.loadOverlaySettings()
        applyBubblePresentation()
        if (!overlaySettings.interactionEnabled) {
            removePanel()
        }
        startOverlayForeground()
    }

    private fun addBubble() {
        if (bubbleView != null) return

        val overlayPosition = preferences.loadOverlayPosition()

        bubbleLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = overlayPosition.x
            y = overlayPosition.y
        }

        bubbleView = ComposeView(this).apply {
            attachOwners(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                BGTacticianTheme {
                    MiniOverlayBubble()
                }
            }
            setOnTouchListener(BubbleTouchListener())
        }

        windowManager.addView(bubbleView, bubbleLayoutParams)
        applyBubblePresentation()
    }

    private fun removeBubble() {
        bubbleView?.let { windowManager.removeView(it) }
        bubbleView = null
    }

    private fun showPanel() {
        if (panelView != null) return

        loadPreferences()
        loadCatalog(forceFresh = true)

        panelView = ComposeView(this).apply {
            attachOwners(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                BGTacticianTheme {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xA608111A))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = ::removePanel
                                )
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(16.dp)
                                .widthIn(max = 420.dp),
                            shape = RoundedCornerShape(28.dp),
                            color = Color(0xFF0D1722),
                            tonalElevation = 10.dp,
                            shadowElevation = 20.dp
                        ) {
                            TacticianDashboard(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(20.dp),
                                uiState = buildDashboardState(),
                                overlayMode = true,
                                onToggleTribe = ::toggleTribe,
                                onSelectAnomaly = ::selectAnomaly,
                                onSetDuos = ::updateDuosMode,
                                onSelectStrategy = { selectedStrategyId = it },
                                onClose = ::removePanel
                            )
                        }
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(panelView, params)
    }

    private fun removePanel() {
        panelView?.let { windowManager.removeView(it) }
        panelView = null
    }

    private fun attachOwners(view: ComposeView) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    private fun toggleTribe(tribe: Tribe) {
        selectedTribes = when {
            selectedTribes.contains(tribe) -> selectedTribes - tribe
            selectedTribes.size >= 5 -> selectedTribes
            else -> selectedTribes + tribe
        }
        persistDashboardPreferences()
        reconcileSelection()
    }

    private fun selectAnomaly(anomaly: String) {
        selectedAnomaly = anomaly
        persistDashboardPreferences()
        reconcileSelection()
    }

    private fun updateDuosMode(enabled: Boolean) {
        duosMode = enabled
        persistDashboardPreferences()
        reconcileSelection()
    }

    private fun reconcileSelection() {
        val next = filteredStrategies()
        if (next.none { it.id == selectedStrategyId }) {
            selectedStrategyId = next.firstOrNull()?.id
        }
    }

    private fun buildDashboardState() = com.bgtactician.app.viewmodel.DashboardUiState(
        catalogVersion = catalogVersion,
        isLoading = !hasLoadedCatalog,
        selectedTribes = selectedTribes,
        selectedAnomaly = selectedAnomaly,
        isDuos = duosMode,
        strategies = filteredStrategies(),
        selectedStrategyId = selectedStrategyId
    )

    private fun persistDashboardPreferences() {
        preferences.saveDashboardPreferences(
            selectedTribes = selectedTribes,
            selectedAnomaly = selectedAnomaly,
            isDuos = duosMode,
            manifestUrlOverride = preferences.loadDashboardPreferences().manifestUrlOverride
        )
    }

    private fun startOverlayForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(
                if (overlaySettings.interactionEnabled) {
                    getString(R.string.overlay_notification_text)
                } else {
                    getString(R.string.overlay_notification_passthrough)
                }
            )
            .setSmallIcon(R.drawable.ic_overlay_notification)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(
                0,
                if (overlaySettings.interactionEnabled) {
                    getString(R.string.overlay_action_passthrough)
                } else {
                    getString(R.string.overlay_action_restore)
                },
                servicePendingIntent(ACTION_TOGGLE_INTERACTION, 201)
            )
            .addAction(
                0,
                getString(R.string.overlay_action_stop),
                servicePendingIntent(ACTION_STOP, 202)
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.overlay_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, OverlayService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun applyBubblePresentation() {
        val view = bubbleView ?: return
        val params = bubbleLayoutParams ?: return
        params.flags = if (overlaySettings.interactionEnabled) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        view.alpha = overlaySettings.bubbleOpacityPercent / 100f
        windowManager.updateViewLayout(view, params)
    }

    private inner class BubbleTouchListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var dragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = bubbleLayoutParams ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragging = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - touchX).toInt()
                    val deltaY = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(deltaX) > 8 || kotlin.math.abs(deltaY) > 8) {
                        dragging = true
                    }
                    params.x = startX + deltaX
                    params.y = startY + deltaY
                    windowManager.updateViewLayout(view, params)
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    preferences.saveOverlayPosition(params.x, params.y)
                    if (!dragging) {
                        if (panelView == null) showPanel() else removePanel()
                    }
                    return true
                }
            }
            return false
        }
    }
}
