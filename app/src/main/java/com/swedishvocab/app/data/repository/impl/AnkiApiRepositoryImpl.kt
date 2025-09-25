package com.swedishvocab.app.data.repository.impl

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.ichi2.anki.api.AddContentApi
import com.swedishvocab.app.data.model.AnkiCard
import com.swedishvocab.app.data.model.AnkiError
import com.swedishvocab.app.data.repository.AnkiApiRepository
import com.swedishvocab.app.data.repository.AnkiImplementationType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnkiApiRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AnkiApiRepository {

    companion object {
        private const val PERMISSION_READ_WRITE_DATABASE = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
        private const val APP_MODEL_NAME = "Glosordalen Basic"
        private const val DEFAULT_DECK_NAME = "Glosordalen"
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

    override suspend fun hasApiPermission(): Boolean = withContext(Dispatchers.IO) {
        val hasPermission = ContextCompat.checkSelfPermission(context, PERMISSION_READ_WRITE_DATABASE) == 
                PackageManager.PERMISSION_GRANTED
        android.util.Log.d("AnkiApiRepository", "API permission check: $hasPermission")
        return@withContext hasPermission
    }

    override suspend fun requestApiPermission(): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun ensureDeckExists(deckName: String): Result<Long> = withContext(Dispatchers.IO) {
        return@withContext try {
            val api = getApi() ?: return@withContext Result.failure(
                AnkiError.ApiNotAvailable("AnkiDroid API not available")
            )

            // Check if deck already exists
            val decks = api.deckList
            val existingDeck = decks.entries.find { it.value == deckName }
            
            if (existingDeck != null) {
                Result.success(existingDeck.key)
            } else {
                // Create new deck
                val deckId = api.addNewDeck(deckName)
                if (deckId != null) {
                    Result.success(deckId)
                } else {
                    Result.failure(AnkiError.DeckCreationFailed("Failed to create deck: $deckName"))
                }
            }
        } catch (e: Exception) {
            Result.failure(AnkiError.ApiError("Error managing deck: ${e.message}"))
        }
    }

    override suspend fun ensureModelExists(modelName: String): Result<Long> = withContext(Dispatchers.IO) {
        return@withContext try {
            val api = getApi() ?: return@withContext Result.failure(
                AnkiError.ApiNotAvailable("AnkiDroid API not available")
            )

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
                        Result.failure(AnkiError.ModelCreationFailed("Built-in model '$modelName' not found. Please ensure AnkiDroid is properly set up."))
                    }
                    else -> {
                        // Create new basic model with Front/Back fields for custom models
                        val modelId = api.addNewBasicModel(modelName)
                        if (modelId != null) {
                            Result.success(modelId)
                        } else {
                            Result.failure(AnkiError.ModelCreationFailed("Failed to create model: $modelName"))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(AnkiError.ApiError("Error managing model: ${e.message}"))
        }
    }

    override suspend fun getOrCreateBasicModel(): Result<Long> {
        if (cachedModelId != null) {
            return Result.success(cachedModelId!!)
        }
        
        return ensureModelExists(APP_MODEL_NAME).onSuccess { modelId ->
            cachedModelId = modelId
        }
    }

    override suspend fun createCard(card: AnkiCard): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val api = getApi() ?: return@withContext Result.failure(
                AnkiError.ApiNotAvailable("AnkiDroid API not available")
            )

            // Ensure deck exists
            val deckResult = if (cachedDeckId != null) {
                Result.success(cachedDeckId!!)
            } else {
                ensureDeckExists(card.deckName ?: DEFAULT_DECK_NAME)
            }

            val deckId = deckResult.getOrElse { 
                return@withContext Result.failure(it)
            }
            
            if (cachedDeckId == null) cachedDeckId = deckId

            // Ensure model exists - use the model specified in the card
            val modelResult = ensureModelExists(card.modelName)
            val modelId = modelResult.getOrElse { 
                return@withContext Result.failure(it)
            }

            // Prepare note fields - AnkiDroid API expects array of strings
            val fieldValues = arrayOf(
                card.fields["Front"] ?: "",
                card.fields["Back"] ?: ""
            )

            // Create the note - tags parameter expects a Set<String>
            val noteId = api.addNote(modelId, deckId, fieldValues, card.tags.toSet())
            
            if (noteId != null) {
                android.util.Log.d("AnkiApiRepository", "Card created successfully with ID: $noteId, Deck: ${card.deckName ?: DEFAULT_DECK_NAME}, Fields: ${fieldValues.joinToString()}")
                Result.success(Unit)
            } else {
                android.util.Log.e("AnkiApiRepository", "Failed to create card - addNote returned null")
                Result.failure(AnkiError.CardCreationFailed("Failed to create card via API"))
            }
        } catch (e: SecurityException) {
            Result.failure(AnkiError.PermissionDenied("Permission denied: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(AnkiError.ApiError("API error: ${e.message}"))
        }
    }

    override suspend fun createCards(cards: List<AnkiCard>): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val api = getApi() ?: return@withContext Result.failure(
                AnkiError.ApiNotAvailable("AnkiDroid API not available")
            )

            // Group cards by deck and model for batch operations  
            val cardsByDeckAndModel = cards.groupBy { 
                Pair(it.deckName ?: DEFAULT_DECK_NAME, it.modelName) 
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

    override fun supportsBatchOperations(): Boolean = true

    override suspend fun getAvailableDecks(): Result<Map<Long, String>> = withContext(Dispatchers.IO) {
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

    override suspend fun getAvailableModels(): Result<Map<Long, String>> = withContext(Dispatchers.IO) {
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
