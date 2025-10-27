package com.talkgrow_.network

import com.talkgrow_.model.NlpRequest
import com.talkgrow_.model.NlpResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("gpt_preprocess")  // Flask 서버의 API 엔드포인트와 맞춰야 함
    suspend fun sendText(
        @Body request: NlpRequest
    ): Response<NlpResponse>
}
