package com.bgtactician.app.autodetect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutoDetectDebugDumper(
    private val context: Context
) {
    private val dumpDir by lazy { File(context.filesDir, "autodetect-debug").apply { mkdirs() } }

    fun dump(
        frame: CapturedFrame,
        observation: DetectionObservation
    ): String? {
        val header = observation.headerRegion?.let { crop(frame.bitmap, it) }
        val preview = observation.debugPreview
        return runCatching {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(frame.timestampMillis))
            val dir = File(dumpDir, timestamp).apply { mkdirs() }
            saveBitmap(File(dir, "frame.png"), frame.bitmap)
            header?.let { saveBitmap(File(dir, "header.png"), it) }
            preview?.let { saveBitmap(File(dir, "processed.png"), it) }
            File(dir, "ocr.txt").writeText(observation.debugText.orEmpty())
            File(dir, "meta.txt").writeText(
                buildString {
                    appendLine("viewport=${observation.viewport?.flattenToString().orEmpty()}")
                    appendLine("header=${observation.headerRegion?.flattenToString().orEmpty()}")
                    appendLine("available=${observation.availableTribes.joinToString(",") { it.name }}")
                    appendLine("banned=${observation.bannedTribes.joinToString(",") { it.name }}")
                    appendLine("attention=${observation.attentionRequired}")
                }
            )
            dir.absolutePath
        }.getOrNull().also {
            header?.recycle()
        }
    }

    private fun crop(source: Bitmap, rect: Rect): Bitmap? {
        val left = rect.left.coerceIn(0, source.width - 1)
        val top = rect.top.coerceIn(0, source.height - 1)
        val right = rect.right.coerceIn(left + 1, source.width)
        val bottom = rect.bottom.coerceIn(top + 1, source.height)
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return null
        return Bitmap.createBitmap(source, left, top, width, height)
    }

    private fun saveBitmap(file: File, bitmap: Bitmap) {
        file.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
