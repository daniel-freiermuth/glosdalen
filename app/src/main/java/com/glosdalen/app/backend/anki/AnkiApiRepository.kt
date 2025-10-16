package com.glosdalen.app.backend.anki

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.ichi2.anki.api.AddContentApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for AnkiDroid AddContentApi implementation.
 * Handles API-specific functionality like permissions and deck management.
 */
@Singleton
class AnkiApiRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : AnkiBackend {

    companion object {
        private const val PERMISSION_READ_WRITE_DATABASE = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
        private const val APP_MODEL_NAME = "Glosdalen Basic"
    }

    private var cachedApi: AddContentApi? = null
    private var cachedDeckId: Long? = null
    private var cachedModelId: Long? = null

    private fun getApi(): AddContentApi? {
        return try {
            if (cachedApi == null && AddContentApi.getAnkiDroidPackageName(context) != null) {
                cachedApi = AddContentApi(context)
            }
            cachedApi
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun isAnkiDroidAvailable(): Boolean = withContext(Dispatchers.IO) {
        val packageName = AddContentApi.getAnkiDroidPackageName(context)
        val apiAvailable = getApi() != null
        android.util.Log.d("AnkiApiRepository", "AnkiDroid package: $packageName, API available: $apiAvailable")
        return@withContext packageName != null && apiAvailable
    }

    /**
     * Check if the API permission is granted
     */
    suspend fun hasApiPermission(): Boolean = withContext(Dispatchers.IO) {
        val hasPermission = ContextCompat.checkSelfPermission(context, PERMISSION_READ_WRITE_DATABASE) == 
                PackageManager.PERMISSION_GRANTED
        android.util.Log.d("AnkiApiRepository", "API permission check: $hasPermission")
        return@withContext hasPermission
    }

    /**
     * Request API permission from the user
     */
    suspend fun requestApiPermission(): Boolean = withContext(Dispatchers.IO) {
        // Permission request is handled automatically by AddContentApi when needed
        // This method indicates whether permission will be requested
        !hasApiPermission()
    }

    /**
     * Trigger permission request by attempting a simple API operation
     */
    suspend fun triggerPermissionRequest(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val api = getApi() ?: return@withContext false
            // This should trigger the permission dialog if not already granted
            api.deckList
            android.util.Log.d("AnkiApiRepository", "Permission request triggered successfully")
            hasApiPermission()
        } catch (e: SecurityException) {
            android.util.Log.d("AnkiApiRepository", "Security exception during permission request: ${e.message}")
            false
        } catch (e: Exception) {
            android.util.Log.d("AnkiApiRepository", "Exception during permission request: ${e.message}")
            false
        }
    }

    /**
     * Ensure the required deck exists, create if necessary
     */
    suspend fun ensureDeckExists(deckName: String): Result<Long> = withContext(Dispatchers.IO) {
        return@withContext try {
            val api = getApi() ?: run {
                android.util.Log.e("AnkiApiRepository", "Failed to get API instance")
                return@withContext Result.failure(
                    AnkiError.ApiNotAvailable("AnkiDroid API not available")
                )
            }

            // Check if deck already exists (case-insensitive, as AnkiDroid treats deck names)
            val decks = api.deckList
            val existingDeck = decks.entries.find { it.value.equals(deckName, ignoreCase = true) }
            
            if (existingDeck != null) {
                Result.success(existingDeck.key)
            } else {
                // Create new deck
                val deckId = api.addNewDeck(deckName)
                if (deckId != null) {
                    Result.success(deckId)
                } else {
                    android.util.Log.e("AnkiApiRepository", "Failed to create deck '$deckName' - API returned null")
                    Result.failure(AnkiError.DeckCreationFailed("Failed to create deck: $deckName"))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AnkiApiRepository", "Exception in ensureDeckExists for '$deckName': ${e.message}", e)
            Result.failure(AnkiError.ApiError("Error managing deck: ${e.message}"))
        }
    }

    /**
     * Ensure the required note type/model exists, create if necessary
     */
    suspend fun ensureModelExists(modelName: String): Result<Long> = withContext(Dispatchers.IO) {
        return@withContext try {
            val api = getApi() ?: run {
                android.util.Log.e("AnkiApiRepository", "Failed to get API instance")
                return@withContext Result.failure(
                    AnkiError.ApiNotAvailable("AnkiDroid API not available")
                )
            }

            // Check if model already exists
            val models = api.modelList
            val existingModel = models.entries.find { it.value == modelName }
            
            if (existingModel != null) {
                Result.success(existingModel.key)
            } else {
                // Handle built-in models vs custom models
                when (modelName) {
                    "Basic (and reversed card)" -> {
                        // This should be a built-in model - if not found, it's an error
                        android.util.Log.e("AnkiApiRepository", "Built-in model '$modelName' not found in AnkiDroid")
                        Result.failure(AnkiError.ModelCreationFailed("Built-in model '$modelName' not found. Please ensure AnkiDroid is properly set up."))
                    }
                    else -> {
                        // Create new basic model with Front/Back fields for custom models
                        val modelId = api.addNewBasicModel(modelName)
                        if (modelId != null) {
                            Result.success(modelId)
                        } else {
                            android.util.Log.e("AnkiApiRepository", "Failed to create model '$modelName' - API returned null")
                            Result.failure(AnkiError.ModelCreationFailed("Failed to create model: $modelName"))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AnkiApiRepository", "Exception in ensureModelExists for '$modelName': ${e.message}", e)
            Result.failure(AnkiError.ApiError("Error managing model: ${e.message}"))
        }
    }

    /**
     * Get or create the basic two-field model for vocabulary cards
     */
    suspend fun getOrCreateBasicModel(): Result<Long> {
        if (cachedModelId != null) {
            return Result.success(cachedModelId!!)
        }
        
        return ensureModelExists(APP_MODEL_NAME).onSuccess { modelId ->
            cachedModelId = modelId
        }
    }

    override suspend fun createCard(card: AnkiCard): Result<Unit> {
        // Delegate to batch implementation for consistency and efficiency
        return createCards(listOf(card))
    }

    override suspend fun createCards(cards: List<AnkiCard>): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val api = getApi() ?: return@withContext Result.failure(
                AnkiError.ApiNotAvailable("AnkiDroid API not available")
            )

            // Group cards by deck and model for batch operations  
            val cardsByDeckAndModel = cards.groupBy { 
                Pair(it.deckName, it.modelName) 
            }
            
            for ((deckModelPair, deckCards) in cardsByDeckAndModel) {
                val (deckName, modelName) = deckModelPair
                
                // Ensure deck exists
                val deckResult = ensureDeckExists(deckName)
                val deckId = deckResult.getOrElse { 
                    return@withContext Result.failure(it)
                }

                // Ensure model exists - use the model specified in the cards
                val modelResult = ensureModelExists(modelName)
                val modelId = modelResult.getOrElse { 
                    return@withContext Result.failure(it)
                }

                // Prepare notes for batch addition
                val notes = deckCards.map { card ->
                    arrayOf(
                        card.fields["Front"] ?: "",
                        card.fields["Back"] ?: ""
                    )
                }

                // Use batch API for better performance - convert to List
                api.addNotes(modelId, deckId, notes.toMutableList(), null)
                
                // Note: API documentation suggests this always returns a valid result
            }

            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(AnkiError.PermissionDenied("Permission denied: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(AnkiError.ApiError("Batch API error: ${e.message}"))
        }
    }

    /**
     * Get list of available decks in AnkiDroid
     */
    suspend fun getAvailableDecks(): Result<Map<Long, String>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val api = getApi() ?: return@withContext Result.failure(
                AnkiError.ApiNotAvailable("AnkiDroid API not available")
            )
            
            val decks = api.deckList
            Result.success(decks)
        } catch (e: Exception) {
            Result.failure(AnkiError.ApiError("Error retrieving decks: ${e.message}"))
        }
    }

    /**
     * Get list of available note types/models in AnkiDroid
     */
    suspend fun getAvailableModels(): Result<Map<Long, String>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val api = getApi() ?: return@withContext Result.failure(
                AnkiError.ApiNotAvailable("AnkiDroid API not available")
            )
            
            val models = api.modelList
            Result.success(models)
        } catch (e: Exception) {
            Result.failure(AnkiError.ApiError("Error retrieving models: ${e.message}"))
        }
    }

    override fun getImplementationType(): AnkiImplementationType = AnkiImplementationType.API
}
