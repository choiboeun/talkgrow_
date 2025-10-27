package com.talkgrow_.util

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * word2id.json 로드 및 캐시
 * assets/word2id.json 에서 1회만 읽어 전역 보관
 */
object VocabRepo {

    private var word2id: Map<String, Int>? = null

    /** 단어 사전 로드 (1회) */
    fun ensureLoaded(context: Context) {
        if (word2id != null) return
        try {
            val jsonStr = context.assets.open("word2id.json").bufferedReader().use { it.readText() }
            val jsonObj = JSONObject(jsonStr)
            val map = mutableMapOf<String, Int>()
            jsonObj.keys().forEach { k ->
                map[k] = jsonObj.getInt(k)
            }
            word2id = map
            Log.i("VocabRepo", "word2id loaded: ${map.size} entries")
        } catch (e: Exception) {
            Log.e("VocabRepo", "failed to load word2id.json: ${e.message}")
            word2id = emptyMap()
        }
    }

    /** 단어→ID 맵 반환 */
    fun getWord2Id(context: Context): Map<String, Int> {
        if (word2id == null) ensureLoaded(context)
        return word2id ?: emptyMap()
    }

    /** 사전 key 집합 반환 (토큰 매칭용) */
    fun getVocabSet(context: Context): Set<String> {
        return getWord2Id(context).keys
    }
}
