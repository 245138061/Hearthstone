package com.bgtactician.app.autodetect

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.bgtactician.app.data.model.BattlegroundHeroNameEntry
import com.bgtactician.app.data.model.BattlegroundHeroNameIndex
import com.bgtactician.app.data.model.HeroSelectionVisionHeroOption
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

class HeroNameOcrDetector {

    private val recognizer by lazy {
        TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
    }

    fun detect(
        frame: CapturedFrame,
        heroNameIndex: BattlegroundHeroNameIndex
    ): HeroNameOcrResult {
        val viewport = GameViewportDetector.detect(frame.bitmap)
            ?: Rect(0, 0, frame.width, frame.height)
        val slotResults = locateHeroNameRois(frame.bitmap, viewport).map { roi ->
            detectSlot(
                bitmap = frame.bitmap,
                roiVariants = roi.variants,
                slot = roi.slot,
                heroNameIndex = heroNameIndex
            )
        }

        return HeroNameOcrResult(
            viewport = viewport,
            slotResults = slotResults
        )
    }

    private fun detectSlot(
        bitmap: Bitmap,
        roiVariants: List<RoiVariant>,
        slot: Int,
        heroNameIndex: BattlegroundHeroNameIndex
    ): HeroNameOcrSlotResult {
        val attempts = mutableListOf<HeroNameOcrCandidate>()
        roiVariants.forEach { roiVariant ->
            val crop = bitmap.safeCrop(roiVariant.roi) ?: return@forEach
            try {
                val mainVariants = preprocessMainVariants(crop)
                attempts += mainVariants.flatMap { variant ->
                    recognizeHeroCandidates(
                        bitmap = variant.bitmap,
                        label = "${roiVariant.label}/${variant.label}",
                        roi = roiVariant.roi,
                        heroNameIndex = heroNameIndex
                    )
                }
                if (attempts.none { it.matchScore >= MATCH_SCORE_THRESHOLD }) {
                    val fallbackVariants = preprocessFallbackVariants(crop)
                    attempts += fallbackVariants.flatMap { variant ->
                        recognizeHeroCandidates(
                            bitmap = variant.bitmap,
                            label = "${roiVariant.label}/${variant.label}",
                            roi = roiVariant.roi,
                            heroNameIndex = heroNameIndex
                        )
                    }
                    fallbackVariants.forEach { it.bitmap.recycle() }
                }
                mainVariants.forEach { it.bitmap.recycle() }
            } finally {
                crop.recycle()
            }
        }

        val best = attempts.maxWithOrNull(
            compareBy<HeroNameOcrCandidate> { it.finalScore }
                .thenBy { it.matchScore }
                .thenBy { it.rawName.length }
        )
        val bestForDebug = best
            ?.takeIf { it.heroCardId != null || it.finalScore >= DEBUG_CANDIDATE_SCORE_THRESHOLD }
        val bestOption = best
            ?.takeIf { it.heroCardId != null && it.finalScore >= FINAL_SCORE_THRESHOLD }
            ?.let { candidate ->
                HeroSelectionVisionHeroOption(
                    slot = slot,
                    name = candidate.rawName,
                    heroCardId = candidate.heroCardId,
                    preferNameMatchSource = true,
                    confidence = candidate.finalScore
                )
            }

        return HeroNameOcrSlotResult(
            slot = slot,
            primaryRoi = roiVariants.first().roi,
            debugRoi = bestForDebug?.roi ?: roiVariants.first().roi,
            option = bestOption,
            matchedHeroCardId = best?.heroCardId,
            score = best?.finalScore ?: 0f,
            rawName = best?.rawName,
            normalizedName = best?.normalizedName,
            debugLabel = buildString {
                append("槽")
                append(slot + 1)
                append(":")
                append(best?.rawName ?: "未识别")
                best?.heroCardId?.let {
                    append(" -> ")
                    append(it)
                }
                if (best != null) {
                    append(" @")
                    append(String.format(Locale.US, "%.2f", best.finalScore))
                    append(" [")
                    append(best.variantLabel)
                    append("]")
                }
            }
        )
    }

