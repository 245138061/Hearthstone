package com.bgtactician.app.autodetect

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

object GameViewportDetector {
    fun detect(bitmap: Bitmap): Rect? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null

        val top = findTopBoundary(bitmap)
        val bottom = findBottomBoundary(bitmap)
        val left = findLeftBoundary(bitmap, top, bottom)
        val right = findRightBoundary(bitmap, top, bottom)

        if (left >= right || top >= bottom) return null

        val rect = Rect(left, top, right, bottom)
        val minWidth = (bitmap.width * 0.45f).toInt()
        val minHeight = (bitmap.height * 0.45f).toInt()
        if (rect.width() < minWidth || rect.height() < minHeight) return null

        return rect
    }

    private fun findTopBoundary(bitmap: Bitmap): Int {
        val searchLimit = max(1, bitmap.height / 4)
        for (y in 0 until searchLimit) {
            if (rowSignal(bitmap, y)) return max(0, y - 2)
        }
        return 0
    }

    private fun findBottomBoundary(bitmap: Bitmap): Int {
        val searchStart = max(0, (bitmap.height * 3) / 4)
        for (y in (bitmap.height - 1) downTo searchStart) {
            if (rowSignal(bitmap, y)) return min(bitmap.height, y + 3)
        }
        return bitmap.height
    }

    private fun findLeftBoundary(bitmap: Bitmap, top: Int, bottom: Int): Int {
        val searchLimit = max(1, bitmap.width / 5)
        for (x in 0 until searchLimit) {
            if (columnSignal(bitmap, x, top, bottom)) return max(0, x - 2)
        }
        return 0
    }

    private fun findRightBoundary(bitmap: Bitmap, top: Int, bottom: Int): Int {
        val searchStart = max(0, (bitmap.width * 4) / 5)
        for (x in (bitmap.width - 1) downTo searchStart) {
            if (columnSignal(bitmap, x, top, bottom)) return min(bitmap.width, x + 3)
        }
        return bitmap.width
    }

    private fun rowSignal(bitmap: Bitmap, y: Int): Boolean {
        val step = max(1, bitmap.width / 48)
        var active = 0
        var samples = 0
        var x = 0
        while (x < bitmap.width) {
            if (!isMostlyBlack(bitmap.getPixel(x, y))) active++
            samples++
            x += step
        }
        return samples > 0 && active >= max(3, samples / 6)
    }

    private fun columnSignal(bitmap: Bitmap, x: Int, top: Int, bottom: Int): Boolean {
        val step = max(1, (bottom - top) / 36)
        var active = 0
        var samples = 0
        var y = top
        while (y < bottom) {
            if (!isMostlyBlack(bitmap.getPixel(x, y))) active++
            samples++
            y += step
        }
        return samples > 0 && active >= max(3, samples / 5)
    }

    private fun isMostlyBlack(pixel: Int): Boolean {
        val alpha = Color.alpha(pixel)
        if (alpha <= 16) return true

        val red = Color.red(pixel)
        val green = Color.green(pixel)
        val blue = Color.blue(pixel)
        val luma = (red * 299 + green * 587 + blue * 114) / 1000

        return luma < 22
    }
}
