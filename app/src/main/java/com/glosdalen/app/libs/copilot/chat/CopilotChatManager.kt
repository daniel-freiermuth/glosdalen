package com.glosdalen.app.libs.copilot.chat

import com.glosdalen.app.libs.copilot.*
import com.glosdalen.app.libs.copilot.auth.CopilotTokenManager
import com.glosdalen.app.libs.copilot.models.*
import com.glosdalen.app.libs.copilot.network.CopilotApiService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Chat Request Handler with streaming support, error handling, and retry logic
 * 
 * Provides robust API communication with:
 * - Automatic retry with exponential backoff
 * - Request validation and sanitization
 * - Comprehensive error handling
 * - Future streaming support
 */
@Singleton
class CopilotChatManager @Inject constructor(
    private val copilotApiService: CopilotApiService,
    private val tokenManager: CopilotTokenManager,
    private val modelManager: CopilotModelManager
) {

    // ================================
    // Public API
    // ================================

    /**
     * Send a simple text message and return response
     */
    suspend fun sendMessage(
        message: String,
        modelId: String? = null,
        maxTokens: Int = 500,
        temperature: Float? = null
    ): Result<String> {
        val request = ChatRequest(
            messages = listOf(Message(role = "user", content = message)),
            model = modelId ?: getDefaultModel(),
            maxTokens = maxTokens,
            temperature = (temperature ?: 0.1f).toDouble(),
            stream = false
        )

        return sendChatRequest(request).map { response ->
            response.choices.firstOrNull()?.message?.content ?: ""
        }
    }

    /**
     * Send a chat request with full control
     */
    suspend fun sendChatRequest(request: ChatRequest): Result<ChatResponse> {
        // Validate request
        val validationResult = validateChatRequest(request)
        if (validationResult.isFailure) {
            return Result.failure(validationResult.exceptionOrNull()!!)
        }

        // Validate model exists and is available
        val modelResult = modelManager.getModelById(request.model)
        if (modelResult.isFailure) {
            return Result.failure(modelResult.exceptionOrNull()!!)
        }

        val model = modelResult.getOrThrow()
        
        // Check token limits
        val tokenCheckResult = validateTokenLimits(request, model)
        if (tokenCheckResult.isFailure) {
            return Result.failure(tokenCheckResult.exceptionOrNull()!!)
        }

        // Execute request with retry logic
        return executeWithRetry {
            performChatRequest(request)
        }
    }

    /**
     * Send multiple requests in batch (for efficiency)
     */
    suspend fun sendBatchRequests(requests: List<ChatRequest>): Result<List<ChatResponse>> {
        if (requests.isEmpty()) {
            return emptyList<ChatResponse>().asSuccess()
        }

        // Validate all requests first
        for (request in requests) {
            val validationResult = validateChatRequest(request)
            if (validationResult.isFailure) {
                return Result.failure(validationResult.exceptionOrNull()!!)
            }
        }

        // Execute requests sequentially (could be made parallel in future)
        val responses = mutableListOf<ChatResponse>()
        
        for (request in requests) {
            val result = sendChatRequest(request)
            if (result.isSuccess) {
                responses.add(result.getOrThrow())
            } else {
                return Result.failure(result.exceptionOrNull()!!)
            }
            
            // Small delay between requests to be respectful
            delay(100)
        }

        return Result.success(responses)
    }

    /**
     * Future: Streaming chat support
     */
    suspend fun sendStreamingMessage(
        message: String,
        modelId: String? = null,
        maxTokens: Int = 150,
        temperature: Float = 0.7f
    ): Flow<String> = flow {
        // This would be implemented when streaming is needed
        // For now, fall back to regular request
        val result = sendMessage(message, modelId, maxTokens, temperature)
        if (result.isSuccess) {
            emit(result.getOrThrow())
        } else {
            throw result.exceptionOrNull()!!
        }
    }

    // ================================
    // Private Implementation
    // ================================

    private suspend fun performChatRequest(request: ChatRequest): Result<ChatResponse> {
        return try {
            // Get authorization header
            val authResult = tokenManager.getAuthorizationHeader()
            if (authResult.isFailure) {
                return Result.failure(authResult.exceptionOrNull()!!)
            }

            val authHeader = authResult.getOrThrow()

            // Make API call
            val response = copilotApiService.getChatCompletion(
                authorization = authHeader,
                request = request
            )

            when {
                response.isSuccessful && response.body() != null -> {
                    val chatResponse = response.body()!!
                    
                    // Validate response
                    if (chatResponse.choices.isEmpty()) {
                        CopilotException.ChatException.ResponseParsingFailed().asFailure()
                    } else {
                        chatResponse.asSuccess()
                    }
                }
                
                response.code() == 401 -> {
                    CopilotException.NetworkException.Unauthorized().asFailure()
                }
                
                response.code() == 403 -> {
                    CopilotException.NetworkException.Forbidden().asFailure()
                }
                
                response.code() == 404 -> {
                    CopilotException.ModelException.ModelNotFound(request.model).asFailure()
                }
                
                response.code() == 429 -> {
                    val retryAfter = response.headers()["Retry-After"]?.toLongOrNull()
                    CopilotException.NetworkException.RateLimited(retryAfter).asFailure()
                }
                
                response.code() in 500..599 -> {
                    val errorBody = response.errorBody()?.string()
                    CopilotException.NetworkException.ServerError(response.code(), errorBody).asFailure()
                }
                
                else -> {
                    val errorBody = response.errorBody()?.string()
                    CopilotException.NetworkException.BadRequest(errorBody).asFailure()
                }
            }
        } catch (e: Exception) {
            when (e) {
                is CopilotException -> e.asFailure()
                is java.net.SocketTimeoutException -> CopilotException.NetworkException.Timeout().asFailure()
                is java.net.UnknownHostException -> CopilotException.NetworkException.NoConnection().asFailure()
                else -> CopilotException.ChatException.ResponseParsingFailed(e).asFailure()
            }
        }
    }

    private fun validateChatRequest(request: ChatRequest): Result<Unit> {
        // Check messages
        if (request.messages.isEmpty()) {
            return CopilotException.ChatException.EmptyMessage().asFailure()
        }

        // Check message content
        for (message in request.messages) {
            if (message.content.isBlank()) {
                return CopilotException.ChatException.EmptyMessage().asFailure()
            }
            
            if (message.role !in listOf("user", "assistant", "system")) {
                return CopilotException.ChatException.InvalidMessage("Invalid role: ${message.role}").asFailure()
            }
        }

        // Check model ID
        if (request.model.isBlank()) {
            return CopilotException.ChatException.InvalidMessage("Model ID cannot be blank").asFailure()
        }

        // Check token limits
        if (request.maxTokens <= 0 || request.maxTokens > 100000) {
            return CopilotException.ChatException.InvalidMessage("Max tokens must be between 1 and 100000").asFailure()
        }

        // Check temperature
        if (request.temperature < 0.0 || request.temperature > 2.0) {
            return CopilotException.ChatException.InvalidMessage("Temperature must be between 0.0 and 2.0").asFailure()
        }

        return Unit.asSuccess()
    }

    private suspend fun validateTokenLimits(request: ChatRequest, model: CopilotModel): Result<Unit> {
        val limits = model.capabilities.limits
        
        // Check max output tokens
        val maxOutputTokens = limits?.maxOutputTokens ?: Int.MAX_VALUE
        if (request.maxTokens > maxOutputTokens) {
            return CopilotException.ModelException.TokenLimitExceeded(
                request.maxTokens, 
                maxOutputTokens
            ).asFailure()
        }

        // Estimate input token count (rough approximation: 1 token ≈ 4 characters)
        val inputText = request.messages.joinToString(" ") { it.content }
        val estimatedInputTokens = inputText.length / 4

        val maxContextTokens = limits?.maxContextTokens ?: Int.MAX_VALUE
        if (estimatedInputTokens > maxContextTokens) {
            return CopilotException.ChatException.ContextLengthExceeded(
                estimatedInputTokens,
                maxContextTokens
            ).asFailure()
        }

        return Unit.asSuccess()
    }

    private suspend fun getDefaultModel(): String {
        // Try to get a free model as default
        val result = modelManager.getFreeModels()
        return result.fold(
            onSuccess = { freeModels ->
                // Prefer GPT-4o if available, otherwise first free model
                freeModels.find { it.id == "gpt-4o" }?.id 
                    ?: freeModels.firstOrNull()?.id 
                    ?: "gpt-4o" // Fallback
            },
            onFailure = {
                "gpt-4o" // Fallback
            }
        )
    }

    // ================================
    // Retry Logic with Exponential Backoff
    // ================================

    private suspend fun <T> executeWithRetry(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        maxDelayMs: Long = 16000,
        backoffMultiplier: Double = 2.0,
        jitterFactor: Double = 0.1,
        operation: suspend () -> Result<T>
    ): Result<T> {
        
        var lastException: Throwable? = null
        var delay = initialDelayMs

        repeat(maxRetries + 1) { attempt ->
            try {
                val result = withTimeout(30000) { // 30 second timeout per attempt
                    operation()
                }

                if (result.isSuccess) {
                    return result
                } else {
                    val exception = result.exceptionOrNull()!!
                    lastException = exception

                    // Don't retry certain errors
                    if (exception is CopilotException && !shouldRetry(exception, attempt)) {
                        return result
                    }

                    // Apply delay before retry (except on last attempt)
                    if (attempt < maxRetries) {
                        val jitter = delay * jitterFactor * Random.nextDouble(-1.0, 1.0)
                        val actualDelay = (delay + jitter).toLong().coerceAtLeast(0)
                        delay(actualDelay)
                        
                        // Exponential backoff with max limit
                        delay = min(maxDelayMs, (delay * backoffMultiplier).toLong())
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                lastException = CopilotException.NetworkException.Timeout()
                if (attempt == maxRetries) {
                    return Result.failure(lastException ?: CopilotException.UnknownException())
                }
                delay(delay)
                delay = min(maxDelayMs, (delay * backoffMultiplier).toLong())
            }
        }

        return Result.failure(lastException ?: CopilotException.UnknownException())
    }

    private fun shouldRetry(exception: CopilotException, attempt: Int): Boolean {
        return when (exception) {
            // Always retry network issues
            is CopilotException.NetworkException.NoConnection,
            is CopilotException.NetworkException.Timeout,
            is CopilotException.NetworkException.ServerError -> true

            // Retry rate limiting with proper delay
            is CopilotException.NetworkException.RateLimited -> attempt < 2

            // Don't retry auth issues
            is CopilotException.AuthException -> false

            // Don't retry validation errors
            is CopilotException.ChatException.EmptyMessage,
            is CopilotException.ChatException.InvalidMessage -> false

            // Don't retry model not found
            is CopilotException.ModelException.ModelNotFound -> false

            // Retry other errors once
            else -> attempt == 0
        }
    }
}

// ================================
// Chat Utilities
// ================================

object ChatUtils {
    
    /**
     * Estimate token count for text (rough approximation)
     */
    fun estimateTokenCount(text: String): Int {
        // Rough estimation: 1 token ≈ 4 characters for English
        // This is not accurate but gives a ballpark figure
        return (text.length / 4).coerceAtLeast(1)
    }

    /**
     * Truncate message to fit within token limits
     */
    fun truncateToTokenLimit(text: String, maxTokens: Int): String {
        val estimatedTokens = estimateTokenCount(text)
        if (estimatedTokens <= maxTokens) {
            return text
        }

        // Truncate to approximate character limit
        val maxChars = maxTokens * 4
        return if (text.length > maxChars) {
            text.take(maxChars - 3) + "..."
        } else {
            text
        }
    }

    /**
     * Create a formatted prompt with optional system context
     */
    fun createFormattedPrompt(
        userMessage: String,
        systemContext: String? = null,
        additionalInstructions: String? = null
    ): String {
        val basePrompt = userMessage
        val contextPart = if (systemContext != null) "\n\nContext: $systemContext" else ""
        val instructionsPart = if (additionalInstructions != null) "\n\n$additionalInstructions" else ""
        
        return "$basePrompt$contextPart$instructionsPart"
    }
}