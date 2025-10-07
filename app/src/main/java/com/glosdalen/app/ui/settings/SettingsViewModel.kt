package com.glosdalen.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glosdalen.app.data.model.DeepLModelType
import com.glosdalen.app.data.model.Language
import com.glosdalen.app.data.repository.VocabularyRepository
import com.glosdalen.app.domain.preferences.UserPreferences
import com.glosdalen.app.domain.template.DeckNameTemplateResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val vocabularyRepository: VocabularyRepository,
    val templateResolver: DeckNameTemplateResolver
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    val currentApiKey = userPreferences.getDeepLApiKey()
    val currentNativeLanguage = userPreferences.getNativeLanguage()
    val currentForeignLanguage = userPreferences.getForeignLanguage()
    val currentDeepLModelType = userPreferences.getDeepLModelType()
    val currentEnableMultipleFormalities = userPreferences.getEnableMultipleFormalities()
    val isFirstLaunch = userPreferences.isFirstLaunch()
    
    fun updateApiKey(apiKey: String) {
        _uiState.value = _uiState.value.copy(apiKey = apiKey)
    }
    
    fun updateNativeLanguage(language: Language) {
        _uiState.value = _uiState.value.copy(nativeLanguage = language)
    }
    
    fun updateForeignLanguage(language: Language) {
        _uiState.value = _uiState.value.copy(foreignLanguage = language)
    }
    
    fun updateDeepLModelType(modelType: DeepLModelType) {
        _uiState.value = _uiState.value.copy(deepLModelType = modelType)
    }
    
    fun updateEnableMultipleFormalities(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enableMultipleFormalities = enabled)
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
    
    fun saveSettings() {
        viewModelScope.launch {
            userPreferences.setNativeLanguage(_uiState.value.nativeLanguage)
            userPreferences.setForeignLanguage(_uiState.value.foreignLanguage)
            userPreferences.setDeepLModelType(_uiState.value.deepLModelType)
            userPreferences.setEnableMultipleFormalities(_uiState.value.enableMultipleFormalities)
            userPreferences.setFirstLaunchCompleted()
            _uiState.value = _uiState.value.copy(settingsSaved = true)
        }
    }
    
    fun clearApiKeyValidation() {
        _uiState.value = _uiState.value.copy(
            apiKeyValidated = false,
            apiKeyError = null
        )
    }
    
    fun clearSettingsSaved() {
        _uiState.value = _uiState.value.copy(settingsSaved = false)
    }
    
    fun initializeFromCurrentSettings(apiKey: String, nativeLanguage: Language, foreignLanguage: Language, deepLModelType: DeepLModelType, enableMultipleFormalities: Boolean) {
        _uiState.value = _uiState.value.copy(
            apiKey = apiKey,
            nativeLanguage = nativeLanguage,
            foreignLanguage = foreignLanguage,
            deepLModelType = deepLModelType,
            enableMultipleFormalities = enableMultipleFormalities
        )
    }
}

data class SettingsUiState(
    val apiKey: String = "",
    val nativeLanguage: Language = Language.GERMAN,
    val foreignLanguage: Language = Language.SWEDISH,
    val deepLModelType: DeepLModelType = DeepLModelType.QUALITY_OPTIMIZED,
    val enableMultipleFormalities: Boolean = true,
    val isValidatingApiKey: Boolean = false,
    val apiKeyValidated: Boolean = false,
    val apiKeyError: String? = null,
    val settingsSaved: Boolean = false
)