    private fun locateHeroNameRois(bitmap: Bitmap, viewport: Rect): List<SlotRoi> {
        val width = viewport.width()
        val height = viewport.height()
        val centers = listOf(0.165f, 0.388f, 0.612f, 0.835f)
        val bannerBottom = detectHeroSelectionBannerRect(bitmap, viewport)?.bottom
        val roiHeight = (height * 0.095f).toInt().coerceAtLeast(1)
        return centers.mapIndexed { slot, centerRatio ->
            val baseWidth = width * 0.205f
            val expandedWidth = width * 0.235f
            val wideWidth = width * 0.285f
            val centerX = viewport.left + (width * centerRatio).toInt()
            val (searchTop, searchBottom, fixedStripTop) = bannerBottom?.let { anchorBottom ->
                val remainingHeight = (viewport.bottom - anchorBottom).coerceAtLeast(1)
                Triple(
                    anchorBottom + (remainingHeight * 0.34f).toInt(),
                    anchorBottom + (remainingHeight * 0.66f).toInt(),
                    anchorBottom + (remainingHeight * 0.53f).toInt()
                )
            } ?: run {
                Triple(
                    viewport.top + (height * 0.48f).toInt(),
                    viewport.top + (height * 0.68f).toInt(),
                    viewport.top + (height * 0.58f).toInt()
                )
            }
            val estimatedTop = estimateNameBandTop(
                bitmap = bitmap,
                left = (centerX - wideWidth / 2f).toInt(),
                right = (centerX + wideWidth / 2f).toInt(),
                searchTop = searchTop,
                searchBottom = searchBottom,
                roiHeight = roiHeight
            )
            // 英雄名字带的位置相对固定，只允许在固定带附近做小范围校正，避免被重掷按钮吸走。
            val anchoredTop = estimatedTop
                ?.coerceIn(
                    fixedStripTop - roiHeight / 3,
                    fixedStripTop + roiHeight / 3
                )
                ?: fixedStripTop
            val topStarts = listOf(
                (anchoredTop - roiHeight / 4).coerceAtLeast(viewport.top),
                anchoredTop.coerceAtMost(viewport.bottom - roiHeight),
                (anchoredTop + roiHeight / 6).coerceAtMost(viewport.bottom - roiHeight),
                (anchoredTop + roiHeight / 3).coerceAtMost(viewport.bottom - roiHeight)
            )
            SlotRoi(
                slot = slot,
                variants = listOf(
                    RoiVariant(
                        label = "high-base",
                        roi = Rect(
                            (centerX - baseWidth / 2f).toInt(),
                            topStarts[0],
                            (centerX + baseWidth / 2f).toInt(),
                            topStarts[0] + roiHeight
                        )
                    ),
                    RoiVariant(
                        label = "base",
                        roi = Rect(
                            (centerX - expandedWidth / 2f).toInt(),
                            topStarts[1],
                            (centerX + expandedWidth / 2f).toInt(),
                            topStarts[1] + roiHeight
                        )
                    ),
                    RoiVariant(
                        label = "double-line-high",
                        roi = Rect(
                            (centerX - wideWidth / 2f).toInt(),
                            topStarts[0],
                            (centerX + wideWidth / 2f).toInt(),
                            topStarts[0] + roiHeight
                        )
                    ),
                    RoiVariant(
                        label = "double-line-tall",
                        roi = Rect(
                            (centerX - wideWidth / 2f).toInt(),
                            (topStarts[0] - roiHeight / 6).coerceAtLeast(viewport.top),
                            (centerX + wideWidth / 2f).toInt(),
                            (topStarts[0] + (roiHeight * 1.42f).toInt()).coerceAtMost(viewport.bottom)
                        )
                    ),
                    RoiVariant(
                        label = "name-strip",
                        roi = Rect(
                            (centerX - expandedWidth / 2f).toInt(),
                            (topStarts[1] + roiHeight / 5).coerceAtMost(viewport.bottom - roiHeight / 2),
                            (centerX + expandedWidth / 2f).toInt(),
                            (topStarts[1] + (roiHeight * 0.78f).toInt()).coerceAtMost(viewport.bottom)
                        )
                    ),
                    RoiVariant(
                        label = "name-strip-tight",
                        roi = Rect(
                            (centerX - baseWidth / 2f).toInt(),
                            (topStarts[1] + roiHeight / 4).coerceAtMost(viewport.bottom - roiHeight / 2),
                            (centerX + baseWidth / 2f).toInt(),
                            (topStarts[1] + (roiHeight * 0.74f).toInt()).coerceAtMost(viewport.bottom)
                        )
                    ),
                    RoiVariant(
                        label = "name-strip-low",
                        roi = Rect(
                            (centerX - expandedWidth / 2f).toInt(),
                            (topStarts[1] + roiHeight / 3).coerceAtMost(viewport.bottom - roiHeight / 2),
                            (centerX + expandedWidth / 2f).toInt(),
                            (topStarts[1] + (roiHeight * 0.86f).toInt()).coerceAtMost(viewport.bottom)
                        )
                    ),
                    RoiVariant(
                        label = "double-line",
                        roi = Rect(
                            (centerX - wideWidth / 2f).toInt(),
                            topStarts[1],
                            (centerX + wideWidth / 2f).toInt(),
                            topStarts[1] + roiHeight
                        )
                    ),
                    RoiVariant(
                        label = "lower-wide",
                        roi = Rect(
                            (centerX - wideWidth / 2f).toInt(),
                            topStarts[2],
                            (centerX + wideWidth / 2f).toInt(),
                            topStarts[2] + roiHeight
                        )
                    ),
                    RoiVariant(
                        label = "fallback-low",
                        roi = Rect(
                            (centerX - wideWidth / 2f).toInt(),
                            topStarts[3],
                            (centerX + wideWidth / 2f).toInt(),
                            topStarts[3] + roiHeight
                        )
                    ),
                    RoiVariant(
                        label = "fallback-low-tall",
                        roi = Rect(
                            (centerX - wideWidth / 2f).toInt(),
                            topStarts[3],
                            (centerX + wideWidth / 2f).toInt(),
                            (topStarts[3] + (roiHeight * 1.36f).toInt()).coerceAtMost(viewport.bottom)
                        )
                    )
                )
            )
        }
    }

