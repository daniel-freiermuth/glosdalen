package com.glosdalen.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glosdalen.app.domain.preferences.CopilotPreferences
import com.glosdalen.app.domain.preferences.UserPreferences
import com.glosdalen.app.libs.copilot.CopilotChat
import com.glosdalen.app.libs.copilot.AuthResult
import com.glosdalen.app.libs.copilot.CopilotException
import com.glosdalen.app.libs.copilot.models.CopilotModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class CopilotSettingsViewModel @Inject constructor(
    private val copilot: CopilotChat,
    private val userPreferences: UserPreferences
) : ViewModel() {
    
    val selectedModel = userPreferences.getCopilotSelectedModel()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CopilotPreferences.AUTO_MODEL)
    
    private val _uiState = MutableStateFlow(CopilotSettingsUiState())
    val uiState: StateFlow<CopilotSettingsUiState> = _uiState.asStateFlow()
    
    init {
        checkAuthenticationStatus()
        loadModels()
    }
    
    fun checkAuthenticationStatus() {
        viewModelScope.launch {
            try {
                val isAuthenticated = copilot.isAuthenticated()
                _uiState.update { it.copy(
                    isAuthenticated = isAuthenticated,
                    authState = if (isAuthenticated) AuthState.Authenticated else AuthState.NotAuthenticated
                )}
                
                // Load models if authenticated
                if (isAuthenticated) {
                    loadModels()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    authState = AuthState.NotAuthenticated,
                    errorMessage = "Failed to check authentication: ${e.message}"
                )}
            }
        }
    }
    
    fun loadModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModels = true) }
            
            val result = copilot.getModels()
            
            result.fold(
                onSuccess = { models ->
                    _uiState.update { it.copy(
                        availableModels = models,
                        isLoadingModels = false
                    )}
                },
                onFailure = { error ->
                    _uiState.update { it.copy(
                        isLoadingModels = false,
                        errorMessage = "Failed to load models: ${error.message}"
                    )}
                }
            )
        }
    }
    
    fun selectModel(modelId: String) {
        viewModelScope.launch {
            userPreferences.setCopilotSelectedModel(modelId)
        }
    }
    
    fun startAuthentication() {
        viewModelScope.launch {
            _uiState.update { it.copy(
                authState = AuthState.RequestingDeviceCode,
                errorMessage = null
            )}
            
            val result = copilot.authenticate()
            
            result.fold(
                onSuccess = { deviceCodeData ->
                    _uiState.update { it.copy(
                        authState = AuthState.WaitingForUser(
                            userCode = deviceCodeData.userCode,
                            verificationUri = deviceCodeData.verificationUri,
                            expiresIn = deviceCodeData.expiresIn
                        ),
                        deviceCode = deviceCodeData
                    )}
                    
                    // Don't automatically poll - let user see the code first
                    // Polling will start when they click "Open GitHub" or after a delay
                    startPollingAfterDelay()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(
                        authState = AuthState.Error,
                        errorMessage = when (error) {
                            is CopilotException.NetworkException.NoConnection ->
                                "No internet connection. Please check your network."
                            is CopilotException.NetworkException.Timeout ->
                                "Request timed out. Please try again."
                            else -> error.message ?: "Failed to start authentication"
                        }
                    )}
                }
            )
        }
    }
    
    fun startPolling() {
        // Called when user clicks "Open GitHub" or manually starts polling
        pollForCompletion()
    }
    
    fun onAppResumed() {
        // When app returns to foreground, automatically resume polling if we have a device code
        val currentState = _uiState.value
        if (currentState.deviceCode != null && currentState.authState is AuthState.WaitingForUser) {
            // Clear any previous error message and resume polling
            _uiState.update { it.copy(errorMessage = null) }
            pollForCompletion()
        }
    }
    
    private fun startPollingAfterDelay() {
        // Give user 5 seconds to see the code before starting to poll
        viewModelScope.launch {
            delay(5000)
            // Only start polling if still in WaitingForUser state
            if (_uiState.value.authState is AuthState.WaitingForUser) {
                pollForCompletion()
            }
        }
    }
    
    private fun pollForCompletion() {
        viewModelScope.launch {
            _uiState.update { it.copy(authState = AuthState.Polling) }
            
            val result = copilot.completeAuthentication()
            
            result.fold(
                onSuccess = { authResult ->
                    when (authResult) {
                        is AuthResult.Success -> {
                            _uiState.update { it.copy(
                                authState = AuthState.Authenticated,
                                isAuthenticated = true,
                                errorMessage = null,
                                deviceCode = null
                            )}
                        }
                        is AuthResult.Failed -> {
                            _uiState.update { it.copy(
                                authState = AuthState.Error,
                                errorMessage = when (authResult.error) {
                                    is CopilotException.AuthException.AccessDenied ->
                                        "Access denied. You declined the authorization."
                                    is CopilotException.AuthException.DeviceCodeExpired ->
                                        "Device code expired. Please try again."
                                    else -> authResult.error.message ?: "Authentication failed"
                                },
                                deviceCode = null
                            )}
                        }
                        else -> {
                            _uiState.update { it.copy(
                                authState = AuthState.Error,
                                errorMessage = "Unexpected authentication result",
                                deviceCode = null
                            )}
                        }
                    }
                },
                onFailure = { error ->
                    // On network errors, keep device code and allow retry
                    val shouldKeepDeviceCode = error is CopilotException.NetworkException
                    
                    _uiState.update { it.copy(
                        authState = if (shouldKeepDeviceCode) AuthState.WaitingForUser(
                            userCode = it.deviceCode?.userCode ?: "",
                            verificationUri = it.deviceCode?.verificationUri ?: "",
                            expiresIn = it.deviceCode?.expiresIn ?: 0
                        ) else AuthState.Error,
                        errorMessage = when (error) {
                            is CopilotException.NetworkException.NoConnection ->
                                "No connection. Check your network and try again."
                            is CopilotException.NetworkException.Timeout ->
                                "Request timed out. You can retry."
                            else -> error.message ?: "Polling failed"
                        },
                        deviceCode = if (shouldKeepDeviceCode) it.deviceCode else null
                    )}
                }
            )
        }
    }
    
    fun logout() {
        viewModelScope.launch {
            val result = copilot.logout()
            
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(
                        authState = AuthState.NotAuthenticated,
                        isAuthenticated = false,
                        errorMessage = null,
                        deviceCode = null
                    )}
                },
                onFailure = { error ->
                    _uiState.update { it.copy(
                        errorMessage = "Failed to logout: ${error.message}"
                    )}
                }
            )
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    fun cancelAuthentication() {
        _uiState.update { it.copy(
            authState = AuthState.NotAuthenticated,
            deviceCode = null,
            errorMessage = null
        )}
    }
}

data class CopilotSettingsUiState(
    val isAuthenticated: Boolean = false,
    val authState: AuthState = AuthState.NotAuthenticated,
    val deviceCode: AuthResult.DeviceCodeRequired? = null,
    val errorMessage: String? = null,
    val availableModels: List<CopilotModel> = emptyList(),
    val isLoadingModels: Boolean = false
)

sealed class AuthState {
    object NotAuthenticated : AuthState()
    object RequestingDeviceCode : AuthState()
    data class WaitingForUser(
        val userCode: String,
        val verificationUri: String,
        val expiresIn: Int
    ) : AuthState()
    object Polling : AuthState()
    object Authenticated : AuthState()
    object Error : AuthState()
}
