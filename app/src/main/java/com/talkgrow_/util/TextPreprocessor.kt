package com.talkgrow_.util

import java.text.Normalizer

/**
 * 한국어 입력 전처리(안드 ↔ Python infer_sentence.py 규칙 정합).
 * - 조사 제거, 불용어 제거, 문장 어미 기준 분리
 * - (선택) vocab과 함께 쓸 때: 사전에 없으면 얕은 어미 스트립 + 간단 원형화 1회 시도
 */
object TextPreprocessor {

    private const val MAX_SENTENCE_LENGTH = 20

    private val particles = listOf(
        "은","는","이","가","을","를",
        "에","에서","와","과","도","만",
        "으로","까지","부터","께서","께","한테"
    )

    private val stopwords = setOf("음","어","응","아","아이구","아이쿠","헐","대박","ㅋㅋ","ㅎㅎ")

    private val sentenceEndings = listOf(
        // 문어체
        "다","니다","습니다","이었다","였다","겠습니다","것입니다",
        // 구어체
        "해","했어","했지","했네","했구나","했군","했지요","했네요",
        "야","지","군","구나","네","라","냐","까","지요","죠","요",
        // 질문형
        "니","니?","냐?","냐","냐고","냐구","냐니까","냐면서","겠니",
        // 명령/청유형
        "자","세요","십시오","해라","하자","합시다","해봐","해요","보자",
        // 감탄형
        "로구나","로군","다니","란 말이냐","군요","네요",
        // 반말/비격식/기타
        "함","함요","하는 거야","하는 거지","하지","했잖아","했거든",
        "할게","할께","할 거야","할 겁니다","할래","할까요","할지도 몰라",
        "했더니","하더라","한다더라","했대","하던데","했더라고"
    )

    // 파이썬과 동일: “사전에 없을 때만” 1회 시도
    private val endingStrip = listOf(
        "네요","군요","입니다","니다","습니다","요",
        "였어","였네","했어","했네","해","자","라",
        "일까","일게","일걸","겠어","겠네","겠죠","죠"
    )

    /** 유니코드 정규화 + 공백 정리 */
    private fun normalizeInput(text: String): String {
        val nfc = Normalizer.normalize(text, Normalizer.Form.NFC)
        return nfc.replace("\\s+".toRegex(), " ").trim()
    }

    /** 토큰에서 앞뒤 문장부호/괄호 등 제거 */
    private fun cleanToken(tok: String): String {
        return tok.trim()
            .trimEnd('.', ',', '!', '?', '"', '\'', ')', ']', '}', '…', '·', '・', '，', '。', '！', '？')
            .trimStart('(', '[', '{', '“', '”', '‘', '’')
    }

    /** 입력 → 문장별 토큰 리스트 (사전 미사용) */
    fun processInputText(input: String): List<List<String>> {
        val spacingCorrected = normalizeInput(input)
        val sentences = splitBySentenceEnding(spacingCorrected)

        val processed = mutableListOf<List<String>>()
        for (sentence in sentences) {
            val tokens = sentence.trim()
                .split(" ")
                .map { cleanToken(it) }                 // ← 문장부호 제거
                .filter { it.isNotEmpty() }

            val filtered = tokens
                .map { removeParticles(it) }
                .filter { it.isNotEmpty() && !stopwords.contains(it) }
                .take(MAX_SENTENCE_LENGTH)

            if (filtered.isNotEmpty()) processed.add(filtered)
        }
        return processed
    }

