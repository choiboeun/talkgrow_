package com.talkgrow_.inference

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil

class V8SignModel(context: Context) {
    private val interpreter: Interpreter

    init {
        val mm = FileUtil.loadMappedFile(context, "model_v8_focus16_284_fp16.tflite")
        val options = Interpreter.Options().apply {
            // 스레드/NNAPI 등 기존 설정 유지
            setNumThreads(4)
        }
        interpreter = Interpreter(mm, options)
    }

    /**
     * @param input [91][134]
     * @return 로짓(클래스 수) FloatArray
     */
    fun run(input: Array<FloatArray>): FloatArray {
        val batch = arrayOf(input) // [1,91,134]
        // 출력 크기: 라벨 수 (예: 284)
        val out = Array(1) { FloatArray(284) { 0f } }
        interpreter.run(batch, out)
        return out[0]
    }

    fun close() {
        interpreter.close()
    }
}
