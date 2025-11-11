// android_app/src/main/java/com/talkgrow_/inference/TFLiteSignInterpreter.kt
package com.talkgrow_.inference

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.MappedByteBuffer

class TFLiteSignInterpreter(
    ctx: Context,
    modelAssetPath: String,
    labelsAssetPath: String
) : AutoCloseable {

    companion object { private const val TAG = "TFLiteSignInterpreter" }

    private val tflite: Interpreter
    private val labels: List<String>

    init {
        val buf: MappedByteBuffer = FileUtil.loadMappedFile(ctx, modelAssetPath)
        tflite = Interpreter(buf, Interpreter.Options())
        labels = parseVocab(ctx, labelsAssetPath)
        Log.d(TAG, "Loaded labels=${labels.size}")
    }

    override fun close() { tflite.close() }

    private fun parseVocab(ctx: Context, assetPath: String): List<String> {
        val json = ctx.assets.open(assetPath).bufferedReader().use { it.readText() }
        val obj = JSONObject(json)
        val maxIdx = obj.keys().asSequence().maxOf { obj.getInt(it) }
        val out = MutableList(maxIdx + 1) { "" }
        obj.keys().forEach { key ->
            val idx = obj.getInt(key)
            if (idx in out.indices) out[idx] = key
        }
        return out
    }

    private fun softmax(logits: FloatArray): FloatArray {
        var maxLogit = Float.NEGATIVE_INFINITY
        for (v in logits) if (v > maxLogit) maxLogit = v
        var sum = 0.0
        val exps = DoubleArray(logits.size)
        for (i in logits.indices) { val e = kotlin.math.exp((logits[i] - maxLogit).toDouble()); exps[i] = e; sum += e }
        val out = FloatArray(logits.size)
        val inv = if (sum == 0.0) 1.0 else 1.0 / sum
        for (i in logits.indices) out[i] = (exps[i] * inv).toFloat()
        return out
    }

    /** 입력: [91,134], 출력: label→prob */
    fun predictProbsFixed(seq: Array<FloatArray>): Map<String, Float> {
        val t = seq.size
        val f = if (t > 0) seq[0].size else 0

        val input = Array(1) { Array(t) { FloatArray(f) } }
        for (i in 0 until t) {
            val row = seq[i]
            for (j in 0 until f) input[0][i][j] = row[j]
        }

        val logits = Array(1) { FloatArray(labels.size) }
        tflite.run(input, logits)

        val probs = softmax(logits[0])
        val map = HashMap<String, Float>(labels.size)
        for (i in probs.indices) {
            val lab = if (i < labels.size) labels[i] else "cls_$i"
            map[lab] = probs[i]
        }
        return map
    }
}
