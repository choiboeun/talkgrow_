package com.talkgrow_.util

import android.content.Context
import android.graphics.*
import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

class YuvToRgbConverter(@Suppress("UNUSED_PARAMETER") context: Context) {
    fun yuvToRgb(image: ImageProxy, out: Bitmap) {
        require(image.format == ImageFormat.YUV_420_888)

        val yPlane = image.planes[0].buffer
        val uPlane = image.planes[1].buffer
        val vPlane = image.planes[2].buffer

        val ySize = yPlane.remaining()
        val uSize = uPlane.remaining()
        val vSize = vPlane.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)

        // Y
        yPlane.get(nv21, 0, ySize)

        // UV -> NV21(VU) 재배열
        val chromaRowStride = image.planes[1].rowStride
        val chromaPixelStride = image.planes[1].pixelStride
        var offset = ySize
        for (row in 0 until image.height / 2) {
            var col = 0
            while (col < image.width / 2) {
                val uIndex = row * chromaRowStride + col * chromaPixelStride
                val vIndex = row * image.planes[2].rowStride + col * image.planes[2].pixelStride
                nv21[offset++] = vPlane.get(vIndex)
                nv21[offset++] = uPlane.get(uIndex)
                col++
            }
        }

        val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val outStream = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, image.width, image.height), 100, outStream)
        val jpegBytes = outStream.toByteArray()
        val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

        val canvas = Canvas(out)
        canvas.drawBitmap(bmp, null, Rect(0, 0, out.width, out.height), null)
    }

    fun release() { /* no-op */ }
}
