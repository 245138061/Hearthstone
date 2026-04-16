package com.bgtactician.app.autodetect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class TavernTierDetection(
    val tier: Int? = null,
    val confidence: Float = 0f,
    val sourceLabel: String? = null,
    val viewport: Rect? = null,
    val roi: Rect? = null,
    val debugLabel: String? = null
)

object TavernTierDetector {

    fun detectTemplateOnly(
        context: Context,
        frame: CapturedFrame
    ): TavernTierDetection {
        val viewport = GameViewportDetector.detect(frame.bitmap)
            ?: return TavernTierDetection(debugLabel = "未识别到游戏视口")
        detectLockedLobbyBadgeTierWithTemplates(context, frame.bitmap, viewport)?.let {
            return it.copy(viewport = viewport)
        }

        return TavernTierDetection(
            viewport = viewport,
            debugLabel = "模板未命中或结果不稳定"
        )
    }

    fun detect(
        context: Context,
        frame: CapturedFrame,
        allowHeroSelectionInference: Boolean = true,
        allowTemplateMatching: Boolean = true
    ): TavernTierDetection {
        val viewport = GameViewportDetector.detect(frame.bitmap)
            ?: return TavernTierDetection(debugLabel = "未识别到游戏视口")

        val heroSelectionDetection = if (allowHeroSelectionInference) {
            detectHeroSelectionTier(frame.bitmap, viewport)
        } else {
            null
        }
        val templateDetection = if (allowTemplateMatching) {
            detectLobbyBadgeTierWithTemplates(context, frame.bitmap, viewport)
        } else {
            null
        }
        val countedDetection = detectLobbyBadgeTier(
            bitmap = frame.bitmap,
            viewport = viewport,
            relaxed = !allowHeroSelectionInference
        )

        resolvePreferredDetection(
            heroSelectionDetection = heroSelectionDetection,
            templateDetection = templateDetection,
            countedDetection = countedDetection
        )?.let { detection ->
            return detection.copy(viewport = viewport)
        }

        return TavernTierDetection(
            viewport = viewport,
            debugLabel = "未识别到稳定酒馆星级"
        )
    }

    fun detect(
        frame: CapturedFrame,
        allowHeroSelectionInference: Boolean = true
    ): TavernTierDetection {
        val viewport = GameViewportDetector.detect(frame.bitmap)
            ?: return TavernTierDetection(debugLabel = "未识别到游戏视口")

        val heroSelectionDetection = if (allowHeroSelectionInference) {
            detectHeroSelectionTier(frame.bitmap, viewport)
        } else {
            null
        }
        val countedDetection = detectLobbyBadgeTier(
            bitmap = frame.bitmap,
            viewport = viewport,
            relaxed = !allowHeroSelectionInference
        )

        resolvePreferredDetection(
            heroSelectionDetection = heroSelectionDetection,
            templateDetection = null,
            countedDetection = countedDetection
        )?.let { detection ->
            return detection.copy(viewport = viewport)
        }

        return TavernTierDetection(
            viewport = viewport,
            debugLabel = "未识别到稳定酒馆星级"
        )
    }

    private fun resolvePreferredDetection(
        heroSelectionDetection: TavernTierDetection?,
        templateDetection: TavernTierDetection?,
        countedDetection: TavernTierDetection?
    ): TavernTierDetection? {
        if (heroSelectionDetection == null) {
            return templateDetection ?: countedDetection
        }

        resolveTierBadgeConsensus(templateDetection, countedDetection)?.let { return it }

        templateDetection
            ?.takeIf(::isStrongTierBadgeDetection)
            ?.let { return it }
        countedDetection
            ?.takeIf(::isStrongTierBadgeDetection)
            ?.let { return it }

        return heroSelectionDetection
    }

    private fun resolveTierBadgeConsensus(
        templateDetection: TavernTierDetection?,
        countedDetection: TavernTierDetection?
    ): TavernTierDetection? {
        val templateTier = templateDetection?.tier
        val countedTier = countedDetection?.tier
        if (templateTier == null || countedTier == null) return null
        if (templateTier != countedTier || templateTier <= 1) return null
        return if (templateDetection.confidence >= countedDetection.confidence) {
            templateDetection
        } else {
            countedDetection
        }
    }

    private fun isStrongTierBadgeDetection(detection: TavernTierDetection): Boolean {
        val tier = detection.tier ?: return false
        return tier >= 2 && detection.confidence >= STRONG_BADGE_CONFIDENCE
    }

    private fun detectLobbyBadgeTierWithTemplates(
        context: Context,
        bitmap: Bitmap,
        viewport: Rect
    ): TavernTierDetection? {
        if (!OpenCVLoader.initLocal()) return null

        val searchRoi = Rect(
            viewport.left + (viewport.width() * 0.28f).toInt(),
            viewport.top + (viewport.height() * 0.04f).toInt(),
            viewport.left + (viewport.width() * 0.64f).toInt(),
            viewport.top + (viewport.height() * 0.26f).toInt()
        ).coerceInside(bitmap.width, bitmap.height) ?: return null

        val components = collectMagentaComponents(bitmap, searchRoi)
        val templates = loadTemplates(context)
        if (components.isEmpty() || templates.isEmpty()) return null

        val candidates = clusterBadgeComponents(components)
            .mapNotNull { bounds ->
                evaluateTemplateBadgeCandidate(
                    bitmap = bitmap,
                    viewport = viewport,
                    bounds = bounds,
                    templates = templates
                )
            }
            .sortedByDescending(TemplateBadgeCandidate::score)

        val best = candidates.firstOrNull() ?: return null
        if (best.confidence < MIN_TEMPLATE_CONFIDENCE) return null
        return TavernTierDetection(
            tier = best.tier,
            confidence = best.confidence,
            sourceLabel = "模板匹配",
            roi = best.bounds,
            debugLabel = buildString {
                append("tier=")
                append(best.tier)
                append(" score=")
                append(best.matchScore.formatScore())
                append(" margin=")
                append(best.margin.formatScore())
                append(" scale=")
                append(best.scale.formatScore())
                append(" gold=")
                append(best.goldRatio.formatScore())
                append(" magenta=")
                append(best.magentaRatio.formatScore())
            }
        )
    }

    private fun detectLockedLobbyBadgeTierWithTemplates(
        context: Context,
        bitmap: Bitmap,
        viewport: Rect
    ): TavernTierDetection? {
        if (!OpenCVLoader.initLocal()) return null

        val searchRoi = buildTemplateOnlyBadgeSearchRoi(bitmap, viewport) ?: return null

        val templates = loadTemplates(context)
        if (templates.isEmpty()) return null

        val directDetection = detectLockedLobbyBadgeTierWithDirectTemplates(
            bitmap = bitmap,
            viewport = viewport,
            searchRoi = searchRoi,
            templates = templates
        )
        val anchoredDetection = detectLockedLobbyBadgeTierWithAnchoredTemplates(
            bitmap = bitmap,
            viewport = viewport,
            searchRoi = searchRoi,
            templates = templates
        )
        return resolveTemplateOnlyDetection(
            directDetection = directDetection,
            anchoredDetection = anchoredDetection
        )
    }

