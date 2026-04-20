package com.bgtactician.app.autodetect

import android.graphics.Bitmap

data class CapturedFrame(
    val bitmap: Bitmap,
    val width: Int,
    val height: Int,
    val timestampMillis: Long
)
