package com.bgtactician.app

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.net.Uri
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgtactician.app.autodetect.CapturedFrame
import com.bgtactician.app.autodetect.HeroNameOcrDetector
import com.bgtactician.app.autodetect.ScreenCapturePermissionStore
import com.bgtactician.app.autodetect.TavernTierDetector
import com.bgtactician.app.data.local.VisionApiSettings
import com.bgtactician.app.data.local.VisionRoutingMode
import com.bgtactician.app.data.model.HeroSelectionVisionResult
import com.bgtactician.app.data.model.HeroSelectionVisionHeroOption
import com.bgtactician.app.data.model.ResolvedHeroStatOption
import com.bgtactician.app.data.model.VisionScreenType
import com.bgtactician.app.data.model.Tribe
import com.bgtactician.app.overlay.OverlayPermissionHelper
import com.bgtactician.app.overlay.OverlayService
import com.bgtactician.app.data.repository.StrategyRepository
import com.bgtactician.app.ui.screen.HomeScreen
import com.bgtactician.app.ui.theme.BGTacticianTheme
import com.bgtactician.app.viewmodel.MainViewModel
import com.bgtactician.app.vision.HeroSelectionVisionSemanticValidator
import com.bgtactician.app.vision.HeroSelectionVisionValidator
import com.bgtactician.app.vision.HeroSelectionVisionRequest
import com.bgtactician.app.vision.OpenAiCompatibleVisionConfig
import com.bgtactician.app.vision.OpenAiCompatibleVisionException
import com.bgtactician.app.vision.OpenAiCompatibleVisionMetrics
import com.bgtactician.app.vision.OpenAiCompatibleVisionProvider
import com.bgtactician.app.vision.VisionEndpoint
import com.bgtactician.app.vision.VisionFailoverOutcome
import com.bgtactician.app.vision.VisionRecognitionScope
import com.bgtactician.app.vision.VisionRequestSource
import com.bgtactician.app.vision.executeVisionFailover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val coroutineScope = rememberCoroutineScope()

    var overlayPermissionGranted by remember {
        mutableStateOf(OverlayPermissionHelper.canDrawOverlays(context))
    }
    var imageDebugLoading by remember { mutableStateOf(false) }
    var imageDebugTitle by remember { mutableStateOf<String?>(null) }
    var imageDebugLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var imageDebugPreviewUri by remember { mutableStateOf<Uri?>(null) }
    var imageDebugPreviewAspectRatio by remember { mutableStateOf<Float?>(null) }
    var imageDebugPreviewOverlayVisible by remember { mutableStateOf(false) }
    var imageDebugPreviewHeroes by remember { mutableStateOf<List<ResolvedHeroStatOption>>(emptyList()) }
    var imageDebugPreviewOcrSlots by remember { mutableStateOf<List<ImageDebugOcrSlotPreview>>(emptyList()) }
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
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        imageDebugLoading = true
        imageDebugTitle = "正在执行图片识别"
        imageDebugLines = emptyList()
        imageDebugPreviewUri = uri
        imageDebugPreviewAspectRatio = null
        imageDebugPreviewOverlayVisible = false
        imageDebugPreviewHeroes = emptyList()
        imageDebugPreviewOcrSlots = emptyList()
        coroutineScope.launch {
            val snapshot = analyzeVisionImage(
                context = context,
                uri = uri,
                settings = VisionApiSettings(
                    baseUrl = uiState.visionBaseUrl.ifBlank { BuildConfig.DEFAULT_VISION_BASE_URL },
                    apiKey = uiState.visionApiKey.ifBlank { BuildConfig.DEFAULT_VISION_API_KEY },
                    model = uiState.visionModel.ifBlank { BuildConfig.DEFAULT_VISION_MODEL },
                    backupBaseUrl = uiState.visionBackupBaseUrl.ifBlank { BuildConfig.DEFAULT_VISION_BACKUP_BASE_URL },
                    backupApiKey = uiState.visionBackupApiKey.ifBlank { BuildConfig.DEFAULT_VISION_BACKUP_API_KEY },
                    backupModel = uiState.visionBackupModel.ifBlank { BuildConfig.DEFAULT_VISION_BACKUP_MODEL },
                    routingMode = uiState.visionRoutingMode
                )
            )
            imageDebugPreviewHeroes = snapshot.visionResult
                ?.let(viewModel::resolvePreviewHeroes)
                .orEmpty()
            imageDebugLoading = false
            imageDebugTitle = snapshot.title
            imageDebugPreviewAspectRatio = snapshot.previewAspectRatio
            imageDebugPreviewOverlayVisible = imageDebugPreviewHeroes.isNotEmpty()
            imageDebugPreviewOcrSlots = snapshot.ocrSlots
            imageDebugLines = buildList {
                addAll(snapshot.lines)
                if (imageDebugPreviewHeroes.isNotEmpty()) {
                    add("仅用于首页预览，未同步到正式识别")
                }
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

private suspend fun analyzeVisionImage(
    context: Context,
    uri: Uri,
    settings: VisionApiSettings
): ImageDebugSnapshot = withContext(Dispatchers.IO) {
    val startAt = System.currentTimeMillis()
    val decodeStartedAt = System.currentTimeMillis()
    val endpoints = settings.toVisionEndpoints()
    val heroNameIndex = StrategyRepository().loadHeroNameIndex(context)
    val heroNameOcrDetector = HeroNameOcrDetector()

    val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream)
    }?.copy(Bitmap.Config.ARGB_8888, false)
    val decodeMillis = System.currentTimeMillis() - decodeStartedAt

    if (bitmap == null) {
        return@withContext ImageDebugSnapshot(
            title = "图片读取失败",
            lines = listOf(
                "无法解码所选图片",
                "图片解码: ${decodeMillis} ms",
                "耗时: ${System.currentTimeMillis() - startAt} ms"
            ),
            previewAspectRatio = 1f
        )
    }

    val frame = CapturedFrame(
        bitmap = bitmap,
        width = bitmap.width,
        height = bitmap.height,
        timestampMillis = System.currentTimeMillis()
    )
    // 首页 AI 视觉测试只用于验证模板匹配命中率，不混其它本地推断。
    val tavernTierDetection = TavernTierDetector.detectTemplateOnly(
        context = context,
        frame = frame
    )
    val ocrResult = heroNameOcrDetector.detect(frame, heroNameIndex)
    val snapshot = analyzeWithFailover(
        frame = frame,
        decodeMillis = decodeMillis,
        startAt = startAt,
        endpoints = endpoints,
        tavernTierDetection = tavernTierDetection,
        ocrResult = ocrResult,
        heroNameIndex = heroNameIndex
    ).copy(
        previewAspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat(),
        ocrSlots = ocrResult.slotResults.map { slot ->
            slot.toPreview(bitmap.width, bitmap.height)
        }
    )

    bitmap.recycle()
    return@withContext snapshot
}

