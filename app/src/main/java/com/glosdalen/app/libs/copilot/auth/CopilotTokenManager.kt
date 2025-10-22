package com.glosdalen.app.libs.copilot.auth

import com.glosdalen.app.libs.copilot.*
import com.glosdalen.app.libs.copilot.models.*
import com.glosdalen.app.libs.copilot.network.*
import com.glosdalen.app.libs.copilot.storage.CopilotStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Token Exchange and Management System
 * 
 * Handles:
 * 1. Exchange OAuth token → Copilot token
 * 2. Automatic token renewal
 * 3. Token validation and expiration
 * 4. Thread-safe token management
 */
@Singleton
class CopilotTokenManager @Inject constructor(
    private val githubApiService: GitHubApiService,
    private val authManager: CopilotAuthManager,
    private val storage: CopilotStorage
) {

    private var currentCopilotToken: CopilotToken? = null
    private val tokenMutex = Mutex()

    // Token expiration buffer - refresh 5 minutes before expiry
    private val EXPIRATION_BUFFER_MS = 5 * 60 * 1000L

    // ================================
    // Public API
    // ================================

    /**
     * Get valid Copilot token (handles exchange and renewal automatically)
     */
    suspend fun getValidCopilotToken(): Result<CopilotToken> {
        return tokenMutex.withLock {
            // Check if current token is valid
            currentCopilotToken?.let { token ->
                if (isTokenValid(token)) {
                    return@withLock token.asSuccess()
                }
            }

            // Try to load from storage
            val storedToken = storage.loadCopilotToken()
            if (storedToken != null) {
                // Parse expiration from token string if not already parsed
                val tokenWithExpiration = if (storedToken.expiresAt == null) {
                    val parsedExpiration = parseExpirationFromToken(storedToken.token)
                    storedToken.copy(expiresAt = parsedExpiration)
                } else {
                    storedToken
                }
                
                if (isTokenValid(tokenWithExpiration)) {
                    currentCopilotToken = tokenWithExpiration
                    return@withLock tokenWithExpiration.asSuccess()
                }
            }

            // Need to exchange/refresh token
            exchangeOrRefreshToken()
        }
    }

    /**
     * Force refresh of Copilot token
     */
    suspend fun refreshCopilotToken(): Result<CopilotToken> {
        return tokenMutex.withLock {
            exchangeOrRefreshToken()
        }
    }

    /**
     * Exchange OAuth token for Copilot token
     */
    suspend fun exchangeOAuthToken(oauthToken: OAuthToken): Result<CopilotToken> {
        return try {
            val accessToken = oauthToken.accessToken 
                ?: return CopilotException.AuthException.InvalidToken().asFailure()
                
            // Validate OAuth token format
            if (accessToken.length < 20) {
                android.util.Log.e("CopilotTokenManager", "OAuth token seems too short: ${accessToken.length} chars")
                return CopilotException.AuthException.InvalidToken().asFailure()
            }
            val response = githubApiService.getCopilotToken(
                authorization = "token $accessToken"
            )

            if (response.isSuccessful && response.body() != null) {
                val copilotToken = response.body()!!
                
                // Parse expiration from token string if not provided in response
                val tokenWithExpiration = if (copilotToken.expiresAt == null) {
                    val parsedExpiration = parseExpirationFromToken(copilotToken.token)
                    copilotToken.copy(expiresAt = parsedExpiration)
                } else {
                    copilotToken
                }
                
                // Store token
                currentCopilotToken = tokenWithExpiration
                storage.saveCopilotToken(tokenWithExpiration)
                
                tokenWithExpiration.asSuccess()
            } else {
                val errorCode = response.code()
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("CopilotTokenManager", "Token exchange failed with code $errorCode: $errorBody")
                
                when (errorCode) {
                    401 -> CopilotException.AuthException.InvalidToken().asFailure()
                    403 -> CopilotException.AuthException.AccessDenied().asFailure()
                    else -> CopilotException.AuthException.TokenExchangeFailed().asFailure()
                }
            }
        } catch (e: Exception) {
            CopilotException.AuthException.TokenExchangeFailed(e).asFailure()
        }
    }

    /**
     * Check if we have a valid Copilot token
     */
    suspend fun hasValidToken(): Boolean {
        return tokenMutex.withLock {
            currentCopilotToken?.let { isTokenValid(it) } ?: false ||
            storage.loadCopilotToken()?.let { isTokenValid(it) } ?: false
        }
    }

    /**
     * Clear all Copilot tokens
     */
    suspend fun clearTokens(): Result<Unit> {
        return tokenMutex.withLock {
            try {
                currentCopilotToken = null
                storage.clearCopilotToken()
                Unit.asSuccess()
            } catch (e: Exception) {
                CopilotException.StorageException.SaveFailed("clear_copilot_token", e).asFailure()
            }
        }
    }

    /**
     * Get token for API authorization header
     */
    suspend fun getAuthorizationHeader(): Result<String> {
        return getValidCopilotToken().map { token ->
            "Bearer ${token.token}"
        }
    }

    // ================================
    // Private Helper Methods
    // ================================

    private suspend fun exchangeOrRefreshToken(): Result<CopilotToken> {
        // Get fresh OAuth token
        val oauthResult = authManager.getOAuthToken()
        if (oauthResult.isFailure) {
            return Result.failure(oauthResult.exceptionOrNull()!!)
        }

        val oauthToken = oauthResult.getOrThrow()
        return exchangeOAuthToken(oauthToken)
    }

    private fun isTokenValid(token: CopilotToken): Boolean {
        val expiresAt = token.expiresAt ?: return true // No expiration means valid
        val currentTime = System.currentTimeMillis()
        // Convert expiresAt from seconds to milliseconds to match System.currentTimeMillis()
        val expiresAtMillis = expiresAt * 1000L
        return currentTime < (expiresAtMillis - EXPIRATION_BUFFER_MS)
    }

    /**
     * Parse expiration timestamp from Copilot token string
     * Token format: tid=...;exp=1760359694;sku=...
     */
    private fun parseExpirationFromToken(token: String): Long? {
        return try {
            val expMatch = Regex("exp=(\\d+)").find(token)
            expMatch?.groupValues?.get(1)?.toLongOrNull()
        } catch (e: Exception) {
            android.util.Log.w("CopilotTokenManager", "Failed to parse expiration from token: $e")
            null
        }
    }

    /**
     * Get time until token expires (in milliseconds)
     */
    fun getTimeUntilExpiration(token: CopilotToken): Long? {
        val expiresAt = token.expiresAt ?: return null
        val currentTime = System.currentTimeMillis()
        // Convert expiresAt from seconds to milliseconds
        val expiresAtMillis = expiresAt * 1000L
        return maxOf(0, expiresAtMillis - currentTime)
    }
}

