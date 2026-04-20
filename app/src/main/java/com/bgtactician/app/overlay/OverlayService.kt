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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.bgtactician.app.R
import com.bgtactician.app.autodetect.CapturedFrame
import com.bgtactician.app.autodetect.HeroNameOcrDetector
import com.bgtactician.app.autodetect.HeroSelectionOcrState
import com.bgtactician.app.autodetect.HeroSelectionOcrStabilizer
import com.bgtactician.app.autodetect.ScreenCaptureManager
import com.bgtactician.app.autodetect.ScreenCapturePermissionStore
import com.bgtactician.app.autodetect.TavernTierDetection
import com.bgtactician.app.autodetect.TavernTierDetector
import com.bgtactician.app.autodetect.TavernTierMonitorStabilizer
import com.bgtactician.app.autodetect.TavernTierMonitorState
import com.bgtactician.app.data.local.AppPreferences
import com.bgtactician.app.data.local.HeroSelectionSessionStore
import com.bgtactician.app.data.local.OverlaySettings
import com.bgtactician.app.data.local.VisionApiSettings
import com.bgtactician.app.data.local.VisionRoutingMode
import com.bgtactician.app.data.model.AutoDetectDebugInfo
import com.bgtactician.app.data.model.AutoDetectStatus
import com.bgtactician.app.data.model.BattlegroundHeroNameIndex
import com.bgtactician.app.data.model.BattlegroundCardStatsCatalog
import com.bgtactician.app.data.model.BattlegroundHeroStatsCatalog
import com.bgtactician.app.data.model.CardRulesCatalog
import com.bgtactician.app.data.model.HeroSelectionVisionHeroOption
import com.bgtactician.app.data.model.HeroSelectionVisionResult
import com.bgtactician.app.data.model.ResolvedHeroStatOption
import com.bgtactician.app.data.model.Tribe
import com.bgtactician.app.data.model.VisionScreenType
import com.bgtactician.app.data.repository.HeroSelectionRecommendationEngine
import com.bgtactician.app.data.repository.StrategyEngine
import com.bgtactician.app.data.repository.StrategyRepository
import com.bgtactician.app.ui.components.MiniOverlayDetectChip
import com.bgtactician.app.ui.components.MiniOverlayBubble
import com.bgtactician.app.ui.screen.HeroSelectionFloatingOverlay
import com.bgtactician.app.ui.screen.TacticianDashboard
import com.bgtactician.app.ui.theme.BGTacticianTheme
import com.bgtactician.app.vision.HeroSelectionVisionRequest
import com.bgtactician.app.vision.HeroSelectionVisionSemanticValidator
import com.bgtactician.app.vision.HeroSelectionVisionValidator
import com.bgtactician.app.vision.OpenAiCompatibleVisionConfig
import com.bgtactician.app.vision.OpenAiCompatibleVisionProvider
import com.bgtactician.app.vision.VisionEndpoint
import com.bgtactician.app.vision.VisionFailoverOutcome
import com.bgtactician.app.vision.VisionRecognitionScope
import com.bgtactician.app.vision.VisionRequestSource
import com.bgtactician.app.vision.executeVisionFailover
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OverlayService : LifecycleService(), SavedStateRegistryOwner {

    companion object {
        private const val ACTION_START = "com.bgtactician.app.overlay.START"
        private const val ACTION_STOP = "com.bgtactician.app.overlay.STOP"
        private const val ACTION_REFRESH_SETTINGS = "com.bgtactician.app.overlay.REFRESH_SETTINGS"
        private const val ACTION_TOGGLE_INTERACTION = "com.bgtactician.app.overlay.TOGGLE_INTERACTION"
        private const val ACTION_UPDATE_CAPTURE_PERMISSION = "com.bgtactician.app.overlay.UPDATE_CAPTURE_PERMISSION"
        private const val ACTION_CLEAR_CAPTURE_PERMISSION = "com.bgtactician.app.overlay.CLEAR_CAPTURE_PERMISSION"
        private const val EXTRA_CAPTURE_RESULT_CODE = "capture_result_code"
        private const val EXTRA_CAPTURE_DATA = "capture_data"
        private const val CHANNEL_ID = "bgtactician_overlay"
        private const val NOTIFICATION_ID = 1001
        private const val BUBBLE_EDGE_MARGIN_DP = 6
        private const val DETECT_CHIP_GAP_DP = 16
        private const val DETECT_CHIP_TOP_OFFSET_DP = 10
        private const val AUTO_DETECT_SESSION_MS = 18_000L
        private const val AUTO_DETECT_TRIGGER_COOLDOWN_MS = 2_500L
        private const val AUTO_DETECT_FRAME_WAIT_MS = 8_000L
        private const val AUTO_DETECT_ATTEMPT_LIMIT = 8
        private const val AUTO_DETECT_SCAN_INTERVAL_MS = 500L
        private const val TAVERN_TIER_MONITOR_INTERVAL_MS = 900L

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()
        private val _autoDetectStatus = MutableStateFlow(AutoDetectStatus.WAITING)
        val autoDetectStatus = _autoDetectStatus.asStateFlow()
        private val _autoDetectDebugInfo = MutableStateFlow(AutoDetectDebugInfo())
        val autoDetectDebugInfo = _autoDetectDebugInfo.asStateFlow()

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

        fun updateCapturePermission(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, OverlayService::class.java)
                .setAction(ACTION_UPDATE_CAPTURE_PERMISSION)
                .putExtra(EXTRA_CAPTURE_RESULT_CODE, resultCode)
                .putExtra(EXTRA_CAPTURE_DATA, data)
            ContextCompat.startForegroundService(context, intent)
        }

        fun clearCapturePermission(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
                .setAction(ACTION_CLEAR_CAPTURE_PERMISSION)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = StrategyRepository()
    private val preferences by lazy { AppPreferences(applicationContext) }
    private val heroNameOcrDetector by lazy { HeroNameOcrDetector() }

    private lateinit var windowManager: WindowManager
    private var bubbleView: ComposeView? = null
    private var detectChipView: ComposeView? = null
    private var heroCardView: ComposeView? = null
    private var panelView: ComposeView? = null
    private var bubbleLayoutParams: WindowManager.LayoutParams? = null
    private var detectChipLayoutParams: WindowManager.LayoutParams? = null
    private var heroCardLayoutParams: WindowManager.LayoutParams? = null

    private var catalogVersion by mutableStateOf("")
    private var hasLoadedCatalog by mutableStateOf(false)
    private var strategies by mutableStateOf(emptyList<com.bgtactician.app.data.model.StrategyComp>())
    private var cardRules by mutableStateOf<CardRulesCatalog>(emptyMap())
    private var recognizedHeroOptions by mutableStateOf(emptyList<HeroSelectionVisionHeroOption>())
    private var recognizedHeroes by mutableStateOf(emptyList<ResolvedHeroStatOption>())
    private var heroStatsUpdatedAtLabel by mutableStateOf<String?>(null)
    private var selectedHeroCardId by mutableStateOf<String?>(null)
    private var selectedHeroName by mutableStateOf<String?>(null)
    private var selectedHeroSlot by mutableStateOf<Int?>(null)
    private var heroOverlayDismissed by mutableStateOf(false)
    private var recognizedHeroOverlayKey by mutableStateOf("")
    private var selectedTribes by mutableStateOf(
        setOf(Tribe.MECH, Tribe.DEMON, Tribe.UNDEAD, Tribe.PIRATE, Tribe.ELEMENTAL)
    )
    private var selectedStrategyId by mutableStateOf<String?>(null)
    private var overlaySettings by mutableStateOf(OverlaySettings())
    private val autoDetectStatusState = mutableStateOf(AutoDetectStatus.WAITING)
    private var autoDetectStatus: AutoDetectStatus
        get() = autoDetectStatusState.value
        set(value) {
            autoDetectStatusState.value = value
            _autoDetectStatus.value = value
        }
    private val autoDetectDebugInfoState = mutableStateOf(AutoDetectDebugInfo())
    private var autoDetectDebugInfo: AutoDetectDebugInfo
        get() = autoDetectDebugInfoState.value
        set(value) {
            autoDetectDebugInfoState.value = value
            _autoDetectDebugInfo.value = value
        }
    private var screenCaptureManager: ScreenCaptureManager? = null
    private var autoDetectJob: Job? = null
    private var tavernTierMonitorJob: Job? = null
    private var lastAutoDetectTriggerAt = 0L
    private var mediaProjectionForegroundArmed = false

    override fun onCreate() {
        savedStateController.performRestore(null)
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        screenCaptureManager = ScreenCaptureManager(applicationContext) {
            serviceScope.launch(Dispatchers.Main.immediate) {
                mediaProjectionForegroundArmed = false
                ScreenCapturePermissionStore.markSessionActive(false)
                autoDetectJob?.cancel()
                autoDetectJob = null
                tavernTierMonitorJob?.cancel()
                tavernTierMonitorJob = null
                HeroSelectionSessionStore.clearRecognizedHeroes(selectedTribes = selectedTribes)
                heroOverlayDismissed = false
                recognizedHeroOverlayKey = ""
                autoDetectStatus = AutoDetectStatus.NEEDS_ATTENTION
                autoDetectDebugInfo = AutoDetectDebugInfo(
                    tavernTier = autoDetectDebugInfo.tavernTier,
                    tavernTierLabel = autoDetectDebugInfo.tavernTierLabel,
                    rawText = "屏幕识别会话已结束，请重新授权后再识别",
                    lastUpdatedLabel = nowLabel()
                )
                startOverlayForeground()
            }
        }
        loadPreferences()
        createNotificationChannel()
        startOverlayForeground()
        addBubble()
        observeHeroSelectionSession()
        loadCatalog()
        syncTavernTierMonitoring()
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
            ACTION_UPDATE_CAPTURE_PERMISSION -> {
                val resultCode = intent.getIntExtra(EXTRA_CAPTURE_RESULT_CODE, 0)
                intent.intentParcelableExtra<Intent>(EXTRA_CAPTURE_DATA)?.let { data ->
                    ScreenCapturePermissionStore.update(resultCode, data)
                    syncTavernTierMonitoring(forceRestart = true)
                    if (autoDetectJob?.isActive == true) {
                        syncAutoDetect(forceRestart = true)
                    }
                }
            }
            ACTION_CLEAR_CAPTURE_PERMISSION -> {
                ScreenCapturePermissionStore.clear()
                stopTavernTierMonitoring(stopCapture = true)
                stopAutoDetect(stopCapture = false)
            }
        }
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    override fun onDestroy() {
        stopTavernTierMonitoring(stopCapture = false)
        stopAutoDetect(stopCapture = true)
        screenCaptureManager?.release()
        screenCaptureManager = null
        removePanel()
        removeHeroCardOverlay()
        removeDetectChip()
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
            cardRules = repository.loadCardRules(
                context = applicationContext,
                ignoreMemoryCache = forceFresh
            )
            catalogVersion = snapshot.catalog.version
            strategies = snapshot.catalog.comps
            hasLoadedCatalog = true
            selectedStrategyId = resolveSelectedStrategyId(filteredStrategies())
        }
    }

    private fun observeHeroSelectionSession() {
        serviceScope.launch {
            HeroSelectionSessionStore.state.collect { session ->
                val nextOverlayKey = session.recognizedHeroes.joinToString("|") { hero ->
                    "${hero.slot}:${hero.heroCardId ?: hero.displayName}"
                }
                if (nextOverlayKey != recognizedHeroOverlayKey) {
                    recognizedHeroOverlayKey = nextOverlayKey
                    heroOverlayDismissed = false
                }
                if (session.selectedTribes.size == 5) {
                    selectedTribes = session.selectedTribes
                }
                recognizedHeroOptions = session.recognizedHeroOptions
                recognizedHeroes = session.recognizedHeroes
                selectedHeroCardId = session.selectedHeroCardId
                selectedHeroName = session.selectedHeroName
                selectedHeroSlot = session.selectedHeroSlot
                heroStatsUpdatedAtLabel = if (recognizedHeroes.isNotEmpty()) {
                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
                } else {
                    null
                }
                reconcileSelection()
                syncHeroCardOverlay()
            }
        }
    }

    private fun filteredStrategies() = StrategyEngine.filter(
        allStrategies = strategies,
        selectedTribes = selectedTribes
    ).let { strategies ->
        sortStrategiesForSelectedHero(
            strategies = strategies,
            selectedHero = recognizedHeroes.firstOrNull { hero ->
                (!selectedHeroCardId.isNullOrBlank() && hero.heroCardId == selectedHeroCardId) ||
                    (selectedHeroSlot != null && hero.slot == selectedHeroSlot) ||
                    (!selectedHeroName.isNullOrBlank() &&
                        (hero.displayName == selectedHeroName || hero.recognizedName == selectedHeroName))
            }
        )
    }

    private fun loadPreferences() {
        val dashboardPreferences = preferences.loadDashboardPreferences()
        selectedTribes = dashboardPreferences.selectedTribes
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

    private fun syncTavernTierMonitoring(forceRestart: Boolean = false) {
        val captureManager = screenCaptureManager ?: return
        if (tavernTierMonitorJob?.isActive == true && !forceRestart) return

        tavernTierMonitorJob?.cancel()
        tavernTierMonitorJob = serviceScope.launch(Dispatchers.Default) {
            if (!startCaptureIfNeeded(captureManager)) {
                withContext(Dispatchers.Main.immediate) {
                    tavernTierMonitorJob = null
                    publishStableTavernTier(null)
                }
                return@launch
            }

            var lastFrameTimestamp = Long.MIN_VALUE
            var monitorState = TavernTierMonitorState()

            while (currentCoroutineContext().isActive) {
                val frame = captureManager.latestFrame()
                if (frame == null) {
                    delay(240L)
                    continue
                }
                if (frame.timestampMillis == lastFrameTimestamp) {
                    frame.bitmap.recycle()
                    delay(180L)
                    continue
                }

                lastFrameTimestamp = frame.timestampMillis
                val detection = try {
                    TavernTierDetector.detect(applicationContext, frame)
                } finally {
                    frame.bitmap.recycle()
                }
                val step = TavernTierMonitorStabilizer.advance(monitorState, detection)
                monitorState = step.state
                if (step.changed) {
                    withContext(Dispatchers.Main.immediate) {
                        publishStableTavernTier(step.updatedStableDetection)
                    }
                }
                delay(TAVERN_TIER_MONITOR_INTERVAL_MS)
            }
        }
    }

    private fun syncAutoDetect(forceRestart: Boolean = false) {
        val captureManager = screenCaptureManager ?: return
        if (!captureManager.isActive() && ScreenCapturePermissionStore.current() == null) {
            stopAutoDetect(stopCapture = false)
            autoDetectStatus = AutoDetectStatus.WAITING
            autoDetectDebugInfo = AutoDetectDebugInfo(
                tavernTier = autoDetectDebugInfo.tavernTier,
                tavernTierLabel = autoDetectDebugInfo.tavernTierLabel
            )
            return
        }

        if (autoDetectJob?.isActive == true && !forceRestart) return

        autoDetectJob?.cancel()
        autoDetectJob = serviceScope.launch(Dispatchers.Default) {
            if (!startCaptureIfNeeded(captureManager)) {
                withContext(Dispatchers.Main.immediate) {
                    autoDetectJob = null
                    HeroSelectionSessionStore.clearRecognizedHeroes(selectedTribes = selectedTribes)
                    heroOverlayDismissed = false
                    recognizedHeroOverlayKey = ""
                    autoDetectStatus = AutoDetectStatus.NEEDS_ATTENTION
                    autoDetectDebugInfo = autoDetectDebugInfo.copy(
                        rawText = "屏幕采集启动失败",
                        lastUpdatedLabel = nowLabel()
                    )
                    startOverlayForeground()
                }
                return@launch
            }

            val sessionResult = runVisionRecognitionSession(captureManager)
            withContext(Dispatchers.Main.immediate) {
                mediaProjectionForegroundArmed = false
                autoDetectJob = null
                autoDetectStatus = sessionResult.status
                autoDetectDebugInfo = sessionResult.debugInfo
                sessionResult.selectedTribes?.let(::applyAutoDetectedTribes)
                val sessionTribes = sessionResult.selectedTribes ?: selectedTribes
                val sessionHeroOptions = sessionResult.recognizedHeroOptions.orEmpty()
                val sessionHeroes = sessionResult.recognizedHeroes.orEmpty()
                if (sessionHeroOptions.isNotEmpty() || sessionHeroes.isNotEmpty()) {
                    HeroSelectionSessionStore.updateVisionResult(
                        selectedTribes = sessionTribes,
                        recognizedHeroOptions = sessionHeroOptions,
                        recognizedHeroes = sessionHeroes
                    )
                } else {
                    HeroSelectionSessionStore.clearRecognizedHeroes(
                        selectedTribes = sessionTribes
                    )
                }
                if (sessionHeroes.isEmpty()) {
                    heroOverlayDismissed = false
                    recognizedHeroOverlayKey = ""
                }
                startOverlayForeground()
            }
        }
    }

    private fun stopTavernTierMonitoring(stopCapture: Boolean) {
        tavernTierMonitorJob?.cancel()
        tavernTierMonitorJob = null
        if (stopCapture) {
            screenCaptureManager?.stopCapture()
            ScreenCapturePermissionStore.markSessionActive(false)
        }
    }

    private fun stopAutoDetect(stopCapture: Boolean = false) {
        autoDetectJob?.cancel()
        autoDetectJob = null
        mediaProjectionForegroundArmed = false
        if (stopCapture) {
            screenCaptureManager?.stopCapture()
            ScreenCapturePermissionStore.markSessionActive(false)
        }
        HeroSelectionSessionStore.clearRecognizedHeroes(selectedTribes = selectedTribes)
        heroOverlayDismissed = false
        recognizedHeroOverlayKey = ""
        autoDetectStatus = AutoDetectStatus.WAITING
        autoDetectDebugInfo = AutoDetectDebugInfo(
            tavernTier = autoDetectDebugInfo.tavernTier,
            tavernTierLabel = autoDetectDebugInfo.tavernTierLabel
        )
        startOverlayForeground()
    }

    private fun restartAutoDetectSession() {
        val captureReady = screenCaptureManager?.isActive() == true
        if (!captureReady && ScreenCapturePermissionStore.current() == null) {
            autoDetectStatus = AutoDetectStatus.NEEDS_ATTENTION
            autoDetectDebugInfo = AutoDetectDebugInfo(
                tavernTier = autoDetectDebugInfo.tavernTier,
                tavernTierLabel = autoDetectDebugInfo.tavernTierLabel,
                rawText = "请先授权屏幕识别"
            )
            return
        }
        if (autoDetectStatus == AutoDetectStatus.SCANNING) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastAutoDetectTriggerAt < AUTO_DETECT_TRIGGER_COOLDOWN_MS) {
            autoDetectDebugInfo = AutoDetectDebugInfo(
                tavernTier = autoDetectDebugInfo.tavernTier,
                tavernTierLabel = autoDetectDebugInfo.tavernTierLabel,
                rawText = "请稍候再试，识别会话正在准备",
                lastUpdatedLabel = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            )
            return
        }
        lastAutoDetectTriggerAt = now
        autoDetectJob?.cancel()
        autoDetectJob = null
        mediaProjectionForegroundArmed = false
        autoDetectStatus = AutoDetectStatus.SCANNING
        autoDetectDebugInfo = AutoDetectDebugInfo(
            tavernTier = autoDetectDebugInfo.tavernTier,
            tavernTierLabel = autoDetectDebugInfo.tavernTierLabel,
            rawText = "本轮识别已启动",
            lastUpdatedLabel = nowLabel()
        )
        syncAutoDetect(forceRestart = true)
    }

    private suspend fun startCaptureIfNeeded(
        captureManager: ScreenCaptureManager
    ): Boolean {
        if (captureManager.isActive()) return true

        val permission = ScreenCapturePermissionStore.consume() ?: return false
        withContext(Dispatchers.Main.immediate) {
            // Android 14+ 要求在创建 MediaProjection 之前，服务已经以前台媒体投屏类型运行。
            mediaProjectionForegroundArmed = true
            startOverlayForeground()
        }
        val startResult = captureManager.start(permission)
        return if (startResult.isSuccess) {
            withContext(Dispatchers.Main.immediate) {
                mediaProjectionForegroundArmed = false
                ScreenCapturePermissionStore.markSessionActive(true)
                startOverlayForeground()
            }
            true
        } else {
            withContext(Dispatchers.Main.immediate) {
                mediaProjectionForegroundArmed = false
                ScreenCapturePermissionStore.markSessionActive(false)
                autoDetectDebugInfo = autoDetectDebugInfo.copy(
                    rawText = startResult.exceptionOrNull()?.message ?: "屏幕采集启动失败",
                    lastUpdatedLabel = nowLabel()
                )
                startOverlayForeground()
            }
            false
        }
    }

    private fun publishStableTavernTier(
        detection: TavernTierDetection?
    ) {
        val nextLabel = detection
            ?.takeIf { it.tier != null }
            ?.let(::formatTavernTierDebugLabel)
        if (autoDetectDebugInfo.tavernTier == detection?.tier &&
            autoDetectDebugInfo.tavernTierLabel == nextLabel
        ) return
        autoDetectDebugInfo = autoDetectDebugInfo.copy(
            tavernTier = detection?.tier,
            tavernTierLabel = nextLabel
        )
    }

    private fun applyAutoDetectedTribes(tribes: Set<Tribe>) {
        if (tribes.isEmpty() || tribes == selectedTribes) return
        selectedTribes = tribes
        selectedStrategyId = null
        persistDashboardPreferences()
        reconcileSelection()
    }

    private fun applyManualSessionTribes(tribes: Set<Tribe>) {
        if (tribes.size != 5 || tribes == selectedTribes) return
        serviceScope.launch {
            val heroStatsCatalog = repository.loadHeroStats(applicationContext)
            val cardStatsCatalog = repository.loadCardStats(applicationContext)
            val heroNameIndex = repository.loadHeroNameIndex(applicationContext)
            val resolvedHeroes = resolveRecognizedHeroes(
                heroOptions = recognizedHeroOptions,
                heroStatsCatalog = heroStatsCatalog,
                cardStatsCatalog = cardStatsCatalog,
                heroNameIndex = heroNameIndex,
                selectedTribes = tribes,
                allStrategies = strategies
            )
            withContext(Dispatchers.Main.immediate) {
                selectedTribes = tribes
                selectedStrategyId = null
                HeroSelectionSessionStore.updateManualTribes(
                    selectedTribes = tribes,
                    recognizedHeroes = resolvedHeroes
                )
                autoDetectDebugInfo = autoDetectDebugInfo.copy(
                    rawText = "已手动校正本局种族：${tribes.joinToString(" / ") { it.label }}",
                    lastUpdatedLabel = nowLabel()
                )
                persistDashboardPreferences()
                reconcileSelection()
                syncHeroCardOverlay()
            }
        }
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
        addDetectChip()
        applyBubblePresentation()
        bubbleView?.post { snapBubbleToRightEdge(savePosition = false) }
    }

    private fun removeBubble() {
        bubbleView?.let { windowManager.removeView(it) }
        bubbleView = null
    }

    private fun addDetectChip() {
        if (detectChipView != null) return
        if (panelView != null) return

        detectChipLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        detectChipView = ComposeView(this).apply {
            attachOwners(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                BGTacticianTheme {
                    MiniOverlayDetectChip(
                        status = autoDetectStatus,
                        tavernTierLabel = autoDetectDebugInfo.tavernTierLabel
                    )
                }
            }
            setOnClickListener {
                if (autoDetectStatus != AutoDetectStatus.SCANNING) {
                    restartAutoDetectSession()
                }
            }
        }

        windowManager.addView(detectChipView, detectChipLayoutParams)
        detectChipView?.post { syncDetectChipPosition() }
    }

    private fun removeDetectChip() {
        detectChipView?.let { windowManager.removeView(it) }
        detectChipView = null
    }

    private fun showPanel() {
        if (panelView != null) return

        loadPreferences()
        if (!hasLoadedCatalog) {
            loadCatalog(forceFresh = true)
        } else {
            reconcileSelection()
        }
        // 用户已经主动进入抽屉查看详情，这一轮的英雄悬浮推荐不应在关闭抽屉后再次弹出。
        if (recognizedHeroes.isNotEmpty()) {
            heroOverlayDismissed = true
        }

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
                                .background(Color.Transparent)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = ::removePanel
                                )
                        )
                        TacticianDashboard(
                            modifier = Modifier.fillMaxSize(),
                            uiState = buildDashboardState(),
                            overlayMode = true,
                            onSelectStrategy = {
                                selectedStrategyId = it
                            },
                            onSelectHero = { hero ->
                                selectHeroAndSyncStrategy(hero)
                            },
                            onApplySessionTribes = ::applyManualSessionTribes,
                            onTriggerAutoDetect = ::restartAutoDetectSession,
                            onClose = ::removePanel
                        )
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
        removeHeroCardOverlay()
        removeDetectChip()
    }

    private fun removePanel() {
        panelView?.let { windowManager.removeView(it) }
        panelView = null
        syncHeroCardOverlay()
        addDetectChip()
        detectChipView?.post { syncDetectChipPosition() }
    }

    private fun syncHeroCardOverlay() {
        val shouldShow = panelView == null && recognizedHeroes.isNotEmpty() && !heroOverlayDismissed
        when {
            shouldShow && heroCardView == null -> addHeroCardOverlay()
            shouldShow && heroCardView != null -> {
                heroCardView?.post { syncHeroCardOverlayPosition() }
            }
            !shouldShow && heroCardView != null -> removeHeroCardOverlay()
        }
    }

    private fun addHeroCardOverlay() {
        if (heroCardView != null || recognizedHeroes.isEmpty() || panelView != null) return

        heroCardLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        heroCardView = ComposeView(this).apply {
            attachOwners(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                BGTacticianTheme {
                    HeroSelectionFloatingOverlay(
                        heroes = recognizedHeroes,
                        selectedHero = buildDashboardState().selectedHero,
                        onClose = null,
                        onSelectHero = { hero ->
                            selectHeroAndSyncStrategy(hero)
                            heroOverlayDismissed = true
                            syncHeroCardOverlay()
                        }
                    )
                }
            }
        }

        windowManager.addView(heroCardView, heroCardLayoutParams)
        heroCardView?.post { syncHeroCardOverlayPosition() }
    }

    private fun removeHeroCardOverlay() {
        heroCardView?.let { windowManager.removeView(it) }
        heroCardView = null
        heroCardLayoutParams = null
    }

    private fun attachOwners(view: ComposeView) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    private fun reconcileSelection() {
        selectedStrategyId = resolveSelectedStrategyId(filteredStrategies())
    }

    private fun selectHeroAndSyncStrategy(hero: ResolvedHeroStatOption) {
        HeroSelectionSessionStore.selectHero(hero)
        hero.recommendation?.recommendedCompId
            ?.takeIf { recommendedId -> filteredStrategies().any { it.id == recommendedId } }
            ?.let { recommendedId ->
                selectedStrategyId = recommendedId
            }
    }

    private fun resolveSelectedStrategyId(filtered: List<com.bgtactician.app.data.model.StrategyComp>): String? {
        val selectedHero = recognizedHeroes.firstOrNull { hero ->
            (!selectedHeroCardId.isNullOrBlank() && hero.heroCardId == selectedHeroCardId) ||
                (selectedHeroSlot != null && hero.slot == selectedHeroSlot) ||
                (!selectedHeroName.isNullOrBlank() &&
                    (hero.displayName == selectedHeroName || hero.recognizedName == selectedHeroName))
        }
        return selectedStrategyId?.takeIf { id -> filtered.any { it.id == id } }
            ?: selectedHero?.recommendation?.recommendedCompId?.takeIf { id ->
                filtered.any { it.id == id }
            }
            ?: filtered.firstOrNull()?.id
    }

    private fun sortStrategiesForSelectedHero(
        strategies: List<com.bgtactician.app.data.model.StrategyComp>,
        selectedHero: ResolvedHeroStatOption?
    ): List<com.bgtactician.app.data.model.StrategyComp> {
        val pinnedIds = listOfNotNull(
            selectedHero?.recommendation?.recommendedCompId,
            selectedHero?.recommendation?.fallbackCompId
        )
        if (pinnedIds.isEmpty()) return strategies
        val originalOrder = strategies.withIndex().associate { it.value.id to it.index }
        return strategies.sortedWith(
            compareByDescending<com.bgtactician.app.data.model.StrategyComp> { strategy ->
                when (strategy.id) {
                    pinnedIds.getOrNull(0) -> 2
                    pinnedIds.getOrNull(1) -> 1
                    else -> 0
                }
            }.thenBy { originalOrder[it.id] ?: Int.MAX_VALUE }
        )
    }

    private fun buildDashboardState() = com.bgtactician.app.viewmodel.DashboardUiState(
        catalogVersion = catalogVersion,
        isLoading = !hasLoadedCatalog,
        selectedTribes = selectedTribes,
        autoDetectStatus = autoDetectStatus,
        autoDetectDebugInfo = autoDetectDebugInfo,
        cardRules = cardRules,
        recognizedHeroes = recognizedHeroes,
        heroStatsUpdatedAtLabel = heroStatsUpdatedAtLabel,
        selectedHeroCardId = selectedHeroCardId,
        selectedHeroName = selectedHeroName,
        selectedHeroSlot = selectedHeroSlot,
        strategies = filteredStrategies(),
        selectedStrategyId = selectedStrategyId
    )

    private suspend fun runVisionRecognitionSession(
        captureManager: ScreenCaptureManager
    ): OverlayVisionSessionResult {
        val heroStatsCatalog = repository.loadHeroStats(applicationContext)
        val cardStatsCatalog = repository.loadCardStats(applicationContext)
        val settings = preferences.loadVisionApiSettings()
        val heroNameIndex = repository.loadHeroNameIndex(applicationContext)
        val startedAt = System.currentTimeMillis()
        val waitDeadline = startedAt + AUTO_DETECT_FRAME_WAIT_MS
        val sessionDeadline = startedAt + AUTO_DETECT_SESSION_MS
        var lastFrameTimestamp = Long.MIN_VALUE
        var attemptCount = 0
        var ocrState = HeroSelectionOcrState()
        var latestTribeAttempt: OverlayVisionAttempt? = null
        var latestOcrResult: HeroNameOcrDetector.HeroNameOcrResult? = null
        var bestStableOptions = emptyList<HeroSelectionVisionHeroOption>()
        var bestResolvedHeroes = emptyList<ResolvedHeroStatOption>()
        var bestUsedAiHeroes = false
        var bestHeroSourceLabel = "本地OCR"
        var stableTribes: Set<Tribe>? = null
        var latestTavernTierDetection: TavernTierDetection? = null
        val failures = mutableListOf<String>()

        while (currentCoroutineContext().isActive && System.currentTimeMillis() < waitDeadline) {
            val frame = captureManager.latestFrame()
            if (frame != null) {
                frame.bitmap.recycle()
                break
            }
            delay(240L)
        }

        while (
            currentCoroutineContext().isActive &&
            System.currentTimeMillis() < sessionDeadline &&
            attemptCount < AUTO_DETECT_ATTEMPT_LIMIT
        ) {
            val frame = captureManager.latestFrame()
            if (frame == null) {
                delay(320L)
                continue
            }
            if (frame.timestampMillis == lastFrameTimestamp) {
                frame.bitmap.recycle()
                delay(220L)
                continue
            }
            lastFrameTimestamp = frame.timestampMillis
            attemptCount += 1

            val tavernTierDetection: TavernTierDetection
            val attempt: OverlayVisionAttempt
            val ocrResult: HeroNameOcrDetector.HeroNameOcrResult
            try {
                tavernTierDetection = TavernTierDetector.detect(applicationContext, frame)
                attempt = analyzeVisionAttempt(
                    frame = frame,
                    settings = settings,
                    tavernTierDetection = tavernTierDetection,
                    heroNameIndex = heroNameIndex
                )
                ocrResult = heroNameOcrDetector.detect(frame, heroNameIndex)
            } finally {
                frame.bitmap.recycle()
            }
            latestTavernTierDetection = tavernTierDetection

            failures += attempt.failures
            latestOcrResult = ocrResult
            latestTribeAttempt = attempt.result?.let { attempt } ?: latestTribeAttempt

            attempt.result
                ?.toDomainTribes()
                ?.takeIf { it.size == 5 }
                ?.let { stableTribes = it }

            val step = HeroSelectionOcrStabilizer.advance(
                state = ocrState,
                observations = ocrResult.slotResults.map { it.toObservation() }
            )
            ocrState = step.state

            val effectiveSelectedTribes = stableTribes?.takeIf { it.size == 5 } ?: selectedTribes
            val blendedHeroSelection = blendHeroSelections(
                ocrOptions = step.stableOptions,
                aiOptions = attempt.result?.selectableHeroOptions.orEmpty(),
                heroStatsCatalog = heroStatsCatalog,
                cardStatsCatalog = cardStatsCatalog,
                heroNameIndex = heroNameIndex,
                selectedTribes = effectiveSelectedTribes,
                allStrategies = strategies
            )
            val resolvedHeroes = blendedHeroSelection.resolvedHeroes

            if (resolvedHeroes.size > bestResolvedHeroes.size) {
                bestStableOptions = blendedHeroSelection.options
                bestResolvedHeroes = resolvedHeroes
                bestUsedAiHeroes = blendedHeroSelection.usesAiHeroes
                bestHeroSourceLabel = blendedHeroSelection.sourceLabel
            }

            if (resolvedHeroes.size >= 3 && step.duplicateHeroCardIds.isEmpty()) {
                return OverlayVisionSessionResult(
                    status = AutoDetectStatus.LOCKED,
                    selectedTribes = effectiveSelectedTribes,
                    recognizedHeroOptions = blendedHeroSelection.options,
                    recognizedHeroes = resolvedHeroes,
                    debugInfo = buildHeroRecognitionDebugInfo(
                        heroSelection = blendedHeroSelection,
                        ocrResult = ocrResult,
                        stableOptions = blendedHeroSelection.options,
                        tribeAttempt = latestTribeAttempt,
                        tavernTierDetection = tavernTierDetection,
                        summaryText = buildString {
                            append(blendedHeroSelection.sourceLabel)
                            append(" 已锁定 ")
                            append(resolvedHeroes.size)
                            append(" 个英雄")
                            val names = resolvedHeroes.joinToString(" / ") { it.displayName }.take(80)
                            if (names.isNotBlank()) {
                                append("：")
                                append(names)
                            }
                            if (stableTribes == null) {
                                append("；5族未稳定，沿用当前环境")
                            }
                        }.take(160),
                        duplicateHeroCardIds = step.duplicateHeroCardIds,
                        chaoticSlots = step.chaoticSlots
                    )
                )
            }

            if (attemptCount >= 5 &&
                (step.duplicateHeroCardIds.isNotEmpty() || step.chaoticSlots.size >= 2)
            ) {
                return OverlayVisionSessionResult(
                    status = AutoDetectStatus.NEEDS_ATTENTION,
                    selectedTribes = effectiveSelectedTribes,
                    recognizedHeroOptions = bestStableOptions,
                    recognizedHeroes = bestResolvedHeroes,
                    debugInfo = buildHeroRecognitionDebugInfo(
                        heroSelection = BlendedHeroSelection(
                            options = bestStableOptions,
                            resolvedHeroes = bestResolvedHeroes,
                            usesAiHeroes = bestUsedAiHeroes,
                            sourceLabel = bestHeroSourceLabel
                        ),
                        ocrResult = ocrResult,
                        stableOptions = bestStableOptions,
                        tribeAttempt = latestTribeAttempt,
                        tavernTierDetection = tavernTierDetection,
                        summaryText = buildString {
                            append("本地 OCR 结果存在冲突")
                            if (step.duplicateHeroCardIds.isNotEmpty()) {
                                append("；检测到重复英雄")
                            }
                            if (step.chaoticSlots.isNotEmpty()) {
                                append("；槽位波动过大")
                            }
                            append("，请手动确认")
                        }.take(160),
                        duplicateHeroCardIds = step.duplicateHeroCardIds,
                        chaoticSlots = step.chaoticSlots
                    )
                )
            }

            delay(AUTO_DETECT_SCAN_INTERVAL_MS)
        }

        if (bestResolvedHeroes.isNotEmpty()) {
            return OverlayVisionSessionResult(
                status = AutoDetectStatus.NEEDS_ATTENTION,
                selectedTribes = stableTribes?.takeIf { it.size == 5 } ?: selectedTribes,
                recognizedHeroOptions = bestStableOptions,
                recognizedHeroes = bestResolvedHeroes,
                debugInfo = buildHeroRecognitionDebugInfo(
                    heroSelection = BlendedHeroSelection(
                        options = bestStableOptions,
                        resolvedHeroes = bestResolvedHeroes,
                        usesAiHeroes = bestUsedAiHeroes,
                        sourceLabel = bestHeroSourceLabel
                    ),
                    ocrResult = latestOcrResult,
                    stableOptions = bestStableOptions,
                    tribeAttempt = latestTribeAttempt,
                    tavernTierDetection = latestTavernTierDetection,
                    summaryText = "${bestHeroSourceLabel}已识别 ${bestResolvedHeroes.size} 个英雄，其余槽位仍在波动，请手动确认".take(160)
                )
            )
        }

        if (latestTribeAttempt?.result != null) {
            val latestAiHeroOptions = latestTribeAttempt.result.selectableHeroOptions
            if (latestAiHeroOptions.isNotEmpty()) {
                return OverlayVisionSessionResult(
                    status = AutoDetectStatus.NEEDS_ATTENTION,
                    selectedTribes = stableTribes?.takeIf { it.size == 5 } ?: selectedTribes,
                    recognizedHeroOptions = latestAiHeroOptions,
                    recognizedHeroes = emptyList(),
                    debugInfo = buildHeroRecognitionDebugInfo(
                        heroSelection = BlendedHeroSelection(
                            options = latestAiHeroOptions,
                            resolvedHeroes = emptyList(),
                            usesAiHeroes = true,
                            sourceLabel = "AI全量识别"
                        ),
                        ocrResult = latestOcrResult,
                        stableOptions = latestAiHeroOptions,
                        tribeAttempt = latestTribeAttempt,
                        tavernTierDetection = latestTavernTierDetection,
                        summaryText = "${latestTribeAttempt.sourceLabel.orEmpty()}已识别 ${latestAiHeroOptions.size} 个英雄原文，但还没稳定命中本地英雄索引".take(160)
                    )
                )
            }
            return OverlayVisionSessionResult(
                status = AutoDetectStatus.NEEDS_ATTENTION,
                selectedTribes = stableTribes?.takeIf { it.size == 5 } ?: selectedTribes,
                recognizedHeroOptions = emptyList(),
                recognizedHeroes = emptyList(),
                debugInfo = buildOcrRecognitionDebugInfo(
                    ocrResult = latestOcrResult,
                    stableOptions = emptyList(),
                    tribeAttempt = latestTribeAttempt,
                    tavernTierDetection = latestTavernTierDetection,
                    summaryText = "${latestTribeAttempt.sourceLabel.orEmpty()}只识别到种族，英雄 OCR 未稳定锁定".take(160)
                )
            )
        }

        return OverlayVisionSessionResult(
            status = AutoDetectStatus.NEEDS_ATTENTION,
            debugInfo = AutoDetectDebugInfo(
                rawText = failures.firstOrNull()
                    ?: if (attemptCount == 0) "未采集到有效画面，请停留在英雄选择页后重试" else "本轮没有识别到有效结果",
                tavernTier = latestTavernTierDetection?.tier,
                tavernTierLabel = stableTavernTierLabel(latestTavernTierDetection),
                lastUpdatedLabel = nowLabel()
            )
        )
    }

    private suspend fun analyzeVisionAttempt(
        frame: CapturedFrame,
        settings: VisionApiSettings,
        tavernTierDetection: TavernTierDetection,
        heroNameIndex: BattlegroundHeroNameIndex
    ): OverlayVisionAttempt {
        val endpoints = settings.toVisionEndpoints()
        if (endpoints.isEmpty()) {
            return OverlayVisionAttempt(
                result = null,
                sourceLabel = "",
                failures = emptyList(),
                tavernTierDetection = tavernTierDetection
            )
        }

        val detailedRecovery = recoverDetailedVision(
            frame = frame,
            endpoints = endpoints,
            heroNameIndex = heroNameIndex
        )
        if (detailedRecovery != null) {
            return OverlayVisionAttempt(
                result = detailedRecovery.result,
                sourceLabel = "${detailedRecovery.sourceLabel} ",
                failures = detailedRecovery.failures,
                tavernTierDetection = tavernTierDetection
            )
        }

        val tribeRecovery = recoverTribes(frame = frame, endpoints = endpoints)
        if (tribeRecovery != null) {
            return OverlayVisionAttempt(
                result = tribeRecovery.result,
                sourceLabel = "${tribeRecovery.sourceLabel} ",
                failures = tribeRecovery.failures,
                tavernTierDetection = tavernTierDetection
            )
        }

        return OverlayVisionAttempt(
            result = null,
            sourceLabel = "",
            failures = listOf("AI 5族补识别未拿到稳定结果"),
            tavernTierDetection = tavernTierDetection
        )
    }

    private suspend fun recoverDetailedVision(
        frame: CapturedFrame,
        endpoints: List<VisionEndpoint>,
        heroNameIndex: BattlegroundHeroNameIndex
    ): OverlayDetailedVisionRecovery? {
        val outcome = executeVisionFailover(
            endpoints = endpoints,
            attempt = { endpoint ->
                OpenAiCompatibleVisionProvider(
                    OpenAiCompatibleVisionConfig(
                        baseUrl = endpoint.baseUrl,
                        apiKey = endpoint.apiKey,
                        model = endpoint.model
                    )
                ).analyzeDetailed(
                    HeroSelectionVisionRequest(
                        frame = frame,
                        source = VisionRequestSource.HERO_SELECTION_AUTOMATION,
                        recognitionScope = VisionRecognitionScope.FULL
                    )
                )
            },
            validate = { execution ->
                val structural = HeroSelectionVisionValidator.validate(
                    execution.result,
                    requireCompleteTribes = false
                ).errors
                val semantic = HeroSelectionVisionSemanticValidator.validate(
                    execution.result,
                    heroNameIndex
                ).errors
                (structural + semantic)
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString("；", prefix = "校验失败: ")
            }
        )

        if (outcome is VisionFailoverOutcome.Success) {
            return OverlayDetailedVisionRecovery(
                result = outcome.value.result,
                sourceLabel = "${outcome.endpoint.label} 全量识别",
                failures = outcome.previousFailures
            )
        }

        return null
    }

    private suspend fun recoverTribes(
        frame: CapturedFrame,
        endpoints: List<VisionEndpoint>
    ): OverlayTribeRecovery? {
        val outcome = executeVisionFailover(
            endpoints = endpoints,
            attempt = { endpoint ->
                OpenAiCompatibleVisionProvider(
                    OpenAiCompatibleVisionConfig(
                        baseUrl = endpoint.baseUrl,
                        apiKey = endpoint.apiKey,
                        model = endpoint.model
                    )
                ).analyzeDetailed(
                    HeroSelectionVisionRequest(
                        frame = frame,
                        source = VisionRequestSource.HERO_SELECTION_AUTOMATION,
                        recognitionScope = VisionRecognitionScope.TRIBES_ONLY
                    )
                )
            },
            validate = { execution ->
                HeroSelectionVisionValidator.validate(execution.result)
                    .errors
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString("；", prefix = "校验失败: ")
            }
        )

        if (outcome is VisionFailoverOutcome.Success) {
            return OverlayTribeRecovery(
                result = outcome.value.result,
                sourceLabel = "${outcome.endpoint.label} 5族补识别",
                failures = outcome.previousFailures
            )
        }

        return null
    }

    private fun HeroSelectionVisionResult.mergeTribesFrom(
        fallback: HeroSelectionVisionResult?
    ): HeroSelectionVisionResult {
        if (HeroSelectionVisionValidator.hasCompleteTribes(this)) return this
        val fallbackTribes = fallback?.availableTribes?.distinct().orEmpty()
        if (fallbackTribes.size != 5) return this
        return copy(
            screenType = if (screenType == VisionScreenType.NON_TARGET && selectableHeroOptions.isNotEmpty()) {
                VisionScreenType.HERO_SELECTION
            } else {
                screenType
            },
            availableTribes = fallbackTribes
        )
    }

    private fun resolveRecognizedHeroes(
        heroOptions: List<HeroSelectionVisionHeroOption>,
        heroStatsCatalog: BattlegroundHeroStatsCatalog,
        cardStatsCatalog: BattlegroundCardStatsCatalog,
        heroNameIndex: BattlegroundHeroNameIndex,
        selectedTribes: Set<Tribe>,
        allStrategies: List<com.bgtactician.app.data.model.StrategyComp>
    ): List<ResolvedHeroStatOption> {
        return HeroSelectionRecommendationEngine.resolveRecognizedHeroes(
            heroOptions = heroOptions,
            heroStatsCatalog = heroStatsCatalog,
            cardStatsCatalog = cardStatsCatalog,
            heroNameIndex = heroNameIndex,
            selectedTribes = selectedTribes,
            allStrategies = allStrategies
        )
    }

    private fun blendHeroSelections(
        ocrOptions: List<HeroSelectionVisionHeroOption>,
        aiOptions: List<HeroSelectionVisionHeroOption>,
        heroStatsCatalog: BattlegroundHeroStatsCatalog,
        cardStatsCatalog: BattlegroundCardStatsCatalog,
        heroNameIndex: BattlegroundHeroNameIndex,
        selectedTribes: Set<Tribe>,
        allStrategies: List<com.bgtactician.app.data.model.StrategyComp>
    ): BlendedHeroSelection {
        val ocrResolved = resolveRecognizedHeroes(
            heroOptions = ocrOptions,
            heroStatsCatalog = heroStatsCatalog,
            cardStatsCatalog = cardStatsCatalog,
            heroNameIndex = heroNameIndex,
            selectedTribes = selectedTribes,
            allStrategies = allStrategies
        )
        val aiResolved = resolveRecognizedHeroes(
            heroOptions = aiOptions,
            heroStatsCatalog = heroStatsCatalog,
            cardStatsCatalog = cardStatsCatalog,
            heroNameIndex = heroNameIndex,
            selectedTribes = selectedTribes,
            allStrategies = allStrategies
        )
        val preferAiBase = aiResolved.size > ocrResolved.size
        val base = if (preferAiBase) aiOptions else ocrOptions
        val supplement = if (preferAiBase) ocrOptions else aiOptions
        val mergedBySlot = linkedMapOf<Int, HeroSelectionVisionHeroOption>()
        base.sortedBy(HeroSelectionVisionHeroOption::slot).forEach { option ->
            mergedBySlot[option.slot] = option
        }
        supplement.sortedBy(HeroSelectionVisionHeroOption::slot).forEach { option ->
            mergedBySlot.putIfAbsent(option.slot, option)
        }
        val mergedOptions = mergedBySlot.values.toList()
        val resolvedHeroes = resolveRecognizedHeroes(
            heroOptions = mergedOptions,
            heroStatsCatalog = heroStatsCatalog,
            cardStatsCatalog = cardStatsCatalog,
            heroNameIndex = heroNameIndex,
            selectedTribes = selectedTribes,
            allStrategies = allStrategies
        )
        val ocrSlots = ocrOptions.mapTo(hashSetOf(), HeroSelectionVisionHeroOption::slot)
        val aiContribution = mergedOptions.any { it.slot !in ocrSlots } ||
            (preferAiBase && aiOptions.isNotEmpty())
        val sourceLabel = when {
            mergedOptions.isEmpty() -> "本地OCR"
            preferAiBase && ocrOptions.isNotEmpty() -> "AI全量识别为主，本地OCR补位"
            preferAiBase -> "AI全量识别"
            aiContribution -> "本地OCR + AI补位"
            else -> "本地OCR"
        }
        return BlendedHeroSelection(
            options = mergedOptions,
            resolvedHeroes = resolvedHeroes,
            usesAiHeroes = aiContribution,
            sourceLabel = sourceLabel
        )
    }

    private fun VisionApiSettings.toVisionEndpoints(): List<VisionEndpoint> {
        val primary = baseUrl.takeIf(String::isNotBlank)
            ?.takeIf { apiKey.isNotBlank() && model.isNotBlank() }
            ?.let {
                VisionEndpoint(
                    label = "主模型",
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model
                )
            }
        val backup = backupBaseUrl.takeIf(String::isNotBlank)
            ?.takeIf { backupApiKey.isNotBlank() && backupModel.isNotBlank() }
            ?.let {
                VisionEndpoint(
                    label = "备用模型",
                    baseUrl = backupBaseUrl,
                    apiKey = backupApiKey,
                    model = backupModel
                )
            }
        return when (routingMode) {
            VisionRoutingMode.AUTO -> listOfNotNull(primary, backup)
            VisionRoutingMode.PRIMARY_ONLY -> listOfNotNull(primary)
            VisionRoutingMode.BACKUP_ONLY -> listOfNotNull(backup)
        }
    }

    private fun buildAiRecognitionDebugInfo(
        result: HeroSelectionVisionResult,
        sourceLabel: String,
        summaryText: String,
        tavernTierDetection: TavernTierDetection? = null
    ): AutoDetectDebugInfo {
        return AutoDetectDebugInfo(
            recognizedTribesLabel = result.availableTribes.joinToString(" / ") { it.label },
            rawText = summaryText,
            tavernTier = tavernTierDetection?.tier,
            tavernTierLabel = stableTavernTierLabel(tavernTierDetection),
            aiSourceLabel = sourceLabel.trim().ifBlank { null },
            aiModelLabel = result.modelName?.trim()?.takeIf(String::isNotBlank),
            aiRequestId = result.requestId?.trim()?.takeIf(String::isNotBlank),
            aiScreenTypeLabel = result.screenType.name.lowercase(),
            aiHeroesLabel = result.selectableHeroOptions
                .sortedBy(HeroSelectionVisionHeroOption::slot)
                .joinToString(" / ") { option ->
                    buildString {
                        append("槽")
                        append(option.slot + 1)
                        append(":")
                        append(option.name?.trim()?.takeIf(String::isNotBlank) ?: "未命名")
                        option.heroCardId?.trim()?.takeIf(String::isNotBlank)?.let { heroCardId ->
                            append(" [")
                            append(heroCardId)
                            append("]")
                        }
                    }
                }
                .takeIf(String::isNotBlank),
            aiSummaryLabel = result.rawSummary?.trim()?.takeIf(String::isNotBlank),
            lastUpdatedLabel = nowLabel()
        )
    }

    private fun buildHeroRecognitionDebugInfo(
        heroSelection: BlendedHeroSelection,
        ocrResult: HeroNameOcrDetector.HeroNameOcrResult?,
        stableOptions: List<HeroSelectionVisionHeroOption>,
        tribeAttempt: OverlayVisionAttempt?,
        summaryText: String,
        tavernTierDetection: TavernTierDetection? = null,
        duplicateHeroCardIds: Set<String> = emptySet(),
        chaoticSlots: Set<Int> = emptySet()
    ): AutoDetectDebugInfo {
        val attemptResult = tribeAttempt?.result
        return if (heroSelection.usesAiHeroes && attemptResult != null) {
            buildAiRecognitionDebugInfo(
                result = attemptResult.copy(heroOptions = heroSelection.options),
                sourceLabel = buildString {
                    append(tribeAttempt.sourceLabel.trim())
                    append("；英雄=")
                    append(heroSelection.sourceLabel)
                },
                summaryText = summaryText,
                tavernTierDetection = tavernTierDetection
            )
        } else {
            buildOcrRecognitionDebugInfo(
                ocrResult = ocrResult,
                stableOptions = stableOptions,
                tribeAttempt = tribeAttempt,
                summaryText = summaryText,
                tavernTierDetection = tavernTierDetection,
                duplicateHeroCardIds = duplicateHeroCardIds,
                chaoticSlots = chaoticSlots,
                heroSourceLabel = "英雄=${heroSelection.sourceLabel}",
                displayHeroOptions = heroSelection.options
            )
        }
    }

    private fun buildOcrRecognitionDebugInfo(
        ocrResult: HeroNameOcrDetector.HeroNameOcrResult?,
        stableOptions: List<HeroSelectionVisionHeroOption>,
        tribeAttempt: OverlayVisionAttempt?,
        summaryText: String,
        tavernTierDetection: TavernTierDetection? = null,
        duplicateHeroCardIds: Set<String> = emptySet(),
        chaoticSlots: Set<Int> = emptySet(),
        heroSourceLabel: String = "英雄=本地OCR",
        displayHeroOptions: List<HeroSelectionVisionHeroOption> = stableOptions
    ): AutoDetectDebugInfo {
        val sourceParts = buildList {
            add(heroSourceLabel)
            tribeAttempt?.sourceLabel
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { add("种族=$it") }
                ?: add("种族=沿用当前环境")
        }
        val heroSlotsLabel = displayHeroOptions
            .sortedBy(HeroSelectionVisionHeroOption::slot)
            .joinToString(" / ") { option ->
                buildString {
                    append("槽")
                    append(option.slot + 1)
                    append(":")
                    append(option.name?.trim()?.takeIf(String::isNotBlank) ?: "未命名")
                    option.heroCardId?.trim()?.takeIf(String::isNotBlank)?.let { heroCardId ->
                        append(" [")
                        append(heroCardId)
                        append("]")
                    }
                }
            }
            .takeIf(String::isNotBlank)
            ?: ocrResult?.slotResults
                ?.sortedBy { it.slot }
                ?.joinToString(" / ") { it.debugLabel }
                ?.takeIf(String::isNotBlank)
        val roiRectLabel = ocrResult?.slotResults
            ?.sortedBy { it.slot }
            ?.joinToString(" / ") {
                "槽${it.slot + 1}=${it.debugRoi.flattenToString()}"
            }
            ?.takeIf(String::isNotBlank)
        val headerLabel = buildList {
            if (stableOptions.isNotEmpty()) {
                add("稳定槽位=${stableOptions.size}")
            }
            if (duplicateHeroCardIds.isNotEmpty()) {
                add("重复英雄=${duplicateHeroCardIds.size}")
            }
            if (chaoticSlots.isNotEmpty()) {
                add("波动槽位=${chaoticSlots.sorted().joinToString(",") { (it + 1).toString() }}")
            }
        }.joinToString("；").takeIf(String::isNotBlank)

        return AutoDetectDebugInfo(
            recognizedTribesLabel = tribeAttempt?.result
                ?.availableTribes
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(" / ") { it.label },
            rawText = summaryText,
            tavernTier = tavernTierDetection?.tier,
            tavernTierLabel = stableTavernTierLabel(tavernTierDetection),
            aiSourceLabel = sourceParts.joinToString("；"),
            aiModelLabel = tribeAttempt?.result?.modelName?.trim()?.takeIf(String::isNotBlank),
            aiRequestId = tribeAttempt?.result?.requestId?.trim()?.takeIf(String::isNotBlank),
            aiScreenTypeLabel = if (stableOptions.isNotEmpty() || ocrResult?.heroOptions?.isNotEmpty() == true) {
                "hero_selection(local_ocr)"
            } else {
                tribeAttempt?.result?.screenType?.name?.lowercase()
            },
            aiHeroesLabel = heroSlotsLabel,
            roiRectLabel = roiRectLabel,
            aiSummaryLabel = tribeAttempt?.result?.rawSummary?.trim()?.takeIf(String::isNotBlank),
            viewportLabel = ocrResult?.viewport?.flattenToString(),
            headerLabel = headerLabel,
            lastUpdatedLabel = nowLabel()
        )
    }

    private fun formatTavernTierDebugLabel(
        detection: TavernTierDetection
    ): String {
        return buildString {
            detection.tier?.let { tier ->
                append("${tier}本")
                detection.sourceLabel?.takeIf(String::isNotBlank)?.let {
                    append(" · ")
                    append(it)
                }
                append(" · 置信 ")
                append(String.format(Locale.US, "%.2f", detection.confidence))
            } ?: run {
                append("未识别")
                detection.debugLabel?.takeIf(String::isNotBlank)?.let {
                    append(" · ")
                    append(it)
                }
            }
        }
    }

    private fun stableTavernTierLabel(
        detection: TavernTierDetection?
    ): String? {
        return detection
            ?.takeIf { it.tier != null }
            ?.let(::formatTavernTierDebugLabel)
    }

    private fun nowLabel(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private data class OverlayVisionAttempt(
        val result: HeroSelectionVisionResult?,
        val sourceLabel: String,
        val failures: List<String>,
        val tavernTierDetection: TavernTierDetection
    )

    private data class OverlayTribeRecovery(
        val result: HeroSelectionVisionResult,
        val sourceLabel: String,
        val failures: List<String> = emptyList()
    )

    private data class OverlayDetailedVisionRecovery(
        val result: HeroSelectionVisionResult,
        val sourceLabel: String,
        val failures: List<String> = emptyList()
    )

    private data class OverlayVisionSessionResult(
        val status: AutoDetectStatus,
        val debugInfo: AutoDetectDebugInfo,
        val selectedTribes: Set<Tribe>? = null,
        val recognizedHeroOptions: List<HeroSelectionVisionHeroOption>? = null,
        val recognizedHeroes: List<ResolvedHeroStatOption>? = null
    )

    private data class BlendedHeroSelection(
        val options: List<HeroSelectionVisionHeroOption>,
        val resolvedHeroes: List<ResolvedHeroStatOption>,
        val usesAiHeroes: Boolean,
        val sourceLabel: String
    )

    private fun persistDashboardPreferences() {
        preferences.saveDashboardPreferences(
            selectedTribes = selectedTribes,
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

        val foregroundType = if (mediaProjectionForegroundArmed || screenCaptureManager?.isActive() == true) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                foregroundType
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
        applyDetectChipPresentation()
        syncDetectChipPosition()
    }

    private fun applyDetectChipPresentation() {
        val view = detectChipView ?: return
        val params = detectChipLayoutParams ?: return
        params.flags = if (overlaySettings.interactionEnabled) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        view.alpha = overlaySettings.bubbleOpacityPercent / 100f
        windowManager.updateViewLayout(view, params)
    }

    private fun bubbleEdgeMarginPx(): Int {
        return (resources.displayMetrics.density * BUBBLE_EDGE_MARGIN_DP).toInt()
    }

    private fun bubbleDockX(view: View): Int {
        val screenWidth = resources.displayMetrics.widthPixels
        return (screenWidth - view.width - bubbleEdgeMarginPx()).coerceAtLeast(0)
    }

    private fun bubbleMaxY(view: View): Int {
        return (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0)
    }

    private fun detectChipGapPx(): Int {
        return (resources.displayMetrics.density * DETECT_CHIP_GAP_DP).toInt()
    }

    private fun detectChipTopOffsetPx(): Int {
        return (resources.displayMetrics.density * DETECT_CHIP_TOP_OFFSET_DP).toInt()
    }

    private fun syncDetectChipPosition() {
        val bubble = bubbleView ?: return
        val bubbleParams = bubbleLayoutParams ?: return
        val chip = detectChipView ?: return
        val chipParams = detectChipLayoutParams ?: return
        val gap = detectChipGapPx()
        val topOffset = detectChipTopOffsetPx()
        val isLandscape = resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels
        if (isLandscape) {
            chipParams.x = (bubbleParams.x - ((chip.width - bubble.width) / 2)).coerceAtLeast(0)
            chipParams.y = (bubbleParams.y - chip.height - gap).coerceAtLeast(0)
        } else {
            chipParams.x = (bubbleParams.x - chip.width - gap).coerceAtLeast(0)
            chipParams.y = (bubbleParams.y + topOffset).coerceAtLeast(0)
        }
        windowManager.updateViewLayout(chip, chipParams)
    }

    private fun syncHeroCardOverlayPosition() {
        val view = heroCardView ?: return
        val params = heroCardLayoutParams ?: return
        params.x = 0
        params.y = 0
        windowManager.updateViewLayout(view, params)
    }

    private fun snapBubbleToRightEdge(savePosition: Boolean) {
        val view = bubbleView ?: return
        val params = bubbleLayoutParams ?: return
        params.x = bubbleDockX(view)
        params.y = params.y.coerceIn(0, bubbleMaxY(view))
        windowManager.updateViewLayout(view, params)
        syncDetectChipPosition()
        if (savePosition) {
            preferences.saveOverlayPosition(params.x, params.y)
        }
    }

    private inline fun <reified T> Intent.intentParcelableExtra(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key)
        }
    }

    private inner class BubbleTouchListener : View.OnTouchListener {
        private var startY = 0
        private var touchY = 0f
        private var dragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = bubbleLayoutParams ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = params.y
                    touchY = event.rawY
                    dragging = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(deltaY) > 8) {
                        dragging = true
                    }
                    params.x = bubbleDockX(view)
                    params.y = (startY + deltaY).coerceIn(0, bubbleMaxY(view))
                    windowManager.updateViewLayout(view, params)
                    syncDetectChipPosition()
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    snapBubbleToRightEdge(savePosition = true)
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
