package com.glosdalen.app.libs.copilot.models

import kotlinx.serialization.Serializable

/**
 * Core data models for the Copilot Chat library
 */

// ================================
// Authentication Models
// ================================

@Serializable
data class OAuthToken(
    @kotlinx.serialization.SerialName("access_token")
    val accessToken: String? = null,
    @kotlinx.serialization.SerialName("token_type")
    val tokenType: String = "Bearer",
    val scope: String? = null,
    val error: String? = null,
    val expiresAt: Long? = null
)

@Serializable
data class CopilotToken(
    val token: String,
    val expiresAt: Long? = null
)

@Serializable
data class DeviceCodeResponse(
    @kotlinx.serialization.SerialName("device_code")
    val deviceCode: String,
    @kotlinx.serialization.SerialName("user_code")
    val userCode: String,
    @kotlinx.serialization.SerialName("verification_uri")
    val verificationUri: String,
    @kotlinx.serialization.SerialName("expires_in")
    val expiresIn: Int,
    @kotlinx.serialization.SerialName("interval")
    val interval: Int
)

// ================================
// Model System
// ================================

enum class CostType {
    FREE,           // No token consumption (GPT-4o, o3-mini, GPT-4.1)
    PROMOTIONAL,    // Temporarily free (Grok during launch)
    PAID           // Consumes Copilot quota (Claude, GPT-5, etc.)
}

enum class ModelCategory {
    LIGHTWEIGHT,    // Fast, efficient models  
    VERSATILE,      // Balanced performance and capability
    POWERFUL        // Most capable, highest resource usage
}

@Serializable
data class VisionCapabilities(
    @kotlinx.serialization.SerialName("max_prompt_image_size")
    val maxPromptImageSize: Long,
    @kotlinx.serialization.SerialName("max_prompt_images")
    val maxPromptImages: Int,
    @kotlinx.serialization.SerialName("supported_media_types")
    val supportedMediaTypes: List<String>
)

@Serializable
data class TokenLimits(
    @kotlinx.serialization.SerialName("max_context_window_tokens")
    val maxContextTokens: Int? = null,
    @kotlinx.serialization.SerialName("max_output_tokens")
    val maxOutputTokens: Int? = null,
    @kotlinx.serialization.SerialName("max_prompt_tokens")
    val maxPromptTokens: Int? = null,
    val vision: VisionCapabilities? = null
)

@Serializable
data class ModelCapabilities(
    val family: String,
    val limits: TokenLimits? = null,
    val supports: ModelSupports,
    val tokenizer: String? = null,
    val type: String = "chat",
    val `object`: String = "model_capabilities"
)

@Serializable
data class ModelSupports(
    val streaming: Boolean = true,
    @kotlinx.serialization.SerialName("tool_calls")
    val toolCalls: Boolean = false,
    val vision: Boolean = false,
    @kotlinx.serialization.SerialName("parallel_tool_calls")
    val parallelToolCalls: Boolean = false,
    @kotlinx.serialization.SerialName("structured_outputs")
    val structuredOutputs: Boolean = false
)

@Serializable
data class CopilotModel(
    val id: String,
    val name: String,
    val vendor: String,
    val category: ModelCategory,
    val costType: CostType,
    val capabilities: ModelCapabilities,
    val preview: Boolean = false,
    val enabled: Boolean = true,
    val description: String? = null
) {
    /**
     * Check if this model is free to use
     */
    fun isFree(): Boolean = costType == CostType.FREE || costType == CostType.PROMOTIONAL
    
    /**
     * Get a user-friendly display name
     */
    fun getDisplayName(): String = if (preview) "$name (Preview)" else name
    
    /**
     * Get cost indicator for UI
     */
    fun getCostIndicator(): String = when (costType) {
        CostType.FREE -> "Free"
        CostType.PROMOTIONAL -> "Free (Limited Time)"
        CostType.PAID -> "Premium"
    }
}

// ================================
// Chat Models
// ================================

@Serializable
data class Message(
    val role: String, // "user" or "assistant"
    val content: String
)

@Serializable
data class ChatRequest(
    val messages: List<Message>,
    val model: String,
    @kotlinx.serialization.SerialName("max_tokens")
    val maxTokens: Int = 150,
    val temperature: Double = 0.1,
    val stream: Boolean = false
)

@Serializable
data class ChatResponse(
    val choices: List<ChatChoice>,
    val id: String,
    val model: String,
    val usage: TokenUsage? = null
)

@Serializable
data class ChatChoice(
    val message: Message,
    @kotlinx.serialization.SerialName("finish_reason")
    val finishReason: String? = null,
    val index: Int = 0
)

@Serializable
data class TokenUsage(
    @kotlinx.serialization.SerialName("prompt_tokens")
    val promptTokens: Int,
    @kotlinx.serialization.SerialName("completion_tokens")
    val completionTokens: Int,
    @kotlinx.serialization.SerialName("total_tokens")
    val totalTokens: Int
)

@Serializable
data class ChatChunk(
    val choices: List<ChatChunkChoice>,
    val id: String,
    val model: String
)

@Serializable
data class ChatChunkChoice(
    val delta: ChatDelta,
    @kotlinx.serialization.SerialName("finish_reason")
    val finishReason: String? = null,
    val index: Int = 0
)

@Serializable
data class ChatDelta(
    val role: String? = null,
    val content: String? = null
)

// ================================
// API Response Models
// ================================

@Serializable
data class ModelsResponse(
    val data: List<RawModel>,
    val `object`: String = "list"
)

@Serializable
data class RawModel(
    val id: String,
    val name: String,
    val vendor: String,
    val capabilities: ModelCapabilities,
    @kotlinx.serialization.SerialName("model_picker_enabled")
    val modelPickerEnabled: Boolean = false,
    @kotlinx.serialization.SerialName("model_picker_category")
    val modelPickerCategory: String? = null,
    val preview: Boolean = false,
    val policy: ModelPolicy? = null,
    val `object`: String = "model",
    val version: String? = null
)

@Serializable
data class ModelPolicy(
    val state: String = "enabled",
    val terms: String? = null
)

// ================================
// Error Models  
// ================================

@Serializable
data class CopilotError(
    val message: String,
    val type: String? = null,
    val code: String? = null
)

@Serializable
data class ErrorResponse(
    val error: CopilotError
)