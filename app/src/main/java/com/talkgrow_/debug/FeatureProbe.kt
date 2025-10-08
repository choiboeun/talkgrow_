// app/src/main/java/com/talkgrow_/debug/FeatureProbe.kt
package com.talkgrow_.debug

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.OutputStream

object FeatureProbe {
    private const val TAG = "FeatureProbe"

    // ... (기존 checkAndDump, quickLayoutSanity 그대로 두세요)

    /** flat(길이 = seqLen*featDim)을 T행,F열 CSV 문자열로 변환 */
    fun flatToCsv(flat: FloatArray, seqLen: Int, featDim: Int): String {
        require(flat.size >= seqLen * featDim) {
            "flat.size(${flat.size}) < seqLen*featDim(${seqLen * featDim})"
        }
        val sb = StringBuilder(seqLen * featDim * 8)
        var off = 0
        repeat(seqLen) {
            for (i in 0 until featDim) {
                if (i > 0) sb.append(',')
                sb.append(flat[off + i])
            }
            sb.append('\n')
            off += featDim
        }
        return sb.toString()
    }

    /**
     * CSV 문자열을 **Downloads** 폴더에 저장. 성공 시 true.
     * 호출 형태는 SafeImageAnalyzer와 동일: saveCsvToDownloads(context, fileName, csv)
     */
    fun saveCsvToDownloads(context: Context, fileName: String, csv: String): Boolean {
        return try {
            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(collection, values) ?: run {
                Log.e(TAG, "resolver.insert returned null")
                return false
            }

            resolver.openOutputStream(uri)?.use { os: OutputStream ->
                os.write(csv.toByteArray(Charsets.UTF_8))
                os.flush()
            } ?: run {
                Log.e(TAG, "openOutputStream == null")
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val fin = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(uri, fin, null, null)
            }
            Log.i(TAG, "Saved CSV to Downloads: $uri")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "saveCsvToDownloads failed: ${t.message}", t)
            false
        }
    }
}