    private fun detectLockedLobbyBadgeTierWithDirectTemplates(
        bitmap: Bitmap,
        viewport: Rect,
        searchRoi: Rect,
        templates: List<TavernTierTemplate>
    ): TavernTierDetection? {
        val searchBitmap = bitmap.safeCrop(searchRoi) ?: return null
        val searchGray = bitmapToGrayMat(searchBitmap)
        searchBitmap.recycle()

        try {
            val ranked = keepBestMatchPerTier(
                templates.mapNotNull { template ->
                    matchTemplateAtBestScale(
                        roiGray = searchGray,
                        template = template,
                        scales = TEMPLATE_ONLY_DIRECT_SCALES
                    )
                }
            )

            val best = ranked.firstOrNull() ?: return null
            val second = ranked.getOrNull(1)
            val margin = best.score - (second?.score ?: 0f)
            if (best.score < TEMPLATE_ONLY_DIRECT_MIN_MATCH_SCORE || margin < TEMPLATE_ONLY_DIRECT_MIN_MARGIN) {
                return null
            }

            val absoluteBounds = Rect(
                searchRoi.left + best.bounds.left,
                searchRoi.top + best.bounds.top,
                searchRoi.left + best.bounds.right,
                searchRoi.top + best.bounds.bottom
            ).coerceInside(bitmap.width, bitmap.height) ?: return null
            if (!isTemplateBadgeBoundsPlausible(viewport, absoluteBounds, templateOnly = true)) return null

            if (!passesTemplateOnlyBadgePosition(viewport, absoluteBounds)) return null

            val detectionRoi = expandTemplateOnlyDetectionRoi(
                bitmap = bitmap,
                viewport = viewport,
                bounds = absoluteBounds
            ) ?: return null
            val centerXRatio = (absoluteBounds.centerX() - viewport.left).toFloat() / viewport.width().coerceAtLeast(1)
            val goldRatio = coarsePixelRatio(bitmap, detectionRoi) { red, green, blue ->
                isGoldStarPixel(Color.rgb(red, green, blue))
            }
            val magentaRatio = coarsePixelRatio(bitmap, detectionRoi) { red, green, blue ->
                isMagentaBadgePixel(Color.rgb(red, green, blue))
            }
            if (best.tier == 1) {
                if (goldRatio < TEMPLATE_ONLY_DIRECT_MIN_GOLD_RATIO) return null
            } else {
                if (goldRatio < TEMPLATE_ONLY_DIRECT_MIN_GOLD_RATIO || magentaRatio < TEMPLATE_ONLY_DIRECT_MIN_MAGENTA_RATIO) {
                    return null
                }
            }

            val confidence = (
                best.score * 0.74f +
                    margin * 1.25f +
                    goldRatio * 0.14f +
                    magentaRatio * 0.18f +
                    (1f - abs(centerXRatio - TEMPLATE_ONLY_TARGET_CENTER_X) / TEMPLATE_ONLY_CENTER_X_TOLERANCE)
                        .coerceIn(0f, 1f) * 0.08f
                ).coerceIn(0f, 0.99f)

            return TavernTierDetection(
                tier = best.tier,
                confidence = confidence,
                sourceLabel = "模板匹配-直接",
                roi = detectionRoi,
                debugLabel = buildString {
                    append("direct tier=")
                    append(best.tier)
                    append(" score=")
                    append(best.score.formatScore())
                    append(" margin=")
                    append(margin.formatScore())
                    append(" scale=")
                    append(best.scale.formatScore())
                    append(" gold=")
                    append(goldRatio.formatScore())
                    append(" magenta=")
                    append(magentaRatio.formatScore())
                }
            )
        } finally {
            searchGray.release()
        }
    }

    private fun detectLockedLobbyBadgeTierWithAnchoredTemplates(
        bitmap: Bitmap,
        viewport: Rect,
        searchRoi: Rect,
        templates: List<TavernTierTemplate>
    ): TavernTierDetection? {
        val components = collectMagentaComponents(bitmap, searchRoi)
        if (components.isEmpty()) return null

        val candidates = clusterBadgeComponents(components)
            .mapNotNull { bounds ->
                if (!isTemplateBadgeBoundsPlausible(viewport, bounds, templateOnly = true)) return@mapNotNull null
                if (!passesTemplateOnlyBadgePosition(viewport, bounds)) return@mapNotNull null

                val candidateRoi = expandedRect(bounds, 6)
                    .coerceInside(bitmap.width, bitmap.height)
                    ?: return@mapNotNull null
                val magentaRatio = coarsePixelRatio(bitmap, candidateRoi) { red, green, blue ->
                    isMagentaBadgePixel(Color.rgb(red, green, blue))
                }
                if (magentaRatio < TEMPLATE_ONLY_ANCHORED_MIN_MAGENTA_RATIO) return@mapNotNull null

                val candidateBitmap = bitmap.safeCrop(candidateRoi) ?: return@mapNotNull null
                val candidateGray = bitmapToGrayMat(candidateBitmap)
                candidateBitmap.recycle()
                try {
                    val ranked = keepBestTemplateResultPerTier(
                        templates.map { template ->
                            TemplateMatchResult(
                                tier = template.tier,
                                score = compareTemplateAtFixedSize(candidateGray, template.gray)
                            )
                        }
                    )

                    val best = ranked.firstOrNull() ?: return@mapNotNull null
                    val second = ranked.getOrNull(1)
                    val margin = best.score - (second?.score ?: 0f)
                    if (best.score < TEMPLATE_ONLY_ANCHORED_MIN_SCORE || margin < TEMPLATE_ONLY_ANCHORED_MIN_MARGIN) {
                        return@mapNotNull null
                    }

                    TemplateBadgeCandidate(
                        tier = best.tier,
                        score = best.score + margin * 2f + magentaRatio,
                        confidence = (
                            best.score * 0.78f +
                                margin * 1.6f +
                                magentaRatio * 0.18f
                            ).coerceIn(0f, 0.95f),
                        bounds = candidateRoi,
                        matchScore = best.score,
                        margin = margin,
                        scale = 1f,
                        goldRatio = 0f,
                        magentaRatio = magentaRatio
                    )
                } finally {
                    candidateGray.release()
                }
            }
            .sortedByDescending(TemplateBadgeCandidate::score)

        val best = candidates.firstOrNull() ?: return null
        return TavernTierDetection(
            tier = best.tier,
            confidence = best.confidence,
            sourceLabel = "模板匹配-锚点",
            roi = best.bounds,
            debugLabel = buildString {
                append("anchored tier=")
                append(best.tier)
                append(" score=")
                append(best.matchScore.formatScore())
                append(" margin=")
                append(best.margin.formatScore())
                append(" magenta=")
                append(best.magentaRatio.formatScore())
            }
        )
    }

