package com.glosdalen.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glosdalen.app.backend.deepl.Language
import com.glosdalen.app.domain.preferences.UserPreferences
import com.glosdalen.app.domain.template.DeckNameTemplateResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    val templateResolver: DeckNameTemplateResolver
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        // Load initial preferences
        viewModelScope.launch {
            combine(
                userPreferences.getNativeLanguage(),
                userPreferences.getForeignLanguage()
            ) { nativeLanguage, foreignLanguage ->
                _uiState.value = _uiState.value.copy(
                    nativeLanguage = nativeLanguage,
                    foreignLanguage = foreignLanguage
                )
            }.collect()
        }
    }
    
    fun updateNativeLanguage(language: Language) {
        viewModelScope.launch {
            userPreferences.setNativeLanguage(language)
            _uiState.value = _uiState.value.copy(nativeLanguage = language)
        }
    }
}

data class SettingsUiState(
    val nativeLanguage: Language = Language.GERMAN,
    val foreignLanguage: Language = Language.SWEDISH
)
