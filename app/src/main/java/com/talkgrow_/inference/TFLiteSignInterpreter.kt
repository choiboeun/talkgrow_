package com.talkgrow_.inference

import android.content.Context
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TFLiteSignInterpreter(context: Context) {

    private val T = 91
    private val D = 134

    private val interpreter: Interpreter
    private val mean = FloatArray(D) { 0f }
    private val std  = FloatArray(D) { 1f }
    private val id2label: List<String>

    // ===== 디버그 유틸 =====
    fun outputClasses(): Int = id2label.size

    /** Top-K 추출 */
    fun topK(probs: FloatArray, k: Int = 5): List<Pair<Int, Float>> {
        val idxs = probs.indices.sortedByDescending { probs[it] }.take(k)
        return idxs.map { it to probs[it] }
    }

    /** 엔트로피(불확실도) — 자연로그 ln 사용, Double로 계산 후 Float로 변환 */
    fun entropy(probs: FloatArray): Float {
        var e = 0.0
        for (p in probs) {
            val pp = p.toDouble()
            if (pp > 1e-9) e -= pp * kotlin.math.ln(pp)
        }
        return e.toFloat()
    }

    init {
        // 1) 모델 로드
        val modelBytes = context.assets.open("model.tflite").use { it.readBytes() }
        val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size)
            .order(ByteOrder.nativeOrder())
            .apply { put(modelBytes); rewind() }

        interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
            setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
        })

        // 2) scaler.json 로드 (mean/std)
        runCatching {
            val txt = context.assets.open("scaler.json")
                .use { BufferedReader(InputStreamReader(it)).readText() }
            val obj = JSONObject(txt)
            val mArr = obj.getJSONArray("mean")
            val sArr = obj.getJSONArray("std")
            for (i in 0 until kotlin.math.min(D, mArr.length())) {
                mean[i] = mArr.getDouble(i).toFloat()
            }
            for (i in 0 until kotlin.math.min(D, sArr.length())) {
                std[i]  = sArr.getDouble(i).toFloat()
            }
        }

        // 3) label2id.json → id2label
        id2label = runCatching {
            val txt = context.assets.open("label2id.json")
                .use { BufferedReader(InputStreamReader(it)).readText() }
            val obj = JSONObject(txt)
            val tmp = mutableListOf<Pair<Int, String>>()
            val it = obj.keys()
            while (it.hasNext()) {
                val label = it.next()
                val idx   = obj.getInt(label)
                tmp += idx to label
            }
            tmp.sortedBy { it.first }.map { it.second }
        }.getOrElse { emptyList() }
    }

    fun labels(): List<String> = id2label

    /** 입력: (1, 91, 134) float32 → 출력: (numClasses) float32 */
    fun runInference(seq: Array<Array<FloatArray>>): FloatArray {
        require(seq.size == 1 && seq[0].size == T && seq[0][0].size == D) { "Input must be (1,$T,$D)" }

        // (1,T,D) 표준화
        val input = ByteBuffer.allocateDirect(4 * T * D).order(ByteOrder.nativeOrder())
        for (i in 0 until T) {
            for (j in 0 until D) {
                val x = seq[0][i][j]
                val s = if (std[j] == 0f) 1f else std[j]
                input.putFloat((x - mean[j]) / s)
            }
        }
        input.rewind()

        val numClasses = id2label.size.takeIf { it > 0 } ?: 3297
        val output = ByteBuffer.allocateDirect(4 * numClasses).order(ByteOrder.nativeOrder())
        output.rewind()

        interpreter.run(input, output)
        output.rewind()

        val probs = FloatArray(numClasses)
        for (i in 0 until numClasses) probs[i] = output.float
        return probs
    }

    fun close() = interpreter.close()
}