    /**
     * 입력 → 문장별 토큰 리스트 (사전 보정 포함)
     * - 그대로 조회 → 실패 시 endingStrip 1회 → 실패 시 간단 원형화 → 그래도 실패 시 <unk> 또는 스킵
     */
    fun processInputTextWithVocab(
        input: String,
        vocab: Set<String>,
        useUnk: Boolean = true,
        unkToken: String = "<unk>"
    ): List<List<String>> {
        val spacingCorrected = normalizeInput(input)
        val sentences = splitBySentenceEnding(spacingCorrected)

        val out = mutableListOf<List<String>>()
        for (sentence in sentences) {
            val tokens = sentence.trim()
                .split(" ")
                .map { cleanToken(it) }
                .filter { it.isNotEmpty() }

            val mapped = mutableListOf<String>()
            for (raw in tokens) {
                // ✅ 우선 1차 필터링: 불용어
                if (stopwords.contains(raw)) continue

                // ✅ 1) 원형 그대로 사전에 있으면 바로 사용
                if (vocab.contains(raw)) {
                    mapped.add(raw)
                    continue
                }

                // ✅ 2) 조사 제거 후 다시 확인
                val noParticle = removeParticles(raw)
                if (vocab.contains(noParticle)) {
                    mapped.add(noParticle)
                    continue
                }

                // ✅ 3) 어미 스트립 1회
                val base1 = stripEndingOnce(noParticle)
                if (base1 != null && vocab.contains(base1)) {
                    mapped.add(base1)
                    continue
                }

                // ✅ 4) 간단 원형화
                val lemma = lemmatizeToBase(noParticle)
                if (lemma != null && vocab.contains(lemma)) {
                    mapped.add(lemma)
                    continue
                }

                // ✅ 5) 최종 실패 시
                if (useUnk) mapped.add(unkToken)
            }

            if (mapped.isNotEmpty()) out.add(mapped.take(MAX_SENTENCE_LENGTH))
        }
        return out
    }


    // 문장 끝 어미로 분리
    private fun splitBySentenceEnding(text: String): List<String> {
        val sentences = mutableListOf<String>()
        val cur = StringBuilder()
        for (word in text.split(" ")) {
            if (word.isEmpty()) continue
            cur.append(word).append(' ')
            if (sentenceEndings.any { word.endsWith(it) }) {
                sentences.add(cur.toString().trim()); cur.clear()
            }
        }
        if (cur.isNotEmpty()) sentences.add(cur.toString().trim())
        return sentences
    }

    // 조사 제거(뒤에서 1회)
    private fun removeParticles(token: String): String {
        for (p in particles) {
            if (token.length > p.length && token.endsWith(p)) {
                return token.dropLast(p.length)
            }
        }
        return token
    }

    // 어미 스트립 1회(화이트리스트)
    private fun stripEndingOnce(token: String): String? {
        for (end in endingStrip) {
            if (token.length > end.length && token.endsWith(end)) {
                return token.dropLast(end.length)
            }
        }
        return null
    }

    // 사전에 없을 때만 시도하는 간단 원형화(보수적, 1회 적용)
    private fun lemmatizeToBase(tok: String): String? {
        var t = tok

        // 1) 공손/평서 어미 제거
        val polite = listOf("입니다","니다","습니다","네요","군요","요")
        for (e in polite) if (t.length > e.length && t.endsWith(e)) {
            t = t.dropLast(e.length); break
        }

        // 2) 연결어미 '고' → '다' (따뜻하고 → 따뜻하다)
        if (t.length > 1 && t.endsWith("고")) {
            return t.dropLast(1) + "다"
        }

        // 3) 어/아/여 → 다 (맛있어 → 맛있다, 따뜻해 → 따뜻하다)
        val aeo = listOf("어","아","여")
        for (e in aeo) if (t.length > e.length && t.endsWith(e)) {
            return t.dropLast(e.length) + "다"
        }

        // 과거형 단순화: 었어/았어/였어 → 다
        val past = listOf("었어","았어","였어")
        for (e in past) if (t.length > e.length && t.endsWith(e)) {
            return t.dropLast(e.length) + "다"
        }

        // 4) 하다 활용형 (해요/해/했어/했다 → 하다)
        val haForms = listOf("해요","해","했어","했다")
        for (e in haForms) if (t.length > e.length && t.endsWith(e)) {
            return t.dropLast(e.length) + "하다"
        }

        return null
    }
}
