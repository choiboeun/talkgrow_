package com.talkgrow_

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

/**
 * 작성자: 조경주, 최보은
 * 작성일: 2025-08-03
 * 기능 설명: EditText 클릭 시 performClick 누락으로 인한 경고 제거용 커스텀 EditText
 *
 * 수정 이력:
 *  -
 *
 * TODO:
 *  - 필요 시 입력창 특화 동작 추가 예정
 */
class CustomEditText(context: Context, attrs: AttributeSet) : AppCompatEditText(context, attrs) {

    // 클릭 이벤트 수행 함수: 접근성 및 경고 제거 목적
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

