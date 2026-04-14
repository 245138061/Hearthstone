package com.bgtactician.app.autodetect

import android.graphics.Bitmap
import android.graphics.Rect
import com.bgtactician.app.data.model.Tribe
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

class OpenCvOcrTribeDetector(
    private val fallback: FrameTribeDetector = ViewportHeuristicDetector()
) : FrameTribeDetector {
    private val recognizer by lazy {
        TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
    }

    override fun analyze(frame: CapturedFrame): DetectionObservation {
        val viewport = GameViewportDetector.detect(frame.bitmap)
            ?: Rect(0, 0, frame.width, frame.height)

        val attempts = buildAttempts(frame.bitmap, viewport)
        if (attempts.isEmpty()) {
            return fallback.analyze(frame).copy(
                viewport = viewport,
                lobbyVisible = false
            )
        }

        val recognizedByVotes = aggregateTribes(attempts)
        val hasQuickGate = passesQuickHeroBannerGate(frame.bitmap, viewport)
        val hasBannerLikeText = hasQuickGate || attempts.any { isLikelyHeroSelectionText(it.rawText) }
        val best = attempts.maxWithOrNull(
            compareBy<RecognitionAttempt> { it.recognizedTribes.size }
                .thenBy { tribeSeparatorScore(it.rawText) }
                .thenBy { it.rawText.length }
        )

        if (best == null) {
            attempts.forEach { it.preview.recycle() }
            return fallback.analyze(frame)
        }

        attempts.forEach { attempt ->
            if (attempt !== best) {
                attempt.preview.recycle()
            }
        }

        val debugText = buildString {
            append("gate=")
            append(hasQuickGate)
            append('\n')
            append(
                attempts.joinToString(separator = "\n\n") { attempt ->
                    buildString {
                        append(attempt.label)
                        append(" -> ")
                        append(
                            attempt.recognizedTribes.takeIf { it.isNotEmpty() }
                                ?.joinToString("/") { it.label }
                                ?: "none"
                        )
                        if (attempt.rawText.isNotBlank()) {
                            append(" :: ")
                            append(attempt.rawText.take(80))
                        }
                    }
                }
            )
        }

        return if (recognizedByVotes.isEmpty()) {
            DetectionObservation(
                hasFrame = true,
                lobbyVisible = hasBannerLikeText,
                attentionRequired = hasBannerLikeText && attempts.none { it.rawText.isNotBlank() },
                viewport = viewport,
                headerRegion = best.roi,
                debugText = debugText,
                debugPreview = best.preview
            )
        } else {
            val banned = if (recognizedByVotes.size == 5) {
                Tribe.entries.toSet() - recognizedByVotes
            } else {
                emptySet()
            }
            DetectionObservation(
                hasFrame = true,
                lobbyVisible = hasBannerLikeText || recognizedByVotes.size == 5,
                availableTribes = recognizedByVotes,
                bannedTribes = banned,
                attentionRequired = recognizedByVotes.size < 3 && attempts.none { it.rawText.isNotBlank() },
                viewport = viewport,
                headerRegion = best.roi,
                debugText = debugText,
                debugPreview = best.preview
            )
        }
    }

    private fun buildAttempts(bitmap: Bitmap, viewport: Rect): List<RecognitionAttempt> {
        val attempts = mutableListOf<RecognitionAttempt>()
        for (candidate in locateLobbyHeaders(viewport)) {
            val crop = bitmap.safeCrop(candidate.roi) ?: continue
            try {
                val variants = preprocessVariants(crop)
                variants.forEach { variant ->
                    val recognition = recognizeTribes(variant.bitmap)
                    attempts += RecognitionAttempt(
                        label = "${candidate.label}/${variant.label}",
                        roi = candidate.roi,
                        preview = variant.bitmap,
                        recognizedTribes = recognition.recognizedTribes,
                        rawText = recognition.rawText
                    )
                }
            } finally {
                crop.recycle()
            }
        }
        return attempts
    }

    private fun aggregateTribes(attempts: List<RecognitionAttempt>): Set<Tribe> {
        val voteMap = mutableMapOf<Tribe, Int>()
        attempts.forEach { attempt ->
            attempt.recognizedTribes.forEach { tribe ->
                voteMap[tribe] = voteMap.getOrDefault(tribe, 0) + 1
            }
        }

        val stable = voteMap
            .filterValues { it >= 2 }
            .toList()
            .sortedByDescending { it.second }
            .map { it.first }
            .toSet()
        if (stable.size == 5) return stable

        val strongSingleAttempt = attempts
            .filter { it.recognizedTribes.size == 5 }
            .filter { hasHeroSelectionTitle(it.rawText) || tribeSeparatorScore(it.rawText) >= 4 }
            .maxByOrNull { tribeSeparatorScore(it.rawText) * 100 + it.rawText.length }
            ?.recognizedTribes
        if (strongSingleAttempt != null) return strongSingleAttempt

        return emptySet()
    }

    private fun tribeSeparatorScore(rawText: String): Int {
        return rawText.count { it == '、' || it == ',' || it == '，' }
    }

    private fun isLikelyHeroSelectionText(rawText: String): Boolean {
        val condensedRaw = rawText
            .lowercase(Locale.ROOT)
            .replace("\\s+".toRegex(), "")
        val normalized = rawText.normalize()
        if (normalized.isBlank()) return false
        val hasHeroTitle = hasHeroSelectionTitle(condensedRaw)
        val tribeHits = TRIBE_ALIASES.count { (_, aliases) -> aliases.any { normalized.contains(it) } }
        return hasHeroTitle || tribeSeparatorScore(normalized) >= 3 || tribeHits >= 3
    }

    private fun hasHeroSelectionTitle(rawText: String): Boolean {
        val condensedRaw = rawText
            .lowercase(Locale.ROOT)
            .replace("\\s+".toRegex(), "")
        return condensedRaw.contains("选择一个英雄") ||
            condensedRaw.contains("选择-个英雄") ||
            condensedRaw.contains("选择个英雄")
    }

    private fun locateLobbyHeaders(viewport: Rect): List<RoiCandidate> {
        val width = viewport.width()
        val height = viewport.height()
        val centerX = viewport.left + width / 2

        fun centeredRoi(
            label: String,
            widthRatio: Float,
            topRatio: Float,
            bottomRatio: Float
        ): RoiCandidate {
            val roiWidth = (width * widthRatio).toInt()
            val left = (centerX - roiWidth / 2).coerceAtLeast(viewport.left)
            val right = (centerX + roiWidth / 2).coerceAtMost(viewport.right)
            return RoiCandidate(
                label = label,
                roi = Rect(
                    left,
                    viewport.top + (height * topRatio).toInt(),
                    right,
                    viewport.top + (height * bottomRatio).toInt()
                )
            )
        }

        return listOf(
            centeredRoi(
                label = "banner-wide",
                widthRatio = 0.46f,
                topRatio = 0.03f,
                bottomRatio = 0.17f
            ),
            centeredRoi(
                label = "banner-tight",
                widthRatio = 0.38f,
                topRatio = 0.05f,
                bottomRatio = 0.15f
            ),
            centeredRoi(
                label = "tribe-line",
                widthRatio = 0.34f,
                topRatio = 0.08f,
                bottomRatio = 0.155f
            ),
            centeredRoi(
                label = "tribe-line-tight",
                widthRatio = 0.30f,
                topRatio = 0.085f,
                bottomRatio = 0.142f
            ),
            centeredRoi(
                label = "tribe-line-tighter",
                widthRatio = 0.27f,
                topRatio = 0.088f,
                bottomRatio = 0.136f
            ),
            centeredRoi(
                label = "tribe-line-ultra",
                widthRatio = 0.25f,
                topRatio = 0.09f,
                bottomRatio = 0.13f
            ),
            centeredRoi(
                label = "tribe-line-ultra-low",
                widthRatio = 0.25f,
                topRatio = 0.095f,
                bottomRatio = 0.135f
            ),
            centeredRoi(
                label = "tribe-line-low",
                widthRatio = 0.34f,
                topRatio = 0.095f,
                bottomRatio = 0.17f
            ),
            RoiCandidate(
                label = "fallback-top",
                roi = Rect(
                    viewport.left + (width * 0.18f).toInt(),
                    viewport.top + (height * 0.03f).toInt(),
                    viewport.right - (width * 0.18f).toInt(),
                    viewport.top + (height * 0.19f).toInt()
                )
            )
        )
    }

    private fun passesQuickHeroBannerGate(bitmap: Bitmap, viewport: Rect): Boolean {
        val width = viewport.width()
        val height = viewport.height()
        val centerX = viewport.left + width / 2
        val banner = bitmap.safeCrop(
            Rect(
                (centerX - width * 0.18f).toInt(),
                viewport.top + (height * 0.03f).toInt(),
                (centerX + width * 0.18f).toInt(),
                viewport.top + (height * 0.135f).toInt()
            )
        ) ?: return false
        val textLine = bitmap.safeCrop(
            Rect(
                (centerX - width * 0.16f).toInt(),
                viewport.top + (height * 0.07f).toInt(),
                (centerX + width * 0.16f).toInt(),
                viewport.top + (height * 0.13f).toInt()
            )
        ) ?: run {
            banner.recycle()
            return false
        }

        return try {
            val purpleRatio = coarsePixelRatio(banner) { red, green, blue ->
                red > green + 16 && blue > green + 16 && red + blue > 170
            }
            val brightRatio = coarsePixelRatio(textLine) { red, green, blue ->
                red + green + blue > 450
            }
            purpleRatio >= 0.02f && brightRatio >= 0.03f
        } finally {
            banner.recycle()
            textLine.recycle()
        }
    }

    private fun coarsePixelRatio(
        bitmap: Bitmap,
        matcher: (red: Int, green: Int, blue: Int) -> Boolean
    ): Float {
        if (bitmap.width <= 0 || bitmap.height <= 0) return 0f
        val stepX = (bitmap.width / 28).coerceAtLeast(1)
        val stepY = (bitmap.height / 18).coerceAtLeast(1)
        var matches = 0
        var total = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                if (matcher(red, green, blue)) {
                    matches += 1
                }
                total += 1
                x += stepX
            }
            y += stepY
        }
        if (total == 0) return 0f
        return matches.toFloat() / total.toFloat()
    }

    private fun preprocessVariants(source: Bitmap): List<BitmapVariant> {
        if (!OpenCVLoader.initLocal()) {
            return listOf(
                BitmapVariant("raw-3x", source.scaleForOcr(3))
            )
        }

        val variants = mutableListOf<BitmapVariant>()
        return try {
            variants += BitmapVariant("raw-3x", source.scaleForOcr(3))
            variants += BitmapVariant("clahe-gray", preprocessClaheVariant(source, thresholdMode = null))
            variants += BitmapVariant("clahe-adaptive", preprocessClaheVariant(source, thresholdMode = ThresholdMode.ADAPTIVE_GAUSSIAN))
            variants += BitmapVariant("clahe-otsu", preprocessClaheVariant(source, thresholdMode = ThresholdMode.OTSU))
            variants
        } catch (_: Throwable) {
            variants.forEach { it.bitmap.recycle() }
            listOf(BitmapVariant("raw-3x", source.scaleForOcr(3)))
        }
    }

    private fun preprocessClaheVariant(source: Bitmap, thresholdMode: ThresholdMode?): Bitmap {
        val srcMat = Mat()
        val rgbaMat = Mat()
        val grayMat = Mat()
        val claheMat = Mat()
        val blurredMat = Mat()
        val thresholdMat = Mat()

        return try {
            Utils.bitmapToMat(source, srcMat)
            srcMat.convertTo(rgbaMat, CvType.CV_8UC4)
            Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.resize(
                grayMat,
                grayMat,
                Size(grayMat.cols() * 3.0, grayMat.rows() * 3.0),
                0.0,
                0.0,
                Imgproc.INTER_CUBIC
            )

            val clahe = Imgproc.createCLAHE(2.4, Size(8.0, 8.0))
            clahe.apply(grayMat, claheMat)
            Imgproc.GaussianBlur(claheMat, blurredMat, Size(3.0, 3.0), 0.0)

            val outputMat = when (thresholdMode) {
                null -> claheMat
                ThresholdMode.ADAPTIVE_GAUSSIAN -> {
                    Imgproc.adaptiveThreshold(
                        blurredMat,
                        thresholdMat,
                        255.0,
                        Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                        Imgproc.THRESH_BINARY,
                        31,
                        10.0
                    )
                    Core.bitwise_not(thresholdMat, thresholdMat)
                    thresholdMat
                }
                ThresholdMode.OTSU -> {
                    Imgproc.threshold(
                        blurredMat,
                        thresholdMat,
                        0.0,
                        255.0,
                        Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU
                    )
                    Core.bitwise_not(thresholdMat, thresholdMat)
                    thresholdMat
                }
            }
            val result = Bitmap.createBitmap(outputMat.cols(), outputMat.rows(), Bitmap.Config.ARGB_8888)
            Imgproc.cvtColor(outputMat, rgbaMat, Imgproc.COLOR_GRAY2RGBA)
            Utils.matToBitmap(rgbaMat, result)
            result
        } finally {
            srcMat.release()
            rgbaMat.release()
            grayMat.release()
            claheMat.release()
            blurredMat.release()
            thresholdMat.release()
        }
    }

    private fun Bitmap.scaleForOcr(multiplier: Int): Bitmap {
        return Bitmap.createScaledBitmap(
            this,
            (width * multiplier).coerceAtLeast(1),
            (height * multiplier).coerceAtLeast(1),
            true
        )
    }

    private fun recognizeTribes(bitmap: Bitmap): RecognitionResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = runCatching {
            Tasks.await(recognizer.process(image), 1500, TimeUnit.MILLISECONDS)
        }.getOrNull() ?: return RecognitionResult()

        val text = buildString {
            append(result.text)
            result.textBlocks.forEach { block ->
                block.lines.forEach { line ->
                    append('\n')
                    append(line.text)
                }
            }
        }

        return RecognitionResult(
            recognizedTribes = parseTribes(text),
            rawText = text.trim()
        )
    }

    private fun parseTribes(raw: String): Set<Tribe> {
        val normalized = raw.normalize()
        if (normalized.isBlank()) return emptySet()

        return TRIBE_ALIASES.mapNotNull { (tribe, aliases) ->
            tribe.takeIf { aliases.any { normalized.contains(it) } }
        }.toSet()
    }

    private fun String.normalize(): String {
        return lowercase(Locale.ROOT)
            .replace("\\s+".toRegex(), "")
            .replace("选择一个英雄", "")
            .replace("选择-个英雄", "")
            .replace("选择个英雄", "")
            .replace("野鲁", "野兽")
            .replace("野曾", "野兽")
            .replace("猪入", "野猪人")
            .replace("猪人", "野猪人")
            .replace("野猜人", "野猪人")
            .replace("鱼入", "鱼人")
            .replace("鱼入人", "鱼人")
            .replace("元索", "元素")
            .replace("兀素", "元素")
            .replace("元紊", "元素")
            .replace("龙族", "龙")
            .replace("亡炅", "亡灵")
            .replace("机槭", "机械")
            .replace("海盛", "海盗")
            .replace("娜迎", "娜迦")
            .replace("纳迦", "娜迦")
            .replace("|", "l")
    }

    private fun Bitmap.safeCrop(roi: Rect): Bitmap? {
        val left = roi.left.coerceIn(0, width - 1)
        val top = roi.top.coerceIn(0, height - 1)
        val right = roi.right.coerceIn(left + 1, width)
        val bottom = roi.bottom.coerceIn(top + 1, height)
        val cropWidth = right - left
        val cropHeight = bottom - top
        if (cropWidth <= 0 || cropHeight <= 0) return null
        return Bitmap.createBitmap(this, left, top, cropWidth, cropHeight)
    }

    private companion object {
        data class RoiCandidate(
            val label: String,
            val roi: Rect
        )

        data class BitmapVariant(
            val label: String,
            val bitmap: Bitmap
        )

        data class RecognitionAttempt(
            val label: String,
            val roi: Rect,
            val preview: Bitmap,
            val recognizedTribes: Set<Tribe>,
            val rawText: String
        )

        enum class ThresholdMode {
            ADAPTIVE_GAUSSIAN,
            OTSU
        }

        data class RecognitionResult(
            val recognizedTribes: Set<Tribe> = emptySet(),
            val rawText: String = ""
        )

        val TRIBE_ALIASES: Map<Tribe, Set<String>> = mapOf(
            Tribe.BEAST to setOf("野兽", "野鲁", "beast", "beasts"),
            Tribe.DEMON to setOf("恶魔", "demon", "demons"),
            Tribe.DRAGON to setOf("龙", "龙族", "dragon", "dragons"),
            Tribe.ELEMENTAL to setOf("元素", "元索", "elemental", "elementals"),
            Tribe.MECH to setOf("机械", "机", "机槭", "mech", "mechs"),
            Tribe.MURLOC to setOf("鱼人", "鱼入", "murloc", "murlocs"),
            Tribe.NAGA to setOf("娜迦", "纳迦", "娜迎", "naga", "nagas"),
            Tribe.PIRATE to setOf("海盗", "海盛", "pirate", "pirates"),
            Tribe.QUILBOAR to setOf("野猪人", "猪人", "quilboar", "quilboars"),
            Tribe.UNDEAD to setOf("亡灵", "亡炅", "undead")
        )
    }
}
