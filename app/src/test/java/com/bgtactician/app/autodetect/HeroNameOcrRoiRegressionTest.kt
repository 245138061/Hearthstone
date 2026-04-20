package com.bgtactician.app.autodetect

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

class HeroNameOcrRoiRegressionTest {

    @Test
    fun `hero select 2 keeps roi away from reroll buttons`() {
        val anchors = HeroNameRoiProbe.analyze(loadFixture("hero-select-2.jpg")).anchors
        assertAnchorInRange(anchors[0], 540, 640, "hero-select-2 槽1")
        assertAnchorInRange(anchors[1], 540, 640, "hero-select-2 槽2")
        assertAnchorInRange(anchors[2], 540, 640, "hero-select-2 槽3")
        assertAnchorInRange(anchors[3], 520, 620, "hero-select-2 槽4")
    }

    @Test
    fun `hero select 3 keeps roi inside name band`() {
        val anchors = HeroNameRoiProbe.analyze(loadFixture("hero-select-3.jpg")).anchors
        assertAnchorInRange(anchors[0], 630, 720, "hero-select-3 槽1")
        assertAnchorInRange(anchors[1], 640, 730, "hero-select-3 槽2")
        assertAnchorInRange(anchors[2], 640, 730, "hero-select-3 槽3")
        assertAnchorInRange(anchors[3], 620, 700, "hero-select-3 槽4")
    }

    private fun loadFixture(name: String): File {
        val candidates = listOf(
            File("app/build/tmp/$name"),
            File("build/tmp/$name")
        )
        return candidates.firstOrNull(File::exists)
            ?: error("未找到测试截图: $name")
    }

    private fun assertAnchorInRange(value: Int, min: Int, max: Int, label: String) {
        assertTrue("$label 锚点越界: $value，不在 [$min, $max]", value in min..max)
    }
}

private object HeroNameRoiProbe {
    private val centers = listOf(0.165f, 0.388f, 0.612f, 0.835f)

    data class ProbeResult(
        val anchors: List<Int>
    )

    fun analyze(file: File): ProbeResult {
        val image = ImageIO.read(file)
        val viewport = detectViewport(image)
        val bannerBottom = detectBannerBottom(image, viewport)
        val viewportWidth = viewport.width()
        val viewportHeight = viewport.height()
        val roiHeight = (viewportHeight * 0.095f).toInt().coerceAtLeast(1)
        val anchors = centers.map { centerRatio ->
            val centerX = viewport.left + (viewportWidth * centerRatio).toInt()
            val searchWindow = if (bannerBottom != null) {
                val remainingHeight = (viewport.bottom - bannerBottom).coerceAtLeast(1)
                SearchWindow(
                    top = bannerBottom + (remainingHeight * 0.34f).toInt(),
                    bottom = bannerBottom + (remainingHeight * 0.66f).toInt(),
                    fixedStripTop = bannerBottom + (remainingHeight * 0.53f).toInt()
                )
            } else {
                SearchWindow(
                    top = viewport.top + (viewportHeight * 0.48f).toInt(),
                    bottom = viewport.top + (viewportHeight * 0.68f).toInt(),
                    fixedStripTop = viewport.top + (viewportHeight * 0.58f).toInt()
                )
            }
            val wideWidth = viewportWidth * 0.285f
            val estimatedTop = estimateNameBandTop(
                image = image,
                left = (centerX - wideWidth / 2f).toInt(),
                right = (centerX + wideWidth / 2f).toInt(),
                searchTop = searchWindow.top,
                searchBottom = searchWindow.bottom,
                roiHeight = roiHeight
            )
            estimatedTop
                ?.coerceIn(
                    searchWindow.fixedStripTop - roiHeight / 3,
                    searchWindow.fixedStripTop + roiHeight / 3
                )
                ?: searchWindow.fixedStripTop
        }
        return ProbeResult(anchors = anchors)
    }

    private fun detectViewport(image: java.awt.image.BufferedImage): ProbeRect {
        val top = findTopBoundary(image)
        val bottom = findBottomBoundary(image)
        val left = findLeftBoundary(image, top, bottom)
        val right = findRightBoundary(image, top, bottom)
        return ProbeRect(left, top, right, bottom)
    }

    private fun findTopBoundary(image: java.awt.image.BufferedImage): Int {
        val searchLimit = max(1, image.height / 4)
        for (y in 0 until searchLimit) {
            if (rowSignal(image, y)) return max(0, y - 2)
        }
        return 0
    }

    private fun findBottomBoundary(image: java.awt.image.BufferedImage): Int {
        val searchStart = max(0, (image.height * 3) / 4)
        for (y in (image.height - 1) downTo searchStart) {
            if (rowSignal(image, y)) return min(image.height, y + 3)
        }
        return image.height
    }

    private fun findLeftBoundary(image: java.awt.image.BufferedImage, top: Int, bottom: Int): Int {
        val searchLimit = max(1, image.width / 5)
        for (x in 0 until searchLimit) {
            if (columnSignal(image, x, top, bottom)) return max(0, x - 2)
        }
        return 0
    }

    private fun findRightBoundary(image: java.awt.image.BufferedImage, top: Int, bottom: Int): Int {
        val searchStart = max(0, (image.width * 4) / 5)
        for (x in (image.width - 1) downTo searchStart) {
            if (columnSignal(image, x, top, bottom)) return min(image.width, x + 3)
        }
        return image.width
    }

