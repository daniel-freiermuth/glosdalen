package com.glosdalen.app.backend.deepl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

@Serializable
data class DeepLTranslateRequest(
    val text: List<String>,
    @SerialName("source_lang")
    val sourceLang: String,
    @SerialName("target_lang")
    val targetLang: String,
    @SerialName("model_type")
    val modelType: String? = null,
    val formality: String? = null,
    @SerialName("split_sentences")
    val splitSentences: String? = null,
    val context: String? = null
)

@Serializable
data class DeepLTranslateResponse(
    val translations: List<DeepLTranslation>
)

@Serializable
data class DeepLTranslation(
    @SerialName("detected_source_language")
    val detectedSourceLanguage: String,
    val text: String
)
