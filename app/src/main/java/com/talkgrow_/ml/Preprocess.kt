package com.talkgrow_.ml

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.abs

object Preprocess {
    const val F = 134
    const val T = 91

    fun normalizeBugForBug(seq: FloatArray, L: Int): FloatArray {
        val out = seq.copyOf()
        val rxIdx = 11*2; val lxIdx = 12*2
        for (t in 0 until L) {
            val base = t*F
            val rx = out[base+rxIdx]; val ry = out[base+rxIdx+1]
            val lx = out[base+lxIdx]; val ly = out[base+lxIdx+1]
            val d  = max(1e-6f, hypot(lx-rx, ly-ry).toFloat())
            var i = 0
            while (i < F) {
                out[base+i  ] = (out[base+i  ] - rx) / d
                out[base+i+1] = (out[base+i+1] - ry) / d
                i += 2
            }
        }
        return out
    }

    fun toLen91(flatLF: FloatArray, L: Int): FloatArray {
        if (L == T) return flatLF.copyOf()
        val out = FloatArray(T*F)
        if (L > T) {
            for (i in 0 until T) {
                val idx = ((i*(L-1)) / (T-1).toFloat()).toInt()
                System.arraycopy(flatLF, idx*F, out, i*F, F)
            }
        } else {
            System.arraycopy(flatLF, 0, out, 0, L*F)
        }
        return out
    }

    fun motionEnergy(flatLF: FloatArray, L: Int): Float {
        if (L < 2) return 0f
        var sum = 0f
        for (t in 1 until L) {
            val a = (t-1)*F; val b = t*F
            var j = 0
            while (j < F) {
                val dx = flatLF[b+j] - flatLF[a+j]
                val dy = flatLF[b+j+1] - flatLF[a+j+1]
                sum += abs(dx) + abs(dy)
                j += 2
            }
        }
        return sum / ((L-1) * (F/2))
    }
}
