package com.talkgrow_.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.MappedByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class RealModelRunner(
    context: Context,
    modelAssetPath: String,
    labelsAssetPath: String
) {
    private val model: MappedByteBuffer = FileUtil.loadMappedFile(context, modelAssetPath)
    private val options = Interpreter.Options().apply { setNumThreads(4) }
    private val tflite = Interpreter(model, options)
    val labels: List<String> = FileUtil.loadLabels(context, labelsAssetPath)
    private val outSize = labels.size

    private val exec = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(false)
    @Volatile private var busy = false

    fun close() {
        if (closed.compareAndSet(false, true)) {
            exec.shutdownNow()
            tflite.close()
        }
    }

    fun runAsync(input91x134: FloatArray, onDone: (String?) -> Unit) {
        if (closed.get()) { onDone(null); return }
        if (busy) { onDone(null); return }
        busy = true
        exec.execute {
            try {
                if (closed.get()) { onDone(null); return@execute }
                val inBuf = Array(1) { Array(Preprocess.T) { FloatArray(Preprocess.F) } }
                var k = 0
                for (i in 0 until Preprocess.T) for (j in 0 until Preprocess.F) {
                    inBuf[0][i][j] = input91x134[k++]
                }
                val out = Array(1) { FloatArray(outSize) }
                tflite.run(inBuf, out)
                val probs = out[0]
                var bi = 0; var bv = probs[0]
                for (i in 1 until probs.size) if (probs[i] > bv) { bv = probs[i]; bi = i }
                onDone(labels.getOrNull(bi))
            } catch (_: Throwable) {
                onDone(null)
            } finally {
                busy = false
            }
        }
    }
}
