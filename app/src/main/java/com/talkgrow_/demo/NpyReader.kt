package com.talkgrow_.demo

import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object NpyReader {
    data class Npy(val shape: IntArray, val data: FloatArray)

    fun loadFloat32FromFile(path: String): Npy =
        FileInputStream(path).use { ins -> loadFloat32(ins) }

    private fun loadFloat32(ins: InputStream): Npy {
        val hdr = readHeader(ins)
        require(hdr.dtype == "<f4") { "지원하지 않는 dtype: ${hdr.dtype}, 필요: <f4" }
        require(hdr.shape.contentEquals(intArrayOf(91, 134))) {
            "규격 불일치: ${hdr.shape.contentToString()} (필요: [91,134])"
        }
        val count = hdr.shape.fold(1) { acc, v -> acc * v }
        val bytes = ins.readBytes()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val arr = FloatArray(count)
        bb.asFloatBuffer().get(arr)
        return Npy(hdr.shape, arr)
    }

    private data class Header(val dtype: String, val shape: IntArray)

    private fun readHeader(ins: InputStream): Header {
        val magic = ByteArray(6); ins.read(magic)
        require(String(magic) == "\u0093NUMPY") { "NPY magic mismatch" }
        val vMajor = ins.read(); val vMinor = ins.read()
        val lenBytes = ByteArray(if (vMajor > 1) 4 else 2); ins.read(lenBytes)
        val hdrLen = if (vMajor > 1)
            ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).int
        else
            ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        val header = ByteArray(hdrLen); ins.read(header)
        val s = String(header)

        val dtype = "'descr': '([^']+)'".toRegex().find(s)?.groupValues?.get(1) ?: error("dtype 없음")
        val shapeStr = "'shape': \\(([^)]*)\\)".toRegex().find(s)?.groupValues?.get(1) ?: error("shape 없음")
        val shape = shapeStr.split(",").mapNotNull { it.trim().toIntOrNull() }.toIntArray()
        return Header(dtype, shape)
    }
}
