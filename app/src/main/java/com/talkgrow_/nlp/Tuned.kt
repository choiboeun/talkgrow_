package com.talkgrow_.nlp


/** 라벨의 기본 품사/우선순위/기본 문장 */
object Tuned {

    val tags: Map<String, MorphemeTag> = mapOf(
        "버스" to MorphemeTag.NOUN, "길" to MorphemeTag.NOUN, "에어컨" to MorphemeTag.NOUN,
        "물품보관" to MorphemeTag.PLACE, "나사렛" to MorphemeTag.PLACE, "샛길" to MorphemeTag.NOUN,
        "건너다" to MorphemeTag.VERB, "공항" to MorphemeTag.PLACE, "송파" to MorphemeTag.PLACE,
        "기차" to MorphemeTag.NOUN, "서울대학교" to MorphemeTag.PLACE,
        "서울농아인협회" to MorphemeTag.PLACE, "맥도날드" to MorphemeTag.PLACE,
        "돈" to MorphemeTag.NOUN, "경찰" to MorphemeTag.NOUN, "보건소" to MorphemeTag.PLACE,
        "보다" to MorphemeTag.VERB, "곳" to MorphemeTag.NOUN, "다음" to MorphemeTag.NOUN, "내리다" to MorphemeTag.VERB,
        "여기" to MorphemeTag.NOUN, "맞다" to MorphemeTag.ADJ, "오른쪽" to MorphemeTag.DIRECTION,
        "오늘" to MorphemeTag.TIME, "가깝다" to MorphemeTag.ADJ
    )

    val priors: Map<String, Float> = buildMap {
        listOf("송파","서울대학교","서울농아인협회","공항","맥도날드","보건소","물품보관").forEach { put(it, 1.20f) }
        listOf("버스","기차","길","샛길","에어컨","경찰","돈").forEach { put(it, 1.12f) }
        listOf("보다","곳","다음","내리다").forEach { put(it, 1.10f) }
        put("건너다", 1.0f)
    }

    /** 단일 라벨 기본 문장 */
    val sentences: Map<String, String> = mapOf(
        "송파" to "여기는 송파입니다.",
        "서울대학교" to "서울대학교입니다.",
        "서울농아인협회" to "서울농아인협회입니다.",
        "공항" to "공항입니다.",
        "맥도날드" to "맥도날드입니다.",
        "보건소" to "보건소입니다.",
        "물품보관" to "물품 보관소입니다.",
        "버스" to "버스입니다.",
        "기차" to "기차입니다.",
        "길" to "여기가 길입니다.",
        "샛길" to "샛길로 가요.",
        "에어컨" to "에어컨입니다.",
        "경찰" to "경찰입니다.",
        "돈" to "돈입니다.",
        "건너다" to "건너가요.",
        "보다" to "보여드릴게요.",
        "곳" to "그곳입니다.",
        "다음" to "다음입니다.",
        "내리다" to "여기서 내립니다.",
        "가깝다" to "가깝습니다."
    )

    /** 라벨 → 자연스러운 한 문장 */
    fun sentenceFor(label: String): String {
        sentences[label]?.let { return it }
        return if (label.endsWith("다")) label.removeSuffix("다") + "요." else "$label 입니다."
    }
}
