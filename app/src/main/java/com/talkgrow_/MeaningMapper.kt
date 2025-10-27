package com.talkgrow_

import java.util.ArrayDeque

class MeaningMapper {

    /**
     * 최근 토큰 덱(최대 3개 정도)을 보고 문장을 만들 수 있으면 반환, 아니면 null.
     * - 단일 인사류는 바로 문장으로 확정
     * - 조합 규칙(버스/곳/가깝다 등) 매칭
     * - 그 외에는 중복 제거 후 기본 문장 생성
     */
    fun tryAssemble(last: ArrayDeque<String>): String? {
        if (last.isEmpty()) return null
        val seq = last.joinToString(",")

        // 1) 인사/감사류는 단독/우세시 즉시 문장화
        if (last.contains("안녕하세요")) return "안녕하세요."
        if (last.contains("감사합니다")) return "감사합니다."
        if (last.contains("반갑다")) return "반갑습니다."

        // 2) 조합 규칙 (필요 시 추가)
        if (seq.contains("버스") && seq.contains("곳") && seq.contains("가깝다")) {
            return "버스 정류장이 가까워요."
        }

        // 3) 길 단독/우세 → 문장화(데모 off일 때 ‘길’만 뜨는 문제 방지)
        if (last.all { it == "길" }) return "길이 보입니다."

        // 4) 기본: 중복 제거 후 결합
        val merged = dedup(last.toList())
        if (merged.isNotEmpty()) return merged.joinToString(" ") + "."

        return null
    }

    private fun dedup(list: List<String>): List<String> {
        val out = ArrayList<String>(list.size)
        var prev: String? = null
        for (t in list) {
            if (t != prev) out.add(t)
            prev = t
        }
        return out
    }
}
