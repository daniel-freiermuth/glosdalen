package com.glosdalen.app.libs.copilot.models

import com.glosdalen.app.libs.copilot.*
import com.glosdalen.app.libs.copilot.auth.CopilotTokenManager
import com.glosdalen.app.libs.copilot.network.CopilotApiService
import com.glosdalen.app.libs.copilot.storage.CopilotStorage
import com.glosdalen.app.libs.copilot.util.TimeProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Model Discovery and Classification System
 * 
 * Automatically discovers, classifies, and caches all available Copilot models
 * with intelligent cost detection and capability analysis.
 */
@Singleton
class CopilotModelManager @Inject constructor(
    private val copilotApiService: CopilotApiService,
    private val tokenManager: CopilotTokenManager,
    private val storage: CopilotStorage,
    private val timeProvider: TimeProvider
) {

    private var cachedModels: List<CopilotModel>? = null
    private val modelMutex = Mutex()

    // ================================
    // Public API
    // ================================

    /**
     * Get all available models (cached or fresh from API)
     */
    suspend fun getAvailableModels(forceRefresh: Boolean = false): Result<List<CopilotModel>> {
        val startTotal = timeProvider.currentTimeMillis()
        return modelMutex.withLock {
            val startCache = timeProvider.currentTimeMillis()
            android.util.Log.d("CopilotModelManager", "getAvailableModels: Checking in-memory cache...")
            if (!forceRefresh && cachedModels != null) {
                android.util.Log.d("CopilotModelManager", "getAvailableModels: In-memory cache hit in ${timeProvider.currentTimeMillis() - startCache}ms")
                android.util.Log.d("CopilotModelManager", "getAvailableModels: Total time ${timeProvider.currentTimeMillis() - startTotal}ms")
                return@withLock Result.success(cachedModels!!)
            }

            android.util.Log.d("CopilotModelManager", "getAvailableModels: Checking persistent cache...")
            val startPersistent = timeProvider.currentTimeMillis()
            if (!forceRefresh) {
                val cachedData = storage.loadModelCache()
                if (cachedData != null && cachedData.isValid(timeProvider)) {
                    cachedModels = cachedData.models
                    android.util.Log.d("CopilotModelManager", "getAvailableModels: Persistent cache hit in ${timeProvider.currentTimeMillis() - startPersistent}ms")
                    android.util.Log.d("CopilotModelManager", "getAvailableModels: Total time ${timeProvider.currentTimeMillis() - startTotal}ms")
                    return@withLock Result.success(cachedModels!!)
                }
            }

            android.util.Log.d("CopilotModelManager", "getAvailableModels: Fetching from API...")
            val startApi = timeProvider.currentTimeMillis()
            val result = discoverModelsFromAPI()
            android.util.Log.d("CopilotModelManager", "getAvailableModels: API fetch took ${timeProvider.currentTimeMillis() - startApi}ms")
            android.util.Log.d("CopilotModelManager", "getAvailableModels: Total time ${timeProvider.currentTimeMillis() - startTotal}ms")
            result
        }
    }

    /**
     * Get only free models
     */
    suspend fun getFreeModels(): Result<List<CopilotModel>> {
        return getAvailableModels().map { models ->
            models.filter { it.isFree() }
        }
    }

    /**
     * Get recommended models for translation tasks
     */
    suspend fun getRecommendedModels(): Result<List<CopilotModel>> {
        return getAvailableModels().map { models ->
            // Sort by: Free first, then by capability and quality
            models.sortedWith(compareBy<CopilotModel> { !it.isFree() }
                .thenBy { it.category.ordinal }
                .thenByDescending { it.capabilities.limits?.maxContextTokens ?: 0 })
                .take(5) // Top 5 recommendations
        }
    }

    /**
     * Find model by ID
     */
    suspend fun getModelById(modelId: String): Result<CopilotModel> {
        val modelsResult = getAvailableModels()
        if (modelsResult.isFailure) {
            return Result.failure(modelsResult.exceptionOrNull()!!)
        }
        
        val models = modelsResult.getOrThrow()
        val model = models.find { it.id == modelId }
        return if (model != null) {
            Result.success(model)
        } else {
            Result.failure(CopilotException.ModelException.ModelNotFound(modelId))
        }
    }

    /**
     * Force refresh model cache
     */
    suspend fun refreshModels(): Result<List<CopilotModel>> {
        return getAvailableModels(forceRefresh = true)
    }

    // ================================
    // Private Implementation
    // ================================

    private suspend fun discoverModelsFromAPI(): Result<List<CopilotModel>> {
        val start = timeProvider.currentTimeMillis()
        android.util.Log.d("CopilotModelManager", "discoverModelsFromAPI: Starting API model discovery...")
        return try {
            val startToken = timeProvider.currentTimeMillis()
            android.util.Log.d("CopilotModelManager", "discoverModelsFromAPI: Getting authorization token...")
            val authResult = tokenManager.getAuthorizationHeader()
            android.util.Log.d("CopilotModelManager", "discoverModelsFromAPI: Token retrieval took ${timeProvider.currentTimeMillis() - startToken}ms")
            if (authResult.isFailure) {
                android.util.Log.e("CopilotModelManager", "discoverModelsFromAPI: Token retrieval failed")
                return Result.failure(authResult.exceptionOrNull()!!)
            }

            val authHeader = authResult.getOrThrow()

            val startApiCall = timeProvider.currentTimeMillis()
            android.util.Log.d("CopilotModelManager", "discoverModelsFromAPI: Calling models API with authHeader length=${authHeader.length}...")
            val response = copilotApiService.getModels(authorization = authHeader)
            android.util.Log.d("CopilotModelManager", "discoverModelsFromAPI: API call took ${timeProvider.currentTimeMillis() - startApiCall}ms")
            android.util.Log.d("CopilotModelManager", "discoverModelsFromAPI: Response code=${response.code()}, isSuccessful=${response.isSuccessful}")
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("CopilotModelManager", "discoverModelsFromAPI: Error body: $errorBody")
            }

            if (response.isSuccessful && response.body() != null) {
                val modelsResponse = response.body()!!
                val startProcessing = timeProvider.currentTimeMillis()
                android.util.Log.d("CopilotModelManager", "discoverModelsFromAPI: Processing models...")
                try {
                    val processedModels = processRawModels(modelsResponse.data)
                    android.util.Log.d("CopilotModelManager", "discoverModelsFromAPI: Model processing took ${timeProvider.currentTimeMillis() - startProcessing}ms")

                    // Cache the results
                    cachedModels = processedModels
                    storage.saveModelCache(processedModels)

                    android.util.Log.d("CopilotModelManager", "discoverModelsFromAPI: Total time ${timeProvider.currentTimeMillis() - start}ms")
                    Result.success(processedModels)
                } catch (processingException: Exception) {
                    android.util.Log.e("CopilotModelManager", "Model processing failed", processingException)
                    Result.failure(CopilotException.ModelException.ModelDiscoveryFailed(processingException))
                }
            } else {
                val errorCode = response.code()
                android.util.Log.e("CopilotModelManager", "API error code: $errorCode")
                android.util.Log.d("CopilotModelManager", "discoverModelsFromAPI: Total time ${timeProvider.currentTimeMillis() - start}ms")
                when (errorCode) {
                    401 -> Result.failure(CopilotException.NetworkException.Unauthorized())
                    403 -> Result.failure(CopilotException.AuthException.AccessDenied())
                    429 -> Result.failure(CopilotException.NetworkException.RateLimited())
                    else -> Result.failure(CopilotException.ModelException.ModelDiscoveryFailed())
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CopilotModelManager", "General exception in discoverModelsFromAPI", e)
            android.util.Log.d("CopilotModelManager", "discoverModelsFromAPI: Total time ${timeProvider.currentTimeMillis() - start}ms")
            Result.failure(CopilotException.ModelException.ModelDiscoveryFailed(e))
        }
    }

    private fun processRawModels(rawModels: List<RawModel>): List<CopilotModel> {
        return rawModels
            .filter { it.modelPickerEnabled }
            .map { rawModel ->
                try {
                    CopilotModel(
                        id = rawModel.id,
                        name = rawModel.name,
                        vendor = rawModel.vendor,
                        category = mapCategory(rawModel.modelPickerCategory),
                        costType = determineCostType(rawModel),
                        capabilities = rawModel.capabilities,
                        preview = rawModel.preview,
                        enabled = true,
                        description = generateModelDescription(rawModel)
                    )
                } catch (e: Exception) {
                    android.util.Log.e("CopilotModelManager", "Error processing model ${rawModel.id}", e)
                    throw e
                }
            }
            .sortedWith(
                compareBy<CopilotModel> { !it.isFree() } // Free models first
                    .thenBy { it.category.ordinal }
                    .thenBy { it.name }
            )
    }

    private fun mapCategory(categoryString: String?): ModelCategory {
        return when (categoryString?.lowercase()) {
            "lightweight" -> ModelCategory.LIGHTWEIGHT
            "versatile" -> ModelCategory.VERSATILE
            "powerful" -> ModelCategory.POWERFUL
            else -> ModelCategory.VERSATILE // Default
        }
    }

    private fun determineCostType(rawModel: RawModel): CostType {
        val policyTerms = rawModel.policy?.terms?.lowercase() ?: ""

        return when {
            // No special terms = FREE
            policyTerms.isEmpty() || policyTerms == "no special terms" -> CostType.FREE
            
            // Promotional pricing = PROMOTIONAL (temporarily free)
            policyTerms.contains("promotional pricing is 0x") -> CostType.PROMOTIONAL
            
            // Has "Enable access" terms = PAID
            policyTerms.contains("enable access") -> CostType.PAID
            
            // Default to FREE for unrecognized patterns
            else -> CostType.FREE
        }
    }

    private fun generateModelDescription(rawModel: RawModel): String {
        val parts = mutableListOf<String>()

        // Add context size info
        val contextTokens = rawModel.capabilities.limits?.maxContextTokens ?: 0
        val contextSize = when {
            contextTokens >= 200000 -> "Very large context (${contextTokens / 1000}K)"
            contextTokens >= 100000 -> "Large context (${contextTokens / 1000}K)"
            contextTokens >= 50000 -> "Medium context (${contextTokens / 1000}K)"
            contextTokens > 0 -> "Standard context (${contextTokens / 1000}K)"
            else -> "Context size unknown"
        }
        parts.add(contextSize)

        // Add special capabilities
        val supports = rawModel.capabilities.supports
        if (supports.vision) parts.add("Vision support")
        if (supports.toolCalls) parts.add("Tool calls")
        if (supports.structuredOutputs) parts.add("Structured outputs")

        // Add vendor-specific info
        when (rawModel.vendor.lowercase()) {
            "anthropic" -> parts.add("Excellent for reasoning and analysis")
            "google" -> parts.add("Strong multilingual capabilities")
            "xai" -> parts.add("Code-focused with detailed explanations")
            "openai", "azure openai" -> when {
                rawModel.id.contains("gpt-5") -> parts.add("Latest generation model")
                rawModel.id.contains("o3") || rawModel.id.contains("o4") -> parts.add("Reasoning-focused model")
                else -> parts.add("Reliable and well-tested")
            }
        }

        return parts.joinToString(" • ")
    }
}