private suspend fun analyzeWithFailover(
    frame: CapturedFrame,
    decodeMillis: Long,
    startAt: Long,
    endpoints: List<VisionEndpoint>,
    tavernTierDetection: com.bgtactician.app.autodetect.TavernTierDetection,
    ocrResult: HeroNameOcrDetector.HeroNameOcrResult,
    heroNameIndex: com.bgtactician.app.data.model.BattlegroundHeroNameIndex
): ImageDebugSnapshot {
    if (endpoints.isEmpty()) {
        val localVision = HeroSelectionVisionResult(
            screenType = if (ocrResult.heroOptions.isNotEmpty()) VisionScreenType.HERO_SELECTION else VisionScreenType.UNKNOWN,
            heroOptions = ocrResult.heroOptions
        )
        return ImageDebugSnapshot(
            title = "本地 OCR 识图结果",
            lines = buildList {
                add(formatTavernTierLine(tavernTierDetection))
                add("来源: 英雄=本地OCR；种族=沿用当前环境")
                add("耗时: ${System.currentTimeMillis() - startAt} ms")
                add("图片解码: ${decodeMillis} ms")
                ocrResult.slotResults.sortedBy { it.slot }.forEach { add(it.debugLabel) }
                add("未配置 AI 5族补识别，当前只验证本地英雄 OCR")
            },
            visionResult = localVision
        )
    }

    val aiDetailed = recoverVisionDetailed(
        frame = frame,
        endpoints = endpoints,
        heroNameIndex = heroNameIndex
    )
    if (aiDetailed != null) {
        val mergedVision = aiDetailed.result.mergeTribesFrom(
            HeroSelectionVisionResult(heroOptions = ocrResult.heroOptions)
        )
        val validation = HeroSelectionVisionValidator.validate(
            mergedVision,
            requireCompleteTribes = false
        )
        return ImageDebugSnapshot(
            title = "AI 视觉识图结果",
            lines = buildList {
                add(formatTavernTierLine(tavernTierDetection))
                add("来源: 英雄/种族=${aiDetailed.sourceLabel}")
                add("耗时: ${System.currentTimeMillis() - startAt} ms")
                add("图片解码: ${decodeMillis} ms")
                if (mergedVision.availableTribes.isNotEmpty()) {
                    add("种族: ${mergedVision.availableTribes.joinToString(" / ") { it.label }}")
                }
                mergedVision.selectableHeroOptions
                    .sortedBy(HeroSelectionVisionHeroOption::slot)
                    .forEach { option ->
                        add(
                            buildString {
                                append("AI槽")
                                append(option.slot + 1)
                                append(":")
                                append(option.name?.trim()?.ifBlank { "未命名" } ?: "未命名")
                                option.heroCardId?.takeIf(String::isNotBlank)?.let {
                                    append(" [")
                                    append(it)
                                    append("]")
                                }
                            }
                        )
                    }
                ocrResult.slotResults.sortedBy { it.slot }.forEach { add("OCR${it.debugLabel}") }
                add("校验: ${if (validation.isValid) "通过" else "失败"}")
                if (!validation.isValid) {
                    add("错误: ${validation.errors.joinToString("；")}")
                }
                aiDetailed.failures.forEach { add("回退失败: $it") }
                mergedVision.modelName?.takeIf { it.isNotBlank() }?.let { add("model: $it") }
                mergedVision.rawSummary?.takeIf { it.isNotBlank() }?.let { add("summary: $it") }
            },
            visionResult = mergedVision
        )
    }

    val tribeRecovery = recoverTribes(frame = frame, endpoints = endpoints)
    val vision = HeroSelectionVisionResult(
        screenType = if (ocrResult.heroOptions.isNotEmpty()) {
            VisionScreenType.HERO_SELECTION
        } else {
            tribeRecovery?.result?.screenType ?: VisionScreenType.UNKNOWN
        },
        availableTribes = tribeRecovery?.result?.availableTribes.orEmpty(),
        heroOptions = ocrResult.heroOptions,
        modelName = tribeRecovery?.result?.modelName,
        requestId = tribeRecovery?.result?.requestId,
        rawSummary = tribeRecovery?.result?.rawSummary
    )
    val validation = HeroSelectionVisionValidator.validate(
        vision,
        requireCompleteTribes = false
    )
    return if (tribeRecovery != null) {
        ImageDebugSnapshot(
            title = if (ocrResult.heroOptions.isNotEmpty()) "本地 OCR + AI 环境识别" else "AI 环境识别结果",
            lines = buildList {
                add(formatTavernTierLine(tavernTierDetection))
                add("来源: 英雄=本地OCR；种族=${tribeRecovery.sourceLabel}")
                add("耗时: ${System.currentTimeMillis() - startAt} ms")
                add("图片解码: ${decodeMillis} ms")
                if (vision.availableTribes.isNotEmpty()) {
                    add("种族: ${vision.availableTribes.joinToString(" / ") { it.label }}")
                }
                ocrResult.slotResults.sortedBy { it.slot }.forEach { add(it.debugLabel) }
                add("校验: ${if (validation.isValid) "通过" else "失败"}")
                if (!validation.isValid) {
                    add("错误: ${validation.errors.joinToString("；")}")
                }
                tribeRecovery.failures.forEach { add("补识别失败: $it") }
                tribeRecovery.result.modelName?.takeIf { it.isNotBlank() }?.let { add("model: $it") }
                tribeRecovery.result.rawSummary?.takeIf { it.isNotBlank() }?.let { add("summary: $it") }
            },
            visionResult = vision
        )
    } else {
        ImageDebugSnapshot(
            title = if (ocrResult.heroOptions.isNotEmpty()) "本地 OCR 识图结果（5族未稳定）" else "识图失败",
            lines = buildList {
                add(formatTavernTierLine(tavernTierDetection))
                add("来源: 英雄=本地OCR；种族=AI补识别失败")
                add("耗时: ${System.currentTimeMillis() - startAt} ms")
                add("图片解码: ${decodeMillis} ms")
                ocrResult.slotResults.sortedBy { it.slot }.forEach { add(it.debugLabel) }
                add("AI 5族补识别未拿到稳定结果")
            },
            visionResult = vision.takeIf { ocrResult.heroOptions.isNotEmpty() }
        )
    }
}

