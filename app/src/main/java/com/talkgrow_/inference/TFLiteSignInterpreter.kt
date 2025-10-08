package com.talkgrow_.inference

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.DataType
import kotlin.math.exp
import kotlin.math.max

class TFLiteSignInterpreter(
    context: Context,
    private val modelAssetName: String = "export_infer.tflite",
) {
    companion object { private const val TAG = "TFLite OK" }

    private val interpreter: Interpreter
    val inputShape: IntArray
    val outputShape: IntArray
    val seqLen: Int
    val featDim: Int
    val numClasses: Int

    init {
        val model = FileUtil.loadMappedFile(context, modelAssetName)
        interpreter = Interpreter(model, Interpreter.Options())

        inputShape  = interpreter.getInputTensor(0).shape()   // [1, L, F]
        outputShape = interpreter.getOutputTensor(0).shape()  // [1, C]
        seqLen = inputShape.getOrNull(1) ?: 1
        featDim = inputShape.getOrNull(2) ?: 1
        numClasses = outputShape.getOrNull(1) ?: 1

        Log.i(TAG, "input=${inputShape.contentToString()} output=${outputShape.contentToString()} " +
                "seqLen=$seqLen featDim=$featDim classes=$numClasses")
    }

    /** features: 길이 == seqLen * featDim; 반환: (최대 인덱스, softmax 확률배열[0..1]) */
    fun infer(features: FloatArray): Pair<Int, FloatArray> {
        val need = seqLen * featDim
        val inArr = if (features.size == need) features else {
            val dst = FloatArray(need)
            val src = if (features.size > need)
                features.copyOfRange(features.size - need, features.size) else features
            val start = need - src.size
            System.arraycopy(src, 0, dst, start, src.size)
            dst
        }

        val input = TensorBuffer.createFixedSize(intArrayOf(1, seqLen, featDim), DataType.FLOAT32)
        input.loadArray(inArr)

        val output = TensorBuffer.createFixedSize(intArrayOf(1, numClasses), DataType.FLOAT32)
        runCatching { interpreter.run(input.buffer, output.buffer.rewind()) }
            .onFailure { e -> Log.e(TAG, "run failed: ${e.message}", e) }

        val logits = output.floatArray
        // ---- softmax (log-sum-exp 안정화) ----
        var maxLogit = Float.NEGATIVE_INFINITY
        for (v in logits) if (v > maxLogit) maxLogit = v
        var sum = 0.0
        val probs = FloatArray(logits.size)
        for (i in logits.indices) {
            val e = exp((logits[i] - maxLogit).toDouble())
            sum += e
            probs[i] = e.toFloat()
        }
        if (sum <= 0.0) {
            val p = 1f / max(1, probs.size)
            for (i in probs.indices) probs[i] = p
        } else {
            val inv = 1.0 / sum
            for (i in probs.indices) probs[i] = (probs[i] * inv).toFloat()
        }

        var bestI = 0
        var bestV = -1f
        for (i in probs.indices) {
            val v = probs[i]
            if (v > bestV) { bestV = v; bestI = i }
        }
        return bestI to probs
    }

    fun close() = runCatching { interpreter.close() }.let { }
}
