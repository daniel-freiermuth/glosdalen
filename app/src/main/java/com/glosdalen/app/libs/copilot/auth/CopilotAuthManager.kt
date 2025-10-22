package com.glosdalen.app.libs.copilot.auth

import com.glosdalen.app.libs.copilot.*
import com.glosdalen.app.libs.copilot.models.*
import com.glosdalen.app.libs.copilot.network.*
import com.glosdalen.app.libs.copilot.storage.CopilotStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import java.sql.ResultSet

/**
 * OAuth Device Flow Authentication Manager
 * 
 * Handles the complete GitHub OAuth device flow:
 * 1. Request device code
 * 2. Present user code to user
 * 3. Poll for authorization
 * 4. Store and manage tokens
 */
@Singleton
class CopilotAuthManager @Inject constructor(
    private val oauthApiService: GitHubOAuthApiService,
    private val storage: CopilotStorage
) {

    private var currentOAuthToken: OAuthToken? = null
    private var deviceCodeData: DeviceCodeData? = null

    // ================================
    // Public API
    // ================================

    /**
     * Start the OAuth device flow
     * Returns device code information for user display
     */
    suspend fun initiateAuth(): Result<DeviceCodeData> {
        return try {
            val response = oauthApiService.getDeviceCode(
                clientId = CopilotApiConstants.CLIENT_ID,
                scope = CopilotApiConstants.OAUTH_SCOPES
            )

            if (response.isSuccessful && response.body() != null) {
                val deviceCode = response.body()!!
                val deviceCodeData = DeviceCodeData(
                    deviceCode = deviceCode.deviceCode,
                    userCode = deviceCode.userCode,
                    verificationUri = deviceCode.verificationUri,
                    expiresIn = deviceCode.expiresIn,
                    interval = deviceCode.interval,
                    createdAt = System.currentTimeMillis()
                )
                
                this.deviceCodeData = deviceCodeData
                deviceCodeData.asSuccess()
            } else {
                val error = parseErrorResponse(response.errorBody()?.string())
                CopilotException.AuthException.InvalidDeviceCode().asFailure()
            }
        } catch (e: Exception) {
            when (e) {
                is java.net.UnknownHostException -> 
                    CopilotException.NetworkException.NoConnection().asFailure()
                is java.net.SocketTimeoutException -> 
                    CopilotException.NetworkException.Timeout().asFailure()
                is javax.net.ssl.SSLException -> 
                    CopilotException.NetworkException.BadRequest("SSL/TLS error: ${e.message}").asFailure()
                else -> 
                    CopilotException.NetworkException.BadRequest("Network error: ${e.message}").asFailure()
            }
        }
    }

    /**
     * Poll for OAuth token using device code
     * This should be called repeatedly until success or timeout
     */
    suspend fun pollForToken(): Result<OAuthToken> {
        val deviceData = deviceCodeData 
            ?: return CopilotException.AuthException.InvalidDeviceCode().asFailure()

        // Check if device code has expired
        val elapsedTime = System.currentTimeMillis() - deviceData.createdAt
        if (elapsedTime > deviceData.expiresIn * 1000) {
            return CopilotException.AuthException.DeviceCodeExpired().asFailure()
        }

        return try {
            val response = oauthApiService.getAccessToken(
                clientId = CopilotApiConstants.CLIENT_ID,
                deviceCode = deviceData.deviceCode
            )

            when (response.code()) {
                200 -> {
                    val tokenResponse = response.body()!!
                    
                    // Check if we have a successful token or an error response
                    when {
                        tokenResponse.accessToken != null -> {
                            // Success: we have an access token
                            currentOAuthToken = tokenResponse
                            storage.saveOAuthToken(tokenResponse)
                            tokenResponse.asSuccess()
                        }
                        tokenResponse.error == "authorization_pending" -> 
                            CopilotException.AuthException.AuthorizationPending().asFailure()
                        tokenResponse.error == "access_denied" -> 
                            CopilotException.AuthException.AccessDenied().asFailure()
                        tokenResponse.error == "expired_token" ->
                            CopilotException.AuthException.DeviceCodeExpired().asFailure()
                        tokenResponse.error == "slow_down" -> 
                            CopilotException.AuthException.AuthorizationPending().asFailure()
                        else -> 
                            CopilotException.AuthException.InvalidDeviceCode().asFailure()
                    }
                }
                400 -> {
                    // Fallback: Parse the specific error from error body
                    val errorBody = response.errorBody()?.string()
                    when {
                        errorBody?.contains("authorization_pending") == true -> 
                            CopilotException.AuthException.AuthorizationPending().asFailure()
                        errorBody?.contains("access_denied") == true -> 
                            CopilotException.AuthException.AccessDenied().asFailure()
                        else -> 
                            CopilotException.AuthException.InvalidDeviceCode().asFailure()
                    }
                }
                else -> CopilotException.NetworkException.ServerError(response.code()).asFailure()
            }
        } catch (e: Exception) {
            when (e) {
                is java.net.UnknownHostException -> 
                    CopilotException.NetworkException.NoConnection().asFailure()
                is java.net.SocketTimeoutException -> 
                    CopilotException.NetworkException.Timeout().asFailure()
                is javax.net.ssl.SSLException -> 
                    CopilotException.NetworkException.BadRequest("SSL/TLS error: ${e.message}").asFailure()
                else -> 
                    CopilotException.NetworkException.BadRequest("Network error: ${e.message}").asFailure()
            }
        }
    }

    /**
     * Poll for token with automatic retry and timeout
     */
    suspend fun pollForTokenWithRetry(): Result<OAuthToken> {
        val deviceData = deviceCodeData 
            ?: return Result.failure(CopilotException.AuthException.InvalidDeviceCode())

        return try {
            withTimeout(CopilotApiConstants.DEVICE_CODE_TIMEOUT) {
                while (true) {
                    val result = pollForToken()
                    if (result.isSuccess) {
                        return@withTimeout result
                    }
                    
                    when (result.exceptionOrNull()) {
                        is CopilotException.AuthException.AuthorizationPending -> {
                            // Continue polling
                            delay(deviceData.interval * 1000L)
                        }
                        else -> return@withTimeout result
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                Result.failure(CopilotException.AuthException.DeviceCodeExpired()) // Never reached
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(CopilotException.AuthException.DeviceCodeExpired())
        }
    }

    /**
     * Check if user is currently authenticated
     */
    suspend fun isAuthenticated(): Boolean {
        return try {
            val token = getValidOAuthToken()
            token != null && !isTokenExpired(token)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get current OAuth token (loads from storage if needed)
     */
    suspend fun getOAuthToken(): Result<OAuthToken> {
        return try {
            val token = getValidOAuthToken()
            if (token != null) {
                token.asSuccess()
            } else {
                CopilotException.AuthException.TokenExpired().asFailure()
            }
        } catch (e: Exception) {
            CopilotException.StorageException.LoadFailed("oauth_token", e).asFailure()
        }
    }

    /**
     * Clear all authentication data
     */
    suspend fun logout(): Result<Unit> {
        return try {
            currentOAuthToken = null
            deviceCodeData = null
            storage.clearAllData()
            Unit.asSuccess()
        } catch (e: Exception) {
            CopilotException.StorageException.SaveFailed("clear_auth", e).asFailure()
        }
    }

    // ================================
    // Private Helper Methods
    // ================================

    private suspend fun getValidOAuthToken(): OAuthToken? {
        // Return cached token if valid
        currentOAuthToken?.let { token ->
            if (!isTokenExpired(token)) {
                return token
            }
        }

        // Load from storage
        val storedToken = storage.loadOAuthToken()
        if (storedToken != null && !isTokenExpired(storedToken)) {
            currentOAuthToken = storedToken
            return storedToken
        }

        return null
    }

    private fun isTokenExpired(token: OAuthToken): Boolean {
        val expiresAt = token.expiresAt ?: return false
        return System.currentTimeMillis() > expiresAt
    }

    private fun parseErrorResponse(errorBody: String?): String {
        return try {
            // Parse JSON error response if available
            errorBody ?: "Unknown error"
        } catch (e: Exception) {
            errorBody ?: "Unknown error"
        }
    }
}

// ================================
// Supporting Data Classes
// ================================

data class DeviceCodeData(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val interval: Int,
    val createdAt: Long
) {
    fun isExpired(): Boolean {
        val elapsedTime = System.currentTimeMillis() - createdAt
        return elapsedTime > expiresIn * 1000
    }
    
    fun getRemainingTime(): Long {
        val elapsedTime = System.currentTimeMillis() - createdAt
        val remainingMs = (expiresIn * 1000) - elapsedTime
        return maxOf(0, remainingMs / 1000) // Return remaining seconds
    }
}