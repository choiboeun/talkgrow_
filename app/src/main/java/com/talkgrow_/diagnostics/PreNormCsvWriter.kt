package com.talkgrow_.diagnostics

import android.content.Context
import com.talkgrow_.util.KP
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 정규화 전 좌표(KP) 프레임을 CSV로 저장 + 세그먼트 태그(segmentId,state) 포함.
 * 파일 위치: getExternalFilesDir(null)/frame_dumps/prenorm_*.csv
 */
class PreNormCsvWriter(ctx: Context) : AutoCloseable {
    private val file = File(
        File(ctx.getExternalFilesDir(null), "frame_dumps").apply { mkdirs() },
        "prenorm_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.csv"
    )
    private val w = FileWriter(file, false).apply { appendLine(header()) }

    private fun header(): String = buildString {
        append("ts,segment,state,srcW,srcH,mirroredPreview,handsVisible")
        fun cols(prefix: String, n: Int) {
            for (i in 0 until n) append(",${prefix}${i}_x,${prefix}${i}_y")
        }
        cols("pose", 33); cols("lh", 21); cols("rh", 21)
    }

    @Synchronized
    fun append(
        ts: Long,
        segmentId: Long,
        state: String,
        srcW: Int,
        srcH: Int,
        mirroredPreview: Boolean,
        handsVisible: Boolean,
        pose: List<KP>,
        left: List<KP>,
        right: List<KP>
    ) {
        fun toCsv(points: List<KP>, n: Int): String {
            val src = points // 리스트를 캡쳐해서 buildString 블록 안에서도 확실히 참조
            return buildString {
                for (i in 0 until n) {
                    val p: KP? = if (i < src.size) src[i] else null
                    append(',')
                    append(p?.x ?: "")
                    append(',')
                    append(p?.y ?: "")
                }
            }
        }

        val line = buildString {
            append(ts).append(',').append(segmentId).append(',').append(state).append(',')
            append(srcW).append(',').append(srcH).append(',')
            append(if (mirroredPreview) 1 else 0).append(',').append(if (handsVisible) 1 else 0)
            append(toCsv(pose, 33)); append(toCsv(left, 21)); append(toCsv(right, 21))
        }
        w.appendLine(line); w.flush()
    }

    fun path(): String = file.absolutePath
    override fun close() { runCatching { w.flush(); w.close() } }
}
