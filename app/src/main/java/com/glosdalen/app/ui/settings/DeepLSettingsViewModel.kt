package com.glosdalen.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glosdalen.app.backend.deepl.DeepLModelType
import com.glosdalen.app.backend.deepl.DeepLRepository
import com.glosdalen.app.domain.coroutines.LatestJobLauncher
import com.glosdalen.app.domain.preferences.FrontPreference
import com.glosdalen.app.domain.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeepLSettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val vocabularyRepository: DeepLRepository,
    private val jobLauncher: LatestJobLauncher
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DeepLSettingsUiState())
    val uiState: StateFlow<DeepLSettingsUiState> = _uiState.asStateFlow()
    
    init {
        // Load initial preferences
        viewModelScope.launch {
            combine(
                userPreferences.getDeepLApiKey(),
                userPreferences.getDeepLModelType(),
                userPreferences.getEnableMultipleFormalities(),
                userPreferences.getFrontPreference()
            ) { apiKey, modelType, multipleFormalities, frontPreference ->
                _uiState.value = _uiState.value.copy(
                    apiKey = apiKey,
                    deepLModelType = modelType,
                    enableMultipleFormalities = multipleFormalities,
                    frontPreference = frontPreference
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
    
    fun updateFrontPreference(preference: FrontPreference) {
        viewModelScope.launch {
            userPreferences.setFrontPreference(preference)
            _uiState.value = _uiState.value.copy(frontPreference = preference)
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
        
        // Outlives this entry-scoped VM, so leaving the page does not drop the save; a newer
        // submission from a re-opened page supersedes (cancels) this one.
        jobLauncher.launch(LatestJobLauncher.Slot.DEEPL_API_KEY) {
            _uiState.value = _uiState.value.copy(
                isValidatingApiKey = true,
                apiKeyError = null
            )
            
            val result = vocabularyRepository.validateApiKey(apiKey)
            // Superseded while validating: the repository reports cancellation as a failure.
            ensureActive()

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
}

data class DeepLSettingsUiState(
    val apiKey: String = "",
    val deepLModelType: DeepLModelType = DeepLModelType.QUALITY_OPTIMIZED,
    val enableMultipleFormalities: Boolean = true,
    val frontPreference: FrontPreference = FrontPreference.NATIVE,
    val isValidatingApiKey: Boolean = false,
    val apiKeyValidated: Boolean = false,
    val apiKeyError: String? = null,
)

