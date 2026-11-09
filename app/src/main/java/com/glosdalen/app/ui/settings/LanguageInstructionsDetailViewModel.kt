package com.glosdalen.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glosdalen.app.backend.deepl.Language
import com.glosdalen.app.domain.preferences.UserPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Edits the Copilot instructions of the one [language] its navigation entry was opened for. */
@HiltViewModel(assistedFactory = LanguageInstructionsDetailViewModel.Factory::class)
class LanguageInstructionsDetailViewModel @AssistedInject constructor(
    @Assisted private val language: Language,
    private val userPreferences: UserPreferences
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(language: Language): LanguageInstructionsDetailViewModel
    }

    val currentInstructions: StateFlow<String> = userPreferences.getLanguageInstructions(language)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    fun updateInstructions(instructions: String) {
        viewModelScope.launch {
            userPreferences.setLanguageInstructions(language, instructions)
        }
    }

    fun getDefaultInstructions(): String = userPreferences.getDefaultLanguageInstructions(language)

    fun clearInstructions() {
        viewModelScope.launch {
            userPreferences.setLanguageInstructions(language, "")
        }
    }

    fun loadDefaultInstructions() {
        val defaultInstructions = getDefaultInstructions()
        viewModelScope.launch {
            userPreferences.setLanguageInstructions(language, defaultInstructions)
        }
    }
}
