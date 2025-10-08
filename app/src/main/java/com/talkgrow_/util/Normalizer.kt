package com.talkgrow_.util

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.nio.charset.Charset
import kotlin.math.max

/**
 * 학습 파이프라인과 일치:
 *  - 기본: 시퀀스 단위 MinMax(0~1) ONLY  ← Python 전처리(_normalize_sequence)와 동일
 *  - 옵션: ENABLE_DATASET_ZNORM=true && feat_norm.json 유효 시 MinMax 뒤 전역 Z-norm 추가
 */
object Normalizer {
    private const val TAG = "Normalizer"

    /** 학습이 MinMax ONLY 였으므로 기본은 false */
    var ENABLE_DATASET_ZNORM: Boolean = false

    private var mean: FloatArray? = null
    private var std: FloatArray? = null
    private var featDimMeta: Int = -1
    private var seqLenMeta: Int = -1
    private var loaded = false

    fun init(context: Context, assetName: String = "feat_norm.json") {
        runCatching {
            val txt = context.assets.open(assetName).use { it.readBytes().toString(Charset.forName("UTF-8")) }
            val j = JSONObject(txt)
            featDimMeta = j.optInt("feat_dim", -1)
            seqLenMeta  = j.optInt("seq_len", -1)
            mean = j.getJSONArray("mean").let { a -> FloatArray(a.length()) { i -> a.getDouble(i).toFloat() } }
            std  = j.getJSONArray("std"). let { a -> FloatArray(a.length()) { i -> a.getDouble(i).toFloat() } }
            loaded = true
            Log.i(TAG, "loaded feat_norm.json feat=$featDimMeta seq=$seqLenMeta (mean/std len=${mean!!.size}/${std!!.size})")
        }.onFailure {
            loaded = false
            Log.w(TAG, "feat_norm.json not found or invalid. Z-norm disabled. (${it.message})")
        }
    }

    /** flat[T*F] 를 in-place 정규화 */
    fun normalizeInPlace(seq: FloatArray, seqLen: Int, featDim: Int) {
        if (seqLen <= 0 || featDim <= 0) return

        // 1) MinMax(0~1): 학습 전처리와 동일
        perSeqMinMax01InPlace(seq, seqLen, featDim)

        // 2) (옵션) 전역 Z-Norm: 학습에서 사용하지 않았다면 끄세요(기본 false)
        if (ENABLE_DATASET_ZNORM && loaded && mean != null && std != null &&
            mean!!.size == featDim && std!!.size == featDim) {

            var off = 0
            for (t in 0 until seqLen) {
                var i = 0
                while (i < featDim) {
                    val m0 = mean!![i]
                    val s0 = max(1e-6f, std!![i])
                    val m1 = mean!![i + 1]
                    val s1 = max(1e-6f, std!![i + 1])

                    seq[off + i]     = (seq[off + i]     - m0) / s0
                    seq[off + i + 1] = (seq[off + i + 1] - m1) / s1
                    i += 2
                }
                off += featDim
            }
        }
    }

    /** Python(_normalize_sequence)과 동일한 방식의 MinMax(좌표축 별) */
    private fun perSeqMinMax01InPlace(seq: FloatArray, seqLen: Int, featDim: Int) {
        var xmin = Float.POSITIVE_INFINITY
        var xmax = Float.NEGATIVE_INFINITY
        var ymin = Float.POSITIVE_INFINITY
        var ymax = Float.NEGATIVE_INFINITY

        var off = 0
        for (t in 0 until seqLen) {
            var i = 0
            while (i < featDim) {
                val x = seq[off + i]
                val y = seq[off + i + 1]
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
                seq[off + i]     = (seq[off + i]     - xmin) / xDen
                seq[off + i + 1] = (seq[off + i + 1] - ymin) / yDen
                i += 2
            }
            off += featDim
        }
    }
}

/** 2D 배열[T][F] 편의용 래퍼 */
class FeatureNormalizer private constructor() {
    fun normalizeInPlace(seq: Array<FloatArray>) {
        if (seq.isEmpty()) return
        val T = seq.size
        val F = seq[0].size
        val flat = FloatArray(T * F)
        var off = 0
        for (t in 0 until T) {
            System.arraycopy(seq[t], 0, flat, off, F)
            off += F
        }
        Normalizer.normalizeInPlace(flat, T, F)
        off = 0
        for (t in 0 until T) {
            System.arraycopy(flat, off, seq[t], 0, F)
            off += F
        }
    }

    companion object {
        fun fromAsset(ctx: Context, assetName: String): FeatureNormalizer {
            Normalizer.init(ctx, assetName)
            return FeatureNormalizer()
        }
    }
}
