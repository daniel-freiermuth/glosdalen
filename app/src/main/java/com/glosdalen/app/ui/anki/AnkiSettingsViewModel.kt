package com.glosdalen.app.ui.anki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glosdalen.app.backend.anki.AnkiRepository
import com.glosdalen.app.backend.anki.AnkiApiRepository
import com.glosdalen.app.backend.anki.AnkiImplementationType
import com.glosdalen.app.domain.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnkiSettingsViewModel @Inject constructor(
    private val ankiRepository: AnkiRepository,
    private val apiRepository: AnkiApiRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnkiSettingsUiState())
    val uiState: StateFlow<AnkiSettingsUiState> = _uiState.asStateFlow()

    init {
        // Load initial preferences
        viewModelScope.launch {
            userPreferences.getDefaultDeckName().collect { deckName ->
                _uiState.value = _uiState.value.copy(
                    selectedDeckName = deckName
                )
            }
        }

        // Check Anki availability and available methods
        refreshAnkiAvailability()
        
        // Load available decks
        loadAvailableDecks()
    }

    fun selectDeck(deckName: String) {
        viewModelScope.launch {
            userPreferences.setDefaultDeckName(deckName)
            _uiState.value = _uiState.value.copy(selectedDeckName = deckName)
        }
    }
    
    fun loadAvailableDecks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingDecks = true)
            
            try {
                val decksResult = apiRepository.getAvailableDecks()
                
                decksResult.fold(
                    onSuccess = { decks ->
                        _uiState.value = _uiState.value.copy(
                            availableDecks = decks,
                            isLoadingDecks = false
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            availableDecks = emptyMap(),
                            isLoadingDecks = false,
                            errorMessage = "Failed to load decks: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    availableDecks = emptyMap(),
                    isLoadingDecks = false,
                    errorMessage = "Error loading decks: ${e.message}"
                )
            }
        }
    }

    fun refreshAnkiAvailability() {
        viewModelScope.launch {
            try {
                val isAvailable = ankiRepository.isAnkiDroidAvailable()
                
                _uiState.value = _uiState.value.copy(
                    isAnkiAvailable = isAvailable,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnkiAvailable = false,
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

data class AnkiSettingsUiState(
    val selectedDeckName: String = "",
    val availableDecks: Map<Long, String> = emptyMap(),
    val isLoadingDecks: Boolean = false,
    val isAnkiAvailable: Boolean = false,
    val errorMessage: String? = null
)
