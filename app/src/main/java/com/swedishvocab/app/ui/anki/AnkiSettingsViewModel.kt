package com.swedishvocab.app.ui.anki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swedishvocab.app.data.model.CardDirection
import com.swedishvocab.app.data.repository.AnkiRepository
import com.swedishvocab.app.data.repository.AnkiImplementationType
import com.swedishvocab.app.data.repository.impl.AnkiApiRepositoryImpl
import com.swedishvocab.app.domain.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnkiSettingsViewModel @Inject constructor(
    private val ankiRepository: AnkiRepository,
    private val apiRepository: AnkiApiRepositoryImpl,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnkiSettingsUiState())
    val uiState: StateFlow<AnkiSettingsUiState> = _uiState.asStateFlow()

    init {
        // Load initial preferences
        viewModelScope.launch {
            combine(
                userPreferences.getDefaultDeckName(),
                userPreferences.getDefaultCardDirection()
            ) { deckName, direction ->
                _uiState.value = _uiState.value.copy(
                    selectedDeckName = deckName,
                    selectedDirection = direction
                )
            }.collect()
        }

        // Check Anki availability
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

    fun selectDirection(direction: CardDirection) {
        viewModelScope.launch {
            userPreferences.setDefaultCardDirection(direction)
            _uiState.value = _uiState.value.copy(selectedDirection = direction)
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
                val implementationType = ankiRepository.getImplementationType()
                val supportsBatch = ankiRepository.supportsBatchOperations()
                val usingApi = implementationType == AnkiImplementationType.API
                
                _uiState.value = _uiState.value.copy(
                    isAnkiAvailable = isAvailable,
                    implementationType = implementationType.name,
                    supportsBatchOperations = supportsBatch,
                    isUsingApiImplementation = usingApi
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnkiAvailable = false,
                    implementationType = "UNAVAILABLE",
                    supportsBatchOperations = false,
                    isUsingApiImplementation = false
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
    val selectedDirection: CardDirection = CardDirection.NATIVE_TO_FOREIGN,
    val availableDecks: Map<Long, String> = emptyMap(),
    val isLoadingDecks: Boolean = false,
    val isAnkiAvailable: Boolean = false,
    val implementationType: String = "UNKNOWN",
    val supportsBatchOperations: Boolean = false,
    val isUsingApiImplementation: Boolean = false,
    val errorMessage: String? = null
)
