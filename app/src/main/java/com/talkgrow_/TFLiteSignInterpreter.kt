package com.talkgrow_

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.Closeable
import java.nio.MappedByteBuffer
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.max
import com.talkgrow_.util.FeatureBuilder134

// 고정 길이/차원
private const val T_FIXED = 91
private const val F_FIXED = 134

// 슬라이딩 윈도우 앙상블
private const val ENSEMBLE_WINDOW_SIZE = 15
private const val ENSEMBLE_STEP = 5

// ⬇️ xy 전제(2칸씩) + pose25의 L/R 어깨 인덱스를 전체 134 벡터 기준으로 맞춤
private const val POSE_OFFSET = 0
private const val L_SH_X_IDX = POSE_OFFSET + FeatureBuilder134.IDX_L_SH_IN_25 * 2 // 10
private const val L_SH_Y_IDX = L_SH_X_IDX + 1                                   // 11
private const val R_SH_X_IDX = POSE_OFFSET + FeatureBuilder134.IDX_R_SH_IN_25 * 2 // 12
private const val R_SH_Y_IDX = R_SH_X_IDX + 1                                   // 13

// ⬇️ Python 스크립트 finetune_end2end.py의 버그 인덱스를 그대로 사용
private const val R_SH_X_BUG = 33
private const val R_SH_Y_BUG = 34
private const val L_SH_X_BUG = 36
private const val L_SH_Y_BUG = 37

