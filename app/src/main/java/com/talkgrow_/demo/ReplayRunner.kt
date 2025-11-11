package com.talkgrow_.demo

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import com.talkgrow_.util.TTSHelper
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ReplayRunner(
    private val context: Context,
    private val tfliteModelAssetPath: String,
    private val id2wordAssetPath: String?
) : AutoCloseable {

    private var interpreter: Interpreter? = null
    private var id2text: List<String>? = null
    private var tts: TTSHelper? = null

    fun initTTS(ttsHelper: TTSHelper?) { this.tts = ttsHelper }
    fun speak(text: String) { tts?.speak(text) }

    fun loadLabelMapIfNeeded() {
        if (id2text != null || id2wordAssetPath.isNullOrBlank()) return
        val raw = readAssetFully(id2wordAssetPath)
        val s = raw.decodeToString()
        id2text = if (s.trim().startsWith("[")) {
            val arr = JSONArray(s)
            List(arr.length()) { i -> arr.optString(i, null) ?: "" }
        } else {
            val obj = JSONObject(s)
            val maxKey = obj.keys().asSequence().mapNotNull { it.toIntOrNull() }.maxOrNull() ?: -1
            List(maxKey + 1) { i -> obj.optString(i.toString(), null) ?: "" }
        }
    }

    private fun ensureInterpreter() {
        if (interpreter != null) return
        interpreter = Interpreter(loadModelFromAsset(tfliteModelAssetPath))
    }

    /** 입력 .npy(91x134 float32) → (bestId, score, 라벨문자열?) */
    fun runReplayFromAsset(npyAssetPath: String, topK: Int = 5): Triple<Int, Float, String?> {
        return runCatching {
            ensureInterpreter()
            val path = if (npyAssetPath.contains('/')) npyAssetPath else "replay/$npyAssetPath"
            val floats = readNpyFloat32FromAsset(path)

            val bb = ByteBuffer.allocateDirect(4 * 1 * 91 * 134).order(ByteOrder.nativeOrder())
            floats.forEach { bb.putFloat(it) }
            bb.rewind()

            val oShape = interpreter!!.getOutputTensor(0).shape()
            val out = Array(1) { FloatArray(oShape[1]) }
            interpreter!!.run(bb, out)

            val logits = out[0]
            var best = 0
            var bestScore = logits[0]
            for (i in 1 until logits.size) if (logits[i] > bestScore) { best = i; bestScore = logits[i] }

            val text = id2text?.getOrNull(best)?.takeIf { it.isNotBlank() }
            Triple(best, bestScore, text)
        }.getOrElse {
            Triple(-1, 0f, null) // 예외 시 라벨 null (id 출력 금지용)
        }
    }

    override fun close() { runCatching { interpreter?.close() } }

    private fun loadModelFromAsset(assetPath: String): MappedByteBuffer {
        val afd: AssetFileDescriptor = context.assets.openFd(assetPath)
        val input = afd.createInputStream().channel
        return input.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
    }

    private fun readAssetFully(assetPath: String): ByteArray {
        val am: AssetManager = context.assets
        am.open(assetPath).use { inp ->
            val bis = BufferedInputStream(inp)
            val bos = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val r = bis.read(buf)
                if (r <= 0) break
                bos.write(buf, 0, r)
            }
            return bos.toByteArray()
        }
    }

    private fun readNpyFloat32FromAsset(assetPath: String): FloatArray {
        val bytes = readAssetFully(assetPath)
        require(bytes[0] == 0x93.toByte() && String(bytes, 1, 5) == "NUMPY") { "Not a NPY file" }
        val v = bytes[7].toInt()
        val headerLen = if (v == 1)
            ByteBuffer.wrap(bytes, 8, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        else
            ByteBuffer.wrap(bytes, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val headerStart = if (v == 1) 10 else 12
        val headerStr = String(bytes, headerStart, headerLen)
        require(headerStr.contains("'descr': '<f4'") || headerStr.contains("\"descr\": \"<f4\"")) { "Expect <f4" }
        val shapeRegex = Regex("""\(\s*(\d+)\s*,\s*(\d+)\s*\)""")
        val (d0, d1) = shapeRegex.find(headerStr)?.destructured
            ?: throw IllegalArgumentException("NPY header parse failed")
        val rows = d0.toInt(); val cols = d1.toInt()
        require(rows == 91 && cols == 134) { "Expected (91,134), got ($rows,$cols)" }

        val dataOffset = headerStart + headerLen
        val floats = FloatArray(rows * cols)
        val bb = ByteBuffer.wrap(bytes, dataOffset, rows * cols * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in floats.indices) floats[i] = bb.getFloat()
        return floats
    }
}
