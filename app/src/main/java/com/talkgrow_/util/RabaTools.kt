package com.talkgrow_.util

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888 사용 시,
 * 한 개 plane(buffer)로 RGBA가 들어온다. rowStride/pixelStride 보정하여 비트맵으로 복사.
 */
object RgbaTools {

    private var cache: Bitmap? = null

    fun obtainBitmap(w: Int, h: Int): Bitmap {
        val cur = cache
        if (cur == null || cur.width != w || cur.height != h) {
            cache = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
        return cache!!
    }

    fun copyImageProxyToBitmap(image: ImageProxy, out: Bitmap) {
        // 일부 버전에선 상수가 노출되지 않으므로 형식 검사는 생략/완화
        val plane = image.planes[0]
        val src = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride // 보통 4 (RGBA)

        val w = image.width
        val h = image.height

        val dst = ByteBuffer.allocate(w * h * 4)

        if (rowStride == w * 4 && pixelStride == 4) {
            src.rewind()
            dst.put(src)
        } else {
            val row = ByteArray(w * 4)
            for (y in 0 until h) {
                val srcRowStart = y * rowStride
                var dstIdx = 0
                var x = 0
                while (x < w) {
                    val srcIdx = srcRowStart + x * pixelStride
                    src.position(srcIdx)
                    row[dstIdx++] = src.get() // R
                    row[dstIdx++] = src.get() // G
                    row[dstIdx++] = src.get() // B
                    row[dstIdx++] = src.get() // A
                    x++
                }
                dst.put(row)
            }
        }
        dst.rewind()
        out.copyPixelsFromBuffer(dst)
    }
}