    private fun rowSignal(image: java.awt.image.BufferedImage, y: Int): Boolean {
        val step = max(1, image.width / 48)
        var active = 0
        var samples = 0
        var x = 0
        while (x < image.width) {
            if (!isMostlyBlack(image.getRGB(x, y))) active += 1
            samples += 1
            x += step
        }
        return samples > 0 && active >= max(3, samples / 6)
    }

    private fun columnSignal(
        image: java.awt.image.BufferedImage,
        x: Int,
        top: Int,
        bottom: Int
    ): Boolean {
        val step = max(1, (bottom - top) / 36)
        var active = 0
        var samples = 0
        var y = top
        while (y < bottom) {
            if (!isMostlyBlack(image.getRGB(x, y))) active += 1
            samples += 1
            y += step
        }
        return samples > 0 && active >= max(3, samples / 5)
    }

    private fun isMostlyBlack(pixel: Int): Boolean {
        return luma(pixel) < 22
    }

    private fun detectBannerBottom(
        image: java.awt.image.BufferedImage,
        viewport: ProbeRect
    ): Int? {
        val width = viewport.width()
        val height = viewport.height()
        val centerX = viewport.left + width / 2
        val banner = ProbeRect(
            (centerX - width * 0.18f).toInt(),
            viewport.top + (height * 0.03f).toInt(),
            (centerX + width * 0.18f).toInt(),
            viewport.top + (height * 0.135f).toInt()
        )
        val textLine = ProbeRect(
            (centerX - width * 0.16f).toInt(),
            viewport.top + (height * 0.07f).toInt(),
            (centerX + width * 0.16f).toInt(),
            viewport.top + (height * 0.13f).toInt()
        )
        val purpleRatio = coarsePixelRatio(image, banner) { red, green, blue ->
            red > green + 16 && blue > green + 16 && red + blue > 170
        }
        val brightRatio = coarsePixelRatio(image, textLine) { red, green, blue ->
            red + green + blue > 450
        }
        return banner.bottom.takeIf { purpleRatio >= 0.02f && brightRatio >= 0.03f }
    }

    private fun estimateNameBandTop(
        image: java.awt.image.BufferedImage,
        left: Int,
        right: Int,
        searchTop: Int,
        searchBottom: Int,
        roiHeight: Int
    ): Int? {
        if (roiHeight <= 0) return null
        val safeLeft = left.coerceIn(0, image.width - 1)
        val safeRight = right.coerceIn(safeLeft + 1, image.width)
        val safeTop = searchTop.coerceIn(0, image.height - 1)
        val safeBottom = searchBottom.coerceIn(safeTop + roiHeight, image.height)
        if (safeBottom - safeTop < roiHeight) return null

        var bestTop: Int? = null
        var bestScore = Float.NEGATIVE_INFINITY
        val step = (roiHeight / 8).coerceAtLeast(4)
        var candidateTop = safeTop
        while (candidateTop + roiHeight <= safeBottom) {
            val roi = ProbeRect(safeLeft, candidateTop, safeRight, candidateTop + roiHeight)
            val darkRatio = coarsePixelRatio(image, roi) { red, green, blue ->
                luma(red, green, blue) <= 96
            }
            val brightTextRatio = coarsePixelRatio(
                image,
                ProbeRect(
                    roi.left,
                    roi.top + roiHeight / 6,
                    roi.right,
                    roi.bottom - roiHeight / 6
                )
            ) { red, green, blue ->
                luma(red, green, blue) >= 188
            }
            val purpleRatio = coarsePixelRatio(image, roi) { red, green, blue ->
                red > green + 14 && blue > green + 14 && red + blue > 165
            }
            val goldRatio = coarsePixelRatio(image, roi) { red, green, blue ->
                red >= 150 && green >= 110 && blue <= 145
            }
            val score = darkRatio * 0.75f +
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

    private fun coarsePixelRatio(
        image: java.awt.image.BufferedImage,
        roi: ProbeRect,
        matcher: (red: Int, green: Int, blue: Int) -> Boolean
    ): Float {
        if (roi.width() <= 0 || roi.height() <= 0) return 0f
        val left = roi.left.coerceIn(0, image.width - 1)
        val top = roi.top.coerceIn(0, image.height - 1)
        val right = roi.right.coerceIn(left + 1, image.width)
        val bottom = roi.bottom.coerceIn(top + 1, image.height)
        val stepX = ((right - left) / 28).coerceAtLeast(1)
        val stepY = ((bottom - top) / 18).coerceAtLeast(1)
        var matches = 0
        var total = 0
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = image.getRGB(x, y)
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                if (matcher(red, green, blue)) matches += 1
                total += 1
                x += stepX
            }
            y += stepY
        }
        return if (total == 0) 0f else matches.toFloat() / total.toFloat()
    }

    private fun luma(pixel: Int): Int {
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        return luma(red, green, blue)
    }

    private fun luma(red: Int, green: Int, blue: Int): Int {
        return (red * 299 + green * 587 + blue * 114) / 1000
    }

    private data class SearchWindow(
        val top: Int,
        val bottom: Int,
        val fixedStripTop: Int
    )

    private data class ProbeRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        fun width(): Int = right - left
        fun height(): Int = bottom - top
    }
}
