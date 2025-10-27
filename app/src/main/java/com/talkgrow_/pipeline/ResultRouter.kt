// app/src/main/java/com/talkgrow_/pipeline/ResultRouter.kt
package com.talkgrow_.pipeline

/**
 * 컴파일을 깨지 않도록 최소 기능만 제공하는 라우터.
 * - topIndex(probs): 가장 높은 확률의 인덱스
 * - labelOf(index): 인덱스로 라벨을 안전하게 꺼냄
 *
 * 현재 앱 플로우(CameraActivity)는 ResultRouter를 사용하지 않지만,
 * 기존 파일이 남아 있어 컴파일 에러를 내고 있으므로 이 스텁으로 대체합니다.
 */
class ResultRouter(
    private val labels: List<String>
) {
    fun topIndex(probs: FloatArray): Int {
        var best = 0
        var bestVal = Float.NEGATIVE_INFINITY
        for (i in probs.indices) {
            val v = probs[i]
            if (v > bestVal) {
                bestVal = v
                best = i
            }
        }
        return best
    }

    fun labelOf(index: Int): String =
        if (index in labels.indices) labels[index] else ""
}
