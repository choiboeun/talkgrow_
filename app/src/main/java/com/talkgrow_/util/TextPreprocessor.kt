package com.talkgrow_.util

/**
 * 한국어 입력을 간단 정규화/토큰화/조사 제거로 문장 단위 토큰 리스트로 변환.
 * - 본 프로젝트의 "좌표→134차원" 전처리와는 별도(선택)
 */
object TextPreprocessor {

    // 최대 문장 길이 (단어 개수)
    private const val MAX_SENTENCE_LENGTH = 20

    // 조사/접미어 리스트
    private val particles = listOf(
        "은","는","이","가","을","를",
        "에","에서","와","과","도","만",
        "으로","까지","부터","께서","께","한테"
    )

    // 감탄사/불필요어
    private val stopwords = setOf(
        "음","어","응","아","아이구","아이쿠","헐","대박","ㅋㅋ","ㅎㅎ"
    )

    // 문장 끝 어미(간단 규칙)
    private val sentenceEndings = listOf(
        // 문어체
        "다","니다","습니다","이었다","였다","겠습니다","것입니다",
        // 구어체
        "해","했어","했지","했네","했구나","했군","했지요","했네요",
        "야","지","군","구나","네","라","냐","까","지요","죠","요",
        // 질문형
        "니","니?","냐","냐?","냐고","냐구","냐니까","냐면서","겠니",
        // 명령/청유형
        "자","세요","십시오","해라","하자","합시다","해봐","해요","보자",
        // 감탄형
        "구나","군요","네요","로구나","로군","네","다니","란 말이냐",
        // 반말/비격식
        "함","함요","하는 거야","하는 거지","하지","했잖아","했거든",
        // 기타
        "할게","할께","할 거야","할 겁니다","할래","할까요","할지도 몰라",
        "했더니","하더라","한다더라","했대","하던데","했더라고"
    )

    /** 입력 문자열 -> 문장별 토큰 리스트 */
    fun processInputText(input: String): List<List<String>> {
        val spacingCorrected = input.replace("\\s+".toRegex(), " ").trim()
        val sentences = splitBySentenceEnding(spacingCorrected)

        val processed = mutableListOf<List<String>>()
        for (sentence in sentences) {
            val tokens = sentence.trim().split(" ").filter { it.isNotEmpty() }
            val filtered = tokens
                .map { removeParticles(it) }
                .filter { it.isNotEmpty() && !stopwords.contains(it) }
                .take(MAX_SENTENCE_LENGTH)

            if (filtered.isNotEmpty()) processed.add(filtered)
        }
        return processed
    }

    // 문장 끝 어미로 분리
    private fun splitBySentenceEnding(text: String): List<String> {
        val sentences = mutableListOf<String>()
        val cur = StringBuilder()
        for (word in text.split(" ")) {
            if (word.isEmpty()) continue
            cur.append(word).append(' ')
            if (sentenceEndings.any { word.endsWith(it) }) {
                sentences.add(cur.toString().trim())
                cur.clear()
            }
        }
        if (cur.isNotEmpty()) sentences.add(cur.toString().trim())
        return sentences
    }

    // 조사 제거
    private fun removeParticles(token: String): String {
        for (p in particles) {
            if (token.length > p.length && token.endsWith(p)) {
                return token.dropLast(p.length)
            }
        }
        return token
    }
}
