// android_app/src/main/java/com/talkgrow_/FrameDumpWriter.kt
package com.talkgrow_

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 세그먼트 원본 프레임들의 누적 버퍼를 NPY(float32, shape=(T,134))로 저장.
 * 파일명: seg_<segmentId>_raw_t<T>_f134.npy
 */
class FrameDumpWriter(
    context: Context,
    sessionPrefix: String = "talkgrow_norm"
) : AutoCloseable {

    private val outDir: File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
        sessionPrefix
    ).apply { mkdirs() }

    private val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** 세그먼트 단위로 호출되도록 설계된 SegmentPipeline의 콜백(ts는 프레임 ts). */
    @Synchronized
    fun append(segmentId: Long, frames: List<FloatArray>) {
        if (frames.isEmpty()) return
        val t = frames.size
        val f = frames[0].size
        val file = File(outDir, "seg_${segmentId}_raw_t${t}_f${f}.npy")
        writeNpyFloat32_2D(file, frames)
    }

    /** 프레임 단위 원시 호출(ts, frame)을 누적 저장하고 싶다면 이 메서드 사용 */
    private val accum = mutableMapOf<Long, MutableList<FloatArray>>()
    @Synchronized
    fun append(ts: Long, frame: FloatArray) {
        val segKey = currentSegKey()
        val list = accum.getOrPut(segKey) { mutableListOf() }
        // 복사 후 누적(외부 변형 방지)
        list += frame.copyOf()
    }

    /** 세그먼트 종료 시 외부에서 명시적으로 flushRaw(segmentId) 호출 */
    @Synchronized
    fun flushRaw(segmentId: Long) {
        val segKey = currentSegKey()
        val data = accum.remove(segKey) ?: return
        append(segmentId, data)
    }

    private fun currentSegKey(): Long = stamp.hashCode().toLong()

    private fun writeNpyFloat32_2D(file: File, data: List<FloatArray>) {
        val t = data.size
        val f = data[0].size
        // NPY v1.0 header
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
