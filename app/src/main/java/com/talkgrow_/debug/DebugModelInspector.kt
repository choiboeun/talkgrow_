package com.talkgrow_.debug

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * 메타데이터 라이브러리 없이도 동작하는 “안정판” 인스펙터.
 * - SHA-256 지문
 * - Interpreter로 입력/출력 텐서 shape/type 확인
 * - 클래스 수(출력 마지막 차원)
 *
 * TAG: TG.DebugModel
 */
object DebugModelInspector {
    private const val TAG = "TG.DebugModel"

    data class Summary(
        val sha256: String,
        val inputShapes: List<IntArray>,
        val inputTypes: List<String>,
        val outputShapes: List<IntArray>,
        val outputTypes: List<String>,
        val numClasses: Int
    )

    fun logModelInfo(context: Context, assetName: String): Summary? {
        return try {
            // 1) 파일 로드 + SHA-256
            val bytes = context.assets.open(assetName).use { it.readAll() }
            val sha = sha256(bytes)

            // 2) Interpreter 로딩 (CPU, XNNPACK)
            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buf.put(bytes); buf.rewind()
            val opts = Interpreter.Options().apply {
                setUseXNNPACK(true)
                setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(6))
            }
            Interpreter(buf, opts).use { itp ->
                val inCnt = itp.inputTensorCount
                val outCnt = itp.outputTensorCount

                val inShapes = (0 until inCnt).map { itp.getInputTensor(it).shape() }
                val inTypes  = (0 until inCnt).map { itp.getInputTensor(it).dataType().name }
                val outShapes = (0 until outCnt).map { itp.getOutputTensor(it).shape() }
                val outTypes  = (0 until outCnt).map { itp.getOutputTensor(it).dataType().name }

                val numClasses = itp.getOutputTensor(0).shape().lastOrNull() ?: -1

                Log.i(TAG, "Model '$assetName' SHA-256=$sha")
                Log.i(TAG, "Inputs($inCnt): shapes=${inShapes.joinToString { it.contentToString() }} types=$inTypes")
                Log.i(TAG, "Outputs($outCnt): shapes=${outShapes.joinToString { it.contentToString() }} types=$outTypes")
                Log.i(TAG, "numClasses (from output[0] last dim) = $numClasses")

                Summary(sha, inShapes, inTypes, outShapes, outTypes, numClasses)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Model inspect failed: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    // ===== helpers =====
    private fun InputStream.readAll(): ByteArray {
        val buf = ByteArray(8 * 1024)
        val bos = ByteArrayOutputStream()
        while (true) {
            val n = this.read(buf)
            if (n <= 0) break
            bos.write(buf, 0, n)
        }
        return bos.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val dig = md.digest(bytes)
        return dig.joinToString("") { "%02x".format(it) }
    }
}
