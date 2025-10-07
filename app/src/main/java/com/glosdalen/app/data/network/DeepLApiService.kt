package com.glosdalen.app.data.network

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface DeepLApiService {
    @POST("v2/translate")
    suspend fun translate(
        @Header("Authorization") authKey: String,
        @Body request: DeepLTranslateRequest
    ): DeepLTranslateResponse
}

data class DeepLTranslateRequest(
    val text: List<String>,
    val source_lang: String,
    val target_lang: String,
    val model_type: String? = null,
    val formality: String? = null,
    val split_sentences: String? = null,
    val context: String? = null
)

data class DeepLTranslateResponse(
    val translations: List<DeepLTranslation>
)

data class DeepLTranslation(
    val detected_source_language: String,
    val text: String
)
