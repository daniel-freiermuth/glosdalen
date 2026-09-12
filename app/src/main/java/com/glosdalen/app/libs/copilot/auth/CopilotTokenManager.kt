package com.glosdalen.app.libs.copilot.auth

import android.util.Log
import com.glosdalen.app.libs.copilot.*
import com.glosdalen.app.libs.copilot.models.*
import com.glosdalen.app.libs.copilot.network.*
import com.glosdalen.app.libs.copilot.storage.CopilotStorage
import com.glosdalen.app.libs.copilot.util.TimeProvider
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
    private val storage: CopilotStorage,
    private val timeProvider: TimeProvider
) {

    companion object {
        private const val TAG = "CopilotTokenManager"
    }

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
        Log.d(TAG, "getValidCopilotToken: Starting...")
        return tokenMutex.withLock {
            // Check if current token is valid
            currentCopilotToken?.let { token ->
                Log.d(TAG, "getValidCopilotToken: Found cached token, checking validity...")
                if (isTokenValid(token)) {
                    Log.d(TAG, "getValidCopilotToken: Cached token is valid")
                    return@withLock token.asSuccess()
                } else {
                    Log.d(TAG, "getValidCopilotToken: Cached token is expired")
                }
            } ?: Log.d(TAG, "getValidCopilotToken: No cached token")

            // Try to load from storage
            Log.d(TAG, "getValidCopilotToken: Loading from storage...")
            val storedToken = storage.loadCopilotToken()
            if (storedToken != null) {
                Log.d(TAG, "getValidCopilotToken: Found stored token")
                // Parse expiration from token string if not already parsed
                val tokenWithExpiration = if (storedToken.expiresAt == null) {
                    val parsedExpiration = parseExpirationFromToken(storedToken.token)
                    Log.d(TAG, "getValidCopilotToken: Parsed expiration: $parsedExpiration")
                    storedToken.copy(expiresAt = parsedExpiration)
                } else {
                    storedToken
                }
                
                if (isTokenValid(tokenWithExpiration)) {
                    Log.d(TAG, "getValidCopilotToken: Stored token is valid")
                    currentCopilotToken = tokenWithExpiration
                    return@withLock tokenWithExpiration.asSuccess()
                } else {
                    Log.d(TAG, "getValidCopilotToken: Stored token is expired")
                }
            } else {
                Log.d(TAG, "getValidCopilotToken: No stored token found")
            }

            // Need to exchange/refresh token
            Log.d(TAG, "getValidCopilotToken: Exchanging/refreshing token...")
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
        Log.d(TAG, "exchangeOAuthToken: Starting token exchange...")
        return try {
            val accessToken = oauthToken.accessToken 
                ?: run {
                    Log.e(TAG, "exchangeOAuthToken: Access token is null!")
                    return CopilotException.AuthException.InvalidToken().asFailure()
                }
                
            // Validate OAuth token format
            if (accessToken.length < 20) {
                Log.e(TAG, "OAuth token seems too short: ${accessToken.length} chars")
                return CopilotException.AuthException.InvalidToken().asFailure()
            }
            Log.d(TAG, "exchangeOAuthToken: Calling GitHub API copilot_internal/v2/token...")
            val response = githubApiService.getCopilotToken(
                authorization = "token $accessToken"
            )
            Log.d(TAG, "exchangeOAuthToken: Got response code=${response.code()}, isSuccessful=${response.isSuccessful}")

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
                Log.e(TAG, "Token exchange failed with code $errorCode: $errorBody")
                
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
        Log.d(TAG, "exchangeOrRefreshToken: Getting OAuth token...")
        // Get fresh OAuth token
        val oauthResult = authManager.getOAuthToken()
        if (oauthResult.isFailure) {
            Log.e(TAG, "exchangeOrRefreshToken: Failed to get OAuth token: ${oauthResult.exceptionOrNull()?.message}")
            return Result.failure(oauthResult.exceptionOrNull()!!)
        }

        val oauthToken = oauthResult.getOrThrow()
        Log.d(TAG, "exchangeOrRefreshToken: Got OAuth token, length=${oauthToken.accessToken?.length ?: 0}")
        return exchangeOAuthToken(oauthToken)
    }

    private fun isTokenValid(token: CopilotToken): Boolean {
        val expiresAt = token.expiresAt ?: return true // No expiration means valid
        val currentTime = timeProvider.currentTimeMillis()
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
            Log.w(TAG, "Failed to parse expiration from token: $e")
            null
        }
    }
}