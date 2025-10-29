package com.glosdalen.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glosdalen.app.backend.deepl.Language
import com.glosdalen.app.domain.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LanguageInstructionsViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {
    
    // Selected language for editing instructions
    private val _selectedLanguage = MutableStateFlow(Language.SWEDISH)
    val selectedLanguage: StateFlow<Language> = _selectedLanguage.asStateFlow()
    
    // Instructions for the currently selected language
    val currentInstructions: StateFlow<String> = _selectedLanguage
        .flatMapLatest { language ->
            userPreferences.getLanguageInstructions(language)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )
    
    // All languages with their instruction status
    val languageStatuses: StateFlow<Map<Language, Boolean>> = userPreferences.getAllLanguageInstructions()
        .map { instructionsMap ->
            Language.values().associateWith { language ->
                instructionsMap[language]?.isNotBlank() == true
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )
    
    /**
     * Select a language to view/edit its instructions
     */
    fun selectLanguage(language: Language) {
        _selectedLanguage.value = language
    }
    
    /**
     * Update instructions for the currently selected language
     */
    fun updateInstructions(instructions: String) {
        viewModelScope.launch {
            userPreferences.setLanguageInstructions(_selectedLanguage.value, instructions)
        }
    }
    
    /**
     * Get the default instructions for the current language
     */
    fun getDefaultInstructions(): String {
        return userPreferences.getDefaultLanguageInstructions(_selectedLanguage.value)
    }
    
    /**
     * Clear instructions for the currently selected language
     */
    fun clearInstructions() {
        viewModelScope.launch {
            userPreferences.setLanguageInstructions(_selectedLanguage.value, "")
        }
    }
    
    /**
     * Load default instructions for the currently selected language
     */
    fun loadDefaultInstructions() {
        val defaultInstructions = getDefaultInstructions()
        viewModelScope.launch {
            userPreferences.setLanguageInstructions(_selectedLanguage.value, defaultInstructions)
        }
    }
}
