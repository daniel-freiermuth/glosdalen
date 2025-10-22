package com.glosdalen.app.libs.copilot

/**
 * Comprehensive error handling and result types for Copilot Chat library
 */

// ================================
// Exception Hierarchy
// ================================

sealed class CopilotException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * Authentication related errors
     */
    sealed class AuthException(message: String, cause: Throwable? = null) : CopilotException(message, cause) {
        class DeviceCodeExpired : AuthException("Device code has expired. Please restart authentication.")
        class InvalidDeviceCode : AuthException("Invalid device code provided.")
        class AuthorizationPending : AuthException("User has not yet authorized the device code.")
        class AccessDenied : AuthException("User denied access to the application.")
        class TokenExpired : AuthException("Authentication token has expired.")
        class InvalidToken : AuthException("Authentication token is invalid or malformed.")
        class TokenExchangeFailed(cause: Throwable? = null) : AuthException("Failed to exchange OAuth token for Copilot token.", cause)
    }
    
    /**
     * Network and API related errors
     */
    sealed class NetworkException(message: String, cause: Throwable? = null) : CopilotException(message, cause) {
        class NoConnection : NetworkException("No internet connection available.")
        class Timeout : NetworkException("Request timed out.")
        class RateLimited(val retryAfter: Long? = null) : NetworkException("Rate limit exceeded. Retry after: ${retryAfter}s")
        class ServerError(val code: Int, val body: String? = null) : NetworkException("Server error: $code - $body")
        class BadRequest(val details: String? = null) : NetworkException("Bad request: $details")
        class Unauthorized : NetworkException("Request unauthorized. Check authentication.")
        class Forbidden : NetworkException("Request forbidden. Check permissions.")
        class NotFound : NetworkException("Requested resource not found.")
    }
    
    /**
     * Model related errors
     */
    sealed class ModelException(message: String, cause: Throwable? = null) : CopilotException(message, cause) {
        class ModelNotFound(val modelId: String) : ModelException("Model not found: $modelId")
        class ModelNotAvailable(val modelId: String) : ModelException("Model not available: $modelId")
        class ModelDiscoveryFailed(cause: Throwable? = null) : ModelException("Failed to discover available models.", cause)
        class UnsupportedFeature(val feature: String, val modelId: String) : ModelException("Feature '$feature' not supported by model: $modelId")
        class TokenLimitExceeded(val requested: Int, val limit: Int) : ModelException("Token limit exceeded: requested $requested, limit is $limit")
        class QuotaExceeded(val modelId: String) : ModelException("Usage quota exceeded for model: $modelId")
    }
    
    /**
     * Chat related errors
     */
    sealed class ChatException(message: String, cause: Throwable? = null) : CopilotException(message, cause) {
        class EmptyMessage : ChatException("Message cannot be empty.")
        class InvalidMessage(val details: String) : ChatException("Invalid message format: $details")
        class ResponseParsingFailed(cause: Throwable? = null) : ChatException("Failed to parse chat response.", cause)
        class StreamingFailed(cause: Throwable? = null) : ChatException("Streaming connection failed.", cause)
        class ContentFiltered : ChatException("Content was filtered by safety systems.")
        class ContextLengthExceeded(val length: Int, val limit: Int) : ChatException("Context length $length exceeds limit of $limit tokens")
    }
    
    /**
     * Storage related errors
     */
    sealed class StorageException(message: String, cause: Throwable? = null) : CopilotException(message, cause) {
        class EncryptionFailed(cause: Throwable? = null) : StorageException("Failed to encrypt data.", cause)
        class DecryptionFailed(cause: Throwable? = null) : StorageException("Failed to decrypt data.", cause)
        class SaveFailed(val key: String, cause: Throwable? = null) : StorageException("Failed to save data for key: $key", cause)
        class LoadFailed(val key: String, cause: Throwable? = null) : StorageException("Failed to load data for key: $key", cause)
        class CorruptedData(val key: String) : StorageException("Corrupted data found for key: $key")
    }
    
    /**
     * Configuration and setup errors
     */
    sealed class ConfigException(message: String, cause: Throwable? = null) : CopilotException(message, cause) {
        class InvalidClientId : ConfigException("Invalid or missing client ID.")
        class InvalidConfiguration(val details: String) : ConfigException("Invalid configuration: $details")
        class LibraryNotInitialized : ConfigException("Copilot library not initialized. Call CopilotChat.builder().build() first.")
    }
    
    /**
     * Unknown or unexpected errors
     */
    class UnknownException(message: String = "An unknown error occurred.", cause: Throwable? = null) : CopilotException(message, cause)
}

// ================================
// Auth Result Types
// ================================

sealed class AuthResult {
    object Success : AuthResult()
    data class DeviceCodeRequired(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val expiresIn: Int
    ) : AuthResult()
    data class Failed(val error: CopilotException.AuthException) : AuthResult()
}

// ================================
// Extension Functions
// ================================

/**
 * Create success result
 */
fun <T> T.asSuccess(): Result<T> = Result.success(this)

/**
 * Create failure result
 */
fun CopilotException.asFailure(): Result<Nothing> = Result.failure(this)