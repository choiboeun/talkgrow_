package com.talkgrow_.util

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import androidx.camera.core.ImageProxy

/**
 * 경량 YUV_420_888 -> ARGB_8888 변환기
 */
class YuvToRgb {

    private var intBuf: IntArray? = null

    fun yuvToRgb(image: ImageProxy, out: Bitmap) {
        require(image.format == ImageFormat.YUV_420_888) { "Expected YUV_420_888" }
        val raw = image.image ?: return
        yuvToRgb(raw, out)
    }

    fun yuvToRgb(image: Image, out: Bitmap) {
        val w = image.width
        val h = image.height

        var buf = intBuf
        if (buf == null || buf.size != w * h) {
            buf = IntArray(w * h)
            intBuf = buf
        }

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        var p = 0
        for (j in 0 until h) {
            val yRow = j * yRowStride
            val uvRow = (j shr 1) * uvRowStride

            for (i in 0 until w) {
                val y = (yBuf.get(yRow + i).toInt() and 0xFF)
                val uvIdx = uvRow + (i shr 1) * uvPixelStride
                val u = (uBuf.get(uvIdx).toInt() and 0xFF) - 128
                val v = (vBuf.get(uvIdx).toInt() and 0xFF) - 128

                var r = (y + 1.402f * v).toInt()
                var g = (y - 0.344136f * u - 0.714136f * v).toInt()
                var b = (y + 1.772f * u).toInt()
                if (r < 0) r = 0 else if (r > 255) r = 255
                if (g < 0) g = 0 else if (g > 255) g = 255
                if (b < 0) b = 0 else if (b > 255) b = 255

                buf[p++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        out.setPixels(buf, 0, w, 0, 0, w, h)
    }

    fun close() { intBuf = null }
}
