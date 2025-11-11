// android_app/src/main/java/com/talkgrow_/NormParams.kt
package com.talkgrow_

import org.json.JSONObject
import com.talkgrow_.inference.TFLiteSignInterpreter

/** 선택 구현: 인터프리터가 정규화 파라미터를 제공할 때 이 인터페이스를 구현하세요. */
interface NormParamsProvider {
    val normMeans: FloatArray?           // 길이 134 예상
    val normStds: FloatArray?            // 길이 134 예상
    val normConfigJson: String?          // JSON 문자열(선택)
}

fun TFLiteSignInterpreter.getMeansOrNull(): FloatArray? =
    (this as? NormParamsProvider)?.normMeans

fun TFLiteSignInterpreter.getStdsOrNull(): FloatArray? =
    (this as? NormParamsProvider)?.normStds

fun TFLiteSignInterpreter.getNormConfigOrNull(): JSONObject? =
    (this as? NormParamsProvider)?.normConfigJson?.let { runCatching { JSONObject(it) }.getOrNull() }
