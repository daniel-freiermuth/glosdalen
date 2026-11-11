package com.glosdalen.app.backend.elevenlabs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * ElevenLabs API Service for Text-to-Speech functionality.
 * 
 * Documentation: https://elevenlabs.io/docs/api-reference/text-to-speech
 */
interface ElevenLabsApiService {
    
    /**
     * Convert text to speech and receive audio as a stream.
     * 
     * @param voiceId The voice ID to use for synthesis
     * @param apiKey ElevenLabs API key (passed as xi-api-key header)
     * @param request The TTS request body
     * @return Raw audio bytes (mpeg format by default)
     */
    @POST("v1/text-to-speech/{voice_id}")
    suspend fun textToSpeech(
        @Path("voice_id") voiceId: String,
        @Header("xi-api-key") apiKey: String,
        @Body request: TextToSpeechRequest
    ): Response<ResponseBody>
    
    /**
     * List the voices available to the account (v2 API, paginated).
     *
     * @param apiKey ElevenLabs API key
     * @param pageSize Number of voices per page (max 100)
     * @param nextPageToken Token from the previous page's [VoicesResponse.nextPageToken]; null for the first page
     */
    @GET("v2/voices")
    suspend fun getVoices(
        @Header("xi-api-key") apiKey: String,
        @Query("page_size") pageSize: Int = 100,
        @Query("next_page_token") nextPageToken: String? = null
    ): Response<VoicesResponse>
    
    /**
     * Get available models.
     * 
     * @param apiKey ElevenLabs API key
     * @return List of available models
     */
    @GET("v1/models")
    suspend fun getModels(
        @Header("xi-api-key") apiKey: String
    ): Response<List<ElevenLabsModel>>
    
    /**
     * Get user subscription info (useful for validating API key).
     * 
     * @param apiKey ElevenLabs API key
     * @return User subscription information
     */
    @GET("v1/user/subscription")
    suspend fun getUserSubscription(
        @Header("xi-api-key") apiKey: String
    ): Response<SubscriptionInfo>
}

@Serializable
data class TextToSpeechRequest(
    val text: String,
    @SerialName("model_id")
    val modelId: String = "eleven_multilingual_v2",
    @SerialName("language_code")
    val languageCode: String? = null,
    @SerialName("voice_settings")
    val voiceSettings: VoiceSettings = VoiceSettings()
)

@Serializable
data class VoiceSettings(
    val stability: Float = 0.5f,
    @SerialName("similarity_boost")
    val similarityBoost: Float = 0.75f,
    val style: Float = 0f,
    @SerialName("use_speaker_boost")
    val useSpeakerBoost: Boolean = true
)

@Serializable
data class VoicesResponse(
    val voices: List<Voice>,
    @SerialName("has_more")
    val hasMore: Boolean = false,
    @SerialName("total_count")
    val totalCount: Int? = null,
    @SerialName("next_page_token")
    val nextPageToken: String? = null
)

@Serializable
data class Voice(
    @SerialName("voice_id")
    val voiceId: String,
    val name: String,
    val category: String? = null,
    val description: String? = null,
    @SerialName("preview_url")
    val previewUrl: String? = null,
    val labels: Map<String, String>? = null,
    @SerialName("verified_languages")
    val verifiedLanguages: List<VerifiedVoiceLanguage>? = null
) {
    /**
     * Languages ElevenLabs has verified for this voice, as upper-case codes (e.g. "EN, SV"),
     * or null when the API reports none.
     */
    fun verifiedLanguageSummary(): String? = verifiedLanguages
        ?.map { it.language.uppercase() }
        ?.distinct()
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString(", ")
}

@Serializable
data class VerifiedVoiceLanguage(
    val language: String,
    @SerialName("model_id")
    val modelId: String? = null,
    val accent: String? = null,
    val locale: String? = null
)

@Serializable
data class ElevenLabsModel(
    @SerialName("model_id")
    val modelId: String,
    val name: String,
    val description: String? = null,
    @SerialName("can_be_finetuned")
    val canBeFinetuned: Boolean = false,
    @SerialName("can_do_text_to_speech")
    val canDoTextToSpeech: Boolean = true,
    @SerialName("can_do_voice_conversion")
    val canDoVoiceConversion: Boolean = false,
    val languages: List<ModelLanguage>? = null
)

@Serializable
data class ModelLanguage(
    @SerialName("language_id")
    val languageId: String,
    val name: String
)

@Serializable
data class SubscriptionInfo(
    val tier: String,
    @SerialName("character_count")
    val characterCount: Int,
    @SerialName("character_limit")
    val characterLimit: Int,
    @SerialName("can_extend_character_limit")
    val canExtendCharacterLimit: Boolean = false,
    @SerialName("allowed_to_extend_character_limit")
    val allowedToExtendCharacterLimit: Boolean = false
)
