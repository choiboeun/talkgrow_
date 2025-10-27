package com.talkgrow_.inference

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.support.common.FileUtil

object LabelLoader {
    /**
     * assets 안의 labels JSON을 읽어 labels 리스트로 반환.
     * - ["버스","곳","가깝다", ...] 형태도 OK
     * - {"0":"버스","1":"곳","2":"가깝다", ...} 형태도 OK
     */
    fun load(ctx: Context, assetName: String): List<String> {
        val byteBuffer = FileUtil.loadMappedFile(ctx, assetName)
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        val text = String(bytes, Charsets.UTF_8).trim()

        return when {
            text.startsWith("[") -> {
                val arr = JSONArray(text)
                (0 until arr.length()).map { arr.getString(it) }
            }
            text.startsWith("{") -> {
                val obj = JSONObject(text)
                // 키를 숫자 인덱스로 정렬해서 반환
                obj.keys().asSequence()
                    .map { it to obj.getString(it) }
                    .sortedBy { it.first.toIntOrNull() ?: Int.MAX_VALUE }
                    .map { it.second }
                    .toList()
            }
            else -> emptyList()
        }
    }
}