private fun formatTavernTierLine(
    detection: com.bgtactician.app.autodetect.TavernTierDetection
): String {
    return buildString {
        append("本地酒馆等级: ")
        val tier = detection.tier
        if (tier != null) {
            append("${tier}本")
            detection.sourceLabel?.let {
                append(" · ")
                append(it)
            }
            append(" · 置信 ")
            append(String.format("%.2f", detection.confidence))
            detection.debugLabel?.takeIf { it.isNotBlank() }?.let {
                append(" · ")
                append(it)
            }
        } else {
            append("未识别")
            detection.debugLabel?.takeIf { it.isNotBlank() }?.let {
                append(" · ")
                append(it)
            }
        }
        append(" · 模式 TEMPLATE_ONLY")
        append(" · 非AI")
    }
}

private data class TribeRecoverySnapshot(
    val result: HeroSelectionVisionResult,
    val sourceLabel: String,
    val failures: List<String> = emptyList()
)

private data class DetailedVisionRecoverySnapshot(
    val result: HeroSelectionVisionResult,
    val sourceLabel: String,
    val failures: List<String> = emptyList()
)

private suspend fun recoverVisionDetailed(
    frame: CapturedFrame,
    endpoints: List<VisionEndpoint>,
    heroNameIndex: com.bgtactician.app.data.model.BattlegroundHeroNameIndex
): DetailedVisionRecoverySnapshot? {
    val aiOutcome = executeVisionFailover(
        endpoints = endpoints,
        attempt = { endpoint ->
            OpenAiCompatibleVisionProvider(
                OpenAiCompatibleVisionConfig(
                    baseUrl = endpoint.baseUrl,
                    apiKey = endpoint.apiKey,
                    model = endpoint.model
                )
            ).analyzeVisionFrameDetailed(frame)
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

    if (aiOutcome is VisionFailoverOutcome.Success) {
        return DetailedVisionRecoverySnapshot(
            result = aiOutcome.value.result,
            sourceLabel = "${aiOutcome.endpoint.label} / 全量识别",
            failures = aiOutcome.previousFailures
        )
    }
    return null
}

private suspend fun recoverTribes(
    frame: CapturedFrame,
    endpoints: List<VisionEndpoint>
): TribeRecoverySnapshot? {
    val aiOutcome = executeVisionFailover(
        endpoints = endpoints,
        attempt = { endpoint ->
            OpenAiCompatibleVisionProvider(
                OpenAiCompatibleVisionConfig(
                    baseUrl = endpoint.baseUrl,
                    apiKey = endpoint.apiKey,
                    model = endpoint.model
                )
            ).analyzeDetailed(
                request = HeroSelectionVisionRequest(
                    frame = frame,
                    source = VisionRequestSource.OFFLINE_DEBUG,
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

    if (aiOutcome is VisionFailoverOutcome.Success) {
        return TribeRecoverySnapshot(
            result = aiOutcome.value.result,
            sourceLabel = "${aiOutcome.endpoint.label} / 5族补识别",
            failures = aiOutcome.previousFailures
        )
    }

    val failures = if (aiOutcome is VisionFailoverOutcome.Failure) aiOutcome.failures else emptyList()
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

private suspend fun OpenAiCompatibleVisionProvider.analyzeVisionFrameDetailed(frame: CapturedFrame) =
    analyzeDetailed(
        request = com.bgtactician.app.vision.HeroSelectionVisionRequest(
            frame = frame,
            recognitionScope = com.bgtactician.app.vision.VisionRecognitionScope.FULL
        )
    )

private data class ImageDebugSnapshot(
    val title: String,
    val lines: List<String>,
    val visionResult: HeroSelectionVisionResult? = null,
    val previewAspectRatio: Float? = null,
    val ocrSlots: List<ImageDebugOcrSlotPreview> = emptyList()
)

data class ImageDebugOcrSlotPreview(
    val slot: Int,
    val leftRatio: Float,
    val topRatio: Float,
    val rightRatio: Float,
    val bottomRatio: Float,
    val label: String,
    val matched: Boolean
)

private fun HeroNameOcrDetector.HeroNameOcrSlotResult.toPreview(
    imageWidth: Int,
    imageHeight: Int
): ImageDebugOcrSlotPreview {
    return ImageDebugOcrSlotPreview(
        slot = slot,
        leftRatio = debugRoi.left.toFloat() / imageWidth.coerceAtLeast(1),
        topRatio = debugRoi.top.toFloat() / imageHeight.coerceAtLeast(1),
        rightRatio = debugRoi.right.toFloat() / imageWidth.coerceAtLeast(1),
        bottomRatio = debugRoi.bottom.toFloat() / imageHeight.coerceAtLeast(1),
        label = buildSlotPreviewLabel(slot, rawName, matchedHeroCardId, score, debugRoi),
        matched = option != null
    )
}

private fun buildSlotPreviewLabel(
    slot: Int,
    rawName: String?,
    matchedHeroCardId: String?,
    score: Float,
    roi: Rect
): String {
    return buildString {
        append("槽")
        append(slot + 1)
        append(" ")
        append(rawName ?: "未识别")
        if (!matchedHeroCardId.isNullOrBlank()) {
            append(" @")
            append(String.format("%.2f", score))
        }
        append(" (")
        append(roi.left)
        append(",")
        append(roi.top)
        append(")-(")
        append(roi.right)
        append(",")
        append(roi.bottom)
        append(")")
    }
}

private fun Throwable.toDebugLines(): List<String> {
    val lines = mutableListOf<String>()
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < 6) {
        val prefix = if (depth == 0) "错误" else "原因$depth"
        val message = current.message?.trim().orEmpty()
        if (message.isNotBlank()) {
            message.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEachIndexed { index, line ->
                    lines += if (index == 0) "$prefix: $line" else line
                }
        } else {
            lines += "$prefix: ${current::class.java.simpleName}"
        }
        current = current.cause
        depth += 1
    }
    return lines
}

private fun Throwable.findVisionMetrics(): OpenAiCompatibleVisionMetrics? {
    var current: Throwable? = this
    while (current != null) {
        if (current is OpenAiCompatibleVisionException) {
            return current.metrics
        }
        current = current.cause
    }
    return null
}

private fun OpenAiCompatibleVisionMetrics.toDebugLines(): List<String> {
    return buildList {
        if (imageEncodeMillis > 0) add("图片编码: ${imageEncodeMillis} ms")
        if (networkMillis > 0) add("网络请求: ${networkMillis} ms")
        if (responseDecodeMillis > 0) add("响应解析: ${responseDecodeMillis} ms")
        if (jsonExtractMillis > 0) add("JSON 提取: ${jsonExtractMillis} ms")
        if (resultDecodeMillis > 0) add("结果解码: ${resultDecodeMillis} ms")
        if (imageBytes > 0) add("图片大小: ${imageBytes / 1024} KB")
        if (requestBodyBytes > 0) add("请求体大小: ${requestBodyBytes / 1024} KB")
        finishReason?.takeIf { it.isNotBlank() }?.let { add("finish_reason: $it") }
        promptTokens?.let { add("prompt_tokens: $it") }
        completionTokens?.let { add("completion_tokens: $it") }
        totalTokens?.let { add("total_tokens: $it") }
    }
}
