package com.glosdalen.app.ui.anki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glosdalen.app.data.model.CardDirection
import com.glosdalen.app.data.repository.AnkiRepository
import com.glosdalen.app.data.repository.AnkiImplementationType
import com.glosdalen.app.data.repository.impl.AnkiApiRepositoryImpl
import com.glosdalen.app.data.repository.impl.AnkiRepositoryImpl
import com.glosdalen.app.domain.preferences.UserPreferences
import com.glosdalen.app.domain.preferences.AnkiMethodPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnkiSettingsViewModel @Inject constructor(
    private val ankiRepository: AnkiRepository,
    private val apiRepository: AnkiApiRepositoryImpl,
    private val ankiRepositoryImpl: AnkiRepositoryImpl,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnkiSettingsUiState())
    val uiState: StateFlow<AnkiSettingsUiState> = _uiState.asStateFlow()

    init {
        // Load initial preferences
        viewModelScope.launch {
            combine(
                userPreferences.getDefaultDeckName(),
                userPreferences.getDefaultCardDirection(),
                userPreferences.getPreferredAnkiMethod()
            ) { deckName, direction, preferredMethod ->
                _uiState.value = _uiState.value.copy(
                    selectedDeckName = deckName,
                    selectedDirection = direction,
                    selectedMethod = preferredMethod
                )
            }.collect()
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

    fun selectDirection(direction: CardDirection) {
        viewModelScope.launch {
            userPreferences.setDefaultCardDirection(direction)
            _uiState.value = _uiState.value.copy(selectedDirection = direction)
        }
    }
    
    fun selectMethod(method: AnkiMethodPreference) {
        viewModelScope.launch {
            userPreferences.setPreferredAnkiMethod(method)
            _uiState.value = _uiState.value.copy(selectedMethod = method)
            // Refresh availability after method change
            refreshAnkiAvailability()
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
                val availableMethods = ankiRepositoryImpl.getAvailableMethods()
                val bothMethodsAvailable = ankiRepositoryImpl.areBothMethodsAvailable()
                
                android.util.Log.d("AnkiSettingsViewModel", "Available methods: $availableMethods")
                android.util.Log.d("AnkiSettingsViewModel", "Both methods available: $bothMethodsAvailable")
                
                _uiState.value = _uiState.value.copy(
                    isAnkiAvailable = isAvailable,
                    implementationType = implementationType.name,
                    supportsBatchOperations = supportsBatch,
                    isUsingApiImplementation = usingApi,
                    availableMethods = availableMethods,
                    bothMethodsAvailable = bothMethodsAvailable
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnkiAvailable = false,
                    implementationType = "UNAVAILABLE",
                    supportsBatchOperations = false,
                    isUsingApiImplementation = false,
                    availableMethods = emptyList(),
                    bothMethodsAvailable = false
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
    val selectedMethod: AnkiMethodPreference = AnkiMethodPreference.AUTO,
    val availableDecks: Map<Long, String> = emptyMap(),
    val isLoadingDecks: Boolean = false,
    val isAnkiAvailable: Boolean = false,
    val implementationType: String = "UNKNOWN",
    val supportsBatchOperations: Boolean = false,
    val isUsingApiImplementation: Boolean = false,
    val availableMethods: List<AnkiImplementationType> = emptyList(),
    val bothMethodsAvailable: Boolean = false,
    val errorMessage: String? = null
)
