package com.talkgrow_

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

/**
 * 작성자: 조경주, 최보은
 * 작성일: 2025-08-03
 *
 * 📘 기능 설명:
 *  - EditText 클릭 시 performClick() 누락으로 인한 접근성(Accessibility) 경고 제거용 커스텀 EditText
 *  - 접근성 도구(TalkBack 등)가 해당 뷰를 클릭했을 때 정상적으로 클릭 이벤트를 인식하도록 보완
 *
 * 🧩 사용 예시 (XML):
 *  <com.talkgrow_.CustomEditText
 *      android:id="@+id/inputField"
 *      android:layout_width="match_parent"
 *      android:layout_height="wrap_content"
 *      android:hint="메시지를 입력하세요" />
 *
 * ⚙️ 추가 기능:
 *  - 향후 입력창 포커스/자동완성 제어, 키보드 동작 등 특화 로직 추가 가능
 */
class CustomEditText : AppCompatEditText {

    /** 생성자 #1 - 코드에서 직접 생성할 때 사용 */
    constructor(context: Context) : super(context)

    /** 생성자 #2 - XML에서 속성과 함께 사용 시 */
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    /** 생성자 #3 - XML + 스타일 지정 시 */
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
            : super(context, attrs, defStyleAttr)

    /**
     * 클릭 이벤트 수행 함수:
     * - 접근성 이벤트에서 performClick 호출 누락 시 경고가 발생하는 문제 방지
     * - super.performClick() 호출 후 true 반환
     */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
