package com.talkgrow_.util

object TextPreprocessor {

    // 최대 문장 길이 (단어 개수)
    private const val MAX_SENTENCE_LENGTH = 20

    // 조사/접미어 리스트 (필요한 만큼 추가 가능)
    private val particles = listOf(
        "은", "는", "이", "가", "을", "를",
        "에", "에서", "와", "과", "도", "만",
        "으로", "까지", "부터", "께서", "께", "한테"
    )

    // 감탄사, 불필요한 말 리스트
    private val stopwords = listOf(
        "음", "어", "응", "아", "아이구",
        "아이쿠", "헐", "대박", "ㅋㅋ", "ㅎㅎ"
    )


    // 문장 끝 어미로 문장 나누기
    private val sentenceEndings = listOf(
        // 문어체 (격식체)
        "다", "니다", "습니다", "이었다", "였다", "겠습니다", "것입니다",

        // 구어체 (일반체)
        "해", "했어", "했지", "했네", "했구나", "했군", "했지요", "했네요",
        "야", "지", "군", "구나", "네", "라", "냐", "까", "지요", "죠", "요",

        // 질문형
        "니", "니?", "냐", "냐?", "냐고", "냐구", "냐니까", "냐면서", "겠니",

        // 명령/청유형
        "자", "세요", "십시오", "해라", "하자", "합시다", "해봐", "해요", "보자",

        // 감탄형
        "구나", "군요", "네요", "로구나", "로군", "네", "다니", "란 말이냐",

        // 반말/비격식
        "함", "함요", "하는 거야", "하는 거지", "하지", "했잖아", "했거든",

        // 기타 흔히 쓰이는 종결 표현들
        "할게", "할께", "할 거야", "할 겁니다", "할래", "할까요", "할지도 몰라",
        "했더니", "하더라", "한다더라", "했대", "하던데", "했더라고"
    )

    fun processInputText(input: String): List<List<String>> {
        // 1. 띄어쓰기 보정: 여러 공백을 하나로, 붙어있는 단어 강제로 분리 못하지만 기본 공백 정리
        val spacingCorrected = input.replace("\\s+".toRegex(), " ").trim()

        // 2. 숫자 정규화 (간단 치환)
        var normalizedText = spacingCorrected

        // 3. 문장 나누기 - 어미 기준으로 분리
        val sentences = splitBySentenceEnding(normalizedText)

        val processedSentences = mutableListOf<List<String>>()

        for (sentence in sentences) {
            // 4. 토큰화
            val tokens = sentence.trim().split(" ").filter { it.isNotEmpty() }

            // 5. 조사 제거, 감탄사 제거
            val filteredTokens = tokens.map { token ->
                removeParticles(token)
            }.filter { token ->
                token.isNotEmpty() && !stopwords.contains(token)
            }

            // 6. 최대 문장 길이 제한
            val limitedTokens = if (filteredTokens.size > MAX_SENTENCE_LENGTH) {
                filteredTokens.take(MAX_SENTENCE_LENGTH)
            } else {
                filteredTokens
            }

            if (limitedTokens.isNotEmpty()) {
                processedSentences.add(limitedTokens)
            }
        }

        return processedSentences
    }

    // 문장 나누기 함수 - 문장 끝 어미가 나오면 문장 나누기
    private fun splitBySentenceEnding(text: String): List<String> {
        val sentences = mutableListOf<String>()
        var currentSentence = StringBuilder()

        val words = text.split(" ")

        for (word in words) {
            currentSentence.append(word).append(" ")

            // 어미 체크
            if (sentenceEndings.any { ending -> word.endsWith(ending) }) {
                sentences.add(currentSentence.toString().trim())
                currentSentence = StringBuilder()
            }
        }

        // 남은 문장
        if (currentSentence.isNotEmpty()) {
            sentences.add(currentSentence.toString().trim())
        }

        return sentences
    }

    // 조사 제거 함수 - 뒤에 조사 붙으면 제거 (예: "나는" -> "나")
    private fun removeParticles(token: String): String {
        for (particle in particles) {
            if (token.endsWith(particle) && token.length > particle.length) {
                return token.dropLast(particle.length)
            }
        }
        return token
    }
}