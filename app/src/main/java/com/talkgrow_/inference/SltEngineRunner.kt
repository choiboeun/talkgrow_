// com/talkgrow_/inference/SltEngineRunner.kt
package com.talkgrow_.inference

import android.content.Context
import android.util.Log
import com.talkgrow_.nlp.Tuned

/**
 * - 프레임→피처(134)→(표준화)→엔진 push는 RealTimeRunner가 담당
 * - 여기서는 확률 후처리(안정 기준)만 맡긴다.
 * - MediaPipe 정규화는 SltEngineGeneric.push()에서 자동 적용(옵션)
 */
class SltEngineRunner(
    ctx: Context,
    private val stableThreshold: Float = 0.75f,
    private val topK: Int = 3,
    labelsAsset: String = "labels_ko_284.json",
    modelAsset: String = "model_v8_focus16_284_fp16.tflite",
    private val mean: FloatArray? = null,
    private val std: FloatArray? = null,
    applyMpNormalization: Boolean = true
) {
    companion object { private const val TAG = "SltRunner" }

    private val engine = SltEngineGeneric.create(
        ctx,
        modelAsset = modelAsset,
        labelsAsset = labelsAsset,
        applyMpNormalization = applyMpNormalization
    )
    private var printedFirstFrameCheck = false

    private var stableCount = 0
    private var lastTopLabel: String? = null
    private val minStableFrames = 4

    fun postProcess(probs: FloatArray?): Pair<String?, List<Pair<String, Float>>> {
        if (probs == null) {
            stableCount = 0; lastTopLabel = null
            return null to emptyList()
        }

        val pairs = probs.indices
            .map { i -> engine.label(i) to probs[i] }
            .sortedByDescending { it.second }
            .take(topK)

        val (curLabel, curProb) = pairs.first()

        if (!printedFirstFrameCheck) {
            printedFirstFrameCheck = true
            Log.i(TAG, "✅ engine T=${engine.t()} F=${engine.f()} labels=${engine.numLabels()}")
        }

        if (curProb >= stableThreshold) {
            if (curLabel == lastTopLabel) stableCount++ else stableCount = 1
            lastTopLabel = curLabel
        } else {
            stableCount = 0; lastTopLabel = null
        }

        val sentence = if (stableCount >= minStableFrames) Tuned.sentenceFor(curLabel) else null
        return sentence to pairs
    }

    fun close() { engine.close() }
}
