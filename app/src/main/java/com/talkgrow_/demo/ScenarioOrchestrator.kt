package com.talkgrow_.demo

import android.content.Context

/**
 * step()을 호출하면 현재 블록(문장)을 한 번에 완료하고 onSentence를 호출합니다.
 * 즉, 손을 한 번 내리면 문장 하나가 출력됩니다.
 */
class ScenarioOrchestrator(
    private val context: Context,
    private val runner: ReplayRunner,
    private val onPartialWord: (who: String, word: String) -> Unit,
    private val onSentence: (who: String, sentence: String) -> Unit
) {
    // assets/replay/ 하위
    private val HELLO    = "replay/안녕하세요.npy"
    private val JAMSIL   = "replay/잠실.npy"
    private val GO       = "replay/가다.npy"
    private val HOW      = "replay/어떻게.npy"
    private val GANGNAM  = "replay/강남.npy"
    private val NEAR     = "replay/가깝다.npy"
    private val TRAIN    = "replay/기차.npy"
    private val COME     = "replay/오다.npy"
    private val THANKS   = "replay/감사.npy"
    private val HAPPY    = "replay/행복.npy"

    private val blocks: List<Block> = listOf(
        Block("사용자", listOf(HELLO)) { _ ->
            "안녕하세요."
        },
        Block("사용자", listOf(JAMSIL, GO, HOW, GO /* ‘가요’ 대용 */)) { _ ->
            "잠실 가려면 어떻게 가요?"
        },
        Block("안내자", listOf(GANGNAM, JAMSIL, NEAR)) { _ ->
            "강남에서 잠실은 가까워요."
        },
        Block("안내자", listOf(TRAIN, COME)) { _ ->
            "지하철로 오세요."
        },
        Block("사용자", listOf(THANKS, HAPPY)) { _ ->
            "감사합니다. 행복하세요."
        }
    )

    private var blockIdx = 0

    fun reset() {
        blockIdx = 0
        capturedWords.clear()
    }

    /** 손을 한 번 내릴 때 호출 → 현재 블록(문장) 전체를 즉시 재생/완료 */
    fun step() {
        if (blockIdx >= blocks.size) { reset(); return }
        val b = blocks[blockIdx]

        // 블록에 들어있는 모든 단어 npy를 차례대로 재생
        capturedWords.clear()
        for (asset in b.assets) {
            val (_, _, text) = runner.runReplayFromAsset(asset, topK = 5)
            if (!text.isNullOrBlank()) {
                capturedWords += text
                onPartialWord(b.who, text)
            }
        }

        // 문장 합성 및 알림
        val sentence = b.compose(capturedWords.toList())
        onSentence(b.who, sentence)

        // 다음 블록으로 이동
        capturedWords.clear()
        blockIdx += 1
        if (blockIdx >= blocks.size) {
            // 끝나면 다음 step()에서 처음부터 시작하도록 리셋
            reset()
        }
    }

    private val capturedWords = mutableListOf<String>()

    private data class Block(
        val who: String,
        val assets: List<String>,
        val compose: (words: List<String>) -> String
    )
}