    private fun evaluateTemplateBadgeCandidate(
        bitmap: Bitmap,
        viewport: Rect,
        bounds: Rect,
        templates: List<TavernTierTemplate>
    ): TemplateBadgeCandidate? {
        if (!isTemplateBadgeBoundsPlausible(viewport, bounds, templateOnly = false)) return null

        val aspectRatio = bounds.width().toFloat() / bounds.height().coerceAtLeast(1)
        if (aspectRatio !in 0.22f..1.25f) return null

        val centerXRatio = (bounds.centerX() - viewport.left).toFloat() / viewport.width().coerceAtLeast(1)
        val centerYRatio = (bounds.centerY() - viewport.top).toFloat() / viewport.height().coerceAtLeast(1)
        if (centerXRatio !in 0.30f..0.62f || centerYRatio !in 0.06f..0.25f) return null

        val candidateRoi = expandedRect(bounds, 4)
            .coerceInside(bitmap.width, bitmap.height)
            ?: return null
        val candidateBitmap = bitmap.safeCrop(candidateRoi) ?: return null
        val candidateGray = bitmapToGrayMat(candidateBitmap)
        candidateBitmap.recycle()

        try {
            val ranked = keepBestMatchPerTier(
                templates.mapNotNull { template ->
                    matchTemplateAtBestScale(
                        roiGray = candidateGray,
                        template = template,
                        scales = CANDIDATE_TEMPLATE_SCALES
                    )
                }
            )

            val best = ranked.firstOrNull() ?: return null
            val second = ranked.getOrNull(1)
            val margin = best.score - (second?.score ?: 0f)
            if (best.score < 0.42f || margin < 0.008f) return null
            val resolvedMatch = best

            val resolvedSecond = ranked
                .asSequence()
                .filter { it.tier != resolvedMatch.tier }
                .maxByOrNull(TemplateMatch::score)
            val resolvedMarginRaw = resolvedMatch.score - (resolvedSecond?.score ?: 0f)
            val resolvedMargin = resolvedMarginRaw

            val absoluteBounds = candidateRoi

            val goldRatio = coarsePixelRatio(bitmap, absoluteBounds) { red, green, blue ->
                isGoldStarPixel(Color.rgb(red, green, blue))
            }
            val magentaRatio = coarsePixelRatio(bitmap, absoluteBounds) { red, green, blue ->
                isMagentaBadgePixel(Color.rgb(red, green, blue))
            }
            val widthScore = 1f - (abs(bounds.width() - 36) / 42f).coerceAtMost(1f)
            val heightScore = 1f - (abs(bounds.height() - 42) / 46f).coerceAtMost(1f)
            val xScore = 1f - (abs(centerXRatio - 0.44f) / 0.18f).coerceAtMost(1f)
            val yScore = 1f - (abs(centerYRatio - 0.15f) / 0.10f).coerceAtMost(1f)

            if (goldRatio < 0.04f || magentaRatio < 0.10f) return null

            val score =
                resolvedMatch.score * 3.2f +
                    resolvedMargin * 4.0f +
                    goldRatio * 1.6f +
                    magentaRatio * 1.2f +
                    widthScore +
                    heightScore +
                    xScore +
                    yScore

            val confidence = (
                resolvedMatch.score * 0.62f +
                    resolvedMargin * 1.5f +
                    goldRatio * 0.35f +
                    magentaRatio * 0.25f +
                    xScore * 0.08f
                ).coerceIn(0f, 0.98f)

            return TemplateBadgeCandidate(
                tier = resolvedMatch.tier,
                score = score,
                confidence = confidence,
                bounds = absoluteBounds,
                matchScore = resolvedMatch.score,
                margin = resolvedMargin,
                scale = resolvedMatch.scale,
                goldRatio = goldRatio,
                magentaRatio = magentaRatio
            )
        } finally {
            candidateGray.release()
        }
    }

    private fun resolveTemplateOnlyDetection(
        directDetection: TavernTierDetection?,
        anchoredDetection: TavernTierDetection?
    ): TavernTierDetection? {
        val resolvedTier = resolveTemplateOnlyConsensus(
            directTier = directDetection?.tier,
            anchoredTier = anchoredDetection?.tier
        ) ?: return null
        return when {
            directDetection?.tier == resolvedTier && anchoredDetection?.tier == resolvedTier -> {
                if (anchoredDetection.confidence >= directDetection.confidence) {
                    anchoredDetection.copy(
                        sourceLabel = "模板匹配-共识",
                        debugLabel = buildString {
                            append(anchoredDetection.debugLabel ?: "")
                            append(" · direct=")
                            append(directDetection.debugLabel ?: "-")
                        }
                    )
                } else {
                    directDetection.copy(
                        sourceLabel = "模板匹配-共识",
                        debugLabel = buildString {
                            append(directDetection.debugLabel ?: "")
                            append(" · anchored=")
                            append(anchoredDetection.debugLabel ?: "-")
                        }
                    )
                }
            }
            directDetection?.tier == resolvedTier -> directDetection
            else -> anchoredDetection
        }
    }

    internal fun resolveTemplateOnlyConsensus(
        directTier: Int?,
        anchoredTier: Int?
    ): Int? {
        if (directTier == null) return anchoredTier
        if (anchoredTier == null) return directTier
        return directTier.takeIf { it == anchoredTier }
    }

    internal fun isTemplateBadgeBoundsPlausible(
        viewport: Rect,
        bounds: Rect,
        templateOnly: Boolean
    ): Boolean = isTemplateBadgeBoundsPlausible(
        viewportWidth = viewport.width(),
        viewportHeight = viewport.height(),
        boundsWidth = bounds.width(),
        boundsHeight = bounds.height(),
        templateOnly = templateOnly
    )

    internal fun isTemplateBadgeBoundsPlausible(
        viewportWidth: Int,
        viewportHeight: Int,
        boundsWidth: Int,
        boundsHeight: Int,
        templateOnly: Boolean
    ): Boolean {
        if (viewportWidth <= 0 || viewportHeight <= 0) return false
        val widthRatio = boundsWidth.toFloat() / viewportWidth.coerceAtLeast(1)
        val heightRatio = boundsHeight.toFloat() / viewportHeight.coerceAtLeast(1)
        return if (templateOnly) {
            widthRatio in TEMPLATE_ONLY_BADGE_MIN_WIDTH_RATIO..TEMPLATE_ONLY_BADGE_MAX_WIDTH_RATIO &&
                heightRatio in TEMPLATE_ONLY_BADGE_MIN_HEIGHT_RATIO..TEMPLATE_ONLY_BADGE_MAX_HEIGHT_RATIO
        } else {
            boundsWidth in 16..80 && boundsHeight in 18..96
        }
    }

