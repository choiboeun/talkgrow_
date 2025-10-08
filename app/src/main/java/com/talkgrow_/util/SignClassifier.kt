// app/src/main/java/com/talkgrow_/SignClassifier.kt
package com.talkgrow_

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

class SignClassifier(ctx: Context) {

    private val TAG = "SignClassifier"
    private val FEAT_DIM = 134
    private val SEQ_LEN = 91

    private val interpreter: Interpreter
    private val labels: List<String>
    private val outElems: Int
    private val window = ArrayDeque<FloatArray>()

    init {
        val model = FileUtil.loadMappedFile(ctx, "export_infer.tflite")
        interpreter = Interpreter(model, Interpreter.Options())

        val outTensor = interpreter.getOutputTensor(0)
        outElems = outTensor.numElements()
        Log.i(TAG, "model ready: out=$outElems")

        // 라벨 로딩(유연 파서)
        val tmp = MutableList(outElems) { i -> "CLASS_$i" }
        try {
            val text = ctx.assets.open("sen_label_map.json").bufferedReader().use { it.readText() }
            Regex("\\{[^}]*?\"id\"\\s*:\\s*(\\d+)\\s*,[^}]*?\"name\"\\s*:\\s*\"([^\"]+)\"")
                .findAll(text).forEach {
                    val id = it.groupValues[1].toInt()
                    val name = it.groupValues[2]
                    if (id in tmp.indices) tmp[id] = name
                }
            Regex("\"([^\"]+)\"\\s*:\\s*(\\d+)").findAll(text).forEach {
                val name = it.groupValues[1]; val id = it.groupValues[2].toInt()
                if (id in tmp.indices) tmp[id] = name
            }
        } catch (_: Throwable) { /* keep defaults */ }
        labels = tmp
    }

    fun clearBuffer() { window.clear() }

    fun pushFrame(pose: com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult?,
                  hand: com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult?) {
        val v = FloatArray(FEAT_DIM) { 0f }
        var off = 0

        pose?.landmarks()?.firstOrNull()?.let { list ->
            val n = min(25, list.size)
            for (i in 0 until n) { v[off++] = list[i].x(); v[off++] = list[i].y() }
        } ?: run { off += 50 }

        if (hand != null && hand.landmarks().isNotEmpty()) {
            val m = HashMap<String, List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>()
            for (i in hand.landmarks().indices) {
                val which = runCatching { hand.handednesses()[i][0].categoryName() }.getOrElse { "Unknown" }
                m[which] = hand.landmarks()[i]
            }
            m["Left"]?.let { lm ->
                val n = min(21, lm.size)
                for (i in 0 until n) { v[off++] = lm[i].x(); v[off++] = lm[i].y() }
            } ?: run { off += 42 }
            m["Right"]?.let { lm ->
                val n = min(21, lm.size)
                for (i in 0 until n) { v[off++] = lm[i].x(); v[off++] = lm[i].y() }
            } ?: run { off += 42 }
        } else { off += 84 }

        while (off < FEAT_DIM) v[off++] = 0f

        window.addLast(v)
        if (window.size > SEQ_LEN) window.removeFirst()
    }

    private fun minMax01InPlace(flat: FloatArray, seqLen: Int, featDim: Int) {
        var xmin = Float.POSITIVE_INFINITY
        var xmax = Float.NEGATIVE_INFINITY
        var ymin = Float.POSITIVE_INFINITY
        var ymax = Float.NEGATIVE_INFINITY
        var off = 0
        for (t in 0 until seqLen) {
            var i = 0
            while (i < featDim) {
                val x = flat[off + i]
                val y = flat[off + i + 1]
                if (x < xmin) xmin = x
                if (x > xmax) xmax = x
                if (y < ymin) ymin = y
                if (y > ymax) ymax = y
                i += 2
            }
            off += featDim
        }
        val xDen = if (xmax > xmin) (xmax - xmin) else 1f
        val yDen = if (ymax > ymin) (ymax - ymin) else 1f
        off = 0
        for (t in 0 until seqLen) {
            var i = 0
            while (i < featDim) {
                flat[off + i]     = (flat[off + i]     - xmin) / xDen
                flat[off + i + 1] = (flat[off + i + 1] - ymin) / yDen
                i += 2
            }
            off += featDim
        }
    }

    fun inferStream(minReadyFrames: Int = 45): Result? {
        if (window.size < minReadyFrames) return null

        // 91x134 버퍼로 복사
        val seqLen = SEQ_LEN
        val featDim = FEAT_DIM
        val flat = FloatArray(seqLen * featDim) { 0f }
        val list = window.toList()
        val n = min(list.size, seqLen)
        var off = 0
        for (t in 0 until n) {
            System.arraycopy(list[t], 0, flat, off, featDim)
            off += featDim
        }

        // ✅ 학습과 동일한 "시퀀스 단위 MinMax(0~1)"
        minMax01InPlace(flat, seqLen, featDim)

        // (옵션) feat_norm.json 기반 Z-Norm을 추가하려면 여기서 하면 됨 (현재는 학습과 일치 위해 OFF)

        val input = ByteBuffer.allocateDirect(flat.size * 4).order(ByteOrder.nativeOrder())
        flat.forEach { input.putFloat(it) }
        input.rewind()

        val output = Array(1) { FloatArray(outElems) }
        runCatching { interpreter.run(input, output) }
            .onFailure {
                Log.e(TAG, "infer failed: ${it.message}")
                return null
            }

        val scores = output[0]
        var maxI = 0
        for (i in 1 until scores.size) if (scores[i] > scores[maxI]) maxI = i
        val label = labels.getOrElse(maxI) { "CLASS_$maxI" }
        return Result(maxI, label, scores[maxI])
    }

    data class Result(val topIndex: Int, val topLabel: String, val topScore: Float)
}
