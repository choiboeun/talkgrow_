// android_app/src/main/java/com/talkgrow_/PreNormSequenceWriter.kt
package com.talkgrow_

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 정규화 직전 시퀀스(T×134)를 NPY(float32)로 저장.
 * 파일명: seg_<segmentId>_prenorm_t<T>_f134.npy
 */
class PreNormSequenceWriter(
    context: Context,
    sessionPrefix: String = "talkgrow_norm"
) : AutoCloseable {

    private val outDir: File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
        sessionPrefix
    ).apply { mkdirs() }

    @Synchronized
    fun appendSequence(segmentId: Long, sequence: Array<FloatArray>) {
        if (sequence.isEmpty()) return
        val t = sequence.size
        val f = sequence[0].size
        val file = File(outDir, "seg_${segmentId}_prenorm_t${t}_f${f}.npy")
        writeNpyFloat32_2D(file, sequence)
    }

    private fun writeNpyFloat32_2D(file: File, data: Array<FloatArray>) {
        val t = data.size
        val f = if (t > 0) data[0].size else 134
        val headerDict = "{'descr': '<f4', 'fortran_order': False, 'shape': ($t, $f), }"
        var padLen = 16 - ((10 + headerDict.length + 1) % 16)
        if (padLen == 16) padLen = 0
        val header = ByteArray(10 + headerDict.length + padLen + 1)
        header[0] = 0x93.toByte(); header[1] = 'N'.code.toByte(); header[2] = 'U'.code.toByte()
        header[3] = 'M'.code.toByte(); header[4] = 'P'.code.toByte(); header[5] = 'Y'.code.toByte()
        header[6] = 1; header[7] = 0
        val headerLen = (header.size - 10).toShort()
        header[8] = (headerLen.toInt() and 0xFF).toByte()
        header[9] = ((headerLen.toInt() ushr 8) and 0xFF).toByte()
        val hdrStr = headerDict + " ".repeat(padLen) + "\n"
        for (i in hdrStr.indices) header[10 + i] = hdrStr[i].code.toByte()

        FileOutputStream(file).use { fos ->
            fos.write(header)
            val bb = ByteBuffer.allocate(t * f * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (row in data) for (v in row) bb.putFloat(v)
            fos.write(bb.array())
        }
    }

    override fun close() { /* no-op */ }
}