    private fun buildTemplateOnlyBadgeSearchRoi(
        bitmap: Bitmap,
        viewport: Rect
    ): Rect? {
        return Rect(
            viewport.left + (viewport.width() * TEMPLATE_ONLY_BADGE_SEARCH_LEFT_RATIO).toInt(),
            viewport.top + (viewport.height() * TEMPLATE_ONLY_BADGE_SEARCH_TOP_RATIO).toInt(),
            viewport.left + (viewport.width() * TEMPLATE_ONLY_BADGE_SEARCH_RIGHT_RATIO).toInt(),
            viewport.top + (viewport.height() * TEMPLATE_ONLY_BADGE_SEARCH_BOTTOM_RATIO).toInt()
        ).coerceInside(bitmap.width, bitmap.height)
    }

    private fun passesTemplateOnlyBadgePosition(
        viewport: Rect,
        bounds: Rect
    ): Boolean {
        if (viewport.width() <= 0 || viewport.height() <= 0) return false
        val centerXRatio = (bounds.centerX() - viewport.left).toFloat() / viewport.width().coerceAtLeast(1)
        val centerYRatio = (bounds.centerY() - viewport.top).toFloat() / viewport.height().coerceAtLeast(1)
        return centerXRatio in TEMPLATE_ONLY_BADGE_CENTER_X_MIN..TEMPLATE_ONLY_BADGE_CENTER_X_MAX &&
            centerYRatio in TEMPLATE_ONLY_BADGE_CENTER_Y_MIN..TEMPLATE_ONLY_BADGE_CENTER_Y_MAX
    }

    private fun detectLobbyBadgeTier(
        bitmap: Bitmap,
        viewport: Rect,
        relaxed: Boolean = false
    ): TavernTierDetection? {
        val searchRoi = if (relaxed) {
            Rect(
                (bitmap.width * 0.16f).toInt(),
                (bitmap.height * 0.02f).toInt(),
                (bitmap.width * 0.97f).toInt(),
                (bitmap.height * 0.36f).toInt()
            )
        } else {
            Rect(
                viewport.left + (viewport.width() * 0.30f).toInt(),
                viewport.top + (viewport.height() * 0.05f).toInt(),
                viewport.left + (viewport.width() * 0.75f).toInt(),
                viewport.top + (viewport.height() * 0.28f).toInt()
            )
        }.coerceInside(bitmap.width, bitmap.height) ?: return null

        val components = collectMagentaComponents(bitmap, searchRoi)
        if (components.isEmpty()) return null

        val candidates = clusterBadgeComponents(components)
            .mapNotNull { cluster ->
                val candidate = evaluateBadgeCandidate(
                    bitmap = bitmap,
                    viewport = viewport,
                    bounds = cluster,
                    relaxed = relaxed
                ) ?: return@mapNotNull null
                candidate
            }
            .sortedByDescending(BadgeCandidate::score)

        val best = selectBestBadgeCandidate(candidates, relaxed) ?: return null
        return TavernTierDetection(
            tier = best.tier,
            confidence = best.confidence,
            sourceLabel = "徽章星级计数",
            roi = best.bounds,
            debugLabel = "stars=${best.starCount} score=${best.score.formatScore()}"
        )
    }

    private fun detectHeroSelectionTier(bitmap: Bitmap, viewport: Rect): TavernTierDetection? {
        if (!passesHeroSelectionBannerGate(bitmap, viewport)) return null

        val roi = Rect(
            viewport.left + (viewport.width() * 0.255f).toInt(),
            viewport.top + (viewport.height() * 0.02f).toInt(),
            viewport.left + (viewport.width() * 0.40f).toInt(),
            viewport.top + (viewport.height() * 0.16f).toInt()
        ).coerceInside(bitmap.width, bitmap.height) ?: return null

        var warmPixels = 0
        var darkPixels = 0
        val area = roi.width() * roi.height()
        forEachPixel(roi) { x, y, _ ->
            val pixel = bitmap.getPixel(x, y)
            if (isWarmBadgePixel(pixel)) warmPixels += 1
            if (isDarkGlyphPixel(pixel)) darkPixels += 1
        }

        val warmRatio = warmPixels.toFloat() / area.coerceAtLeast(1)
        val darkRatio = darkPixels.toFloat() / area.coerceAtLeast(1)
        if (warmRatio < 0.05f || darkRatio < 0.018f) return null

        return TavernTierDetection(
            tier = 1,
            confidence = (warmRatio * 3.4f + darkRatio * 4.2f).coerceIn(0f, 0.88f),
            sourceLabel = "英雄选择页推断",
            roi = roi,
            debugLabel = "warm=${warmRatio.formatScore()} dark=${darkRatio.formatScore()}"
        )
    }

