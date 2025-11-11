// android_app/src/main/java/com/talkgrow_/SegmentPipeline.kt
package com.talkgrow_

import android.util.Log
import com.talkgrow_.inference.TFLiteSignInterpreter
import com.talkgrow_.util.FeatureBuilder134
import com.talkgrow_.util.FrameInput
import com.talkgrow_.util.KP
import org.json.JSONObject
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max

class SegmentPipeline(
    private val interpreter: TFLiteSignInterpreter,
    private val mapper: MeaningMapper,
    private val onSentence: (String) -> Unit,
    private val onLabelBadge: (String?) -> Unit,
    private val onClearResult: () -> Unit,
    private val defaultMirroredPreview: Boolean = false,
    private val showTopLeftBadge: Boolean = false,
    private val minTop1Prob: Float = 0.10f,
    private val warmupFrames: Int = 8,
    private val frameSubsample: Int = 1,
    private val onRawFrameDump: ((Long, FloatArray) -> Unit)? = null,
    private val onPreNormSequenceDump: ((Long, Array<FloatArray>) -> Unit)? = null,
    private val onNormalizedSequenceDump: ((Long, Array<FloatArray>) -> Unit)? = null,
    private val onPredictionLogged: ((Long, String, Float, Long) -> Unit)? = null,
    private val onNormalizerReady: ((FloatArray, FloatArray, JSONObject?) -> Unit)? = null
) {
    companion object {
        private const val TAG = "SegmentPipeline"
        private const val LOG_VERBOSE = true
        private const val DEMO_CLIP_LENGTH = 30
        private const val MIN_RESULT_DURATION_MS = 2_500L
        private const val POST_SIGN_DELAY_MS = 4_000L
        private const val MAX_CLIP_KEEP = 400
        private const val IDLE_BUFFER_SIZE = 24
        private const val HAND_GAP_GRACE_MS = 3_200L
        private const val MOTION_STILL_HOLD_MS = 1_000L
        private const val MOTION_START_THRESHOLD = 0.22f
        private const val MOTION_STOP_THRESHOLD  = 0.12f
        private const val MIN_FRAMES_FOR_SEGMENT = 24
        private const val IDEAL_FRAMES_FOR_SEGMENT = 91
        private const val FORCE_FINALIZE_FRAMES = 140
        private const val FORCE_FINALIZE_DURATION_MS = 9_000L
        private val ALLOWED: Set<String>? = null
    }

    private var demo = false

    init {
        Log.i(TAG, "SegmentPipeline init -> minFrames=$MIN_FRAMES_FOR_SEGMENT warmup=$warmupFrames frameSubsample=$frameSubsample")
        tryNotifyNormalizer()
    }

    private val segmentFrames = ArrayList<FloatArray>()
    private val idleBuffer = ArrayDeque<FloatArray>()
    private val lastTokens = ArrayDeque<String>()

    private var hasResultDisplayed = false
    private var isDemoResultActive = false
    private var lastResultDisplayTime = 0L

    private var prevRawFrame: FloatArray? = null
    private var capturing = false
    private var lastMotionTime = 0L
    private var segmentStartTimeMs = 0L
    private var lastHandsSeenMs = 0L
    private var lastHandsOk = false
    private var framesSinceStart = 0
    private var frameStrideCursor = 0
    private var segmentOrdinal = 0L

    private var isSegmentReady = false
    private var segmentReadyTime = 0L
    private var currentDemoIndex = -1
    private var demoStep = 0
    private val demoScenario: List<Pair<String, String>> = listOf(
        "안녕하세요" to "안녕하세요",
        "반갑다" to "반갑습니다",
        "감사합니다" to "감사합니다"
    )

    fun setDemo(b: Boolean) { demo = b; resetAll(); logVerbose("setDemo=$b") }

    fun resetAll() {
        segmentFrames.clear(); idleBuffer.clear(); lastTokens.clear()
        prevRawFrame = null; capturing = false
        hasResultDisplayed = false; isDemoResultActive = false
        if (showTopLeftBadge) onLabelBadge(null)
        onClearResult()
        lastResultDisplayTime = 0L
        lastMotionTime = 0L
        segmentStartTimeMs = 0L
        lastHandsSeenMs = 0L
        lastHandsOk = false
        framesSinceStart = 0
        frameStrideCursor = 0
        segmentOrdinal = 0
        isSegmentReady = false
        segmentReadyTime = 0L
        currentDemoIndex = -1
        demoStep = 0
        FeatureBuilder134.reset()
        tryNotifyNormalizer()
        logVerbose("resetAll()")
    }

    fun onFrame(
        pose33: List<KP>,
        left21: List<KP>,
        right21: List<KP>,
        handsVisible: Boolean,
        mirroredPreview: Boolean?,
        ts: Long
    ) {
        val mirror = false
        val now = System.currentTimeMillis()

        if (frameSubsample > 1) {
            if (frameStrideCursor++ % frameSubsample != 0) {
                if (handsVisible) lastHandsSeenMs = now
                return
            }
        }

        framesSinceStart++
        logVerbose("onFrame ts=$ts hv=$handsVisible capturing=$capturing seg=${segmentFrames.size}")

        if (hasResultDisplayed) {
            val enoughShown = now - lastResultDisplayTime > MIN_RESULT_DURATION_MS
            if (enoughShown && handsVisible) {
                onClearResult()
                if (showTopLeftBadge) onLabelBadge(null)
                hasResultDisplayed = false
                isDemoResultActive = false
                logVerbose("clear shown result; ready for next")
            } else if (isDemoResultActive) return
        }

        val rawFrame = FeatureBuilder134.build(
            FrameInput(pose33 = pose33, left21 = left21, right21 = right21, mirroredPreview = mirror)
        )
        onRawFrameDump?.invoke(ts, rawFrame)
        val motionEnergy = computeMotionEnergy(prevRawFrame, rawFrame)
        prevRawFrame = rawFrame.copyOf()

        if (handsVisible) lastHandsSeenMs = now
        val handsOk = handsVisible || (now - lastHandsSeenMs) <= HAND_GAP_GRACE_MS

        if (!demo) handleLiveModeFrame(now, handsOk, motionEnergy, rawFrame)
        else       handleDemoMode(now, handsOk, motionEnergy, rawFrame)

        lastHandsOk = handsOk
    }

    private fun handleLiveModeFrame(now: Long, handsOk: Boolean, motionEnergy: Float, rawFrame: FloatArray) {
        if (!handsOk) {
            if (capturing && segmentFrames.isNotEmpty()) { logVerbose("hands lost -> finalize segment"); finalizeSegment(now) }
            capturing = false; segmentFrames.clear(); idleBuffer.clear(); segmentStartTimeMs = 0L; return
        }
        if (!capturing) {
            idleBuffer.addLast(rawFrame.copyOf()); while (idleBuffer.size > IDLE_BUFFER_SIZE) idleBuffer.removeFirst()
            if (motionEnergy >= MOTION_START_THRESHOLD) {
                capturing = true; segmentFrames.clear(); segmentFrames.addAll(idleBuffer); idleBuffer.clear()
                lastMotionTime = now; segmentStartTimeMs = now; logVerbose("segment started (motion=$motionEnergy)")
            } else return
        } else if (motionEnergy >= MOTION_STOP_THRESHOLD) lastMotionTime = now

        segmentFrames.add(rawFrame.copyOf()); while (segmentFrames.size > MAX_CLIP_KEEP) segmentFrames.removeAt(0)

        val segmentDurationMs = if (segmentStartTimeMs == 0L) 0L else now - segmentStartTimeMs
        val reachedFrameLimit = segmentFrames.size >= FORCE_FINALIZE_FRAMES
        val reachedDurationLimit = segmentDurationMs >= FORCE_FINALIZE_DURATION_MS
        if ((reachedFrameLimit || reachedDurationLimit) && segmentFrames.size >= MIN_FRAMES_FOR_SEGMENT) {
            logVerbose("segment forced finalize (frames=${segmentFrames.size}, duration=${segmentDurationMs}ms)")
            finalizeSegment(now); capturing = false; segmentFrames.clear(); segmentStartTimeMs = 0L; idleBuffer.clear(); return
        }
        if (motionEnergy <= MOTION_STOP_THRESHOLD && now - lastMotionTime >= MOTION_STILL_HOLD_MS) {
            logVerbose("segment ended (no motion) -> finalize")
            finalizeSegment(now); capturing = false; segmentFrames.clear(); segmentStartTimeMs = 0L
        }
    }

    private fun finalizeSegment(now: Long) {
        val frameCount = segmentFrames.size
        val requiredWarmup = max(warmupFrames, MIN_FRAMES_FOR_SEGMENT)
        if (frameCount < MIN_FRAMES_FOR_SEGMENT) { logVerbose("segment too short ($frameCount < $MIN_FRAMES_FOR_SEGMENT) -> drop"); segmentFrames.clear(); segmentStartTimeMs = 0L; return }
        if (framesSinceStart < requiredWarmup)   { logVerbose("warmup ($framesSinceStart < $requiredWarmup) -> drop"); segmentFrames.clear(); segmentStartTimeMs = 0L; return }

        val L = segmentFrames.size
        val starts = if (L <= IDEAL_FRAMES_FOR_SEGMENT) intArrayOf(0) else intArrayOf(0, (L - IDEAL_FRAMES_FOR_SEGMENT) / 2, L - IDEAL_FRAMES_FOR_SEGMENT)

        val probAgg: MutableMap<String, Float> = mutableMapOf()
        val segmentId = segmentOrdinal++

        for (s in starts) {
            // (덤프용) 윈도우만 앞에서 91으로 잘라 저장 — raw 기준
            val rawSlice = sliceFrames(segmentFrames, s, IDEAL_FRAMES_FOR_SEGMENT)
            val fixed91 = padOrHeadCropTo91(rawSlice)
            val preNormSeq = fixed91.map { it.copyOf() }.toTypedArray()
            onPreNormSequenceDump?.invoke(segmentId, preNormSeq)

            // (추론입력) 전 구간 정규화 후 s부터 91프레임 윈도우
            val normSeq = FeatureBuilder134.buildNormalizedWindow(segmentFrames, s)
            onNormalizedSequenceDump?.invoke(segmentId, normSeq)

            val probs = interpreter.predictProbsFixed(normSeq)
            for ((label, p) in probs) probAgg[label] = (probAgg[label] ?: 0f) + p
        }

        if (probAgg.isEmpty()) { if (showTopLeftBadge) onLabelBadge(null); logVerbose("empty probs"); return }

        val denom = starts.size.toFloat().coerceAtLeast(1f)
        var bestLabel: String? = null; var bestProb = -1f
        for ((label, sumP) in probAgg) {
            val avg = sumP / denom
            if (avg > bestProb) { bestProb = avg; bestLabel = label }
        }
        val label = bestLabel ?: run { if (showTopLeftBadge) onLabelBadge(null); logVerbose("no top1"); return }

        logVerbose("top1 label=$label prob=$bestProb len=$L")
        if (bestProb < minTop1Prob) { logVerbose("prob too low (<$minTop1Prob) -> discard"); if (showTopLeftBadge) onLabelBadge(null); return }
        onPredictionLogged?.invoke(segmentId, label, bestProb, now)
        if (ALLOWED != null && !ALLOWED.contains(label)) { if (showTopLeftBadge) onLabelBadge(null); logVerbose("label filtered"); return }

        if (showTopLeftBadge) onLabelBadge(label)
        val sentence = label
        onSentence(sentence)
        hasResultDisplayed = true
        lastResultDisplayTime = now
        lastTokens.clear()
        logVerbose("emit sentence=$sentence")
        segmentFrames.clear(); segmentStartTimeMs = 0L
    }

    private fun sliceFrames(src: List<FloatArray>, start: Int, len: Int): List<FloatArray> {
        if (src.isEmpty() || len <= 0) return emptyList()
        val s = start.coerceIn(0, max(0, src.size - 1))
        val e = (s + len).coerceAtMost(src.size)
        return if (s < e) src.subList(s, e) else src.takeLast(len)
    }

    // 학습 규칙: 앞에서 91프레임, 부족분은 뒤 제로패드 (덤프용)
    private fun padOrHeadCropTo91(src: List<FloatArray>): List<FloatArray> {
        val need = IDEAL_FRAMES_FOR_SEGMENT
        if (src.isEmpty()) return List(need) { FloatArray(134) { 0f } }
        return if (src.size >= need) src.subList(0, need)
        else {
            val pad = List(need - src.size) { FloatArray(134) { 0f } }
            src + pad
        }
    }

    private fun computeMotionEnergy(prev: FloatArray?, curr: FloatArray): Float {
        if (prev == null) return 0f
        var sum = 0f
        for (i in curr.indices) sum += abs(curr[i] - prev[i])
        return sum / curr.size
    }

    private fun handleDemoMode(now: Long, handsOk: Boolean, motionEnergy: Float, rawFrame: FloatArray) {
        if (!handsOk) {
            if (capturing && segmentFrames.isNotEmpty()) finalizeDemo(now)
            capturing = false; segmentFrames.clear(); idleBuffer.clear(); segmentStartTimeMs = 0L; return
        }
        if (!capturing) {
            if (motionEnergy >= MOTION_START_THRESHOLD) {
                capturing = true; segmentFrames.clear(); lastMotionTime = now; segmentStartTimeMs = now
            } else return
        } else if (motionEnergy >= MOTION_STOP_THRESHOLD) lastMotionTime = now

        segmentFrames.add(rawFrame.copyOf()); while (segmentFrames.size > MAX_CLIP_KEEP) segmentFrames.removeAt(0)

        if (!isSegmentReady && segmentFrames.size >= DEMO_CLIP_LENGTH) {
            if (demoStep >= demoScenario.size) { segmentFrames.clear(); return }
            isSegmentReady = true; segmentReadyTime = now; currentDemoIndex = demoStep; demoStep++
        }
        if (isSegmentReady && currentDemoIndex != -1 && now - segmentReadyTime >= POST_SIGN_DELAY_MS) finalizeDemo(now)

        if (motionEnergy <= MOTION_STOP_THRESHOLD && now - lastMotionTime >= MOTION_STILL_HOLD_MS) {
            finalizeDemo(now); capturing = false; segmentFrames.clear(); segmentStartTimeMs = 0L
        }
    }

    private fun finalizeDemo(now: Long) {
        val pair = demoScenario.getOrNull(currentDemoIndex)
        segmentFrames.clear(); capturing = false; segmentStartTimeMs = 0L
        if (pair == null) { isSegmentReady = false; currentDemoIndex = -1; return }
        val (label, sentence) = pair
        if (showTopLeftBadge) onLabelBadge(null)
        onSentence(if (sentence.isBlank()) label else sentence)
        hasResultDisplayed = true; isDemoResultActive = true; lastResultDisplayTime = now
        isSegmentReady = false; currentDemoIndex = -1
    }

    private fun tryNotifyNormalizer() {
        try {
            val means: FloatArray? = interpreter.getMeansOrNull()
            val stds: FloatArray? = interpreter.getStdsOrNull()
            val cfg: JSONObject? = interpreter.getNormConfigOrNull()
            if (means != null && stds != null) onNormalizerReady?.invoke(means, stds, cfg)
        } catch (_: Throwable) { /* optional */ }
    }

    private fun logVerbose(msg: String) { if (LOG_VERBOSE) Log.d(TAG, msg) }
}