    private fun estimateNameBandTop(
        bitmap: Bitmap,
        left: Int,
        right: Int,
        searchTop: Int,
        searchBottom: Int,
        roiHeight: Int
    ): Int? {
        if (roiHeight <= 0) return null
        val safeLeft = left.coerceIn(0, bitmap.width - 1)
        val safeRight = right.coerceIn(safeLeft + 1, bitmap.width)
        val safeTop = searchTop.coerceIn(0, bitmap.height - 1)
        val safeBottom = searchBottom.coerceIn(safeTop + roiHeight, bitmap.height)
        if (safeBottom - safeTop < roiHeight) return null

        var bestTop: Int? = null
        var bestScore = Float.NEGATIVE_INFINITY
        val step = (roiHeight / 8).coerceAtLeast(4)
        var candidateTop = safeTop
        while (candidateTop + roiHeight <= safeBottom) {
            val roi = Rect(safeLeft, candidateTop, safeRight, candidateTop + roiHeight)
            val darkRatio = coarsePixelRatio(bitmap, roi) { red, green, blue ->
                luma(red, green, blue) <= 96
            }
            val brightTextRatio = coarsePixelRatio(
                bitmap,
                Rect(
                    roi.left,
                    roi.top + roiHeight / 6,
                    roi.right,
                    roi.bottom - roiHeight / 6
                )
            ) { red, green, blue ->
                luma(red, green, blue) >= 188
            }
            val purpleRatio = coarsePixelRatio(bitmap, roi) { red, green, blue ->
                red > green + 14 && blue > green + 14 && red + blue > 165
            }
            val goldRatio = coarsePixelRatio(bitmap, roi) { red, green, blue ->
                red >= 150 && green >= 110 && blue <= 145
            }
            val score =
                darkRatio * 0.75f +
                    brightTextRatio * 2.2f +
                    purpleRatio * 0.55f -
                    goldRatio * 1.1f
            if (darkRatio >= 0.08f && (brightTextRatio >= 0.012f || purpleRatio >= 0.12f) && score > bestScore) {
                bestScore = score
                bestTop = candidateTop
            }
            candidateTop += step
        }
        return bestTop
    }

    private fun luma(red: Int, green: Int, blue: Int): Int {
        return (red * 299 + green * 587 + blue * 114) / 1000
    }

