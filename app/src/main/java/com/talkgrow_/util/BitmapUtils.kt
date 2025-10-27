package com.talkgrow_.util

import android.graphics.Bitmap
import android.graphics.Matrix

object BitmapUtils {
    fun rotateBitmap(src: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees % 360 == 0) return src
        val m = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}
