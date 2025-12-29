package com.glosdalen.app.libs.copilot.network

import com.glosdalen.app.libs.copilot.models.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Network API interfaces for Copilot Chat library
 */

// ================================
// GitHub OAuth API
// ================================

interface GitHubOAuthApiService {
    
    /**
     * Start OAuth device flow - Step 1: Get device code
     */
    @FormUrlEncoded
    @POST("login/device/code")
    suspend fun getDeviceCode(
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Field("client_id") clientId: String,
        @Field("scope") scope: String
    ): Response<DeviceCodeResponse>
    
    /**
     * OAuth device flow - Step 2: Poll for access token
     */
    @FormUrlEncoded
    @POST("login/oauth/access_token")
    suspend fun getAccessToken(
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Field("client_id") clientId: String,
        @Field("device_code") deviceCode: String,
        @Field("grant_type") grantType: String = "urn:ietf:params:oauth:grant-type:device_code"
    ): Response<OAuthToken>
}

// ================================
// GitHub API (for token exchange)
// ================================

interface GitHubApiService {
    
    /**
     * Exchange OAuth token for Copilot token
     * 
     * NOTE: This is a reverse-engineered GitHub Copilot internal API endpoint.
     * There is no official documentation for this API.
     * 
     * Endpoint: https://api.github.com/copilot_internal/v2/token
     * Method: GET
     * Auth: token {oauth_token}
     * User-Agent: GithubCopilot/1.155.0
     */
    @GET("copilot_internal/v2/token")
    suspend fun getCopilotToken(
        @Header("Authorization") authorization: String, // "token {oauth_token}"
        @Header("User-Agent") userAgent: String = "GithubCopilot/1.155.0"
    ): Response<CopilotToken>
}

// ================================
// Copilot Individual API
// ================================

interface CopilotApiService {
    
    /**
     * Get all available models with capabilities
     */
    @GET("models")
    suspend fun getModels(
        @Header("Authorization") authorization: String, // "Bearer {copilot_token}"
        @Header("User-Agent") userAgent: String = "GithubCopilot/1.155.0",
        @Header("Editor-Version") editorVersion: String = "vscode/1.85.0",
        @Header("Editor-Plugin-Version") pluginVersion: String = "copilot/1.155.0"
    ): Response<ModelsResponse>
    
    /**
     * Send chat completion request
     */
    @POST("chat/completions")
    suspend fun getChatCompletion(
        @Header("Authorization") authorization: String, // "Bearer {copilot_token}"
        @Header("Content-Type") contentType: String = "application/json",
        @Header("User-Agent") userAgent: String = "GithubCopilot/1.155.0",
        @Header("Editor-Version") editorVersion: String = "vscode/1.85.0",
        @Header("Editor-Plugin-Version") pluginVersion: String = "copilot/1.155.0",
        @Body request: ChatRequest
    ): Response<ChatResponse>
}

// ================================
// API Client Configuration
// ================================

object CopilotApiConstants {
    // OAuth & GitHub API
    const val GITHUB_BASE_URL = "https://github.com/"
    const val GITHUB_API_BASE_URL = "https://api.github.com/"
    
    // Copilot API
    const val COPILOT_API_BASE_URL = "https://api.githubcopilot.com/"
    
    // OAuth Configuration  
    const val CLIENT_ID = "Iv1.b507a08c87ecfe98"
    const val OAUTH_SCOPES = "read:user user:email copilot models"
    
    // Headers
    const val USER_AGENT = "GithubCopilot/1.155.0"
    const val EDITOR_VERSION = "vscode/1.85.0"
    const val EDITOR_PLUGIN_VERSION = "copilot/1.155.0"
    
    // Timeouts
    const val CONNECT_TIMEOUT = 30L // seconds
    const val READ_TIMEOUT = 60L // seconds
    const val WRITE_TIMEOUT = 60L // seconds
    
    // OAuth Device Flow
    const val DEVICE_CODE_POLL_INTERVAL = 5000L // milliseconds
    const val DEVICE_CODE_TIMEOUT = 600000L // 10 minutes
}

// ================================
// Request/Response Wrappers
// ================================

data class ApiRequest<T>(
    val url: String,
    val headers: Map<String, String>,
    val body: T? = null
)

data class ApiResponse<T>(
    val isSuccessful: Boolean,
    val code: Int,
    val data: T? = null,
    val errorBody: String? = null,
    val headers: Map<String, String> = emptyMap()
)

// ================================
// Streaming Support
// ================================

/**
 * For future streaming implementation
 */
interface StreamingCallback {
    fun onChunk(chunk: ChatChunk)
    fun onComplete()
    fun onError(error: Throwable)
}

// ================================
// Request Builder
// ================================

class CopilotRequestBuilder {
    private val headers = mutableMapOf<String, String>()
    
    fun withAuth(token: String) = apply {
        headers["Authorization"] = "Bearer $token"
    }
    
    fun withUserAgent(userAgent: String = CopilotApiConstants.USER_AGENT) = apply {
        headers["User-Agent"] = userAgent
    }
    
    fun withEditorHeaders(
        version: String = CopilotApiConstants.EDITOR_VERSION,
        pluginVersion: String = CopilotApiConstants.EDITOR_PLUGIN_VERSION
    ) = apply {
        headers["Editor-Version"] = version
        headers["Editor-Plugin-Version"] = pluginVersion
    }
    
    fun withContentType(contentType: String = "application/json") = apply {
        headers["Content-Type"] = contentType
    }
    
    fun build(): Map<String, String> = headers.toMap()
}