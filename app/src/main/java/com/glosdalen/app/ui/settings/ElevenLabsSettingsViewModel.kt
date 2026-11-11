package com.glosdalen.app.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glosdalen.app.backend.elevenlabs.ElevenLabsError
import com.glosdalen.app.backend.elevenlabs.ElevenLabsModel
import com.glosdalen.app.backend.elevenlabs.ElevenLabsRepository
import com.glosdalen.app.backend.elevenlabs.Voice
import com.glosdalen.app.domain.preferences.ElevenLabsPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ElevenLabsSettingsVM"

@HiltViewModel
class ElevenLabsSettingsViewModel @Inject constructor(
    private val repository: ElevenLabsRepository,
    private val preferences: ElevenLabsPreferences
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ElevenLabsSettingsUiState())
    val uiState: StateFlow<ElevenLabsSettingsUiState> = _uiState.asStateFlow()
    
    init {
        // Load saved preferences
        viewModelScope.launch {
            combine(
                preferences.getApiKey(),
                preferences.getVoiceId(),
                preferences.getVoiceName(),
                preferences.getModelId()
            ) { apiKey, voiceId, voiceName, modelId ->
                ElevenLabsSettings(
                    apiKey = apiKey,
                    voiceId = voiceId,
                    voiceName = voiceName,
                    modelId = modelId
                )
            }.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    apiKey = settings.apiKey,
                    selectedVoiceId = settings.voiceId,
                    selectedVoiceName = settings.voiceName,
                    selectedModelId = settings.modelId,
                    isConfigured = settings.apiKey.isNotBlank()
                )
                
                // Auto-load voices when screen opens with saved API key
                // Skip if we're currently validating (validateAndSaveApiKey handles loading)
                val isValidating = _uiState.value.isValidatingApiKey
                if (settings.apiKey.isNotBlank() && _uiState.value.voices.isEmpty() && !isValidating) {
                    loadVoicesAndModels(settings.apiKey)
                }
            }
        }
    }
    
    fun updateApiKey(apiKey: String) {
        _uiState.value = _uiState.value.copy(
            apiKey = apiKey,
            apiKeyError = null,
            apiKeyValidated = false
        )
    }
    
    fun validateAndSaveApiKey() {
        val apiKey = _uiState.value.apiKey.trim()
        Log.d(TAG, "validateAndSaveApiKey: apiKey length=${apiKey.length}, first8=${apiKey.take(8)}")
        if (apiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                apiKeyError = "API key cannot be empty"
            )
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isValidatingApiKey = true,
                apiKeyError = null
            )
            
            Log.d(TAG, "validateAndSaveApiKey: Calling repository.validateApiKey...")
            repository.validateApiKey(apiKey).fold(
                onSuccess = { subscription ->
                    Log.d(TAG, "validateAndSaveApiKey: Validation successful, tier=${subscription.tier}")
                    preferences.setApiKey(apiKey)
                    _uiState.value = _uiState.value.copy(
                        isValidatingApiKey = false,
                        apiKeyValidated = true,
                        isConfigured = true,
                        characterUsage = "${subscription.characterCount} / ${subscription.characterLimit}",
                        subscriptionTier = subscription.tier
                    )
                    // Load voices and models after successful validation
                    // Pass the validated apiKey directly to avoid race conditions
                    Log.d(TAG, "validateAndSaveApiKey: Now calling loadVoicesAndModels with validated key")
                    loadVoicesAndModels(apiKey)
                },
                onFailure = { error ->
                    Log.e(TAG, "validateAndSaveApiKey: Validation failed: $error")
                    val errorMessage = when (error) {
                        is ElevenLabsError.InvalidApiKey -> "Invalid API key. Please check your key."
                        is ElevenLabsError.NetworkError -> "Network error. Please check your connection."
                        is ElevenLabsError.QuotaExceeded -> "API quota exceeded."
                        else -> "Validation failed: ${error.message}"
                    }
                    _uiState.value = _uiState.value.copy(
                        isValidatingApiKey = false,
                        apiKeyError = errorMessage
                    )
                }
            )
        }
    }
    
    private fun loadVoicesAndModels(apiKey: String) {
        // Guard: don't load if already loading
        if (_uiState.value.isLoadingVoices) {
            Log.d(TAG, "loadVoicesAndModels: Already loading, skipping")
            return
        }
        
        Log.d(TAG, "loadVoicesAndModels called with apiKey length: ${apiKey.length}")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingVoices = true, voicesError = null)
            
            // Load voices using the explicit API key
            repository.getVoices(apiKey).fold(
                onSuccess = { voices ->
                    Log.d(TAG, "loadVoicesAndModels: Got ${voices.size} voices")
                    _uiState.value = _uiState.value.copy(
                        voices = voices,
                        voicesLoaded = true,
                        isLoadingVoices = false
                    )
                    // No built-in default voice: pick the account's first voice if none is chosen yet
                    if (_uiState.value.selectedVoiceId == null) {
                        voices.firstOrNull()?.let(::selectVoice)
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "loadVoicesAndModels failed: $error")
                    val errorMessage = when (error) {
                        is ElevenLabsError.InvalidApiKey -> "Invalid API key - check key permissions"
                        is ElevenLabsError.NetworkError -> "Network error - check connection"
                        is ElevenLabsError.NoApiKey -> "No API key configured"
                        is ElevenLabsError.QuotaExceeded -> "API quota exceeded"
                        is ElevenLabsError.ApiError -> "API error ${error.code}: ${error.message}"
                        else -> "Failed to load voices: ${error.message}"
                    }
                    Log.e(TAG, "Voice loading error message: $errorMessage")
                    _uiState.value = _uiState.value.copy(
                        isLoadingVoices = false,
                        voicesError = errorMessage
                    )
                }
            )
            
            // Load models using the explicit API key
            repository.getModels(apiKey).fold(
                onSuccess = { models ->
                    _uiState.value = _uiState.value.copy(models = models)
                },
                onFailure = { error ->
                    Log.e(TAG, "loadModels failed: $error")
                }
            )
        }
    }
    
    fun selectVoice(voice: Voice) {
        viewModelScope.launch {
            preferences.setVoiceId(voice.voiceId)
            preferences.setVoiceName(voice.name)
            _uiState.value = _uiState.value.copy(
                selectedVoiceId = voice.voiceId,
                selectedVoiceName = voice.name
            )
        }
    }
    
    /**
     * Use a voice ID typed in by the user. This is the only way to choose a voice when the
     * account's voice list cannot be loaded (e.g. a key scoped to text-to-speech without `voices_read`).
     */
    fun useVoiceId(voiceId: String) {
        val id = voiceId.trim()
        if (id.isBlank()) return
        viewModelScope.launch {
            preferences.setVoiceId(id)
            preferences.setVoiceName(id)
            _uiState.value = _uiState.value.copy(
                selectedVoiceId = id,
                selectedVoiceName = id
            )
        }
    }
    
    fun selectModel(model: ElevenLabsModel) {
        viewModelScope.launch {
            preferences.setModelId(model.modelId)
            _uiState.value = _uiState.value.copy(
                selectedModelId = model.modelId
            )
        }
    }
    
    fun testVoice() {
        if (_uiState.value.isTestingVoice) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingVoice = true)
            
            repository.speakText(
                text = "Hello! This is a test of the ElevenLabs text-to-speech system.",
                onComplete = {
                    _uiState.value = _uiState.value.copy(isTestingVoice = false)
                },
                onError = { error ->
                    _uiState.value = _uiState.value.copy(
                        isTestingVoice = false,
                        testError = when (error) {
                            is ElevenLabsError.NoVoiceSelected -> "Choose a voice first"
                            is ElevenLabsError.InvalidApiKey -> "Invalid API key"
                            is ElevenLabsError.NetworkError -> "Network error"
                            is ElevenLabsError.QuotaExceeded -> "Character quota exceeded"
                            else -> "Playback failed"
                        }
                    )
                }
            )
        }
    }
    
    fun stopTestVoice() {
        repository.stopPlayback()
        _uiState.value = _uiState.value.copy(isTestingVoice = false)
    }
    
    fun clearApiKey() {
        viewModelScope.launch {
            preferences.setApiKey("")
            _uiState.value = ElevenLabsSettingsUiState()
        }
    }
    
    fun refreshVoices() {
        val apiKey = _uiState.value.apiKey.trim()
        if (apiKey.isNotBlank()) {
            loadVoicesAndModels(apiKey)
        }
    }
}

