package com.glosdalen.app.libs.copilot

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.glosdalen.app.libs.copilot.auth.*
import com.glosdalen.app.libs.copilot.chat.*
import com.glosdalen.app.libs.copilot.models.*
import com.glosdalen.app.libs.copilot.network.*
import com.glosdalen.app.libs.copilot.storage.*
import com.glosdalen.app.libs.copilot.util.*
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main entry point for the Copilot Chat Library
 * 
 * Provides a clean, easy-to-use API for integrating GitHub Copilot Chat
 * functionality into Android applications.
 */
@Singleton
class CopilotChat @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // Core managers (lazy initialization)
    private val timeProvider by lazy { SystemTimeProvider() }
    private val storage by lazy { CopilotStorage(context, timeProvider) }
    private val authManager by lazy { CopilotAuthManager(oauthApiService, storage, timeProvider) }
    private val tokenManager by lazy { CopilotTokenManager(githubApiService, authManager, storage, timeProvider) }
    private val modelManager by lazy { CopilotModelManager(copilotApiService, tokenManager, storage, timeProvider) }
    private val chatManager by lazy { CopilotChatManager(copilotApiService, tokenManager, modelManager) }

    // Network services (lazy initialization)
    private val oauthApiService by lazy { createOAuthApiService() }
    private val githubApiService by lazy { createGitHubApiService() }
    private val copilotApiService by lazy { createCopilotApiService() }

    // ================================
    // Public API
    // ================================

    /**
     * Check if user is authenticated
     */
    suspend fun isAuthenticated(): Boolean {
        return authManager.isAuthenticated()
    }

    /**
     * Start authentication process
     * Returns device code information for user display
     */
    suspend fun authenticate(): Result<AuthResult.DeviceCodeRequired> {
        return authManager
          .initiateAuth()
          .fold(
            onSuccess = { data ->
                Result.success(AuthResult.DeviceCodeRequired(
                    deviceCode = data.deviceCode,
                    userCode = data.userCode,
                    verificationUri = data.verificationUri,
                    expiresIn = data.expiresIn
                ))
            },
            onFailure = { error ->
                Result.failure(error)
            }
          )
    }

    /**
     * Complete authentication by polling for token
     */
    suspend fun completeAuthentication(): Result<AuthResult> {
        return authManager
          .pollForTokenWithRetry()
          .fold(
            onSuccess = { Result.success(AuthResult.Success) },
            onFailure = { error ->
                when (error) {
                    is CopilotException.AuthException -> Result.success(AuthResult.Failed(error))
                    else -> Result.failure(error)
                }
            }
          )
    }

    /**
     * Send a simple chat message
     */
    suspend fun chat(
        message: String,
        modelId: String? = null,
        temperature: Float? = null
    ): Result<String> {
        return chatManager.sendMessage(
            message = message,
            modelId = modelId,
            temperature = temperature
        )
    }

    /**
     * Get available models
     */
    suspend fun getModels(): Result<List<CopilotModel>> {
        return modelManager.getAvailableModels()
    }

    /**
     * Force refresh models bypassing all caches
     */
    suspend fun refreshModels(): Result<List<CopilotModel>> {
        return modelManager.refreshModels()
    }

    /**
     * Logout and clear all data
     */
    suspend fun logout(): Result<Unit> {
        return authManager.logout().onSuccess {
            tokenManager.clearTokens()
            storage.clearAllData()
        }
    }

    // ================================
    // Network Service Creation
    // ================================

    private fun createOAuthApiService(): GitHubOAuthApiService {
        val client = createHttpClient()
        val retrofit = Retrofit.Builder()
            .baseUrl(CopilotApiConstants.GITHUB_BASE_URL)
            .client(client)
            .addConverterFactory(createJsonConverter())
            .build()
        
        return retrofit.create(GitHubOAuthApiService::class.java)
    }

    private fun createGitHubApiService(): GitHubApiService {
        val client = createHttpClient()
        val retrofit = Retrofit.Builder()
            .baseUrl(CopilotApiConstants.GITHUB_API_BASE_URL)
            .client(client)
            .addConverterFactory(createJsonConverter())
            .build()
        
        return retrofit.create(GitHubApiService::class.java)
    }

    private fun createCopilotApiService(): CopilotApiService {
        val client = createHttpClient()
        val retrofit = Retrofit.Builder()
            .baseUrl(CopilotApiConstants.COPILOT_API_BASE_URL)
            .client(client)
            .addConverterFactory(createJsonConverter())
            .build()
        
        return retrofit.create(CopilotApiService::class.java)
    }

    private fun createHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(CopilotApiConstants.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(CopilotApiConstants.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(CopilotApiConstants.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }

    private fun createJsonConverter() = 
        json.asConverterFactory("application/json".toMediaType())

    companion object {
        // Shared Json instance to avoid redundant creation (performance optimization)
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
