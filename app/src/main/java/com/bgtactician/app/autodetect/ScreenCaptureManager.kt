package com.bgtactician.app.autodetect

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.atomic.AtomicReference

class ScreenCaptureManager(
    private val context: Context,
    private val onProjectionStopped: (() -> Unit)? = null
) {
    private val projectionManager =
        context.getSystemService(MediaProjectionManager::class.java)

    private val captureThread = HandlerThread("bgt-screen-capture").apply { start() }
    private val captureHandler = Handler(captureThread.looper)
    private val latestFrame = AtomicReference<CapturedFrame?>(null)

    @Volatile
    private var active = false

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    fun isActive(): Boolean = active

    @Synchronized
    fun start(permission: ScreenCapturePermission): Result<Unit> = runCatching {
        stopCapture()

        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener(
            { source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                handleImage(image)
            },
            captureHandler
        )

        val projection = projectionManager.getMediaProjection(
            permission.resultCode,
            Intent(permission.data)
        ) ?: error("MediaProjection permission token is invalid")
        projection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    stopCapture()
                    onProjectionStopped?.invoke()
                }
            },
            captureHandler
        )

        val display = projection.createVirtualDisplay(
            "bgtactician-capture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            captureHandler
        )

        imageReader = reader
        mediaProjection = projection
        virtualDisplay = display
        active = true
    }

    fun latestFrame(): CapturedFrame? {
        val snapshot = latestFrame.get() ?: return null
        return CapturedFrame(
            bitmap = snapshot.bitmap.copy(Bitmap.Config.ARGB_8888, false),
            width = snapshot.width,
            height = snapshot.height,
            timestampMillis = snapshot.timestampMillis
        )
    }

    @Synchronized
    fun stopCapture() {
        active = false
        val projection = mediaProjection
        mediaProjection = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.stop()
        latestFrame.getAndSet(null)?.bitmap?.recycle()
    }

    fun release() {
        stopCapture()
        captureThread.quitSafely()
    }

    private fun handleImage(image: Image) {
        try {
            val bitmap = image.toBitmap()
            val frame = CapturedFrame(
                bitmap = bitmap,
                width = image.width,
                height = image.height,
                timestampMillis = System.currentTimeMillis()
            )
            latestFrame.getAndSet(frame)?.bitmap?.recycle()
        } finally {
            image.close()
        }
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes.first()
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val paddedBitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        buffer.rewind()
        paddedBitmap.copyPixelsFromBuffer(buffer)

        val cropped = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
        if (cropped != paddedBitmap) {
            paddedBitmap.recycle()
        }
        return cropped
    }
}
