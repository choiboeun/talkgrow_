// app/src/main/java/com/talkgrow_/util/LabelLoader.kt
package com.talkgrow_.util

import android.content.Context
import org.json.JSONObject
import java.nio.charset.Charset

/**
 * 간단한 라벨 로더: { "안녕하세요": 242, ... } 형태 JSON을 양방향 맵으로 로드
 */
object LabelLoader {
    data class Labels(
        val name2id: Map<String, Int>,
        val id2name: Map<Int, String>
    ) {
        val size: Int get() = id2name.size
    }

    fun loadSmart(
        ctx: Context,
        assetName: String,
        alias: Map<String, String>? = null,
        numClassesHint: Int = 0
    ): Labels {
        val txt = ctx.assets.open(assetName).use { it.readBytes().toString(Charset.forName("UTF-8")) }
        val j = JSONObject(txt)
        val keys = j.keys()
        val n2i = HashMap<String, Int>()
        val i2n = HashMap<Int, String>()
        while (keys.hasNext()) {
            val k = keys.next()
            val id = j.getInt(k)
            val name = alias?.get(k) ?: k
            n2i[name] = id
            i2n[id] = name
        }
        return Labels(n2i, i2n)
    }
}
