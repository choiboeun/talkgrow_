package com.talkgrow_

import com.talkgrow_.util.FeatureBuilder134
import com.talkgrow_.util.FeatureBuilder134.FrameInput
import com.talkgrow_.util.KP
import java.util.ArrayDeque
import kotlin.math.min

class SegmentPipeline(
    private val interpreter: TFLiteSignInterpreter,
    private val mapper: MeaningMapper,
    private val onSentence: (String) -> Unit,
    private val onLabelBadge: (String?) -> Unit,
    private val onClearResult: () -> Unit,
    private val mirroredPreview: Boolean = true
) {
    private var demo = false
    private val clip = ArrayList<FloatArray>()
    private val last = ArrayDeque<String>()
    private var lastBadge: String? = null

    private var demoStep = 0
    // **[유지] 데모 시나리오: 안녕하세요 -> 반갑습니다 -> 감사합니다**
    private val demoScenario = listOf(
        Pair("안녕하세요", "안녕하세요"),
        Pair("반갑다", "반갑습니다"),
        Pair("감사합니다", "감사합니다")
    )

    private var hasResultDisplayed = false
    private var isDemoResultActive = false

    private var lastInferenceTime = 0L
    private var lastResultDisplayTime = 0L

    private var isSegmentReady = false
    private var segmentReadyTime = 0L
    private var currentDemoIndex = -1

    fun setDemo(b: Boolean) { demo = b; resetAll() }
    fun resetAll() {
        clip.clear()
        last.clear()
        lastBadge = null
        demoStep = 0
        hasResultDisplayed = false
        isDemoResultActive = false
        onLabelBadge(null)
        onClearResult()

        lastInferenceTime = 0L
        lastResultDisplayTime = 0L
        isSegmentReady = false
        segmentReadyTime = 0L
        currentDemoIndex = -1
    }

    private companion object {
        const val T = 50
        const val F = FeatureBuilder134.F
        const val DEMO_CLIP_LENGTH = 30
        const val MIN_TOKENS_FOR_ASSEMBLE = 1

        // **[수정] 추론 최소 간격 단축: 1500L -> 1000L**
        const val MIN_INFERENCE_INTERVAL_MS = 1000L
        const val MIN_RESULT_DURATION_MS = 2500L
        // **[수정] 동작 멈춤 후 지연 시간 증가: 3000L -> 4000L**
        const val POST_SIGN_DELAY_MS = 4000L
    }

    fun onFrame(
        pose33: List<KP>,
        left21: List<KP>,
        right21: List<KP>,
        handsVisible: Boolean,
        ts: Long
    ) {
        val currentTime = System.currentTimeMillis()

        // 1. [결과 사라짐 로직]
        if (hasResultDisplayed) {
            val shouldClearOnMovement = handsVisible
            val isDurationOver = currentTime - lastResultDisplayTime > MIN_RESULT_DURATION_MS

            if (shouldClearOnMovement && isDurationOver) {
                onClearResult()
                onLabelBadge(null)
                hasResultDisplayed = false
                isDemoResultActive = false
            } else if (isDemoResultActive) {
                return
            }
        }

        // 2. [세그먼트 준비 완료 후 지연 및 표시 로직 (데모 모드)]
        if (demo && isSegmentReady && currentDemoIndex != -1) {
            val isDelayOver = currentTime - segmentReadyTime >= POST_SIGN_DELAY_MS

            // **[수정] 손 보임 여부와 상관없이 시간만 체크하여 결과 표시**
            if (isDelayOver) {
                val (label, sentence) = demoScenario[currentDemoIndex]

                onLabelBadge(null)
                onSentence(sentence ?: label)

                hasResultDisplayed = true
                isDemoResultActive = true
                lastResultDisplayTime = currentTime
                lastInferenceTime = currentTime

                isSegmentReady = false
                currentDemoIndex = -1

                return
            }
        }

        // 3. [Hand Disappearance Logic] (손이 안 보일 때)
        if (!handsVisible) {
            if (isSegmentReady) {
                // SegmentReady 상태에서는 클립을 지우고, 2번 로직이 4초를 기다리도록 둡니다.
                clip.clear()
                return
            } else {
                clip.clear()
                onLabelBadge(null)
                isDemoResultActive = false
                isSegmentReady = false
                currentDemoIndex = -1
            }
            return
        }

        // 4. [프레임 추가 방지]
        if (isDemoResultActive || isSegmentReady) {
            return
        }


        // 5. [특징점 추출 및 클립 추가]
        val feature = FeatureBuilder134.build(
            FrameInput(pose33, left21, right21, mirroredPreview)
        )
        if (feature.isEmpty()) return
        clip.add(feature)


        // 6. [추론 실행 조건 확인]
        val isReadyForInference = currentTime - lastInferenceTime > MIN_INFERENCE_INTERVAL_MS

        if (demo) {
            if (isReadyForInference) {
                handleDemoMode(currentTime)
            }
        } else {
            // 실제 추론 모드
            if (clip.size >= T && isReadyForInference) {
                finalizeSegment(currentTime)
            }
        }
    }

    // DEMO 모드 핸들러
    private fun handleDemoMode(currentTime: Long) {
        if (clip.size >= DEMO_CLIP_LENGTH && !isSegmentReady) {
            if (demoStep >= demoScenario.size) {
                clip.clear()
                onLabelBadge(null)
                return
            }

            // 결과 출력 대신 준비 상태로 전환 (동작 멈춤 감지)
            isSegmentReady = true
            segmentReadyTime = currentTime
            currentDemoIndex = demoStep

            demoStep++
            clip.clear()
        }
    }


    private fun finalizeSegment(currentTime: Long) {
        // 실제 모델 추론 로직 (데모 모드에서는 실행되지 않음)
        lastInferenceTime = currentTime

        val out = Array(T) { FloatArray(F) { 0f } }
        val n = min(T, clip.size)
        for (i in 0 until n) out[i] = clip[clip.size - n + i]

        clip.clear()

        val probs = interpreter.predictProbs(out)
        if (probs.isEmpty()) {
            onLabelBadge(null)
            return
        }

        val top1 = probs.entries.sortedByDescending { it.value }.getOrNull(0)
        var picked = top1?.key ?: "알수없음"

        if (picked != "안녕하세요" && picked != "감사합니다" && picked != "반갑다") {
            onLabelBadge(null)
            return
        }

        onLabelBadge(null)

        last.addLast(picked)
        while (last.size > 3) last.removeFirst()

        if (last.size >= MIN_TOKENS_FOR_ASSEMBLE) {
            mapper.tryAssemble(last)?.let { sentence ->
                onSentence(sentence)
                hasResultDisplayed = true
                lastResultDisplayTime = currentTime
                last.clear()
            }
        }
    }
}