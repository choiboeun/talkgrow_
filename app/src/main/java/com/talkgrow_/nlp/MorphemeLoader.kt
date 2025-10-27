package com.talkgrow_.nlp

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object MorphemeLoader {
    private const val TAG = "MorphemeLoader"

    /** assets/<dir> 아래의 morpheme JSON들을 모두 읽어 label→tag 사전 구성 */
    fun loadTagDictFromAssets(ctx: Context, assetsDir: String = "morpheme"): Map<String, MorphemeTag> {
        val am = ctx.assets
        val files = runCatching { am.list(assetsDir)?.toList().orEmpty() }.getOrElse { emptyList() }
        if (files.isEmpty()) {
            Log.w(TAG, "no files under assets/$assetsDir")
            return emptyMap()
        }
        val labelSet = linkedSetOf<String>()
        for (fn in files) {
            if (!fn.lowercase().endsWith(".json")) continue
            runCatching {
                val txt = am.open("$assetsDir/$fn").use { it.readBytes().toString(Charsets.UTF_8) }
                collectLabelsFromJson(txt, labelSet)
            }.onFailure { e -> Log.w(TAG, "parse $fn failed", e) }
        }
        return labelSet.associateWith { guessTag(it) }
    }

    /** 단일 assets 파일에서 “이 영상의 어휘(라벨) 집합” 추출 */
    fun loadVocabularyFromAssetFile(ctx: Context, assetPath: String): Set<String> {
        val txt = ctx.assets.open(assetPath).use { it.readBytes().toString(Charsets.UTF_8) }
        val s = linkedSetOf<String>()
        collectLabelsFromJson(txt, s)
        Log.i(TAG, "scenario vocab ${s.size} loaded from assets/$assetPath")
        return s
    }

    /** 절대경로 파일에서 “이 영상의 어휘(라벨) 집합” 추출 (개발/디버그용) */
    fun loadVocabularyFromFilePath(filePath: String): Set<String> {
        val s = linkedSetOf<String>()
        val txt = File(filePath).readText(Charsets.UTF_8)
        collectLabelsFromJson(txt, s)
        Log.i(TAG, "scenario vocab ${s.size} loaded from $filePath")
        return s
    }

    internal fun collectLabelsFromJson(txt: String, out: MutableSet<String>) {
        val obj = JSONObject(txt)
        val arr: JSONArray = obj.optJSONArray("data") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val seg = arr.optJSONObject(i) ?: continue
            val attrs = seg.optJSONArray("attributes") ?: JSONArray()
            for (j in 0 until attrs.length()) {
                val a = attrs.optJSONObject(j) ?: continue
                val name = a.optString("name", "").trim()
                if (name.isNotEmpty()) out += name
            }
        }
    }

    /** 간이 태깅 휴리스틱 */
    private fun guessTag(label: String): MorphemeTag {
        val placeHints = listOf("역","대학교","박물관","터미널","동","구청","군청","보건소","백화점","월드")
        if (placeHints.any { label.endsWith(it) }) return MorphemeTag.PLACE
        if (label in listOf("송파","서울","강남","명동","여의도","영등포","마포대교","국립박물관")) return MorphemeTag.PLACE
        if (label.lastOrNull() in listOf('시','분','호')) return MorphemeTag.TIME
        if (label.matches(Regex("^[0-9]+(시|분|호)$"))) return MorphemeTag.TIME
        if (label in listOf("오늘","시간","만원")) return MorphemeTag.TIME
        if (label in listOf("오른쪽","왼쪽","뒤")) return MorphemeTag.DIRECTION
        if (label.endsWith("다")) return MorphemeTag.VERB
        if (label in listOf("맞다","괜찮다","가깝다","늦다","심하다","다르다","따뜻하다")) return MorphemeTag.ADJ
        if (label in listOf("안녕하세요","감사합니다","미안합니다","오케이","수고","반갑다")) return MorphemeTag.INTERJ
        return MorphemeTag.NOUN
    }
}
