package com.talkgrow_.debug

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

object SignDebug {
    private val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /** 앱 내부저장소에 CSV 파일 한 줄 append */
    fun appendCsv(context: Context, fileName: String, headerIfNew: String? = null, line: String) {
        val dir = File(context.filesDir, "debug")
        if (!dir.exists()) dir.mkdirs()
        val f = File(dir, fileName)
        val newFile = !f.exists()
        f.appendText((if (newFile && headerIfNew != null) headerIfNew + "\n" else "") + line + "\n")
    }

    fun nowStamp(): String = sdf.format(System.currentTimeMillis())
}