    private fun detectHeroSelectionBannerRect(bitmap: Bitmap, viewport: Rect): Rect? {
        val width = viewport.width()
        val height = viewport.height()
        val centerX = viewport.left + width / 2
        val banner = Rect(
            (centerX - width * 0.18f).toInt(),
            viewport.top + (height * 0.03f).toInt(),
            (centerX + width * 0.18f).toInt(),
            viewport.top + (height * 0.135f).toInt()
        )
        val textLine = Rect(
            (centerX - width * 0.16f).toInt(),
            viewport.top + (height * 0.07f).toInt(),
            (centerX + width * 0.16f).toInt(),
            viewport.top + (height * 0.13f).toInt()
        )
        val purpleRatio = coarsePixelRatio(bitmap, banner) { red, green, blue ->
            red > green + 16 && blue > green + 16 && red + blue > 170
        }
        val brightRatio = coarsePixelRatio(bitmap, textLine) { red, green, blue ->
            red + green + blue > 450
        }
        return banner.takeIf {
            purpleRatio >= 0.02f && brightRatio >= 0.03f
        }
    }

    private fun coarsePixelRatio(
        bitmap: Bitmap,
        roi: Rect,
        matcher: (red: Int, green: Int, blue: Int) -> Boolean
    ): Float {
        if (roi.width() <= 0 || roi.height() <= 0) return 0f
        val left = roi.left.coerceIn(0, bitmap.width - 1)
        val top = roi.top.coerceIn(0, bitmap.height - 1)
        val right = roi.right.coerceIn(left + 1, bitmap.width)
        val bottom = roi.bottom.coerceIn(top + 1, bitmap.height)
        val stepX = ((right - left) / 28).coerceAtLeast(1)
        val stepY = ((bottom - top) / 18).coerceAtLeast(1)
        var matches = 0
        var total = 0
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = bitmap.getPixel(x, y)
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)
                if (matcher(red, green, blue)) matches++
                total++
                x += stepX
            }
            y += stepY
        }
        return if (total == 0) 0f else matches.toFloat() / total.toFloat()
    }

    private fun preprocessMainVariants(source: Bitmap): List<BitmapVariant> {
        val variants = mutableListOf(BitmapVariant("raw-3x", source.scaleForOcr(3)))
        if (!OpenCVLoader.initLocal()) return variants
        return try {
            variants += BitmapVariant("light-text", preprocessLightTextVariant(source))
            variants += BitmapVariant("cream-text", preprocessCreamTextVariant(source))
            variants += BitmapVariant("binary", preprocessThresholdVariant(source, inverted = false))
            variants += BitmapVariant("binary-inv", preprocessThresholdVariant(source, inverted = true))
            variants
        } catch (_: Throwable) {
            variants
        }
    }

    private fun preprocessFallbackVariants(source: Bitmap): List<BitmapVariant> {
        if (!OpenCVLoader.initLocal()) return emptyList()
        return try {
            listOf(
                BitmapVariant("adaptive", preprocessAdaptiveVariant(source)),
                BitmapVariant("adaptive-inv", preprocessAdaptiveVariant(source, inverted = true)),
                BitmapVariant("top-hat", preprocessTopHatVariant(source))
            )
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun preprocessThresholdVariant(source: Bitmap, inverted: Boolean): Bitmap {
        val srcMat = Mat()
        val rgbaMat = Mat()
        val grayMat = Mat()
        val scaledMat = Mat()
        val blurredMat = Mat()
        val thresholdMat = Mat()
        return try {
            Utils.bitmapToMat(source, srcMat)
            srcMat.convertTo(rgbaMat, CvType.CV_8UC4)
            Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.resize(
                grayMat,
                scaledMat,
                Size(grayMat.cols() * 3.0, grayMat.rows() * 3.0),
                0.0,
                0.0,
                Imgproc.INTER_CUBIC
            )
            Imgproc.GaussianBlur(scaledMat, blurredMat, Size(3.0, 3.0), 0.0)
            Imgproc.threshold(
                blurredMat,
                thresholdMat,
                0.0,
                255.0,
                Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU
            )
            if (inverted) {
                Core.bitwise_not(thresholdMat, thresholdMat)
            }
            thresholdMat.toBitmap()
        } finally {
            srcMat.release()
            rgbaMat.release()
            grayMat.release()
            scaledMat.release()
            blurredMat.release()
            thresholdMat.release()
        }
    }

    private fun preprocessLightTextVariant(source: Bitmap): Bitmap {
        val srcMat = Mat()
        val rgbaMat = Mat()
        val grayMat = Mat()
        val scaledMat = Mat()
        val blurMat = Mat()
        val highlightMat = Mat()
        val thresholdMat = Mat()
        return try {
            Utils.bitmapToMat(source, srcMat)
            srcMat.convertTo(rgbaMat, CvType.CV_8UC4)
            Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.resize(
                grayMat,
                scaledMat,
                Size(grayMat.cols() * 3.2, grayMat.rows() * 3.2),
                0.0,
                0.0,
                Imgproc.INTER_CUBIC
            )
            Imgproc.GaussianBlur(scaledMat, blurMat, Size(0.0, 0.0), 1.1)
            Core.addWeighted(scaledMat, 1.8, blurMat, -0.8, 12.0, highlightMat)
            Imgproc.threshold(
                highlightMat,
                thresholdMat,
                178.0,
                255.0,
                Imgproc.THRESH_BINARY
            )
            thresholdMat.toBitmap()
        } finally {
            srcMat.release()
            rgbaMat.release()
            grayMat.release()
            scaledMat.release()
            blurMat.release()
            highlightMat.release()
            thresholdMat.release()
        }
    }

    private fun preprocessAdaptiveVariant(source: Bitmap, inverted: Boolean = false): Bitmap {
        val srcMat = Mat()
        val rgbaMat = Mat()
        val grayMat = Mat()
        val scaledMat = Mat()
        val sharpenedMat = Mat()
        val thresholdMat = Mat()
        return try {
            Utils.bitmapToMat(source, srcMat)
            srcMat.convertTo(rgbaMat, CvType.CV_8UC4)
            Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.resize(
                grayMat,
                scaledMat,
                Size(grayMat.cols() * 3.0, grayMat.rows() * 3.0),
                0.0,
                0.0,
                Imgproc.INTER_CUBIC
            )
            Imgproc.GaussianBlur(scaledMat, sharpenedMat, Size(0.0, 0.0), 1.2)
            Core.addWeighted(scaledMat, 1.55, sharpenedMat, -0.55, 0.0, sharpenedMat)
            Imgproc.adaptiveThreshold(
                sharpenedMat,
                thresholdMat,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                31,
                8.0
            )
            if (inverted) {
                Core.bitwise_not(thresholdMat, thresholdMat)
            }
            thresholdMat.toBitmap()
        } finally {
            srcMat.release()
            rgbaMat.release()
            grayMat.release()
            scaledMat.release()
            sharpenedMat.release()
            thresholdMat.release()
        }
    }

    private fun preprocessCreamTextVariant(source: Bitmap): Bitmap {
        val srcMat = Mat()
        val rgbaMat = Mat()
        val scaledMat = Mat()
        val maskMat = Mat()
        val grayMaskMat = Mat()
        return try {
            Utils.bitmapToMat(source, srcMat)
            srcMat.convertTo(rgbaMat, CvType.CV_8UC4)
            Imgproc.resize(
                rgbaMat,
                scaledMat,
                Size(rgbaMat.cols() * 3.0, rgbaMat.rows() * 3.0),
                0.0,
                0.0,
                Imgproc.INTER_CUBIC
            )
            Core.inRange(
                scaledMat,
                org.opencv.core.Scalar(150.0, 150.0, 150.0, 0.0),
                org.opencv.core.Scalar(255.0, 255.0, 255.0, 255.0),
                maskMat
            )
            Imgproc.GaussianBlur(maskMat, grayMaskMat, Size(3.0, 3.0), 0.0)
            Imgproc.threshold(
                grayMaskMat,
                grayMaskMat,
                110.0,
                255.0,
                Imgproc.THRESH_BINARY
            )
            grayMaskMat.toBitmap()
        } finally {
            srcMat.release()
            rgbaMat.release()
            scaledMat.release()
            maskMat.release()
            grayMaskMat.release()
        }
    }

    private fun preprocessTopHatVariant(source: Bitmap): Bitmap {
        val srcMat = Mat()
        val rgbaMat = Mat()
        val grayMat = Mat()
        val scaledMat = Mat()
        val topHatMat = Mat()
        val thresholdMat = Mat()
        return try {
            Utils.bitmapToMat(source, srcMat)
            srcMat.convertTo(rgbaMat, CvType.CV_8UC4)
            Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.resize(
                grayMat,
                scaledMat,
                Size(grayMat.cols() * 3.2, grayMat.rows() * 3.2),
                0.0,
                0.0,
                Imgproc.INTER_CUBIC
            )
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
            Imgproc.morphologyEx(
                scaledMat,
                topHatMat,
                Imgproc.MORPH_TOPHAT,
                kernel
            )
            Imgproc.threshold(
                topHatMat,
                thresholdMat,
                0.0,
                255.0,
                Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU
            )
            thresholdMat.toBitmap()
        } finally {
            srcMat.release()
            rgbaMat.release()
            grayMat.release()
            scaledMat.release()
            topHatMat.release()
            thresholdMat.release()
        }
    }

    private fun recognizeHeroCandidates(
        bitmap: Bitmap,
        label: String,
        roi: Rect,
        heroNameIndex: BattlegroundHeroNameIndex
    ): List<HeroNameOcrCandidate> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = runCatching {
            Tasks.await(recognizer.process(image), 1500, TimeUnit.MILLISECONDS)
        }.getOrNull() ?: return emptyList()

        val lines = linkedSetOf<String>()
        result.text.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach(lines::add)
        result.textBlocks.forEach { block ->
            val blockLines = block.lines
                .map { it.text.trim() }
                .filter(String::isNotBlank)
            blockLines.forEach(lines::add)
            if (blockLines.size >= 2) {
                lines += blockLines.joinToString(separator = "")
                blockLines.zipWithNext { left, right -> left + right }
                    .forEach(lines::add)
            }
        }

        return lines.mapNotNull { line ->
            val normalized = HeroNameOcrHeuristics.normalize(line)
            if (normalized.isBlank()) return@mapNotNull null
            if (HeroNameOcrHeuristics.isLikelyNoise(line, normalized)) return@mapNotNull null
            val textScore = computeTextQualityScore(normalized)
            val match = matchHeroName(normalized, heroNameIndex)
            val variantBias = variantScoreBias(label)
            val finalScore = when {
                match != null -> (match.score * 0.78f + textScore * 0.22f + variantBias).coerceIn(0f, 1f)
                else -> (textScore * 0.22f + variantBias).coerceIn(0f, 1f)
            }
            HeroNameOcrCandidate(
                variantLabel = label,
                roi = Rect(roi),
                rawName = line.trim(),
                normalizedName = normalized,
                heroCardId = match?.heroCardId,
                localizedName = match?.localizedName,
                matchScore = match?.score ?: 0f,
                finalScore = finalScore
            )
        }
    }

    private fun matchHeroName(
        query: String,
        heroNameIndex: BattlegroundHeroNameIndex
    ): HeroNameMatch? {
        if (query.isBlank()) return null
        val matches = heroNameIndex.heroes.mapNotNull { entry ->
            val aliases = entry.searchAliases().map(HeroNameOcrHeuristics::normalize).filter(String::isNotBlank)
            val bestAlias = aliases.maxOfOrNull { alias ->
                aliasScore(query, alias)
            } ?: return@mapNotNull null
            HeroNameMatch(
                heroCardId = entry.heroCardId,
                localizedName = entry.localizedName.takeIf(String::isNotBlank),
                score = bestAlias
            )
        }.sortedByDescending(HeroNameMatch::score)

        return matches.firstOrNull()?.takeIf { it.score >= MATCH_SCORE_THRESHOLD }
    }

    private fun aliasScore(
        query: String,
        alias: String
    ): Float {
        if (query == alias) return 1f
        if (query.contains(alias) || alias.contains(query)) {
            val lengthPenalty = abs(query.length - alias.length).toFloat() /
                max(query.length, alias.length).coerceAtLeast(1)
            return (0.94f - lengthPenalty * 0.16f).coerceIn(0f, 0.96f)
        }
        val similarity = levenshteinSimilarity(query, alias)
        return when {
            similarity >= 0.88f -> similarity
            similarity >= 0.72f -> similarity * 0.9f
            else -> similarity * 0.72f
        }
    }

    private fun variantScoreBias(label: String): Float {
        return when {
            label.contains("name-strip-tight") -> 0.08f
            label.contains("name-strip-low") -> 0.04f
            label.contains("name-strip") -> 0.06f
            label.contains("high-base") -> 0.03f
            label.contains("base") -> 0.02f
            label.contains("double-line-high") -> 0.01f
            label.contains("lower-wide") -> -0.08f
            label.contains("fallback-low-tall") -> -0.12f
            label.contains("fallback-low") -> -0.10f
            else -> 0f
        }
    }

    private fun levenshteinSimilarity(
        left: String,
        right: String
    ): Float {
        if (left.isBlank() || right.isBlank()) return 0f
        val distance = levenshteinDistance(left, right)
        val maxLength = max(left.length, right.length).coerceAtLeast(1)
        return 1f - distance.toFloat() / maxLength.toFloat()
    }

    private fun levenshteinDistance(
        left: String,
        right: String
    ): Int {
        val costs = IntArray(right.length + 1) { it }
        for (i in 1..left.length) {
            var previous = i - 1
            costs[0] = i
            for (j in 1..right.length) {
                val current = costs[j]
                val substitution = if (left[i - 1] == right[j - 1]) previous else previous + 1
                costs[j] = minOf(
                    costs[j] + 1,
                    costs[j - 1] + 1,
                    substitution
                )
                previous = current
            }
        }
        return costs[right.length]
    }

    private fun computeTextQualityScore(normalized: String): Float {
        if (normalized.isBlank()) return 0f
        val lengthScore = when (normalized.length) {
            in 2..4 -> 1f
            in 5..9 -> 0.92f
            1 -> 0.4f
            else -> 0.72f
        }
        val digitPenalty = normalized.count(Char::isDigit).toFloat() * 0.08f
        return (lengthScore - digitPenalty).coerceIn(0f, 1f)
    }

    private fun BattlegroundHeroNameEntry.searchAliases(): List<String> {
        return buildList {
            addAll(aliases)
            add(name)
            localizedName.takeIf(String::isNotBlank)?.let(::add)
        }.distinct()
    }

    private fun Bitmap.scaleForOcr(multiplier: Int): Bitmap {
        return Bitmap.createScaledBitmap(
            this,
            (width * multiplier).coerceAtLeast(1),
            (height * multiplier).coerceAtLeast(1),
            true
        )
    }

    private fun Bitmap.safeCrop(roi: Rect): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val left = roi.left.coerceIn(0, width - 1)
        val top = roi.top.coerceIn(0, height - 1)
        val right = roi.right.coerceIn(left + 1, width)
        val bottom = roi.bottom.coerceIn(top + 1, height)
        if (right <= left || bottom <= top) return null
        return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
    }

    private fun Mat.toBitmap(): Bitmap {
        val rgbaMat = Mat()
        return try {
            Imgproc.cvtColor(this, rgbaMat, Imgproc.COLOR_GRAY2RGBA)
            Bitmap.createBitmap(cols(), rows(), Bitmap.Config.ARGB_8888).also { output ->
                Utils.matToBitmap(rgbaMat, output)
            }
        } finally {
            rgbaMat.release()
        }
    }

    data class HeroNameOcrResult(
        val viewport: Rect,
        val slotResults: List<HeroNameOcrSlotResult>
    ) {
        val heroOptions: List<HeroSelectionVisionHeroOption>
            get() = slotResults.mapNotNull(HeroNameOcrSlotResult::option).sortedBy(HeroSelectionVisionHeroOption::slot)
    }

    data class HeroNameOcrSlotResult(
        val slot: Int,
        val primaryRoi: Rect,
        val debugRoi: Rect,
        val option: HeroSelectionVisionHeroOption?,
        val matchedHeroCardId: String?,
        val score: Float,
        val rawName: String?,
        val normalizedName: String?,
        val debugLabel: String
    ) {
        fun toObservation(): HeroSelectionOcrObservation {
            return HeroSelectionOcrObservation(
                slot = slot,
                heroCardId = matchedHeroCardId,
                option = option,
                score = score
            )
        }
    }

    private data class SlotRoi(
        val slot: Int,
        val variants: List<RoiVariant>
    )

    private data class RoiVariant(
        val label: String,
        val roi: Rect
    )

    private data class BitmapVariant(
        val label: String,
        val bitmap: Bitmap
    )

    private data class HeroNameMatch(
        val heroCardId: String,
        val localizedName: String?,
        val score: Float
    )

    private data class HeroNameOcrCandidate(
        val variantLabel: String,
        val roi: Rect,
        val rawName: String,
        val normalizedName: String,
        val heroCardId: String?,
        val localizedName: String?,
        val matchScore: Float,
        val finalScore: Float
    )

    private companion object {
        private const val MATCH_SCORE_THRESHOLD = 0.61f
        private const val FINAL_SCORE_THRESHOLD = 0.72f
        private const val DEBUG_CANDIDATE_SCORE_THRESHOLD = 0.48f
    }
}
