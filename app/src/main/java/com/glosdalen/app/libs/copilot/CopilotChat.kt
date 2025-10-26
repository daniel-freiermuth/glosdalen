package com.glosdalen.app.libs.copilot

import android.content.Context
import com.glosdalen.app.libs.copilot.auth.*
import com.glosdalen.app.libs.copilot.chat.*
import com.glosdalen.app.libs.copilot.models.*
import com.glosdalen.app.libs.copilot.network.*
import com.glosdalen.app.libs.copilot.storage.*
import com.glosdalen.app.libs.copilot.util.*
import kotlinx.coroutines.flow.Flow
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Main entry point for the Copilot Chat Library
 * 
 * Provides a clean, easy-to-use API for integrating GitHub Copilot Chat
 * functionality into Android applications.
 */
@Singleton
class CopilotChat private constructor(
    private val context: Context,
    private val configuration: CopilotConfiguration
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
    // Simple API (for basic usage)
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
        modelId: String? = null
    ): Result<String> {
        return chatManager.sendMessage(message, modelId)
    }

    /**
     * Get available models
     */
    suspend fun getModels(): Result<List<CopilotModel>> {
        return modelManager.getAvailableModels()
    }

    /**
     * Get only free models
     */
    suspend fun getFreeModels(): Result<List<CopilotModel>> {
        return modelManager.getFreeModels()
    }

    /**
     * Get recommended models
     */
    suspend fun getRecommendedModels(): Result<List<CopilotModel>> {
        return modelManager.getRecommendedModels()
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
    // Advanced API (for complex usage)
    // ================================

    /**
     * Get authentication manager for advanced auth operations
     */
    fun auth(): CopilotAuthManager = authManager

    /**
     * Get token manager for advanced token operations
     */
    fun tokens(): CopilotTokenManager = tokenManager

    /**
     * Get model manager for advanced model operations
     */
    fun models(): CopilotModelManager = modelManager

    /**
     * Get chat manager for advanced chat operations
     */
    fun chat(): CopilotChatManager = chatManager

    /**
     * Get storage for advanced data operations
     */
    fun storage(): CopilotStorage = storage

    // ================================
    // Streaming API (future enhancement)
    // ================================

    /**
     * Send streaming chat message
     */
    suspend fun chatStream(
        message: String,
        modelId: String? = null
    ): Flow<String> {
        return chatManager.sendStreamingMessage(message, modelId)
    }

    // ================================
    // Library Information
    // ================================

    /**
     * Get library configuration
     */
    fun getConfiguration(): CopilotConfiguration = configuration

    /**
     * Get current status
     */
    suspend fun getStatus(): CopilotStatus {
        val isAuth = isAuthenticated()
        val hasValidToken = tokenManager.hasValidToken()
        val storageInfo = storage.getStorageInfo()
        
        val modelStats = if (isAuth && hasValidToken) {
            modelManager.getModelStatistics().getOrNull()
        } else null

        return CopilotStatus(
            isAuthenticated = isAuth,
            hasValidTokens = hasValidToken,
            totalModels = modelStats?.totalModels ?: 0,
            freeModels = modelStats?.freeModels ?: 0,
            storageInfo = storageInfo
        )
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
            .connectTimeout(configuration.connectionTimeout, TimeUnit.SECONDS)
            .readTimeout(configuration.readTimeout, TimeUnit.SECONDS)
            .writeTimeout(CopilotApiConstants.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }

    private fun createJsonConverter() = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }.asConverterFactory("application/json".toMediaType())

    // ================================
    // Builder Pattern
    // ================================

    class Builder(private val context: Context) {
        private var enableDebugLogging = false
        private var customUserAgent: String? = null
        private var connectionTimeout = CopilotApiConstants.CONNECT_TIMEOUT
        private var readTimeout = CopilotApiConstants.READ_TIMEOUT

        fun enableDebugLogging(enable: Boolean) = apply {
            enableDebugLogging = enable
        }

        fun setUserAgent(userAgent: String) = apply {
            customUserAgent = userAgent
        }

        fun setConnectionTimeout(timeoutSeconds: Long) = apply {
            connectionTimeout = timeoutSeconds
        }

        fun setReadTimeout(timeoutSeconds: Long) = apply {
            readTimeout = timeoutSeconds
        }

        fun build(): CopilotChat {
            val configuration = CopilotConfiguration(
                enableDebugLogging = enableDebugLogging,
                userAgent = customUserAgent ?: CopilotApiConstants.USER_AGENT,
                connectionTimeout = connectionTimeout,
                readTimeout = readTimeout
            )

            return CopilotChat(context.applicationContext, configuration)
        }
    }

    companion object {
        /**
         * Create a new builder
         */
        fun builder(context: Context): Builder = Builder(context)

        /**
         * Quick setup with default configuration
         */
        fun create(context: Context): CopilotChat = builder(context).build()
    }
}

// ================================
// Supporting Data Classes
// ================================

data class CopilotConfiguration(
    val enableDebugLogging: Boolean = false,
    val userAgent: String = CopilotApiConstants.USER_AGENT,
    val connectionTimeout: Long = CopilotApiConstants.CONNECT_TIMEOUT,
    val readTimeout: Long = CopilotApiConstants.READ_TIMEOUT
)

data class CopilotStatus(
    val isAuthenticated: Boolean,
    val hasValidTokens: Boolean,
    val totalModels: Int,
    val freeModels: Int,
    val storageInfo: StorageInfo
)

