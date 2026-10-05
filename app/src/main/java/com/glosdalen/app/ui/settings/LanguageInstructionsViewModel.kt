package com.glosdalen.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glosdalen.app.backend.deepl.Language
import com.glosdalen.app.domain.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class LanguageInstructionsViewModel @Inject constructor(
    userPreferences: UserPreferences
) : ViewModel() {
    
    // All languages with their instruction status
    val languageStatuses: StateFlow<Map<Language, Boolean>> = userPreferences.getAllLanguageInstructions()
        .map { instructionsMap ->
            Language.entries.associateWith { language ->
                instructionsMap[language]?.isNotBlank() == true
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )
}
