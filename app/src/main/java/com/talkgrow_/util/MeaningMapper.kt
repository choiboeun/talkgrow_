package com.talkgrow_.util

import android.content.Context
import org.json.JSONObject
import java.nio.charset.Charset

object MeaningMapper {
    private var map: Map<String, String>? = null

    /** assets/meaning_map.json 이 있으면 로드, 없으면 조용히 패스 */
    fun init(context: Context, assetName: String = "meaning_map.json") {
        runCatching {
            val txt = context.assets.open(assetName)
                .use { it.readBytes().toString(Charset.forName("UTF-8")) }
            val j = JSONObject(txt)
            val m = mutableMapOf<String, String>()
            val it = j.keys()
            while (it.hasNext()) {
                val k = it.next()
                m[k] = j.getString(k)
            }
            map = m
        }.onFailure {
            map = null // 파일 없으면 그냥 맵핑 안 함
        }
    }

    /** 라벨을 자연어로 치환(없으면 원문 반환) */
    fun pretty(raw: String?): String? {
        val m = map ?: return raw
        return raw?.let { m[it] ?: it }
    }
}
