package com.glosdalen.app.ui.anki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glosdalen.app.backend.anki.AnkiRepository
import com.glosdalen.app.domain.preferences.AnkiMethodPreference
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

    init {
        observeConditions()
    }

    private fun observeConditions() {
        viewModelScope.launch {
            userPreferences.getPreferredAnkiMethod().collect { preferredMethod ->
                updateDialogState(preferredMethod)
            }
        }
    }

    private suspend fun updateDialogState(preferredMethod: AnkiMethodPreference) {
        // Only show when user wants API or AUTO (not INTENT)
        val wantsApi = preferredMethod != AnkiMethodPreference.INTENT

        // Check repository state for installed vs permission
        val apiEndpointAvailable = ankiRepository.isApiEndpointAvailable()
        val hasPermission = ankiRepository.isApiPermissionGranted()

        val shouldShow = wantsApi && apiEndpointAvailable && !hasPermission

        _uiState.update {
            it.copy(
                shouldShow = shouldShow,
            )
        }
    }

    fun onDontNeedApi() {
        viewModelScope.launch {
            // Set preference to INTENT and permanently dismiss
            userPreferences.setPreferredAnkiMethod(AnkiMethodPreference.INTENT)
            _uiState.update { it.copy(shouldShow = false) }
        }
    }

    fun onDismiss() {
        _uiState.update { it.copy(shouldShow = false) }
    }

    /**
     * Re-check permission status when app resumes.
     * If permission is now granted, hide the dialog automatically.
     */
    fun recheckPermissionStatus() {
        viewModelScope.launch {
            val preferredMethod = userPreferences.getPreferredAnkiMethod().first()
            updateDialogState(preferredMethod)
        }
    }
}


data class AnkiApiInfoUiState(
    val shouldShow: Boolean = false,
)
