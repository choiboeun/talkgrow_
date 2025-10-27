// RealTimeRunner.kt
package com.talkgrow_.inference

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import com.talkgrow_.util.KP
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 제스처 실시간 러너
 * - 포즈/양손 랜드마크를 91x134 피처 시퀀스로 변환
 * - "의미 없는" 구간은 세그먼트로 인정하지 않음
 * - 추론 입력 정규화는 항상 딥카피 상에서 수행(원본 오염 방지)
 * - 세그먼트 시작/종료 시 에너지/히스토리 리셋
 */
class RealTimeRunner(
    context: Context,
    modelAssetName: String,
    private val mean: FloatArray? = null,
    private val std: FloatArray? = null,
    /** ACTIVE 구간 중에도 91 프레임 이상이면 지속 추론 수행 */
    private val inferWhileActive: Boolean = false,
    private val onGestureStart: (Long) -> Unit = {},
    private val onGestureEnd:   (Long) -> Unit = {}
) : AutoCloseable {

    enum class RunnerState { IDLE, ACTIVE }

    data class Landmarks(
        val pose: List<KP>?,
        val leftHand: List<KP>?,
        val rightHand: List<KP>?,
        val mirroredPreview: Boolean,
        val timestampMs: Long
    )

    data class Feedback(
        val state: RunnerState,
        val hasPose: Boolean,
        val hasAnyHand: Boolean,
        val windowCount: Int,
        val requiredWindow: Int = TARGET_LEN,
        val probs: FloatArray?
    )

    // ----------------- 게이트 파라미터 -----------------
    private val minHandsToStart = 1           // 시작하려면 최소 손 n개
    private val minConsecutiveToStart = 5     // n프레임 연속 존재해야 시작
    private val minDurationFrames = 12        // 너무 짧으면 무의미
    private val minEnergyToStart = 0.020f     // 시작 에너지 임계
    private val minEnergyToKeep  = 0.010f     // 유지 에너지 임계(약간 낮게)
    private val minNonZeroCoverage = 24       // 유효 프레임 최소

    // ----------------- 모델/피처 파라미터 -----------------
    companion object {
        const val TARGET_LEN = 91
        const val FEAT_DIM   = 134    // (pose25 + left21 + right21) * 2(xy)
        const val LABELS     = 284
    }

    private val interpreter: Interpreter = Interpreter(
        FileUtil.loadMappedFile(context, modelAssetName),
        Interpreter.Options().apply {
            setNumThreads(4)
            runCatching { setUseNNAPI(true) }
        }
    )

    private val ioLock = ReentrantLock()
    @Volatile private var closed = false

    // 슬라이딩 윈도우: 각 원소는 길이 134의 피처 벡터
    private val window = ArrayList<FloatArray>(TARGET_LEN)

    // 상태/타임스탬프/게이트
    private var lastSeenMs: Long = 0L
    private var state: RunnerState = RunnerState.IDLE
    private val idleGapMs = 350L

    private var consecutivePresent = 0
    private var lastFeat: FloatArray? = null
    private var emaEnergy = 0f

    // 0-행(패딩 공유)
    private val ZERO = FloatArray(FEAT_DIM) { 0f }

    // ----------------------------------------------------

    fun pushFrame(lm: Landmarks): Feedback {
        val handsCount =
            (if ((lm.leftHand?.size ?: 0) >= 5) 1 else 0) +
                    (if ((lm.rightHand?.size ?: 0) >= 5) 1 else 0)

        val hasPose = !lm.pose.isNullOrEmpty()
        val hasAnyHand = handsCount >= 1
        val anyPresent = hasPose || hasAnyHand
        val ts = lm.timestampMs

        var outProbs: FloatArray? = null

        if (anyPresent) {
            // 1) 피처 생성 + 모션 에너지 업데이트(EMA)
            val feat = buildFeature134(lm.pose, lm.leftHand, lm.rightHand, lm.mirroredPreview)
            val energy = motionEnergy(lastFeat, feat)
            emaEnergy = if (emaEnergy == 0f) energy else (emaEnergy * 0.85f + energy * 0.15f)

            // 2) 시작 게이트 (IDLE -> ACTIVE)
            if (state == RunnerState.IDLE) {
                consecutivePresent =
                    if (handsCount >= minHandsToStart) consecutivePresent + 1 else 0
                if (consecutivePresent >= minConsecutiveToStart && emaEnergy >= minEnergyToStart) {
                    state = RunnerState.ACTIVE
                    onGestureStart(ts)
                    window.clear()
                    // 새 세그먼트 기준으로 히스토리 리셋
                    emaEnergy = 0f
                    lastFeat = null
                }
            }

            // 3) ACTIVE 중에는 창 적재 및 (옵션)즉시 추론
            if (state == RunnerState.ACTIVE) {
                window.add(feat)
                if (window.size > TARGET_LEN) {
                    repeat(window.size - TARGET_LEN) { window.removeAt(0) }
                }
                lastSeenMs = ts
                if (inferWhileActive && window.size >= TARGET_LEN) {
                    outProbs = safeRunTflite(lastFixedWindow(window, TARGET_LEN, FEAT_DIM))
                }
            }

            lastFeat = feat
        } else {
            // 아무것도 안 보임 → 일정 시간 지나면 세그먼트 종료/판정
            consecutivePresent = 0
            if (state == RunnerState.ACTIVE && (ts - lastSeenMs) >= idleGapMs) {
                outProbs = endAndInferIfMeaningful(ts)
            }
        }

        return Feedback(
            state = state,
            hasPose = hasPose,
            hasAnyHand = hasAnyHand,
            windowCount = window.size.coerceAtMost(TARGET_LEN),
            probs = outProbs
        )
    }

    /**
     * 세그먼트 종료시 의미 여부를 판단하고, 의미 있으면 zero-pad 후 추론 결과 반환.
     * 의미 없으면 null.
     */
    private fun endAndInferIfMeaningful(ts: Long): FloatArray? {
        if (window.isEmpty()) {
            state = RunnerState.IDLE
            onGestureEnd(ts)
            // 종료 시 히스토리 리셋
            emaEnergy = 0f
            lastFeat = null
            return null
        }

        // 품질 기준: 길이 / 커버리지 / 평균 에너지
        val longEnough = window.size >= minDurationFrames
        val enoughCoverage =
            window.size >= minNonZeroCoverage || window.size >= TARGET_LEN / 2
        val energetic = emaEnergy >= minEnergyToKeep

        val meaningful = longEnough && enoughCoverage && energetic

        val fixed = if (window.size >= TARGET_LEN)
            lastFixedWindow(window, TARGET_LEN, FEAT_DIM)
        else
            toFixedWindow(window, TARGET_LEN, FEAT_DIM)

        window.clear()
        state = RunnerState.IDLE
        onGestureEnd(ts)

        // 종료 시 히스토리 리셋
        emaEnergy = 0f
        lastFeat = null

        return if (meaningful) safeRunTflite(fixed) else null
    }

    /** 외부에서 강제 종료/추론하고 싶을 때 사용 가능(패딩 포함) */
    fun endAndInferWithZeroPad(ts: Long): FloatArray? = endAndInferIfMeaningful(ts)

    /** ACTIVE 중 즉시 추론(창 길이에 맞춰 패딩/자르기) */
    fun forceInferNow(): FloatArray? {
        if (window.isEmpty()) return null
        val fixed = if (window.size >= TARGET_LEN)
            lastFixedWindow(window, TARGET_LEN, FEAT_DIM)
        else
            toFixedWindow(window, TARGET_LEN, FEAT_DIM)
        return safeRunTflite(fixed)
    }

    // ----------------- TFLite 호출부 -----------------

    /** 입력은 항상 딥카피해서 정규화(원본 오염 방지) */
    private fun safeRunTflite(input91x134: Array<FloatArray>): FloatArray? {
        if (closed) return null
        val copy = Array(input91x134.size) { i -> input91x134[i].clone() } // deep copy
        return ioLock.withLock { if (!closed) runTflite(copy) else null }
    }

    private fun runTflite(input91x134: Array<FloatArray>): FloatArray {
        // (옵션) 특성별 정규화
        val m = mean
        val s = std
        if (m != null && s != null && m.size == FEAT_DIM && s.size == FEAT_DIM) {
            for (i in 0 until TARGET_LEN) {
                val row = input91x134[i]
                for (j in 0 until FEAT_DIM) {
                    row[j] = (row[j] - m[j]) / max(s[j], 1e-6f)
                }
            }
        }

        // TFLite 입력/출력 모양에 맞춤
        val input = arrayOf(input91x134)         // [1, 91, 134]
        val out = Array(1) { FloatArray(LABELS) } // [1, 284]

        interpreter.run(input, out)
        return out[0]
    }

    override fun close() {
        ioLock.withLock {
            if (closed) return
            closed = true
            runCatching { interpreter.close() }
        }
    }

    // ----------------- Feature / Energy -----------------

    /** 학습 피처: XY만 134차원(25+21+21)*2 */
    private fun buildFeature134(
        pose: List<KP>?, left: List<KP>?, right: List<KP>?, mirrored: Boolean
    ): FloatArray {
        val out = FloatArray(FEAT_DIM) { 0f }
        var c = 0
        fun write(list: List<KP>?) {
            if (list.isNullOrEmpty()) return
            for (kp in list) {
                if (c + 1 >= FEAT_DIM) break
                val xRaw = kp.x
                val x = if (mirrored) 1f - xRaw else xRaw
                out[c++] = x
                out[c++] = kp.y
            }
        }
        write(pose); write(left); write(right)
        return out
    }

    /** 두 연속 피처 사이 평균 L1 이동량(모션 에너지, 0~1 근사) */
    private fun motionEnergy(prev: FloatArray?, cur: FloatArray): Float {
        if (prev == null) return 0f
        var acc = 0f
        var cnt = 0
        val n = min(prev.size, cur.size)
        var i = 0
        while (i + 1 < n) {
            val dx = abs(cur[i]   - prev[i])
            val dy = abs(cur[i+1] - prev[i+1])
            acc += dx + dy
            cnt += 2
            i += 2
        }
        if (cnt == 0) return 0f
        return (acc / cnt).coerceIn(0f, 1f)
    }

    // 고정 길이 91 프레임으로 패딩
    private fun toFixedWindow(src: List<FloatArray>, targetLen: Int, dim: Int): Array<FloatArray> {
        val out = Array(targetLen) { ZERO }
        val copy = min(src.size, targetLen)
        for (i in 0 until copy) out[i] = src[i]
        return out
    }

    // 최근 91 프레임만 유지
    private fun lastFixedWindow(src: List<FloatArray>, targetLen: Int, dim: Int): Array<FloatArray> {
        val out = Array(targetLen) { ZERO }
        val start = (src.size - targetLen).coerceAtLeast(0)
        var idx = 0
        for (i in start until src.size) out[idx++] = src[i]
        return out
    }
}
