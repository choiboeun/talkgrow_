// File: app/src/main/java/com/talkgrow_/HeuristicGestureDetector.kt
package com.talkgrow_

import android.os.SystemClock
import com.talkgrow_.util.KP
import kotlin.math.abs
import kotlin.math.hypot

class HeuristicGestureDetector(
    private val coolDownMs: Long = 1200L,
    private val windowMs: Long = 900L
) {
    data class Pt(val x: Float, val y: Float)
    data class Sample(val ts: Long, val rWrist: Pt?, val lWrist: Pt?, val rIndex: Pt?, val lIndex: Pt?)
    private val buf = ArrayDeque<Sample>()

    private var lastEmitTs = 0L
    private var warmUpUntil: Long = 0L
    private var handsStable = false

    /** MP Hands: 0=wrist, 8=index tip */
    private fun kp(list: List<KP>?, idx: Int): Pt? {
        if (list == null || idx !in list.indices) return null
        val p = list[idx]; return Pt(p.x, p.y)
    }

    fun push(ts: Long, left: List<KP>?, right: List<KP>?): String? {
        val s = Sample(
            ts = ts,
            rWrist = kp(right, 0), rIndex = kp(right, 8),
            lWrist = kp(left, 0),  lIndex = kp(left, 8)
        )
        buf.addLast(s)
        trim(ts)

        // 손이 들어온 직후 500ms 워밍업
        val handsNow = (s.rWrist != null || s.lWrist != null)
        if (handsNow && !handsStable) {
            handsStable = true
            warmUpUntil = ts + 500L
        } else if (!handsNow) {
            handsStable = false
        }
        if (!handsStable || ts < warmUpUntil) return null

        // 디바운스
        if (SystemClock.uptimeMillis() - lastEmitTs < coolDownMs) return null

        detectHello()?.let { return emit("안녕하세요") }
        detectThanks()?.let { return emit("감사합니다") }
        detectGlad()?.let { return emit("반갑다") }
        detectBus()?.let { return emit("버스") }
        detectHere()?.let { return emit("곳") }
        detectNear()?.let { return emit("가깝다") }

        return null
    }

    private fun emit(label: String): String {
        lastEmitTs = SystemClock.uptimeMillis()
        return label
    }

    private fun trim(now: Long) {
        val cut = now - windowMs
        while (buf.isNotEmpty() && buf.first().ts < cut) buf.removeFirst()
    }

    private fun dist(a: Pt?, b: Pt?): Float {
        if (a == null || b == null) return 999f
        return hypot(a.x - b.x, a.y - b.y)
    }

    // ===== 규칙들 =====

    // 오른손 좌우 파동 + 최소속도 (안녕하세요)
    private fun detectHello(): Boolean {
        val xs = buf.mapNotNull { it.rWrist?.x }
        val ts = buf.map { it.ts }
        if (xs.size < 8) return false

        val minX = xs.minOrNull()!!; val maxX = xs.maxOrNull()!!
        val amp = maxX - minX
        if (amp < 0.20f) return false // 진폭 강화

        // 왕복 2회 이상 + 구간 속도 체크(연속 프레임 x 이동량 평균)
        var flips = 0
        var prevCentered = xs.first() - xs.average().toFloat()
        var speedAcc = 0f; var speedN = 0
        for (i in 1 until xs.size) {
            val cur = xs[i] - xs.average().toFloat()
            if (prevCentered * cur < 0) flips++
            val dt = (ts[i] - ts[i-1]).coerceAtLeast(1).toFloat()
            speedAcc += abs(xs[i] - xs[i-1]) / dt
            speedN++
            prevCentered = cur
        }
        val avgSpeed = speedAcc / speedN.coerceAtLeast(1)
        return flips >= 2 && avgSpeed > 0.0006f
    }

    // 위→아래 빠른 낙하 (감사합니다)
    private fun detectThanks(): Boolean {
        val r = buf.mapNotNull { it.rWrist?.y }
        val ts = buf.map { it.ts }
        if (r.size < 6) return false
        val start = r.first(); val end = r.last()
        val drop = end - start
        if (!(start < 0.35f && drop > 0.18f)) return false

        // 빠른 낙하: 최대 프레임 간 속도
        var maxVy = 0f
        for (i in 1 until r.size) {
            val vy = (r[i] - r[i-1]) / (ts[i] - ts[i-1]).coerceAtLeast(1)
            maxVy = maxOf(maxVy, vy)
        }
        return maxVy > 0.0022f
    }

    // 손뼉(검지끝 거리 급감→급증) (반갑다)
    private fun detectGlad(): Boolean {
        val d = buf.map { dist(it.rIndex, it.lIndex) }
        if (d.size < 8) return false
        val minD = d.minOrNull()!!
        val maxD = d.maxOrNull()!!
        // 가까워졌다가(<=0.06) 다시 벌어짐(>=0.16) + 최근 프레임은 증가 구간
        return (minD <= 0.06f && maxD >= 0.16f && d.last() > minD + 0.06f)
    }

    // 양손 같은 높이 + 좌우로 크게 벌어짐 (버스)
    private fun detectBus(): Boolean {
        val last = buf.last()
        val lw = last.lWrist; val rw = last.rWrist
        if (lw == null || rw == null) return false
        val yDiff = abs(lw.y - rw.y)
        val xGap = abs(lw.x - rw.x)
        return yDiff < 0.08f && xGap > 0.52f
    }

    // 한 손 가리킴 (곳/여기)
    private fun detectHere(): Boolean {
        // 오른손만 있을 때, index.y > wrist.y + margin 이 다수 프레임
        val frames = buf.mapNotNull { s ->
            if (s.lWrist == null && s.lIndex == null && s.rIndex != null && s.rWrist != null)
                (s.rIndex!!.y > s.rWrist!!.y + 0.08f) else null
        }
        if (frames.size < 6) return false
        return frames.count { it } >= (frames.size * 0.7f).toInt()
    }

    // 양손 검지 끝이 근접 유지 (가깝다)
    private fun detectNear(): Boolean {
        val nearCnt = buf.count { dist(it.rIndex, it.lIndex) < 0.08f }
        return nearCnt >= 6
    }
}
