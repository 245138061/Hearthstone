package com.bgtactician.app

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.bgtactician.app.autodetect.ScreenCapturePermissionStore
import com.bgtactician.app.data.local.VisionApiSettings
import com.bgtactician.app.data.local.VisionRoutingMode
import com.bgtactician.app.data.model.HeroSelectionVisionResult
import com.bgtactician.app.data.model.HeroSelectionVisionHeroOption
import com.bgtactician.app.data.model.ResolvedHeroStatOption
import com.bgtactician.app.data.model.VisionScreenType
import com.bgtactician.app.data.model.Tribe
import com.bgtactician.app.overlay.OverlayPermissionHelper
import com.bgtactician.app.overlay.OverlayService
import com.bgtactician.app.ui.screen.HomeScreen
import com.bgtactician.app.ui.theme.BGTacticianTheme
import com.bgtactician.app.viewmodel.MainViewModel
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
        imageDebugTitle = "正在请求 AI 识图"
        imageDebugLines = emptyList()
        imageDebugPreviewUri = uri
        imageDebugPreviewAspectRatio = null
        imageDebugPreviewOverlayVisible = false
        imageDebugPreviewHeroes = emptyList()
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
        },
        onUpdateVisionRoutingMode = viewModel::updateVisionRoutingMode,
        imageDebugLoading = imageDebugLoading,
        imageDebugTitle = imageDebugTitle,
        imageDebugLines = imageDebugLines,
        imageDebugPreviewImage = imageDebugPreviewUri,
        imageDebugPreviewAspectRatio = imageDebugPreviewAspectRatio,
        imageDebugPreviewOverlayVisible = imageDebugPreviewOverlayVisible,
        imageDebugPreviewHeroes = imageDebugPreviewHeroes,
        onPickAiVisionImage = {
            imagePickerLauncher.launch("image/*")
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
    if (endpoints.isEmpty()) {
        return@withContext ImageDebugSnapshot(
            title = "AI 识图配置不完整",
            lines = listOf(
                "未配置可用的主模型或备用模型",
                "耗时: ${System.currentTimeMillis() - startAt} ms"
            ),
            previewAspectRatio = 1f
        )
    }

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
    val snapshot = analyzeWithFailover(
        frame = frame,
        decodeMillis = decodeMillis,
        startAt = startAt,
        endpoints = endpoints
    ).copy(previewAspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat())

    bitmap.recycle()
    return@withContext snapshot
}

private suspend fun analyzeWithFailover(
    frame: CapturedFrame,
    decodeMillis: Long,
    startAt: Long,
    endpoints: List<VisionEndpoint>
): ImageDebugSnapshot {
    return when (
        val outcome = executeVisionFailover(
            endpoints = endpoints,
            attempt = { endpoint ->
                val provider = OpenAiCompatibleVisionProvider(
                    OpenAiCompatibleVisionConfig(
                        baseUrl = endpoint.baseUrl,
                        apiKey = endpoint.apiKey,
                        model = endpoint.model
                    )
                )
                provider.analyzeVisionFrameDetailed(frame)
            },
            validate = { execution ->
                val validation = HeroSelectionVisionValidator.validate(
                    execution.result,
                    requireCompleteTribes = false
                )
                validation.errors.takeIf { !validation.isValid }?.joinToString("；", prefix = "校验失败: ")
            }
        )
    ) {
        is VisionFailoverOutcome.Success -> {
            val execution = outcome.value
            val endpoint = outcome.endpoint
            val tribeRecovery = if (HeroSelectionVisionValidator.hasCompleteTribes(execution.result)) {
                null
            } else {
                recoverTribes(frame = frame, endpoints = endpoints)
            }
            val vision = execution.result.mergeTribesFrom(tribeRecovery?.result)
            val validation = HeroSelectionVisionValidator.validate(vision)
            val hasHeroes = vision.selectableHeroOptions.isNotEmpty()
            val title = when {
                !validation.isValid && hasHeroes -> "AI 识图结果（5族未稳定）"
                outcome.previousFailures.isEmpty() -> "AI 识图结果"
                else -> "AI 识图结果（已切换备用模型）"
            }
            ImageDebugSnapshot(
                title = title,
                lines = buildList {
                    add("来源: ${endpoint.label}")
                    add("耗时: ${System.currentTimeMillis() - startAt} ms")
                    add("图片解码: ${decodeMillis} ms")
                    addAll(execution.metrics.toDebugLines())
                    add("screen_type: ${vision.screenType}")
                    if (vision.availableTribes.isNotEmpty()) {
                        add("种族: ${vision.availableTribes.joinToString(" / ") { it.label }}")
                    }
                    if (vision.selectableHeroOptions.isNotEmpty()) {
                        add("可用英雄: ${vision.selectableHeroOptions.joinToString(" / ") { option -> option.name ?: "未知英雄" }}")
                    }
                    tribeRecovery?.sourceLabel?.let { add("种族补识别: $it") }
                    add("校验: ${if (validation.isValid) "通过" else "失败"}")
                    if (!validation.isValid) {
                        add("错误: ${validation.errors.joinToString("；")}")
                        if (hasHeroes) {
                            add("已保留英雄识别结果，5族暂未稳定")
                        }
                    }
                    outcome.previousFailures.forEach { add("前序失败: $it") }
                    tribeRecovery?.failures?.forEach { add("补识别失败: $it") }
                    vision.modelName?.let { add("model: $it") }
                    vision.confidence?.let { add("confidence: $it") }
                    vision.rawSummary?.takeIf { it.isNotBlank() }?.let { add("summary: $it") }
                },
                visionResult = vision.takeIf { validation.isValid || hasHeroes }
            )
        }
        is VisionFailoverOutcome.Failure -> {
            val tribeRecovery = recoverTribes(frame = frame, endpoints = endpoints)
            if (tribeRecovery != null) {
                ImageDebugSnapshot(
                    title = "AI 识图结果（只识别到环境）",
                    lines = buildList {
                        add("来源: ${tribeRecovery.sourceLabel}")
                        add("耗时: ${System.currentTimeMillis() - startAt} ms")
                        add("图片解码: ${decodeMillis} ms")
                        outcome.failures.forEach { add("模型失败: $it") }
                        tribeRecovery.failures.forEach { add("补识别失败: $it") }
                        add("种族: ${tribeRecovery.result.availableTribes.joinToString(" / ") { it.label }}")
                        add("英雄未稳定识别到，可继续测试环境识别")
                    },
                    visionResult = tribeRecovery.result
                )
            } else {
                ImageDebugSnapshot(
                    title = "AI 识图失败",
                    lines = buildList {
                        add("耗时: ${System.currentTimeMillis() - startAt} ms")
                        add("图片解码: ${decodeMillis} ms")
                        outcome.failures.forEach { add("失败: $it") }
                        add("AI 5族补识别也未拿到稳定结果")
                    }
                )
            }
        }
    }
}

private data class TribeRecoverySnapshot(
    val result: HeroSelectionVisionResult,
    val sourceLabel: String,
    val failures: List<String> = emptyList()
)

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
    val previewAspectRatio: Float? = null
)

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
