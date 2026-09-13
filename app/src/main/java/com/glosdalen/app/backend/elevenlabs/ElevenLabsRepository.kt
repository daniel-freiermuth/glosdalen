package com.glosdalen.app.backend.elevenlabs

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.glosdalen.app.domain.preferences.ElevenLabsPreferences
import com.glosdalen.app.libs.copilot.util.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Error types for ElevenLabs TTS operations.
 */
sealed class ElevenLabsError : Exception() {
    data object NoApiKey : ElevenLabsError()
    data object InvalidApiKey : ElevenLabsError()
    data object QuotaExceeded : ElevenLabsError()
    data object NetworkError : ElevenLabsError()
    data class ApiError(val code: Int, override val message: String?) : ElevenLabsError()
    data class PlaybackError(override val message: String?) : ElevenLabsError()
}

/**
 * Repository for ElevenLabs TTS functionality.
 * Handles text-to-speech conversion and audio playback.
 */
@Singleton
class ElevenLabsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ElevenLabsApiService,
    private val preferences: ElevenLabsPreferences,
    private val timeProvider: TimeProvider
) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioFile: File? = null
    private val playerLock = Any()
    
    companion object {
        private const val TAG = "ElevenLabsRepository"
        private const val AUDIO_CACHE_DIR = "elevenlabs_audio"
        private const val MAX_CACHE_AGE_DAYS = 30
    }
    
    /**
     * Generate a cache key based on text, language, voice, and model.
     */
    private fun generateCacheKey(text: String, languageCode: String?, voiceId: String, modelId: String): String {
        val input = "$text|$languageCode|$voiceId|$modelId"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Get cached audio file if it exists and is valid.
     */
    private fun getCachedAudioFile(cacheKey: String): File? {
        val cacheDir = File(context.cacheDir, AUDIO_CACHE_DIR)
        val cachedFile = File(cacheDir, "$cacheKey.mp3")
        
        return if (cachedFile.exists() && cachedFile.length() > 0) {
            Log.d(TAG, "Cache HIT for key: $cacheKey")
            cachedFile
        } else {
            Log.d(TAG, "Cache MISS for key: $cacheKey")
            null
        }
    }
    
    /**
     * Clean up old cached audio files.
     */
    private fun cleanupOldCache() {
        try {
            val cacheDir = File(context.cacheDir, AUDIO_CACHE_DIR)
            if (!cacheDir.exists()) return
            
            val cutoffTime = timeProvider.currentTimeMillis() - (MAX_CACHE_AGE_DAYS * 24 * 60 * 60 * 1000L)
            val files = cacheDir.listFiles() ?: return
            
            var deletedCount = 0
            files.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }
            
            if (deletedCount > 0) {
                Log.d(TAG, "Cleaned up $deletedCount old cached audio files")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up cache", e)
        }
    }
    
    /**
     * Check if ElevenLabs is configured with an API key.
     */
    suspend fun isConfigured(): Boolean {
        return preferences.getApiKey().first().isNotBlank()
    }
    
    /**
     * Validate the API key by fetching user subscription info.
     */
    suspend fun validateApiKey(apiKey: String): Result<SubscriptionInfo> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getUserSubscription(apiKey)
            
            when {
                response.isSuccessful -> {
                    response.body()?.let { 
                        Result.success(it)
                    } ?: Result.failure(ElevenLabsError.ApiError(response.code(), "Empty response"))
                }
                response.code() == 401 -> Result.failure(ElevenLabsError.InvalidApiKey)
                response.code() == 429 -> Result.failure(ElevenLabsError.QuotaExceeded)
                else -> Result.failure(ElevenLabsError.ApiError(response.code(), response.message()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate API key", e)
            Result.failure(ElevenLabsError.NetworkError)
        }
    }
    
    /**
     * Get list of available voices.
     */
    suspend fun getVoices(): Result<List<Voice>> {
        val apiKey = preferences.getApiKey().first()
        return getVoices(apiKey)
    }
    
    /**
     * Get list of available voices with explicit API key.
     */
    suspend fun getVoices(apiKey: String): Result<List<Voice>> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getVoices called with apiKey length: ${apiKey.length}, blank: ${apiKey.isBlank()}")
        if (apiKey.isBlank()) {
            Log.d(TAG, "getVoices: API key is blank, returning NoApiKey error")
            return@withContext Result.failure(ElevenLabsError.NoApiKey)
        }
        
        try {
            Log.d(TAG, "getVoices: Making API call with key starting with: ${apiKey.take(8)}...")
            val response = apiService.getVoices(apiKey)
            Log.d(TAG, "getVoices: Response code: ${response.code()}, message: ${response.message()}")
            
            when {
                response.isSuccessful -> {
                    response.body()?.let { 
                        Log.d(TAG, "getVoices: Success, got ${it.voices.size} voices from API, hasMore: ${it.hasMore}, totalCount: ${it.totalCount}")
                        it.voices.forEach { voice ->
                            Log.d(TAG, "getVoices: API Voice: ${voice.name} (${voice.voiceId}), category: ${voice.category}")
                        }
                        
                        // Combine premade voices with API voices, avoiding duplicates
                        val apiVoiceIds = it.voices.map { v -> v.voiceId }.toSet()
                        val premadeVoices = Voice.PREMADE_VOICES.filter { v -> v.voiceId !in apiVoiceIds }
                        val allVoices = premadeVoices + it.voices
                        
                        Log.d(TAG, "getVoices: Combined ${premadeVoices.size} premade + ${it.voices.size} API = ${allVoices.size} total voices")
                        
                        Result.success(allVoices)
                    } ?: Result.success(Voice.PREMADE_VOICES)
                }
                response.code() == 401 -> {
                    // Try to get more details from the error response
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "getVoices: 401 Unauthorized. Error body: $errorBody")
                    Log.e(TAG, "getVoices: API key used (first 8 chars): ${apiKey.take(8)}, length: ${apiKey.length}")
                    
                    // Check if it's a permission issue
                    if (errorBody?.contains("missing_permissions") == true || 
                        errorBody?.contains("voices_read") == true) {
                        Result.failure(ElevenLabsError.ApiError(401, 
                            "API key missing 'voices_read' permission. Please create a new key with full permissions at elevenlabs.io"))
                    } else {
                        Result.failure(ElevenLabsError.InvalidApiKey)
                    }
                }
                response.code() == 403 -> {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "getVoices: 403 Forbidden - Permission denied. Error body: $errorBody")
                    Result.failure(ElevenLabsError.ApiError(403, "Permission denied: $errorBody"))
                }
                response.code() == 429 -> Result.failure(ElevenLabsError.QuotaExceeded)
                else -> {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "getVoices: Error ${response.code()}. Error body: $errorBody")
                    Result.failure(ElevenLabsError.ApiError(response.code(), errorBody ?: response.message()))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch voices", e)
            Result.failure(ElevenLabsError.NetworkError)
        }
    }
    
    /**
     * Get list of available models.
     */
    suspend fun getModels(): Result<List<ElevenLabsModel>> {
        val apiKey = preferences.getApiKey().first()
        return getModels(apiKey)
    }
    
    /**
     * Get list of available models with explicit API key.
     */
    suspend fun getModels(apiKey: String): Result<List<ElevenLabsModel>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(ElevenLabsError.NoApiKey)
        }
        
        try {
            val response = apiService.getModels(apiKey)
            
            when {
                response.isSuccessful -> {
                    response.body()?.let { models ->
                        // Filter to only TTS-capable models
                        Result.success(models.filter { it.canDoTextToSpeech })
                    } ?: Result.success(emptyList())
                }
                response.code() == 401 -> Result.failure(ElevenLabsError.InvalidApiKey)
                response.code() == 429 -> Result.failure(ElevenLabsError.QuotaExceeded)
                else -> Result.failure(ElevenLabsError.ApiError(response.code(), response.message()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch models", e)
            Result.failure(ElevenLabsError.NetworkError)
        }
    }
    
    /**
     * Generate audio file for text without playing it.
     * Useful for generating audio files to attach to Anki cards.
     * Returns the cached or newly generated audio file.
     * 
     * @param text The text to convert to speech
     * @param languageCode Optional ISO 639-1 language code
     * @return Result containing the audio file or an error
     */
    suspend fun generateAudioFile(
        text: String,
        languageCode: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val apiKey = preferences.getApiKey().first()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(ElevenLabsError.NoApiKey)
        }
        
        val voiceId = preferences.getVoiceId().first()
        val modelId = preferences.getModelId().first()
        
        Log.d(TAG, "generateAudioFile: text='${text.take(50)}', languageCode='$languageCode', voiceId='$voiceId'")
        
        // Generate cache key and check if we have cached audio
        val cacheKey = generateCacheKey(text, languageCode, voiceId, modelId)
        val cachedFile = getCachedAudioFile(cacheKey)
        
        if (cachedFile != null) {
            Log.d(TAG, "Returning cached audio file for: ${text.take(20)}")
            return@withContext Result.success(cachedFile)
        }
        
        // Generate new audio file
        try {
            val request = TextToSpeechRequest(
                text = text,
                modelId = modelId,
                languageCode = languageCode
            )
            
            val response = apiService.textToSpeech(voiceId, apiKey, request)
            
            when {
                response.isSuccessful -> {
                    response.body()?.let { responseBody ->
                        val audioFile = saveAudioToCacheFile(cacheKey, responseBody.bytes())
                        Log.d(TAG, "Generated new audio file for: ${text.take(20)}")
                        Result.success(audioFile)
                    } ?: Result.failure(ElevenLabsError.ApiError(response.code(), "Empty audio response"))
                }
                response.code() == 401 -> Result.failure(ElevenLabsError.InvalidApiKey)
                response.code() == 429 -> Result.failure(ElevenLabsError.QuotaExceeded)
                else -> Result.failure(ElevenLabsError.ApiError(response.code(), response.message()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate audio file", e)
            Result.failure(ElevenLabsError.NetworkError)
        }
    }
    
    /**
     * Convert text to speech and play the audio.
     * 
     * @param text The text to speak
     * @param languageCode Optional ISO 639-1 language code for explicit language control.
     *                     If null, ElevenLabs will auto-detect the language.
     * @param onComplete Called when playback completes successfully
     * @param onError Called if an error occurs
     */
    suspend fun speakText(
        text: String,
        languageCode: String? = null,
        onComplete: () -> Unit = {},
        onError: (ElevenLabsError) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val apiKey = preferences.getApiKey().first()
        if (apiKey.isBlank()) {
            withContext(Dispatchers.Main) { onError(ElevenLabsError.NoApiKey) }
            return@withContext
        }
        
        val voiceId = preferences.getVoiceId().first()
        val modelId = preferences.getModelId().first()
        
        Log.d(TAG, "speakText: text='${text.take(50)}', languageCode='$languageCode', voiceId='$voiceId', modelId='$modelId'")
        
        // Generate cache key and check if we have cached audio
        val cacheKey = generateCacheKey(text, languageCode, voiceId, modelId)
        val cachedFile = getCachedAudioFile(cacheKey)
        
        if (cachedFile != null) {
            // Use cached audio
            try {
                stopPlayback()
                currentAudioFile = cachedFile
                withContext(Dispatchers.Main) {
                    playAudioFile(cachedFile, onComplete, onError)
                }
                return@withContext
            } catch (e: Exception) {
                Log.w(TAG, "Failed to play cached audio, fetching from API", e)
                // Fall through to fetch from API
            }
        }
        
        try {
            // Stop any currently playing audio
            stopPlayback()
            
            val request = TextToSpeechRequest(
                text = text,
                modelId = modelId,
                languageCode = languageCode
            )
            
            val response = apiService.textToSpeech(voiceId, apiKey, request)
            
            when {
                response.isSuccessful -> {
                    response.body()?.let { responseBody ->
                        // Save audio to cache file
                        val audioFile = saveAudioToCacheFile(cacheKey, responseBody.bytes())
                        currentAudioFile = audioFile
                        
                        // Clean up old cache files periodically (10% chance)
                        if (Math.random() < 0.1) {
                            cleanupOldCache()
                        }
                        
                        // Play the audio
                        withContext(Dispatchers.Main) {
                            playAudioFile(audioFile, onComplete, onError)
                        }
                    } ?: withContext(Dispatchers.Main) {
                        onError(ElevenLabsError.ApiError(response.code(), "Empty audio response"))
                    }
                }
                response.code() == 401 -> withContext(Dispatchers.Main) { 
                    onError(ElevenLabsError.InvalidApiKey) 
                }
                response.code() == 429 -> withContext(Dispatchers.Main) { 
                    onError(ElevenLabsError.QuotaExceeded) 
                }
                else -> withContext(Dispatchers.Main) { 
                    onError(ElevenLabsError.ApiError(response.code(), response.message())) 
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert text to speech", e)
            withContext(Dispatchers.Main) { onError(ElevenLabsError.NetworkError) }
        }
    }
    
    /**
     * Stop any currently playing audio.
     */
    fun stopPlayback() {
        val player: MediaPlayer?
        synchronized(playerLock) {
            player = mediaPlayer
            mediaPlayer = null
            currentAudioFile = null
        }
        // Release outside the lock — only one thread can win the capture above
        try {
            player?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping playback", e)
        }
    }
    
    /**
     * Check if audio is currently playing.
     */
    fun isPlaying(): Boolean {
        return synchronized(playerLock) {
            try {
                mediaPlayer?.isPlaying == true
            } catch (e: Exception) {
                false
            }
        }
    }
    
    private fun saveAudioToCacheFile(cacheKey: String, audioBytes: ByteArray): File {
        val cacheDir = File(context.cacheDir, AUDIO_CACHE_DIR).apply { 
            if (!exists()) mkdirs() 
        }
        val audioFile = File(cacheDir, "$cacheKey.mp3")
        
        FileOutputStream(audioFile).use { fos ->
            fos.write(audioBytes)
        }
        
        Log.d(TAG, "Saved audio to cache: $cacheKey (${audioBytes.size} bytes)")
        return audioFile
    }
    
    private suspend fun playAudioFile(
        file: File,
        onComplete: () -> Unit,
        onError: (ElevenLabsError) -> Unit
    ) {
        try {
            val newPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                
                setOnCompletionListener {
                    stopPlayback()
                    onComplete()
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    stopPlayback()
                    onError(ElevenLabsError.PlaybackError("Playback error: $what"))
                    true
                }
                
                // Use suspendCancellableCoroutine for prepare
                suspendCancellableCoroutine { continuation ->
                    setOnPreparedListener {
                        continuation.resume(Unit)
                    }
                    prepareAsync()
                    
                    continuation.invokeOnCancellation {
                        try {
                            release()
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
                
                start()
            }
            synchronized(playerLock) {
                mediaPlayer = newPlayer
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play audio", e)
            stopPlayback()
            onError(ElevenLabsError.PlaybackError(e.message))
        }
    }
}