    private fun collectMagentaComponents(bitmap: Bitmap, roi: Rect): List<ComponentBox> {
        val width = roi.width()
        val height = roi.height()
        val mask = BooleanArray(width * height)
        forEachPixel(roi) { x, y, index ->
            mask[index] = isMagentaBadgePixel(bitmap.getPixel(x, y))
        }

        val visited = BooleanArray(mask.size)
        val queueX = IntArray(mask.size)
        val queueY = IntArray(mask.size)
        val components = mutableListOf<ComponentBox>()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val startIndex = y * width + x
                if (!mask[startIndex] || visited[startIndex]) continue

                visited[startIndex] = true
                queueX[0] = x
                queueY[0] = y
                var head = 0
                var tail = 1
                var size = 0
                var left = x
                var top = y
                var right = x
                var bottom = y

                while (head < tail) {
                    val currentX = queueX[head]
                    val currentY = queueY[head]
                    head += 1
                    size += 1
                    left = min(left, currentX)
                    top = min(top, currentY)
                    right = max(right, currentX)
                    bottom = max(bottom, currentY)

                    for (nextY in max(0, currentY - 1)..min(height - 1, currentY + 1)) {
                        for (nextX in max(0, currentX - 1)..min(width - 1, currentX + 1)) {
                            val nextIndex = nextY * width + nextX
                            if (!mask[nextIndex] || visited[nextIndex]) continue
                            visited[nextIndex] = true
                            queueX[tail] = nextX
                            queueY[tail] = nextY
                            tail += 1
                        }
                    }
                }

                val bounds = Rect(
                    roi.left + left,
                    roi.top + top,
                    roi.left + right + 1,
                    roi.top + bottom + 1
                )
                if (size >= 30 && bounds.width() in 5..100 && bounds.height() in 5..120) {
                    components += ComponentBox(bounds = bounds, pixels = size)
                }
            }
        }
        return components
    }

    private fun clusterBadgeComponents(components: List<ComponentBox>): List<Rect> {
        if (components.isEmpty()) return emptyList()
        val consumed = BooleanArray(components.size)
        val clusters = mutableListOf<Rect>()

        for (index in components.indices) {
            if (consumed[index]) continue
            var merged = Rect(components[index].bounds)
            consumed[index] = true
            var changed = true

            while (changed) {
                changed = false
                for (candidateIndex in components.indices) {
                    if (consumed[candidateIndex]) continue
                    val candidate = components[candidateIndex].bounds
                    if (!Rect.intersects(expandedRect(merged, 14), candidate)) continue
                    merged.union(candidate)
                    consumed[candidateIndex] = true
                    changed = true
                }
            }

            if (merged.width() in 18..110 && merged.height() in 18..120) {
                clusters += merged
            }
        }
        return clusters
    }

    private fun evaluateBadgeCandidate(
        bitmap: Bitmap,
        viewport: Rect,
        bounds: Rect,
        relaxed: Boolean
    ): BadgeCandidate? {
        val starCount = countGoldStars(bitmap, bounds)
        if (starCount !in 1..6) return null

        val centerXRatio = (bounds.centerX() - viewport.left).toFloat() / viewport.width().coerceAtLeast(1)
        val centerYRatio = (bounds.centerY() - viewport.top).toFloat() / viewport.height().coerceAtLeast(1)
        val frameXRatio = bounds.centerX().toFloat() / bitmap.width.coerceAtLeast(1)
        val frameYRatio = bounds.centerY().toFloat() / bitmap.height.coerceAtLeast(1)

        if (relaxed) {
            if (frameXRatio !in 0.16f..0.97f || frameYRatio !in 0.02f..0.38f) return null
        } else {
            if (centerXRatio !in 0.34f..0.60f || centerYRatio !in 0.06f..0.30f) return null
        }
        val widthScore = 1f - (abs(bounds.width() - 48) / 64f).coerceAtMost(1f)
        val heightScore = 1f - (abs(bounds.height() - 64) / 72f).coerceAtMost(1f)
        val xScore = if (relaxed) {
            1f - (abs(frameXRatio - 0.62f) / 0.46f).coerceAtMost(1f)
        } else {
            1f - (abs(centerXRatio - 0.46f) / 0.14f).coerceAtMost(1f)
        }
        val yScore = if (relaxed) {
            1f - (abs(frameYRatio - 0.16f) / 0.20f).coerceAtMost(1f)
        } else {
            1f - (abs(centerYRatio - 0.18f) / 0.10f).coerceAtMost(1f)
        }
        val score = starCount * 1.2f + widthScore + heightScore + xScore * 1.6f + yScore
        val confidence = (0.48f + starCount * 0.05f + widthScore * 0.09f + xScore * 0.12f)
            .coerceIn(0f, 0.96f)

        return BadgeCandidate(
            tier = starCount,
            starCount = starCount,
            score = score,
            confidence = confidence,
            bounds = bounds
        )
    }

    private fun selectBestBadgeCandidate(
        candidates: List<BadgeCandidate>,
        relaxed: Boolean
    ): BadgeCandidate? {
        val best = candidates.firstOrNull() ?: return null
        if (!relaxed) return best

        val highTierCandidate = candidates
            .asSequence()
            .filter { it.tier >= 3 && it.confidence >= RELAXED_HIGH_TIER_CONFIDENCE }
            .maxByOrNull(BadgeCandidate::score)

        if (highTierCandidate == null) return best
        if (best.tier <= 2) return highTierCandidate
        if (highTierCandidate.score + RELAXED_HIGH_TIER_SCORE_MARGIN >= best.score && highTierCandidate.tier > best.tier) {
            return highTierCandidate
        }
        return best
    }

    private fun countGoldStars(bitmap: Bitmap, bounds: Rect): Int {
        val width = bounds.width()
        val height = bounds.height()
        val mask = BooleanArray(width * height)
        forEachPixel(bounds) { x, y, index ->
            mask[index] = isGoldStarPixel(bitmap.getPixel(x, y))
        }
        return countGoldStarsFromMask(width = width, height = height, mask = mask)
    }

    internal fun countGoldStarsFromMask(width: Int, height: Int, mask: BooleanArray): Int {
        if (width <= 0 || height <= 0 || mask.size != width * height) return 0

        val rowCounts = IntArray(height)
        for (y in 0 until height) {
            var count = 0
            for (x in 0 until width) {
                if (mask[y * width + x]) count += 1
            }
            rowCounts[y] = count
        }

        val peakRow = rowCounts.indices.maxByOrNull { rowCounts[it] } ?: return 0
        val peakRowCount = rowCounts[peakRow]
        if (peakRowCount < max(3, (width * 0.05f).toInt())) return 0

        val rowThreshold = max(2, (peakRowCount * 0.35f).toInt())
        var top = peakRow
        while (top > 0 && rowCounts[top - 1] >= rowThreshold) {
            top -= 1
        }
        var bottom = peakRow
        while (bottom < height - 1 && rowCounts[bottom + 1] >= rowThreshold) {
            bottom += 1
        }

        top = max(0, top - 1)
        bottom = min(height - 1, bottom + 1)
        val bandHeight = bottom - top + 1
        if (bandHeight <= 0) return 0

        val columnCounts = IntArray(width)
        for (x in 0 until width) {
            var count = 0
            for (y in top..bottom) {
                if (mask[y * width + x]) count += 1
            }
            columnCounts[x] = count
        }

        val smoothedColumns = smoothColumnCounts(columnCounts)
        val signal = extractPrimaryStarSignal(smoothedColumns, bandHeight) ?: return 0
        return matchStarTemplate(signal)?.tier ?: 0
    }

    private fun smoothColumnCounts(columnCounts: IntArray): IntArray {
        val smoothed = IntArray(columnCounts.size)
        for (x in columnCounts.indices) {
            smoothed[x] =
                columnCounts[x] +
                    (if (x > 0) columnCounts[x - 1] else 0) +
                    (if (x < columnCounts.lastIndex) columnCounts[x + 1] else 0)
        }
        return smoothed
    }

    private fun extractPrimaryStarSignal(
        smoothedColumns: IntArray,
        bandHeight: Int
    ): FloatArray? {
        val peakColumnCount = smoothedColumns.maxOrNull() ?: return null
        if (peakColumnCount <= 0) return null

        val activeThreshold = max(
            2,
            max((bandHeight * 0.40f).toInt(), (peakColumnCount * 0.18f).toInt())
        )
        var left = smoothedColumns.indexOfFirst { it >= activeThreshold }
        if (left < 0) return null
        var right = smoothedColumns.indexOfLast { it >= activeThreshold }
        if (right < left) return null

        left = max(0, left - 1)
        right = min(smoothedColumns.lastIndex, right + 1)
        val rawWidth = right - left + 1
        if (rawWidth < 3) return null

        val signal = FloatArray(TEMPLATE_SIGNAL_WIDTH)
        for (index in signal.indices) {
            val sourceX = left + ((rawWidth - 1) * index.toFloat() / (signal.size - 1).coerceAtLeast(1))
            val floorX = sourceX.toInt()
            val ceilX = min(right, floorX + 1)
            val blend = sourceX - floorX
            val interpolated =
                smoothedColumns[floorX] * (1f - blend) +
                    smoothedColumns[ceilX] * blend
            signal[index] = interpolated / peakColumnCount.toFloat()
        }
        return signal
    }

    private fun matchStarTemplate(signal: FloatArray): TemplateMatchResult? {
        if (signal.isEmpty()) return null

        val scores = (1..6).map { tier ->
            TemplateMatchResult(
                tier = tier,
                score = cosineSimilarity(signal, buildStarTemplate(signal.size, tier))
            )
        }.sortedByDescending(TemplateMatchResult::score)

        val best = scores.firstOrNull() ?: return null
        val second = scores.getOrNull(1)
        val margin = best.score - (second?.score ?: 0f)
        if (best.score < 0.45f || margin < 0.015f) return null
        return best
    }

    private fun buildStarTemplate(length: Int, tier: Int): FloatArray {
        val centers = FloatArray(tier) { index ->
            if (tier == 1) {
                0.5f
            } else {
                0.12f + index * (0.76f / (tier - 1).toFloat())
            }
        }
        val spacing = if (tier == 1) 0.34f else 0.76f / (tier - 1).toFloat()
        val sigma = max(0.045f, spacing * 0.18f)
        val template = FloatArray(length)

        for (x in template.indices) {
            val position = if (length == 1) 0.5f else x.toFloat() / (length - 1).toFloat()
            var value = 0f
            for (center in centers) {
                val distance = position - center
                value += kotlin.math.exp(-(distance * distance) / (2f * sigma * sigma))
            }
            template[x] = value
        }

        val maxValue = template.maxOrNull() ?: 0f
        if (maxValue <= 0f) return template
        for (x in template.indices) {
            template[x] /= maxValue
        }
        return template
    }

    private fun cosineSimilarity(left: FloatArray, right: FloatArray): Float {
        if (left.size != right.size || left.isEmpty()) return 0f

        var dot = 0f
        var leftNorm = 0f
        var rightNorm = 0f
        for (index in left.indices) {
            dot += left[index] * right[index]
            leftNorm += left[index] * left[index]
            rightNorm += right[index] * right[index]
        }
        if (leftNorm <= 0f || rightNorm <= 0f) return 0f
        return (dot / kotlin.math.sqrt(leftNorm * rightNorm)).coerceIn(0f, 1f)
    }

    private fun matchTemplateAtBestScale(
        roiGray: Mat,
        template: TavernTierTemplate,
        scales: FloatArray = TEMPLATE_SCALES
    ): TemplateMatch? {
        var best: TemplateMatch? = null
        scales.forEach { scale ->
            val scaledWidth = (template.width * scale).roundToInt()
            val scaledHeight = (template.height * scale).roundToInt()
            if (scaledWidth < 10 || scaledHeight < 10) return@forEach
            if (scaledWidth > roiGray.width() || scaledHeight > roiGray.height()) return@forEach

            val resized = Mat()
            val result = Mat()
            try {
                Imgproc.resize(
                    template.gray,
                    resized,
                    Size(scaledWidth.toDouble(), scaledHeight.toDouble()),
                    0.0,
                    0.0,
                    Imgproc.INTER_AREA
                )
                Imgproc.matchTemplate(roiGray, resized, result, Imgproc.TM_CCOEFF_NORMED)
                val mmr = Core.minMaxLoc(result)
                val bounds = Rect(
                    mmr.maxLoc.x.roundToInt(),
                    mmr.maxLoc.y.roundToInt(),
                    mmr.maxLoc.x.roundToInt() + scaledWidth,
                    mmr.maxLoc.y.roundToInt() + scaledHeight
                )
                val candidate = TemplateMatch(
                    tier = template.tier,
                    scale = scale,
                    score = mmr.maxVal.toFloat(),
                    bounds = bounds
                )
                if (best == null || candidate.score > best.score) {
                    best = candidate
                }
            } finally {
                resized.release()
                result.release()
            }
        }
        return best
    }

    private fun keepBestMatchPerTier(matches: List<TemplateMatch>): List<TemplateMatch> {
        return matches
            .groupBy(TemplateMatch::tier)
            .mapNotNull { (_, tierMatches) -> tierMatches.maxByOrNull(TemplateMatch::score) }
            .sortedByDescending(TemplateMatch::score)
    }

    private fun keepBestTemplateResultPerTier(results: List<TemplateMatchResult>): List<TemplateMatchResult> {
        return results
            .groupBy(TemplateMatchResult::tier)
            .mapNotNull { (_, tierResults) -> tierResults.maxByOrNull(TemplateMatchResult::score) }
            .sortedByDescending(TemplateMatchResult::score)
    }

    private fun compareTemplateAtFixedSize(
        candidateGray: Mat,
        templateGray: Mat
    ): Float {
        val resizedCandidate = Mat()
        val result = Mat()
        return try {
            Imgproc.resize(
                candidateGray,
                resizedCandidate,
                templateGray.size(),
                0.0,
                0.0,
                Imgproc.INTER_AREA
            )
            Imgproc.matchTemplate(resizedCandidate, templateGray, result, Imgproc.TM_CCOEFF_NORMED)
            result.get(0, 0)?.firstOrNull()?.toFloat() ?: 0f
        } finally {
            resizedCandidate.release()
            result.release()
        }
    }

    private fun loadTemplates(context: Context): List<TavernTierTemplate> {
        cachedTemplates?.let { return it }
        synchronized(templateLock) {
            cachedTemplates?.let { return it }

            val loaded = mutableListOf<TavernTierTemplate>()
            val assetPaths = runCatching {
                context.applicationContext.assets
                    .list("tavern_tier_templates")
                    ?.filter { TEMPLATE_ASSET_REGEX.matches(it) }
                    ?.sorted()
            }.getOrNull().orEmpty()
            val resolvedAssetPaths = if (assetPaths.isNotEmpty()) {
                assetPaths.map { "tavern_tier_templates/$it" }
            } else {
                (1..6).map { "tavern_tier_templates/tier_${it}.png" }
            }
            for (assetPath in resolvedAssetPaths) {
                val tier = TEMPLATE_ASSET_REGEX
                    .find(assetPath.substringAfterLast('/'))
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?.takeIf { it in 1..6 }
                    ?: continue
                val bitmap = runCatching {
                    context.applicationContext.assets.open(assetPath).use(BitmapFactory::decodeStream)
                }.getOrNull() ?: continue

                val gray = bitmapToGrayMat(bitmap)
                bitmap.recycle()
                loaded += TavernTierTemplate(
                    tier = tier,
                    gray = gray,
                    width = gray.width(),
                    height = gray.height()
                )
            }
            cachedTemplates = loaded
            return loaded
        }
    }

    private fun expandTemplateOnlyDetectionRoi(
        bitmap: Bitmap,
        viewport: Rect,
        bounds: Rect
    ): Rect? {
        val targetWidth = (viewport.width() * TEMPLATE_ONLY_TARGET_WIDTH_RATIO).roundToInt()
        val targetHeight = (viewport.height() * TEMPLATE_ONLY_TARGET_HEIGHT_RATIO).roundToInt()
        val horizontalInset = ((targetWidth - bounds.width()).coerceAtLeast(0) + 1) / 2 + 4
        val verticalInset = ((targetHeight - bounds.height()).coerceAtLeast(0) + 1) / 2 + 4
        return Rect(
            bounds.left - horizontalInset,
            bounds.top - verticalInset,
            bounds.right + horizontalInset,
            bounds.bottom + verticalInset
        ).coerceInside(bitmap.width, bitmap.height)
    }

    private fun bitmapToGrayMat(bitmap: Bitmap): Mat {
        val rgba = Mat()
        val gray = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        try {
            val conversion = if (rgba.channels() == 4) Imgproc.COLOR_RGBA2GRAY else Imgproc.COLOR_RGB2GRAY
            Imgproc.cvtColor(rgba, gray, conversion)
            Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)
            return gray
        } finally {
            rgba.release()
        }
    }

    private fun passesHeroSelectionBannerGate(bitmap: Bitmap, viewport: Rect): Boolean {
        val width = viewport.width()
        val height = viewport.height()
        val centerX = viewport.left + width / 2
        val banner = Rect(
            (centerX - width * 0.18f).toInt(),
            viewport.top + (height * 0.03f).toInt(),
            (centerX + width * 0.18f).toInt(),
            viewport.top + (height * 0.135f).toInt()
        ).coerceInside(bitmap.width, bitmap.height) ?: return false
        val textLine = Rect(
            (centerX - width * 0.16f).toInt(),
            viewport.top + (height * 0.07f).toInt(),
            (centerX + width * 0.16f).toInt(),
            viewport.top + (height * 0.13f).toInt()
        ).coerceInside(bitmap.width, bitmap.height) ?: return false
        val medallion = Rect(
            (centerX - width * 0.24f).toInt(),
            viewport.top + (height * 0.03f).toInt(),
            (centerX - width * 0.13f).toInt(),
            viewport.top + (height * 0.14f).toInt()
        ).coerceInside(bitmap.width, bitmap.height) ?: return false

        val purpleRatio = coarsePixelRatio(bitmap, banner) { red, green, blue ->
            red > green + 16 && blue > green + 16 && red + blue > 170
        }
        val purpleCoverage = coarseHorizontalCoverage(bitmap, banner) { red, green, blue ->
            red > green + 16 && blue > green + 16 && red + blue > 170
        }
        val brightRatio = coarsePixelRatio(bitmap, textLine) { red, green, blue ->
            red + green + blue > 450
        }
        val medallionRatio = coarsePixelRatio(bitmap, medallion) { red, green, blue ->
            isHeroSelectionMedallionPixel(red, green, blue)
        }
        return purpleRatio >= 0.11f &&
            purpleCoverage >= 0.46f &&
            brightRatio >= 0.032f &&
            medallionRatio >= 0.09f
    }

    private fun coarsePixelRatio(
        bitmap: Bitmap,
        roi: Rect,
        matcher: (red: Int, green: Int, blue: Int) -> Boolean
    ): Float {
        if (roi.width() <= 0 || roi.height() <= 0) return 0f
        val stepX = (roi.width() / 28).coerceAtLeast(1)
        val stepY = (roi.height() / 18).coerceAtLeast(1)
        var matches = 0
        var total = 0
        var y = roi.top
        while (y < roi.bottom) {
            var x = roi.left
            while (x < roi.right) {
                val pixel = bitmap.getPixel(x, y)
                if (matcher(Color.red(pixel), Color.green(pixel), Color.blue(pixel))) {
                    matches += 1
                }
                total += 1
                x += stepX
            }
            y += stepY
        }
        return if (total == 0) 0f else matches.toFloat() / total.toFloat()
    }

    private fun coarseHorizontalCoverage(
        bitmap: Bitmap,
        roi: Rect,
        matcher: (red: Int, green: Int, blue: Int) -> Boolean
    ): Float {
        if (roi.width() <= 0 || roi.height() <= 0) return 0f
        val stepX = (roi.width() / 48).coerceAtLeast(1)
        val stepY = (roi.height() / 10).coerceAtLeast(1)
        var sampledColumns = 0
        var longestActiveRun = 0
        var currentRun = 0

        var x = roi.left
        while (x < roi.right) {
            var colMatches = 0
            var colTotal = 0
            var y = roi.top
            while (y < roi.bottom) {
                val pixel = bitmap.getPixel(x, y)
                if (matcher(Color.red(pixel), Color.green(pixel), Color.blue(pixel))) {
                    colMatches += 1
                }
                colTotal += 1
                y += stepY
            }

            val active = colTotal > 0 && colMatches.toFloat() / colTotal.toFloat() >= 0.30f
            if (active) {
                currentRun += 1
                longestActiveRun = max(longestActiveRun, currentRun)
            } else {
                currentRun = 0
            }

            sampledColumns += 1
            x += stepX
        }

        return if (sampledColumns == 0) 0f else longestActiveRun.toFloat() / sampledColumns.toFloat()
    }

    private fun isMagentaBadgePixel(pixel: Int): Boolean {
        val red = Color.red(pixel)
        val green = Color.green(pixel)
        val blue = Color.blue(pixel)
        return red >= 90 && blue >= 90 && red - green >= 20 && blue - green >= 15
    }

    private fun isGoldStarPixel(pixel: Int): Boolean {
        val red = Color.red(pixel)
        val green = Color.green(pixel)
        val blue = Color.blue(pixel)
        return red >= 150 &&
            green >= 105 &&
            blue <= 140 &&
            red + green >= 290 &&
            green - blue >= 18 &&
            red - blue >= 35
    }

    private fun isWarmBadgePixel(pixel: Int): Boolean {
        val red = Color.red(pixel)
        val green = Color.green(pixel)
        val blue = Color.blue(pixel)
        return red >= 120 && green >= 78 && blue <= 110
    }

    private fun isDarkGlyphPixel(pixel: Int): Boolean {
        val red = Color.red(pixel)
        val green = Color.green(pixel)
        val blue = Color.blue(pixel)
        val luma = (red * 299 + green * 587 + blue * 114) / 1000
        return luma <= 78
    }

    private fun isHeroSelectionMedallionPixel(red: Int, green: Int, blue: Int): Boolean {
        return red >= 112 &&
            green >= 72 &&
            blue <= 118 &&
            red + green >= 215 &&
            red - blue >= 20
    }

    private fun expandedRect(bounds: Rect, inset: Int): Rect {
        return Rect(
            bounds.left - inset,
            bounds.top - inset,
            bounds.right + inset,
            bounds.bottom + inset
        )
    }

    private fun forEachPixel(roi: Rect, block: (x: Int, y: Int, index: Int) -> Unit) {
        var index = 0
        for (y in roi.top until roi.bottom) {
            for (x in roi.left until roi.right) {
                block(x, y, index)
                index += 1
            }
        }
    }

    private fun Bitmap.safeCrop(roi: Rect): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val left = roi.left.coerceIn(0, width - 1)
        val top = roi.top.coerceIn(0, height - 1)
        val right = roi.right.coerceIn(left + 1, width)
        val bottom = roi.bottom.coerceIn(top + 1, height)
        val cropWidth = right - left
        val cropHeight = bottom - top
        if (cropWidth <= 0 || cropHeight <= 0) return null
        return Bitmap.createBitmap(this, left, top, cropWidth, cropHeight)
    }

    private fun Rect.coerceInside(width: Int, height: Int): Rect? {
        val safeLeft = left.coerceIn(0, width)
        val safeTop = top.coerceIn(0, height)
        val safeRight = right.coerceIn(0, width)
        val safeBottom = bottom.coerceIn(0, height)
        if (safeRight <= safeLeft || safeBottom <= safeTop) return null
        return Rect(safeLeft, safeTop, safeRight, safeBottom)
    }

    private fun Float.formatScore(): String = String.format("%.2f", this)

    private data class ComponentBox(
        val bounds: Rect,
        val pixels: Int
    )

    private data class BadgeCandidate(
        val tier: Int,
        val starCount: Int,
        val score: Float,
        val confidence: Float,
        val bounds: Rect
    )

    private data class TemplateMatchResult(
        val tier: Int,
        val score: Float
    )

    private data class TavernTierTemplate(
        val tier: Int,
        val gray: Mat,
        val width: Int,
        val height: Int
    )

    private data class TemplateMatch(
        val tier: Int,
        val scale: Float,
        val score: Float,
        val bounds: Rect
    )

    private data class TemplateBadgeCandidate(
        val tier: Int,
        val score: Float,
        val confidence: Float,
        val bounds: Rect,
        val matchScore: Float,
        val margin: Float,
        val scale: Float,
        val goldRatio: Float,
        val magentaRatio: Float
    )

    private const val TEMPLATE_SIGNAL_WIDTH = 96
    private const val MIN_TEMPLATE_CONFIDENCE = 0.56f
    private const val STRONG_BADGE_CONFIDENCE = 0.60f
    private const val RELAXED_HIGH_TIER_CONFIDENCE = 0.66f
    private const val RELAXED_HIGH_TIER_SCORE_MARGIN = 1.30f
    private val TEMPLATE_SCALES = floatArrayOf(0.82f, 0.9f, 1.0f, 1.1f, 1.18f)
    private val CANDIDATE_TEMPLATE_SCALES = floatArrayOf(0.48f, 0.56f, 0.64f, 0.72f, 0.82f, 0.92f, 1.0f, 1.1f)
    private val TEMPLATE_ONLY_DIRECT_SCALES = floatArrayOf(0.34f, 0.40f, 0.48f, 0.56f, 0.64f, 0.72f, 0.82f, 0.92f, 1.0f, 1.1f, 1.2f)

    private const val TEMPLATE_ONLY_BADGE_SEARCH_LEFT_RATIO = 0.44f
    private const val TEMPLATE_ONLY_BADGE_SEARCH_TOP_RATIO = 0.10f
    private const val TEMPLATE_ONLY_BADGE_SEARCH_RIGHT_RATIO = 0.62f
    private const val TEMPLATE_ONLY_BADGE_SEARCH_BOTTOM_RATIO = 0.27f
    private const val TEMPLATE_ONLY_BADGE_MIN_WIDTH_RATIO = 0.012f
    private const val TEMPLATE_ONLY_BADGE_MAX_WIDTH_RATIO = 0.045f
    private const val TEMPLATE_ONLY_BADGE_MIN_HEIGHT_RATIO = 0.035f
    private const val TEMPLATE_ONLY_BADGE_MAX_HEIGHT_RATIO = 0.105f
    private const val TEMPLATE_ONLY_BADGE_CENTER_X_MIN = 0.40f
    private const val TEMPLATE_ONLY_BADGE_CENTER_X_MAX = 0.61f
    private const val TEMPLATE_ONLY_BADGE_CENTER_Y_MIN = 0.11f
    private const val TEMPLATE_ONLY_BADGE_CENTER_Y_MAX = 0.23f

    private const val TEMPLATE_ONLY_TARGET_WIDTH_RATIO = 0.028f
    private const val TEMPLATE_ONLY_TARGET_HEIGHT_RATIO = 0.070f
    private const val TEMPLATE_ONLY_TARGET_CENTER_X = 0.47f
    private const val TEMPLATE_ONLY_CENTER_X_TOLERANCE = 0.08f
    private const val TEMPLATE_ONLY_DIRECT_MIN_MATCH_SCORE = 0.34f
    private const val TEMPLATE_ONLY_DIRECT_MIN_MARGIN = 0.005f
    private const val TEMPLATE_ONLY_DIRECT_MIN_GOLD_RATIO = 0.03f
    private const val TEMPLATE_ONLY_DIRECT_MIN_MAGENTA_RATIO = 0.08f
    private const val TEMPLATE_ONLY_ANCHORED_MIN_MAGENTA_RATIO = 0.10f
    private const val TEMPLATE_ONLY_ANCHORED_MIN_SCORE = 0.23f
    private const val TEMPLATE_ONLY_ANCHORED_MIN_MARGIN = 0.10f
    private val TEMPLATE_ASSET_REGEX = Regex("""tier_(\d+)(?:_[^.]+)?\.png""")
    private val templateLock = Any()
    @Volatile
    private var cachedTemplates: List<TavernTierTemplate>? = null

}
