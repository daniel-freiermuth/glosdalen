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
     * Get list of available voices using v2 API.
     * Uses a higher page size to fetch more voices and includes all voice types.
     * 
     * @param apiKey ElevenLabs API key
     * @param pageSize Number of voices to fetch (max 100)
     * @return List of available voices including default/premade voices
     */
    @GET("v2/voices")
    suspend fun getVoices(
        @Header("xi-api-key") apiKey: String,
        @Query("page_size") pageSize: Int = 100
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
    // Not from API - added locally to indicate multilingual support
    val languageHint: String? = null
) {
    /**
     * Get a display name that includes language support information.
     */
    fun displayName(): String = when {
        languageHint != null -> "$name ($languageHint)"
        else -> name
    }
    
    companion object {
        /**
         * Default premade multilingual voices from ElevenLabs.
         * All voices support the eleven_multilingual_v2 model and can speak 29+ languages.
         * languageHint indicates which languages the voice performs best in.
         * 
         * Source: https://elevenlabs.io/docs/voices/premade-voices
         */
        val PREMADE_VOICES = listOf(
            // Excellent for most languages - professionally trained multilingual
            Voice("21m00Tcm4TlvDq8ikWAM", "Rachel", "premade", "Calm, American female", languageHint = "EN, ES, FR, DE, IT, PT, PL"),
            Voice("pNInz6obpgDQGcFmaJgB", "Adam", "premade", "Deep, American male", languageHint = "EN, ES, FR, DE, IT, PT"),
            Voice("EXAVITQu4vr4xnSDxMaL", "Bella", "premade", "Soft, American female", languageHint = "EN, ES, FR, IT, PT"),
            Voice("ErXwobaYiN019PkySvjV", "Antoni", "premade", "Well-rounded, American male", languageHint = "EN, ES, FR, DE, IT"),
            Voice("VR6AewLTigWG4xSOukaG", "Arnold", "premade", "Crisp, American male", languageHint = "EN, DE, FR, ES"),
            Voice("TxGEqnHWrfWFTfGW9XjX", "Josh", "premade", "Deep, American male", languageHint = "EN, ES, FR"),
            Voice("AZnzlk1XvdvUeBnXmlld", "Domi", "premade", "Strong, American female", languageHint = "EN, ES, FR"),
            Voice("MF3mGyEYCl7XYWbV9V6O", "Elli", "premade", "Emotional, American female", languageHint = "EN, ES, FR"),
            Voice("yoZ06aMxZJJ28mfd3POQ", "Sam", "premade", "Raspy, American male", languageHint = "EN, ES"),
            
            // Nordic/European accents - excellent for European languages
            Voice("XB0fDUnXU5powFXDhCwa", "Charlotte", "premade", "Swedish female", languageHint = "SV, EN, DE, NL, NO, DA"),
            Voice("onwK4e9ZLuTAKqWW03F9", "Daniel", "premade", "British male", languageHint = "EN, FR, DE, ES, IT"),
            Voice("pFZP5JQG7iQjIQuC4Bku", "Lily", "premade", "British female", languageHint = "EN, FR, DE, ES, IT"),
            Voice("Xb7hH8MSUJpSbSDYk0k2", "Alice", "premade", "British female", languageHint = "EN, FR, DE"),
            Voice("t0jbNlBVZ17f02VDIeMI", "George", "premade", "British male", languageHint = "EN, FR, DE"),
            
            // Good all-around voices
            Voice("XrExE9yKIg1WjnnlVkGX", "Matilda", "premade", "Warm, American female", languageHint = "EN, ES, FR"),
            Voice("bIHbv24MWmeRgasZH58o", "Will", "premade", "Friendly, American male", languageHint = "EN, ES, FR"),
            Voice("cgSgspJ2msm6clMCkdW9", "Jessica", "premade", "Expressive, American female", languageHint = "EN, ES, FR"),
            Voice("cjVigY5qzO86Huf0OWal", "Eric", "premade", "Friendly, American male", languageHint = "EN, ES"),
            Voice("iP95p4xoKVk53GoZ742B", "Chris", "premade", "Casual, American male", languageHint = "EN, ES"),
            Voice("nPczCjzI2devNBz1zQrb", "Brian", "premade", "Deep, American male", languageHint = "EN, ES"),
            Voice("pqHfZKP75CvOlQylNhV4", "Bill", "premade", "Trustworthy, American male", languageHint = "EN"),
            Voice("z9fAnlkpzviPz146aGWa", "Glinda", "premade", "Dramatic, American female", languageHint = "EN"),
        )
    }
}

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
