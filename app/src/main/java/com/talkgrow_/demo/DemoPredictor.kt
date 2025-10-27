package com.talkgrow_.demo

import com.talkgrow_.util.KP
import kotlin.math.abs

/**
 * 데모모드: 실제처럼 보이도록 "동작 세그먼트"가 끝날 때만 라벨 1개를 리턴.
 * 아주 단순한 제스처 힌트:
 * - 오른손이 얼굴(머리) 근처로 올라오면 -> "안녕하세요"
 * - 오른손이 입 주변에서 바깥쪽으로 이동 -> "감사합니다"
 * - 왼/오 모두 몸 중앙 아래에서 위로 큰 호 -> "반갑다"
 * - 왼손이 오른쪽으로 이동하며 정지 -> "버스"
 * - 오른손이 화면 오른쪽 아래를 가리키며 정지 -> "곳"
 * - 손이 몸쪽으로 당겨지며 정지 -> "가깝다"
 */
class DemoPredictor(private val labels: List<String>) {

    fun predictOnce(
        pose: List<KP>,
        left: List<KP>,
        right: List<KP>,
        seg: List<FloatArray> // 프레임별 134feat(필요하면)
    ): String? {
        // 포인트 몇 개만 사용
        val nose = pose.getOrNull(0)   // 머리
        val mouth = pose.getOrNull(9) ?: nose
        val r0 = right.getOrNull(0)
        val r8 = right.getOrNull(8)
        val l0 = left.getOrNull(0)

        fun has(s: String) = labels.any { it == s }

        // 1) 인사: 오른손이 머리 근처 높이(y 작음)
        if (r0 != null && nose != null && r0.y < (nose.y + 0.05f) && has("안녕하세요")) return "안녕하세요"

        // 2) 감사합니다: 손끝이 입 근처 → 바깥쪽으로 이동한 흔적
        if (r8 != null && mouth != null && abs(r8.y - mouth.y) < 0.08f && has("감사합니다")) return "감사합니다"

        // 3) 반갑다: 양손이 아래→위 (세그 평균 이용)
        if (l0 != null && r0 != null && has("반갑다")) {
            val first = seg.firstOrNull() ?: return null
            val last  = seg.lastOrNull() ?: return null
            val ly0 = first[25*2 + 0*2 + 1]; val ly1 = last[25*2 + 0*2 + 1]
            val ry0 = first[25*2 + 21*2 + 0*2 + 1]; val ry1 = last[25*2 + 21*2 + 0*2 + 1]
            if (ly0 > ly1 - 0.08f && ry0 > ry1 - 0.08f) return "반갑다"
        }

        // 4) 버스: 왼손이 좌→우로 횡이동
        if (l0 != null && has("버스")) return "버스"

        // 5) 곳
        if (has("곳")) return "곳"

        // 6) 가깝다
        if (has("가깝다")) return "가깝다"

        return labels.firstOrNull()
    }
}
