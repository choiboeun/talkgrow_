package com.talkgrow_

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class MeaningMapper(
    private val ctx: Context? = null,
    private val rulesAssetPath: String = "mapper_rules.json"
) {

    data class Rule(
        val id: String,
        val seq: List<List<String>>, // 각 스텝별 허용 토큰 집합(문자 라벨)
        val window: Int,
        val emit: String,
        val tts: String,
        val cooldownMs: Long
    )

    private val rules = mutableListOf<Rule>()
    private var lastEmitAt = 0L

    init {
        val json = ctx?.assets?.open(rulesAssetPath)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("mapper rules asset not found: $rulesAssetPath")
        val root = JSONObject(json)

        // 1) vocab: { "라벨": ID } → id→라벨 역인덱스
        val id2token = mutableMapOf<Int, String>()
        root.optJSONObject("vocab")?.let { voc ->
            voc.keys().forEach { k ->
                val v = voc.optInt(k, Int.MIN_VALUE)
                if (v != Int.MIN_VALUE) id2token[v] = k
            }
        }
        // (보강) vocab 없을 때: 루트에서 값이 Int인 항목을 스캔해도 됨
        if (id2token.isEmpty()) {
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = root.opt(k)
                if (v is Int) id2token[v] = k
            }
        }
        if (id2token.isEmpty()) {
            throw IllegalStateException("vocab not found: provide {\"vocab\": {\"라벨\":ID,...}} or a root map.")
        }

        // 2) lex: 별칭 → (ID/문자 혼용 가능) → 문자 라벨 리스트로 정규화
        val lex = mutableMapOf<String, List<String>>()
        root.optJSONObject("lex")?.let { lx ->
            lx.keys().forEach { alias ->
                val arr = lx.get(alias)
                val list = when (arr) {
                    is JSONArray -> (0 until arr.length()).map { idx ->
                        when (val e = arr.get(idx)) {
                            is Int -> id2token[e] ?: error("lex '$alias': unknown id $e")
                            is String -> e
                            else -> error("lex '$alias': unsupported element ${e::class.java}")
                        }
                    }
                    is String -> listOf(arr)
                    is Int -> listOf(id2token[arr] ?: error("lex '$alias': unknown id $arr"))
                    else -> error("lex '$alias': unsupported type ${arr::class.java}")
                }
                lex[alias] = list
            }
        }

        // 3) rules 파싱
        val rArr = root.optJSONArray("rules")
            ?: throw IllegalStateException("rules array missing")
        for (i in 0 until rArr.length()) {
            val o = rArr.getJSONObject(i)
            val id = o.optString("id", "rule_$i")
            val window = o.optInt("window", o.optJSONObject("when")?.optInt("window", 3) ?: 3)
            val raw = o.optJSONArray("seq")
                ?: o.optJSONObject("when")?.optJSONArray("seq")
                ?: o.optJSONArray("tokens")
                ?: error("rule $id: seq/tokens not found")

            val seq = mutableListOf<List<String>>()
            for (j in 0 until raw.length()) {
                val el = raw.get(j)
                when (el) {
                    is Int -> { // 숫자 ID
                        val tok = id2token[el] ?: error("rule $id: unknown id $el")
                        seq += listOf(tok)
                    }
                    is String -> {
                        if (el.startsWith("$")) {
                            val key = el.removePrefix("$")
                            seq += (lex[key] ?: error("rule $id: unknown lex '$key'"))
                        } else {
                            seq += listOf(el) // 직접 라벨
                        }
                    }
                    else -> error("rule $id: unsupported seq element ${el::class.java}")
                }
            }

            val emit = o.optString("emit", o.optString("out"))
                .takeIf { it.isNotBlank() } ?: error("rule $id: emit/out missing")
            val tts = o.optString("tts", emit)
            val cooldown = o.optLong("cooldown_ms", 1000L)

            rules += Rule(id, seq, window, emit, tts, cooldown)
        }
    }

    /**
     * tokens: 확정 라벨 시퀀스 (예: ["안녕하세요","건강","괜찮다"])
     * return: 문장(없으면 null)
     */
    fun mapTokens(tokens: List<String>, nowMs: Long = System.currentTimeMillis()): String? {
        // (선택) 과도한 연속 발화 보호: per-rule 쿨다운은 emit 시각만 갱신
        if (nowMs - lastEmitAt < 50) return null

        for (rule in rules) {
            val k = rule.seq.size
            if (k == 0) continue
            val maxStart = (tokens.size - 1).coerceAtLeast(0)
            for (s in 0..maxStart) {
                var i = s
                var matched = 0
                var steps = 0
                while (i < tokens.size && matched < k && steps < rule.window) {
                    val wantSet = rule.seq[matched]
                    val got = tokens[i]
                    if (wantSet.contains(got)) matched++
                    i++; steps++
                }
                if (matched == k) {
                    lastEmitAt = nowMs
                    return rule.emit
                }
            }
        }
        return null
    }
}
