package com.talkgrow_

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.util.Log

class PreprocessResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preprocess_result)

        val resultText = intent.getStringExtra("processed_text") ?: "결과가 없습니다."
        val resultTextView = findViewById<TextView>(R.id.text_preprocessed_result)
        resultTextView.text = resultText

        // 디버깅용 로그
        Log.d("PreprocessResultActivity", "processed_text: $resultText")
    }
}