// ================================
// Token Validation Utilities
// ================================

object TokenValidator {
    
    /**
     * Validate OAuth token format
     */
    fun isValidOAuthToken(token: OAuthToken): Boolean {
        return token.accessToken?.isNotBlank() == true && 
               token.tokenType.equals("Bearer", ignoreCase = true)
    }

    /**
     * Validate Copilot token format
     */
    fun isValidCopilotToken(token: CopilotToken): Boolean {
        return token.token.isNotBlank() && 
               token.token.startsWith("ghs_") // GitHub token prefix
    }

    /**
     * Check if token is close to expiration
     */
    fun isTokenNearExpiration(token: CopilotToken, bufferMs: Long = 5 * 60 * 1000L): Boolean {
        val expiresAt = token.expiresAt ?: return false
        val currentTime = System.currentTimeMillis()
        return currentTime > (expiresAt - bufferMs)
    }

    /**
     * Get human-readable expiration status
     */
    fun getExpirationStatus(token: CopilotToken): String {
        val expiresAt = token.expiresAt ?: return "No expiration"
        val currentTime = System.currentTimeMillis()
        
        return when {
            currentTime > expiresAt -> "Expired"
            isTokenNearExpiration(token) -> "Expires soon"
            else -> {
                val remainingMs = expiresAt - currentTime
                val remainingMinutes = remainingMs / (60 * 1000)
                when {
                    remainingMinutes < 60 -> "${remainingMinutes}m remaining"
                    remainingMinutes < 1440 -> "${remainingMinutes / 60}h remaining"
                    else -> "${remainingMinutes / 1440}d remaining"
                }
            }
        }
    }
}