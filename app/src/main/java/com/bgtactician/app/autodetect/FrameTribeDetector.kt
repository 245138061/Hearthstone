package com.bgtactician.app.autodetect

import android.graphics.Bitmap
import android.graphics.Rect
import com.bgtactician.app.data.model.Tribe

data class CapturedFrame(
    val bitmap: Bitmap,
    val width: Int,
    val height: Int,
    val timestampMillis: Long
)

data class DetectionObservation(
    val hasFrame: Boolean,
    val lobbyVisible: Boolean = false,
    val availableTribes: Set<Tribe> = emptySet(),
    val bannedTribes: Set<Tribe> = emptySet(),
    val attentionRequired: Boolean = false,
    val viewport: Rect? = null,
    val headerRegion: Rect? = null,
    val debugText: String? = null,
    val debugPreview: Bitmap? = null
)

interface FrameTribeDetector {
    fun analyze(frame: CapturedFrame): DetectionObservation
}

class ViewportHeuristicDetector : FrameTribeDetector {
    override fun analyze(frame: CapturedFrame): DetectionObservation {
        val viewport = GameViewportDetector.detect(frame.bitmap)
            ?: return DetectionObservation(
                hasFrame = true,
                attentionRequired = true
            )

        val viewportArea = viewport.width().toFloat() * viewport.height().toFloat()
        val frameArea = frame.width.toFloat() * frame.height.toFloat()
        val coverage = if (frameArea <= 0f) 0f else viewportArea / frameArea

        return DetectionObservation(
            hasFrame = true,
            lobbyVisible = coverage >= 0.52f,
            attentionRequired = coverage < 0.36f,
            viewport = viewport
        )
    }
}
