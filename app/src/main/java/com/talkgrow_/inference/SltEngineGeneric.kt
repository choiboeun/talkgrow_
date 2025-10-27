// com/talkgrow_/inference/SltEngineGeneric.kt
package com.talkgrow_.inference

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import java.util.ArrayDeque

class SltEngineGeneric private constructor(
    private val tflite: Interpreter,
    private val labels: List<String>,
    private val T: Int, // model time length
    private val F: Int, // model feature size (expect 134)
    private val applyMpNormalization: Boolean
) {
    companion object {
        private const val TAG = "SltEngine"

        fun create(
            ctx: Context,
            modelAsset: String = "model_v8_focus16_284_fp16.tflite",
            labelsAsset: String = "labels_ko_284.json",
            applyMpNormalization: Boolean = true
        ): SltEngineGeneric {
            val mapped = FileUtil.loadMappedFile(ctx, modelAsset)
            val options = Interpreter.Options().apply { setNumThreads(4) }
            val tfl = Interpreter(mapped, options)

            val inShape = tfl.getInputTensor(0).shape() // e.g. [1, 91, 134] or [1, 2144]
            val outShape = tfl.getOutputTensor(0).shape() // [1, C]
            val (T, F) = when (inShape.size) {
                3 -> inShape[1] to inShape[2]
                2 -> {
                    val flat = inShape[1]
                    require(flat % 134 == 0) { "입력이 2D(flat)인데 134로 나눠떨어지지 않음: $flat" }
                    (flat / 134) to 134
                }
                else -> error("지원하지 않는 입력 텐서: ${inShape.joinToString()}")
            }

            val labels = LabelLoader.load(ctx, labelsAsset)
            val outC = tfl.getOutputTensor(0).shape().last()
            require(labels.isEmpty() || labels.size == outC) {
                "라벨 개수(${labels.size}) != 모델 출력($outC)"
            }

            Log.i(TAG, "✅ model loaded: in=${inShape.joinToString()} out=${outShape.joinToString()}")
            Log.i(TAG, "✅ resolved T=$T, F=$F (F는 반드시 134가 되어야 함)")
            if (F != 134) {
                Log.w(TAG, "⚠️ F가 134가 아님 → 학습 파이프라인과 불일치 가능성!")
            }
            Log.i(TAG, "✅ labels: ${labels.size}")
            Log.i(TAG, "✅ MP-NORM: ${if (applyMpNormalization) "ENABLED" else "DISABLED"}")

            return SltEngineGeneric(tfl, labels, T, F, applyMpNormalization)
        }
    }

    private val window = ArrayDeque<FloatArray>(T)
    private var firstDebugLogged = false

    fun t() = T
    fun f() = F
    fun numLabels() = labels.size
    fun label(i:Int) = labels.getOrElse(i) { "UNK_$i" }
    fun close() = runCatching { tflite.close() }.isSuccess

    /** 1프레임(F=134)을 푸시하고, 창이 가득 찼을 때 softmax 확률 반환 */
    fun push(frameF134: FloatArray): FloatArray? {
        require(frameF134.size == 134) { "frame size ${frameF134.size} != 134" }

        if (applyMpNormalization) {
            if (!firstDebugLogged) {
                // 원본 좌표로 한 번 로그 찍고
                MpNormalizer.debugLogOnce(frameF134)
                firstDebugLogged = true
            }
            // 이후 in-place 정규화
            MpNormalizer.normalizeXYInPlace(frameF134)
        }

        if (window.size == T) window.removeFirst()
        window.addLast(frameF134)
        if (window.size < T) return null
        return runOnce(window)
    }

    private fun runOnce(seq: Collection<FloatArray>): FloatArray {
        val outC = tflite.getOutputTensor(0).shape().last()
        val input = ByteBuffer.allocateDirect(4 * T * F).order(ByteOrder.nativeOrder())
        for (f in seq) for (v in f) input.putFloat(v)
        input.rewind()

        val output = Array(1) { FloatArray(outC) }
        tflite.run(input, output)
        return softmaxIfNeeded(output[0])
    }

    private fun softmaxIfNeeded(x: FloatArray): FloatArray {
        val s = x.sum()
        if (s in 0.98f..1.02f) return x
        val m = x.maxOrNull() ?: 0f
        var sum = 0.0
        val e = FloatArray(x.size)
        for (i in x.indices) {
            val v = exp((x[i] - m).toDouble())
            e[i] = v.toFloat(); sum += v
        }
        if (sum==0.0) return x
        for (i in x.indices) e[i] = (e[i]/sum).toFloat()
        return e
    }
}
