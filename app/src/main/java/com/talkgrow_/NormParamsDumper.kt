// android_app/src/main/java/com/talkgrow_/NormParamsDumper.kt
package com.talkgrow_

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 앱에서 사용 중인 정규화 파라미터(mean/std)와 구성(cfg)을 JSON으로 저장.
 * 파일명: Documents/talkgrow_norm/norm_params_app.json
 */
object NormParamsDumper {
    fun dump(context: Context, means: FloatArray, stds: FloatArray, config: JSONObject? = null) {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "talkgrow_norm")
        dir.mkdirs()
        val obj = JSONObject().apply {
            put("means", JSONArray(means.map { it }))
            put("stds", JSONArray(stds.map { it }))
            if (config != null) put("config", config)
        }
        File(dir, "norm_params_app.json").writeText(obj.toString())
    }
}