private data class ElevenLabsSettings(
    val apiKey: String,
    val voiceId: String?,
    val voiceName: String?,
    val modelId: String
)

data class ElevenLabsSettingsUiState(
    val apiKey: String = "",
    val isConfigured: Boolean = false,
    val isValidatingApiKey: Boolean = false,
    val apiKeyValidated: Boolean = false,
    val apiKeyError: String? = null,
    
    val subscriptionTier: String? = null,
    val characterUsage: String? = null,
    
    val voices: List<Voice> = emptyList(),
    val voicesLoaded: Boolean = false,
    val models: List<ElevenLabsModel> = emptyList(),
    val isLoadingVoices: Boolean = false,
    val voicesError: String? = null,
    
    val selectedVoiceId: String? = null,
    val selectedVoiceName: String? = null,
    val selectedModelId: String = ElevenLabsPreferences.DEFAULT_MODEL_ID,
    
    val isTestingVoice: Boolean = false,
    val testError: String? = null
) {
    /**
     * The saved voice is not in the account's voice list: ElevenLabs retired it (legacy voices,
     * Default voices after 2026-12-31) or the account cannot use it. The user should pick another.
     */
    val selectedVoiceUnavailable: Boolean
        get() = voicesLoaded && selectedVoiceId != null && voices.none { it.voiceId == selectedVoiceId }
    
    /** The voice list could not be loaded or is empty, so a voice ID has to be entered by hand. */
    val voiceListUnavailable: Boolean
        get() = !isLoadingVoices && (voicesError != null || (voicesLoaded && voices.isEmpty()))
}
