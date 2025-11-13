package com.glosdalen.app.ui.anki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glosdalen.app.backend.anki.AnkiRepository
import com.glosdalen.app.domain.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnkiApiInfoViewModel @Inject constructor(
    private val ankiRepository: AnkiRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnkiApiInfoUiState())
    val uiState: StateFlow<AnkiApiInfoUiState> = _uiState

    private suspend fun updateDialogState() {
        // Check repository state for installed vs permission
        val apiEndpointAvailable = ankiRepository.isApiEndpointAvailable()
        val hasPermission = ankiRepository.isApiPermissionGranted()

        val shouldShow = apiEndpointAvailable && !hasPermission

        _uiState.update {
            it.copy(
                shouldShow = shouldShow,
            )
        }
    }

    fun onDismiss() {
        _uiState.update { it.copy(shouldShow = false) }
    }
}


data class AnkiApiInfoUiState(
    val shouldShow: Boolean = false,
)
