package com.talkgrow_.util

import java.util.ArrayDeque

class FrameWindow(private val T: Int, private val D: Int) {
    private val q = ArrayDeque<FloatArray>(T)

    /** 1프레임(feature 길이 D) 추가 */
    fun add(frameD: FloatArray) {
        require(frameD.size == D) { "Expected D=$D, got ${frameD.size}" }
        if (q.size == T) q.removeFirst()
        q.addLast(frameD)
    }

    fun size(): Int = q.size
    fun isReady(): Boolean = q.size == T

    /** (1, T, D) 플랫 배열로 반환 (모델 입력용) */
    fun to1xTxD(): Array<FloatArray> {
        val out = FloatArray(T * D)
        var idx = 0
        for (row in q) {
            System.arraycopy(row, 0, out, idx, D)
            idx += D
        }
        return arrayOf(out)
    }

    fun clear() { q.clear() }
}
