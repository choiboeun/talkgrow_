// File: app/src/main/java/com/talkgrow_/LabelResolver.kt
package com.talkgrow_

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp

class LabelResolver(
    context: Context,
    assetName: String
) {
    private val labels: List<String>

    init {
        val txt = context.assets.open(assetName).use { it.readBytes() }
            .toString(Charsets.UTF_8).trim()
        labels = parseFlexible(txt)
    }

    private fun parseFlexible(txt: String): List<String> {
        return runCatching {
            if (txt.startsWith("[")) {
                val arr = JSONArray(txt)
                (0 until arr.length()).map { arr.optString(it) }.map { it.trim() }
            } else {
                val obj = JSONObject(txt)
                obj.keys().asSequence().toList()
                    .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
                    .map { k -> obj.optString(k) }.map { it.trim() }
            }
        }.getOrElse { emptyList() }
            .map { if (it.startsWith("__CLASS_")) "" else it }
    }

    fun labelOf(index: Int): String =
        if (index in labels.indices) labels[index] else ""

    /** logits/probs → top1 with softmax confidence */
    fun top1WithConfidence(logitsOrProbs: FloatArray): Triple<Int, String, Float> {
        if (logitsOrProbs.isEmpty()) return Triple(-1, "", 0f)
        val maxLogit = logitsOrProbs.maxOrNull()!!
        var sum = 0.0
        val exps = DoubleArray(logitsOrProbs.size)
        for (i in logitsOrProbs.indices) {
            val e = exp((logitsOrProbs[i] - maxLogit).toDouble())
            exps[i] = e; sum += e
        }
        var bestIdx = 0
        var bestProb = (exps[0] / sum).toFloat()
        for (i in 1 until exps.size) {
            val p = (exps[i] / sum).toFloat()
            if (p > bestProb) { bestProb = p; bestIdx = i }
        }
        return Triple(bestIdx, labelOf(bestIdx), bestProb)
    }
}
