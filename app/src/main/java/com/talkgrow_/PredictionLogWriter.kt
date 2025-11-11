// android_app/src/main/java/com/talkgrow_/PredictionLogWriter.kt
package com.talkgrow_

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 세그먼트별 승인된 Top-1 예측 CSV 로그
 * 경로: /sdcard/Android/data/com.talkgrow_/files/frame_dumps/prediction_*.csv
 * 컬럼: segment,timestamp,label,prob
 */
class PredictionLogWriter(
    context: Context,
    sessionPrefix: String = "prediction"
) : AutoCloseable {

    private val file: File
    private val writer: BufferedWriter

    init {
        val dir = File(context.getExternalFilesDir(null), "frame_dumps").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        file = File(dir, "${sessionPrefix}_${stamp}.csv")
        writer = BufferedWriter(FileWriter(file, false))
        writer.appendLine("segment,timestamp,label,prob")
    }

    @Synchronized
    fun append(segmentId: Long, label: String, probability: Float, timestamp: Long) {
        try {
            writer.appendLine("$segmentId,$timestamp,$label,$probability")
        } catch (ioe: IOException) {
            ioe.printStackTrace()
        }
    }

    fun path(): String = file.absolutePath

    override fun close() {
        runCatching { writer.flush(); writer.close() }
    }
}
