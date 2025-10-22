package com.glosdalen.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glosdalen.app.backend.deepl.DeepLModelType
import com.glosdalen.app.backend.deepl.DeepLRepository
import com.glosdalen.app.domain.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeepLSettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val vocabularyRepository: DeepLRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DeepLSettingsUiState())
    val uiState: StateFlow<DeepLSettingsUiState> = _uiState.asStateFlow()
    
    init {
        // Load initial preferences
        viewModelScope.launch {
            combine(
                userPreferences.getDeepLApiKey(),
                userPreferences.getDeepLModelType(),
                userPreferences.getEnableMultipleFormalities()
            ) { apiKey, modelType, multipleFormalities ->
                _uiState.value = _uiState.value.copy(
                    apiKey = apiKey,
                    deepLModelType = modelType,
                    enableMultipleFormalities = multipleFormalities
                )
            }.collect()
        }
    }
    
    fun updateApiKey(apiKey: String) {
        _uiState.value = _uiState.value.copy(apiKey = apiKey)
    }
    
    fun updateDeepLModelType(modelType: DeepLModelType) {
        viewModelScope.launch {
            userPreferences.setDeepLModelType(modelType)
            _uiState.value = _uiState.value.copy(deepLModelType = modelType)
        }
    }
    
    fun updateEnableMultipleFormalities(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setEnableMultipleFormalities(enabled)
            _uiState.value = _uiState.value.copy(enableMultipleFormalities = enabled)
        }
    }
    
    fun validateAndSaveApiKey() {
        val apiKey = _uiState.value.apiKey.trim()
        if (apiKey.isEmpty()) {
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
            
            val result = vocabularyRepository.validateApiKey(apiKey)
            
            result.fold(
                onSuccess = {
                    userPreferences.setDeepLApiKey(apiKey)
                    _uiState.value = _uiState.value.copy(
                        isValidatingApiKey = false,
                        apiKeyValidated = true
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isValidatingApiKey = false,
                        apiKeyError = "Invalid API key. Please check your key."
                    )
                }
            )
        }
    }
    
    fun clearApiKeyValidation() {
        _uiState.value = _uiState.value.copy(
            apiKeyValidated = false,
            apiKeyError = null
        )
    }
}

data class DeepLSettingsUiState(
    val apiKey: String = "",
    val deepLModelType: DeepLModelType = DeepLModelType.QUALITY_OPTIMIZED,
    val enableMultipleFormalities: Boolean = true,
    val isValidatingApiKey: Boolean = false,
    val apiKeyValidated: Boolean = false,
    val apiKeyError: String? = null,
)
