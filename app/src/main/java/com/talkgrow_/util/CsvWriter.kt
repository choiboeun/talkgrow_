// android_app/src/main/java/com/talkgrow_/util/CsvWriter.kt
package com.talkgrow_.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvWriter {
    private val sdf = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    /** seq: Array(91){ FloatArray(134) } 를 CSV로 저장 */
    fun writeNormalizedSeq(ctx: Context, segmentId: Long, seq: Array<FloatArray>): File {
        val dir = File(ctx.getExternalFilesDir(null), "norm_seq")
        dir.mkdirs()
        val f = File(dir, "seg_${segmentId}_${sdf.format(Date())}.csv")
        f.bufferedWriter().use { bw ->
            for (i in seq.indices) {
                bw.append(seq[i].joinToString(",") { v -> v.toString() })
                bw.newLine()
            }
        }
        return f
    }
}
