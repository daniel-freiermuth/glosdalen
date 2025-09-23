package com.swedishvocab.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swedishvocab.app.data.model.CardType
import com.swedishvocab.app.data.repository.VocabularyRepository
import com.swedishvocab.app.domain.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val vocabularyRepository: VocabularyRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    val currentApiKey = userPreferences.getDeepLApiKey()
    val currentDeckName = userPreferences.getDefaultDeckName()
    val currentCardType = userPreferences.getDefaultCardType()
    val isFirstLaunch = userPreferences.isFirstLaunch()
    
    fun updateApiKey(apiKey: String) {
        _uiState.value = _uiState.value.copy(apiKey = apiKey)
    }
    
    fun updateDeckName(deckName: String) {
        _uiState.value = _uiState.value.copy(deckName = deckName)
    }
    
    fun updateCardType(cardType: CardType) {
        _uiState.value = _uiState.value.copy(cardType = cardType)
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
            val deckName = _uiState.value.deckName.trim()
            if (deckName.isNotEmpty()) {
                userPreferences.setDefaultDeckName(deckName)
            }
            
            userPreferences.setDefaultCardType(_uiState.value.cardType)
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
    
    fun initializeFromCurrentSettings(apiKey: String, deckName: String, cardType: CardType) {
        _uiState.value = _uiState.value.copy(
            apiKey = apiKey,
            deckName = deckName,
            cardType = cardType
        )
    }
}

data class SettingsUiState(
    val apiKey: String = "",
    val deckName: String = "",
    val cardType: CardType = CardType.UNIDIRECTIONAL,
    val isValidatingApiKey: Boolean = false,
    val apiKeyValidated: Boolean = false,
    val apiKeyError: String? = null,
    val settingsSaved: Boolean = false
)
