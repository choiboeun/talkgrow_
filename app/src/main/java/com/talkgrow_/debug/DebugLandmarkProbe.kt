package com.talkgrow_.debug

import android.content.Context
import android.os.Environment
import android.util.Log
import com.talkgrow_.util.KP
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * 프레임 단위로 원본(프리뷰 기준) 좌표와 전처리된([0..1]) 좌표를 시간기반 매칭해서
 * 오프셋(px 및 정규화 단위)을 계산/로그/CSV로 저장.
 * - 로그 태그: SLT-PROBE
 * - CSV 경로: Android/data/<pkg>/files/Documents/slt_debug/probe_*.csv
 */
class DebugLandmarkProbe(
    private val ctx: Context,
    previewWidthPx: Int,
    previewHeightPx: Int
) {

    data class RawFrame(
        val ts: Long,
        val pose: List<KP>?,   // 정규화 0..1 (프리뷰 기준)
        val left: List<KP>?,
        val right: List<KP>?
    )

    data class PrepFrame(
        val ts: Long,
        val pose: List<KP>?,
        val left: List<KP>?,
        val right: List<KP>?
    )

    private val rawMap = ConcurrentHashMap<Long, RawFrame>()
    private val prepMap = ConcurrentHashMap<Long, PrepFrame>()

    @Volatile private var previewW = previewWidthPx
    @Volatile private var previewH = previewHeightPx

    private val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val outFile: File by lazy {
        val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "slt_debug")
        dir.mkdirs()
        File(dir, "probe_${sdf.format(System.currentTimeMillis())}.csv")
    }
    private var writer: FileWriter? = null

    fun updatePreviewSize(w: Int, h: Int) {
        previewW = w.coerceAtLeast(1)
        previewH = h.coerceAtLeast(1)
    }

    fun onRaw(ts: Long, pose: List<KP>?, left: List<KP>?, right: List<KP>?) {
        rawMap[ts] = RawFrame(ts, pose, left, right)
        tryJoin(ts)
    }

    fun onPreprocessed(ts: Long, pose: List<KP>?, left: List<KP>?, right: List<KP>?) {
        prepMap[ts] = PrepFrame(ts, pose, left, right)
        tryJoin(ts)
    }

    private fun tryJoin(ts: Long) {
        val raw = rawMap[ts] ?: return
        val pre = prepMap[ts] ?: return

        val report = compareFrames(raw, pre)
        logAndWrite(ts, report)

        rawMap.remove(ts)
        prepMap.remove(ts)
    }

    data class Report(
        val count: Int,
        val meanPx: Float,
        val maxPx: Float,
        val meanNorm: Float,
        val maxNorm: Float
    )

    private fun compareFrames(raw: RawFrame, pre: PrepFrame): Report {
        var sumPx = 0f
        var maxPx = 0f
        var sumNm = 0f
        var maxNm = 0f
        var cnt = 0

        fun accum(rawList: List<KP>?, preList: List<KP>?) {
            if (rawList == null || preList == null) return
            val n = minOf(rawList.size, preList.size)
            for (i in 0 until n) {
                val r = rawList[i]
                val p = preList[i]
                val dxN = r.x - p.x
                val dyN = r.y - p.y
                val dn = sqrt(dxN*dxN + dyN*dyN)
                sumNm += dn
                if (dn > maxNm) maxNm = dn

                val dxP = dxN * previewW
                val dyP = dyN * previewH
                val dp = sqrt(dxP*dxP + dyP*dyP)
                sumPx += dp
                if (dp > maxPx) maxPx = dp

                cnt++
            }
        }

        accum(raw.pose, pre.pose)
        accum(raw.left, pre.left)
        accum(raw.right, pre.right)

        val meanPx = if (cnt > 0) sumPx / cnt else 0f
        val meanNm = if (cnt > 0) sumNm / cnt else 0f
        return Report(cnt, meanPx, maxPx, meanNm, maxNm)
    }

    private fun logAndWrite(ts: Long, r: Report) {
        Log.i(
            "SLT-PROBE",
            "ts=$ts points=${r.count} meanPx=${"%.2f".format(r.meanPx)} maxPx=${"%.2f".format(r.maxPx)} " +
                    "meanN=${"%.4f".format(r.meanNorm)} maxN=${"%.4f".format(r.maxNorm)}"
        )
        try {
            if (writer == null) {
                writer = FileWriter(outFile, true).apply {
                    write("timestamp,points,mean_px,max_px,mean_norm,max_norm\n")
                }
            }
            writer?.apply {
                write("$ts,${r.count},${"%.3f".format(r.meanPx)},${"%.3f".format(r.maxPx)}," +
                        "${"%.5f".format(r.meanNorm)},${"%.5f".format(r.maxNorm)}\n")
                flush()
            }
        } catch (t: Throwable) {
            Log.w("SLT-PROBE", "write csv failed", t)
        }
    }

    fun close() {
        runCatching { writer?.close() }
        writer = null
    }
}
