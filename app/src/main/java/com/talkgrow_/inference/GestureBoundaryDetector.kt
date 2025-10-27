package com.talkgrow_.inference

/**
 * 아주 단순한 상태기계:
 * - 기본은 IDLE
 * - hasTracking=true 가 N프레임 이상 지속되면 ACTIVE 진입
 * - hasTracking=false 가 M프레임 이상 지속되면 IDLE 복귀
 * - (선택) 움직임 에너지 임계값도 같이 보되, 너무 낮으면 tracking 기준만으로도 ACTIVE 허용
 */
class GestureBoundaryDetector(
    private val startThreshold: Float = 0.02f,
    private val endThreshold: Float = 0.01f,
    private val minStartFrames: Int = 2,
    private val minEndFrames: Int = 6
) {
    enum class State { IDLE, ACTIVE }

    data class UpdateResult(
        val startedAtMs: Long? = null,
        val endedAtMs: Long? = null
    )

    private var state: State = State.IDLE
    private var startCnt = 0
    private var endCnt = 0

    fun update(energy: Float, hasTracking: Boolean, tsMs: Long): UpdateResult {
        var started: Long? = null
        var ended: Long? = null

        when (state) {
            State.IDLE -> {
                // tracking이 들어오고 에너지가 startThreshold 근처면 카운트 업
                // 에너지가 아주 작아도(정지자세 시작) 카운트는 올려 ACTIVE 진입을 쉽게 함
                if (hasTracking && (energy >= startThreshold || energy >= 0f)) {
                    startCnt++
                    if (startCnt >= minStartFrames) {
                        state = State.ACTIVE
                        started = tsMs
                        startCnt = 0
                        endCnt = 0
                    }
                } else {
                    startCnt = 0
                }
            }
            State.ACTIVE -> {
                // tracking이 끊기거나 에너지가 충분히 낮아지면 종료 카운트
                if (!hasTracking || energy <= endThreshold) {
                    endCnt++
                    if (endCnt >= minEndFrames) {
                        state = State.IDLE
                        ended = tsMs
                        startCnt = 0
                        endCnt = 0
                    }
                } else {
                    endCnt = 0
                }
            }
        }
        return UpdateResult(started, ended)
    }

    fun currentState(): State = state
}
