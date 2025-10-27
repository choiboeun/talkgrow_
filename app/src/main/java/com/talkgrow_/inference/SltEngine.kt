package com.talkgrow_.inference

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class SltEngine private constructor(
    private val tflite: Interpreter,
    private val labels: List<String>
) {
    companion object {
        private const val TAG = "SltEngine"
        private const val T = 91
        private const val F = 134

        fun create(ctx: Context): SltEngine {
            val modelCandidates = listOf(
                "model_v8_focus16_284_fp16.tflite"
            )

            val mappedModel = run {
                var buf: java.nio.MappedByteBuffer? = null
                var picked: String? = null
                for (name in modelCandidates) {
                    try {
                        buf = FileUtil.loadMappedFile(ctx, name)
                        picked = name
                        break
                    } catch (_: Throwable) {}
                }
                if (buf == null) throw IllegalStateException("Model not found in assets: $modelCandidates")
                Log.i(TAG, "✅ 모델 로드: $picked")
                buf!!
            }

            val options = Interpreter.Options().apply { setNumThreads(4) }
            val tfl = Interpreter(mappedModel, options)

            val labels = tryLoadLabels(ctx)

            runCatching {
                val inShape = tfl.getInputTensor(0).shape().joinToString()
                val outShape = tfl.getOutputTensor(0).shape().joinToString()
                Log.i(TAG, "in=[$inShape] out=[$outShape]")
            }

            val outC = tfl.getOutputTensor(0).shape().last()
            if (labels.isNotEmpty() && labels.size != outC) {
                throw IllegalStateException("라벨 개수(${labels.size})와 모델 출력 차원($outC)이 다릅니다. 라벨/모델을 맞춰주세요.")
            }

            return SltEngine(tfl, labels)
        }

        private fun tryLoadLabels(ctx: Context): List<String> {
            // 1) labels.txt (한 줄 당 1라벨)
            runCatching {
                val ls = FileUtil.loadLabels(ctx, "labels.txt")
                if (ls.isNotEmpty()) {
                    Log.i(TAG, "✅ 라벨 로드: labels.txt (${ls.size})")
                    return ls
                }
            }

            // 2) labels_ko_284.json (배열형 / {"labels":[...]} / {"0":"라벨",...})
            runCatching {
                val text = readAssetText(ctx, "labels_ko_284.json").trim()
                if (text.startsWith("[")) {
                    val arr = JSONArray(text)
                    val out = ArrayList<String>(arr.length())
                    for (i in 0 until arr.length()) out += arr.getString(i)
                    Log.i(TAG, "✅ 라벨 로드: 배열형 (${out.size})")
                    return out
                }
                val obj = JSONObject(text)
                if (obj.has("labels")) {
                    val arr = obj.getJSONArray("labels")
                    val out = ArrayList<String>(arr.length())
                    for (i in 0 until arr.length()) out += arr.getString(i)
                    Log.i(TAG, "✅ 라벨 로드: labels필드 (${out.size})")
                    return out
                }
                // 숫자키-객체형
                val keys = obj.keys().asSequence()
                    .mapNotNull { k -> k.toIntOrNull()?.let { it to k } }
                    .sortedBy { it.first }
                    .toList()
                val out = ArrayList<String>(keys.size)
                for ((_, key) in keys) {
                    val v = obj.getString(key)
                    out += if (v.startsWith("__CLASS_")) "UNK_$key" else v
                }
                Log.i(TAG, "✅ 라벨 로드: 숫자키 객체형 (${out.size}) 예: 24='${out.getOrNull(24)}' 47='${out.getOrNull(47)}' 173='${out.getOrNull(173)}'")
                return out
            }

            Log.w(TAG, "⚠️ 라벨 파일을 찾지 못함. 확률만 출력합니다.")
            return emptyList()
        }

        private fun readAssetText(ctx: Context, name: String): String =
            ctx.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    val numLabels: Int get() = labels.size
    fun close() = runCatching { tflite.close() }.isSuccess

    /** 가변 길이 → 슬라이딩 평균 */
    fun inferWithSliding(frames: List<FloatArray>, win: Int = T, step: Int = 5): FloatArray {
        if (frames.isEmpty()) return FloatArray(outputC()) { 0f }

        val outs = mutableListOf<FloatArray>()
        if (frames.size < T) {
            outs += inferSingle(toLen91(frames))
        } else {
            var s = 0
            val lastStart = max(0, frames.size - win)
            while (s <= lastStart) {
                outs += inferSingle(frames.subList(s, s + win))
                s += step
            }
            if (lastStart % step != 0) outs += inferSingle(frames.subList(lastStart, lastStart + win))
        }

        val C = outputC()
        val avg = FloatArray(C)
        for (o in outs) for (i in o.indices) avg[i] += o[i]
        if (outs.isNotEmpty()) for (i in avg.indices) avg[i] /= outs.size
        return avg
    }

    private fun outputC(): Int = tflite.getOutputTensor(0).shape().last()

    private fun inferSingle(seq91: List<FloatArray>): FloatArray {
        require(seq91.size == T) { "seq len=${seq91.size}, need $T" }
        val outC = outputC()

        val input = ByteBuffer.allocateDirect(4 * T * F).order(ByteOrder.nativeOrder())
        for (t in 0 until T) {
            val f = seq91[t]
            require(f.size == F) { "frame[$t] len=${f.size}, need $F" }
            for (j in 0 until F) input.putFloat(f[j])
        }
        val output = Array(1) { FloatArray(outC) }
        return try {
            tflite.run(input, output)
            softmax(output[0])
        } catch (e: Throwable) {
            Log.e(TAG, "tflite.run failed: ${e.message}", e)
            FloatArray(outC) { 0f }
        }
    }

    private fun toLen91(src: List<FloatArray>): List<FloatArray> {
        val out = ArrayList<FloatArray>(T)
        val n = src.size
        if (n >= T) {
            val step = (n - 1).toFloat() / (T - 1)
            var acc = 0f
            repeat(T) {
                val i = min(n - 1, acc.toInt())
                out += src[i]
                acc += step
            }
        } else {
            out += src
            repeat(T - n) { out += FloatArray(F) }
        }
        return out
    }

    private fun softmax(x: FloatArray): FloatArray {
        val m = x.maxOrNull() ?: 0f
        var s = 0.0
        val e = FloatArray(x.size)
        for (i in x.indices) {
            val v = exp((x[i] - m).toDouble())
            e[i] = v.toFloat()
            s += v
        }
        if (s == 0.0) return FloatArray(x.size)
        for (i in x.indices) e[i] = (e[i] / s).toFloat()
        return e
    }

    fun labels(): List<String> = labels
}