class TFLiteSignInterpreter(
    context: Context,
    modelAssetName: String = "model_v8_focus16_284_fp16.tflite",
    labelsAssetName: String = "labels_ko_284.json"
) : Closeable {

    private val tflite: Interpreter
    private val labels: List<String>

    // 훈련시 사용한 Z-Score 통계 (길이 134)
    private val MEAN_VECTOR = floatArrayOf(
        0.096683666f, 0.04263474f, 0.09938422f, 0.037350506f, 0.0938614f, 0.03752628f, 0.10221642f, 0.04030414f, 0.091447607f, 0.04052453f,
        0.11072469f, 0.073209725f, 0.08329025f, 0.072233565f, 0.11518891f, 0.11269367f, 0.0768936f, 0.10430008f, 0.11110873f, 0.13052993f,
        0.084026955f, 0.10064639f, 0.10516808f, 0.15834604f, 0.08751269f, 0.15831786f, 0.10560855f, 0.19132806f, 0.08848364f, 0.19128747f,
        0.10521621f, 0.19166863f, 0.089343525f, 0.19166858f, 0.09880953f, 0.048610557f, 0.09467609f, 0.04860078f, 0.1106287f, 0.13742754f,
        0.0852532f, 0.10171245f, 0.10904338f, 0.13519699f, 0.08675894f, 0.0992267f, 0.10863203f, 0.13305607f, 0.08686341f, 0.098980926f,
        0.078666f, 0.094513655f, 0.08070263f, 0.094040476f, 0.08209657f, 0.09472655f, 0.082880266f, 0.095870174f, 0.08339264f, 0.09671307f,
        0.08103381f, 0.09478566f, 0.08208018f, 0.09669076f, 0.08274483f, 0.097938157f, 0.083210684f, 0.098873615f, 0.08006864f, 0.09556585f,
        0.08120065f, 0.097821623f, 0.08180609f, 0.09928537f, 0.08217296f, 0.10019083f, 0.0793076f, 0.09642692f, 0.080344915f, 0.09851175f,
        0.080842935f, 0.09942563f, 0.08112536f, 0.09985904f, 0.07875624f, 0.09734855f, 0.07948379f, 0.09900812f, 0.0798266f, 0.09965992f,
        0.08002702f, 0.09995462f, 0.10465526f, 0.12282051f, 0.10297234f, 0.12374728f, 0.10223017f, 0.12631232f, 0.10194424f, 0.12897743f,
        0.10166066f, 0.13104075f, 0.10367751f, 0.1285305f, 0.1027644f, 0.13227922f, 0.10201763f, 0.13434885f, 0.10143834f, 0.13587017f,
        0.10439897f, 0.12914574f, 0.10332397f, 0.13291681f, 0.10246202f, 0.13501191f, 0.10176899f, 0.1365763f, 0.1047581f, 0.12953164f,
        0.1037485f, 0.133016f, 0.10293424f, 0.13500887f, 0.10226338f, 0.13647151f, 0.10483953f, 0.12980279f, 0.1041137f, 0.13276659f,
        0.10348963f, 0.13446999f, 0.10293046f, 0.1356878f
    )
    private val STD_VECTOR = floatArrayOf(
        0.19683658f, 0.089965686f, 0.2034532f, 0.078379005f, 0.19202703f, 0.07870419f, 0.20842253f, 0.08587301f, 0.18756689f, 0.08617784f,
        0.23158722f, 0.1500319f, 0.17385787f, 0.14832956f, 0.23962548f, 0.23540281f, 0.16169757f, 0.21504083f, 0.23217186f, 0.27587157f,
        0.17522125f, 0.22356215f, 0.21461986f, 0.32972747f, 0.18115655f, 0.32963634f, 0.21555062f, 0.38967398f, 0.1829491f, 0.38960317f,
        0.21511854f, 0.3903958f, 0.18461503f, 0.3903956f, 0.20045653f, 0.10031144f, 0.19336469f, 0.10030269f, 0.23156446f, 0.29260996f,
        0.17778322f, 0.23329455f, 0.22859986f, 0.28968093f, 0.18032375f, 0.22970793f, 0.22780542f, 0.28472269f, 0.18029094f, 0.22648174f,
        0.17205603f, 0.21909651f, 0.17585273f, 0.22060686f, 0.17850235f, 0.22520243f, 0.18086801f, 0.23027174f, 0.18193129f, 0.23416379f,
        0.1769262f, 0.22856835f, 0.17902075f, 0.23572789f, 0.18106206f, 0.23958373f, 0.18195634f, 0.24370183f, 0.17532045f, 0.22954106f,
        0.17755877f, 0.23701636f, 0.17866062f, 0.24234396f, 0.17937005f, 0.24485779f, 0.1738081f, 0.23019002f, 0.17600699f, 0.23683709f,
        0.17688823f, 0.24124244f, 0.17739813f, 0.24300411f, 0.1728299f, 0.23069453f, 0.17425472f, 0.23746379f, 0.17507158f, 0.23987597f,
        0.1754335f, 0.2414242f, 0.22261843f, 0.27222002f, 0.2187157f, 0.27542794f, 0.2172465f, 0.28204179f, 0.21677381f, 0.2887859f,
        0.21627656f, 0.2939276f, 0.22084819f, 0.28730947f, 0.21873878f, 0.29630148f, 0.21731366f, 0.30227566f, 0.21623734f, 0.30622032f,
        0.22235705f, 0.2881793f, 0.21999507f, 0.29759437f, 0.21835026f, 0.30306229f, 0.21700457f, 0.30713063f, 0.22309794f, 0.28830495f,
        0.22131571f, 0.2969847f, 0.21933286f, 0.30206388f, 0.21803206f, 0.30600622f, 0.22337149f, 0.28817695f, 0.22205876f, 0.29559305f,
        0.22045606f, 0.29970929f, 0.21939144f, 0.30299956f
    )

    init {
        val model: MappedByteBuffer = FileUtil.loadMappedFile(context, modelAssetName)
        val opts = Interpreter.Options()
        tflite = Interpreter(model, opts)

        labels = readLabels(context, labelsAssetName)
        require(labels.isNotEmpty()) { "Label list is empty" }

        val inShape = runCatching { tflite.getInputTensor(0).shape() }.getOrNull()
        val outShape = runCatching { tflite.getOutputTensor(0).shape() }.getOrNull()

        Log.i("TFLiteSign", "model in=${inShape?.contentToString()} out=${outShape?.contentToString()} labels=${labels.size}")

        if (outShape != null && outShape.isNotEmpty() && outShape.last() != labels.size) {
            Log.e("TFLiteSign", "모델 출력차원(${outShape.last()}) ≠ 라벨수(${labels.size})")
        }
    }

    /**
     * ✅ 최종 수정: raw keypoint에 'Bug-for-bug normalization'을 적용한 후 Z-Score를 적용합니다.
     */
    fun predictProbs(seq: Array<FloatArray>): Map<String, Float> {
        // 원본 시퀀스의 복사본을 만들어 사용합니다.
        val seqToProcess = seq.map { it.clone() }.toTypedArray()

        val seq91 = toLen91(seqToProcess)

        // 1. ✅ 버그 있는 중심 이동 및 스케일 정규화 적용
        applyBugForBugNormalization(seq91)

        // ⭐️ 추가된 로그: Bug 정규화 직후의 어깨 좌표 확인 (L_SH_X_IDX=10, R_SH_X_IDX=12)
        if (seq91.isNotEmpty()) {
            val lshxBugNorm = seq91[0][L_SH_X_IDX]
            val rshxBugNorm = seq91[0][R_SH_X_IDX]
            Log.i("BugNormCheck", "BugNorm L_SH_X(10): %.4f, R_SH_X(12): %.4f".format(lshxBugNorm, rshxBugNorm))
        }

        val numClasses = labels.size
        val sumProbs = FloatArray(numClasses)
        var numWindows = 0

        val T = seq91.size
        val win = ENSEMBLE_WINDOW_SIZE
        val step = ENSEMBLE_STEP

        for (s in 0 until T - win + 1 step step) {
            val clip = seq91.sliceArray(s until s + win)
            val pad = toLen91(clip)

            // 2. Z-Score 정규화만 적용합니다.
            applyZScoreNormalization(pad)

            val probs = runTFLiteInference(pad)
            if (probs.isEmpty()) continue
            for (i in 0 until numClasses) sumProbs[i] += probs[i]
            numWindows++
        }
        if (numWindows == 0) return emptyMap()

        val mean = sumProbs.map { it / numWindows.toFloat() }
        val out = LinkedHashMap<String, Float>(labels.size)
        var bestL = ""; var bestP = -1f
        for (i in labels.indices) {
            val p = mean[i].coerceIn(0f, 1f)
            out[labels[i]] = p
            if (p > bestP) { bestP = p; bestL = labels[i] }
        }
        Log.i("TFLiteSign", "Prediction Success: windows=$numWindows top=$bestL p=${"%.4f".format(bestP)}")
        return out
    }

    override fun close() { runCatching { tflite.close() } }

    // ---------- 내부 도우미 ----------

    private fun runTFLiteInference(finalSeq: Array<FloatArray>): FloatArray {
        val input = Array(1) { Array(T_FIXED) { FloatArray(F_FIXED) } }
        for (t in 0 until T_FIXED) System.arraycopy(finalSeq[t], 0, input[0][t], 0, F_FIXED)
        val output = Array(1) { FloatArray(labels.size) }
        return try {
            tflite.run(input, output)
            softmax(output[0])
        } catch (e: Exception) {
            Log.e("TFLiteSign", "tflite.run failed: ${e.message}")
            floatArrayOf()
        }
    }

    /**
     * 학습 시 사용된 'bug-for-bug normalization' 로직을 재현합니다.
     * - 잘못된 인덱스 (33, 34, 36, 37)를 사용합니다.
     * - Center Shift 시 i % 3 == 2 인덱스를 제외합니다.
     */
    private fun applyBugForBugNormalization(seq: Array<FloatArray>) {
        if (seq.isEmpty()) return

        for (frame in seq) {
            val rsX = frame[R_SH_X_BUG] // 33번 인덱스
            val rsY = frame[R_SH_Y_BUG] // 34번 인덱스

            // 1. Buggy Center Shift: (i % 3 == 0) 또는 (i % 3 == 1)인 좌표만 이동
            for (i in frame.indices) {
                if (i % 3 == 0) { // X 좌표가 0, 3, 6, ... 인덱스일 경우
                    frame[i] -= rsX
                } else if (i % 3 == 1) { // Y 좌표가 1, 4, 7, ... 인덱스일 경우
                    frame[i] -= rsY
                }
                // i % 3 == 2 인덱스 (2, 5, 8, ...)는 이동하지 않습니다. (버그 재현)
            }

            // 2. Scale Normalization: 어깨 간 거리로 나누기 (버그 인덱스 사용)
            val distSq = (frame[R_SH_X_BUG] - frame[L_SH_X_BUG]) * (frame[R_SH_X_BUG] - frame[L_SH_X_BUG]) +
                    (frame[R_SH_Y_BUG] - frame[L_SH_Y_BUG]) * (frame[R_SH_Y_BUG] - frame[L_SH_Y_BUG])

            val shoulderDist = sqrt(max(distSq, 1e-6f))

            // 3. Scale
            for (i in frame.indices) {
                frame[i] /= shoulderDist
            }
        }
    }

    private fun applyZScoreNormalization(seq: Array<FloatArray>) {
        if (seq.isEmpty()) return
        for (t in 0 until seq.size) {
            for (f in 0 until F_FIXED) {
                val div = STD_VECTOR[f].coerceAtLeast(1e-6f)
                seq[t][f] = (seq[t][f] - MEAN_VECTOR[f]) / div
            }
        }

        // 기존 ZScoreCheck 로그 (Idx 10 = L_SH_X)
        if (seq.isNotEmpty()) {
            val lshxNorm = seq[0][L_SH_X_IDX]
            Log.i("ZScoreCheck", "Normalized L_SH_X (Idx 10) in Frame 0: %.4f".format(lshxNorm))
        }
    }

    /**
     * 시퀀스 길이를 T_FIXED=91로 맞춥니다.
     */
    private fun toLen91(seq: Array<FloatArray>): Array<FloatArray> {
        val L = seq.size
        if (L == T_FIXED) return seq
        if (L > T_FIXED) {
            val indices = FloatArray(T_FIXED) { i -> i * (L - 1f) / (T_FIXED - 1f) }
                .map { it ->
                    if (it - it.toInt() >= 0.5f) it.toInt() + 1 else it.toInt()
                }
                .map { it.coerceIn(0, L - 1) }

            return Array(T_FIXED) { i -> seq[indices[i]].clone() }
        }

        // 길이가 짧으면 Zero Padding
        val F = seq.firstOrNull()?.size ?: F_FIXED
        val out = Array(T_FIXED) { FloatArray(F) }
        for (i in 0 until L) System.arraycopy(seq[i], 0, out[i], 0, F)
        return out
    }


    private fun readLabels(context: Context, assetName: String): List<String> {
        val text = context.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }

        runCatching {
            val arr = JSONArray(text)
            val list = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) list.add(arr.getString(i))
            if (list.isNotEmpty()) return list
        }

        runCatching {
            val obj = JSONObject(text)
            val keys = obj.keys().asSequence().toList().sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
            val maxIdx = keys.maxOfOrNull { it.toIntOrNull() ?: -1 } ?: -1
            require(maxIdx >= 0) { "invalid label object" }
            val list = MutableList(maxIdx + 1) { "" }
            for (k in keys) {
                val idx = k.toIntOrNull() ?: continue
                list[idx] = obj.getString(k)
                list[idx] = obj.getString(k) // 중복 호출 제거
            }
            return list.map { if (it.isBlank()) "__EMPTY__" else it }
        }

        throw JSONException("Unsupported label json format for $assetName")
    }

    private fun softmax(x: FloatArray): FloatArray {
        var mx = Float.NEGATIVE_INFINITY
        for (v in x) if (v > mx) mx = v
        var sum = 0.0
        val out = FloatArray(x.size)
        for (i in x.indices) {
            val e = exp((x[i] - mx).toDouble())
            out[i] = e.toFloat()
            sum += e
        }
        if (sum > 0.0) {
            val inv = (1.0 / sum).toFloat()
            for (i in out.indices) out[i] *= inv
        }
        return out
    }
}