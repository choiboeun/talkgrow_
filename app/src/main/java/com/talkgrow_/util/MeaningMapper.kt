package com.talkgrow_.util

import android.content.Context
import org.json.JSONArray
import java.util.ArrayDeque

/**
 * assets/mapper_rules.json 를 로드해
 * ["라벨","라벨2"...] → "문장" 매핑을 수행.
 * - 긴 조합 우선, 없으면 단일 토큰 기본문장으로 폴백.
 */
class MeaningMapper(private val context: Context) {

    private var loaded = false
    private val comboMap = HashMap<String, String>() // "라벨1,라벨2" → 문장

    private fun ensureLoaded() {
        if (loaded) return
        val am = context.assets
        am.open("mapper_rules.json").use { input ->
            val arr = JSONArray(input.bufferedReader().readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val labels = o.getJSONArray("labels")
                val key = (0 until labels.length()).joinToString(",") { labels.getString(it) }
                comboMap[key] = o.getString("sentence")
            }
        }
        loaded = true
    }

    fun tryAssemble(tokens: ArrayDeque<String>): String? {
        ensureLoaded()
        if (tokens.isEmpty()) return null
        val list = tokens.toList()

        // 1) 긴 시퀀스부터 완전일치 검색
        for (n in list.size downTo 2) {
            val key = list.takeLast(n).joinToString(",")
            comboMap[key]?.let { return it }
        }
        // 2) 단일 토큰 폴백
        val last = list.last()
        comboMap[last]?.let { return it } // 단일 키가 있을 수도 있음 (labels:["감사"])
        return defaultForSingle(last)
    }

    private fun defaultForSingle(label: String): String? {
        return when (label) {
            "안녕하세요" -> "안녕하세요."
            "감사", "감사합니다" -> "감사합니다."
            "죄송" -> "죄송합니다."
            "부탁" -> "부탁드립니다."
            else -> label // 마지막 수단: 라벨 그대로 발화
        }
    }
}
