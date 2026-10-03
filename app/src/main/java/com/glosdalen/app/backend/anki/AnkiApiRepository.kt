package com.glosdalen.app.backend.anki

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.glosdalen.app.libs.copilot.util.TimeProvider
import com.ichi2.anki.api.AddContentApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for AnkiDroid AddContentApi implementation.
 * Handles API-specific functionality like permissions and deck management.
 */
@Singleton
class AnkiApiRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider
) : AnkiBackend {

    companion object {
        private const val PERMISSION_READ_WRITE_DATABASE = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
        private const val APP_MODEL_NAME = "Glosdalen Basic"
    }

    private var cachedApi: AddContentApi? = null
    private var cachedDeckId: Long? = null
    private var cachedModelId: Long? = null
    private val audioFileCounter = AtomicLong(0)

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

            // Validate deck name - reject if it has whitespace around ::
            if (deckName.contains(Regex("\\s+::|::\\s+"))) {
                android.util.Log.e("AnkiApiRepository", "Invalid deck name with whitespace around '::': '$deckName'")
                return@withContext Result.failure(
                    AnkiError.DeckCreationFailed("Invalid deck name: whitespace not allowed around '::' separator. Please check your deck name template.")
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
            android.util.Log.d("AnkiApiRepository", "Available models: ${models.values.toList()}")
            
            // First try exact match
            val existingModel = models.entries.find { it.value == modelName }
            
            if (existingModel != null) {
                android.util.Log.d("AnkiApiRepository", "Found exact model match: ${existingModel.value}")
                Result.success(existingModel.key)
            } else {
                // Handle built-in models vs custom models
                when (modelName) {
                    "Basic (and reversed card)" -> {
                        // Try case-insensitive match first
                        val caseInsensitiveMatch = models.entries.find { 
                            it.value.equals(modelName, ignoreCase = true) 
                        }
                        if (caseInsensitiveMatch != null) {
                            android.util.Log.d("AnkiApiRepository", "Found case-insensitive model match: ${caseInsensitiveMatch.value}")
                            return@withContext Result.success(caseInsensitiveMatch.key)
                        }
                        
                        // Try to find a model that indicates bidirectional/reversed cards
                        // Handles various localized versions:
                        // - English: "Basic (and reversed card)"
                        // - German: "Einfach (beide Richtungen)"
                        // - Other languages with "reversed", "both", "directions", etc.
                        val reversedModel = models.entries.find { modelEntry ->
                            val name = modelEntry.value.lowercase()
                            name.contains("reversed") ||
                            name.contains("beide richtungen") ||  // German
                            name.contains("both directions") ||
                            name.contains("inverso") ||           // Spanish/Italian
                            name.contains("inversé") ||           // French
                            name.contains("omgekeerd") ||         // Dutch
                            (name.contains("basic") && name.contains("reverse")) ||
                            (name.contains("einfach") && name.contains("richtung"))  // German partial
                        }
                        if (reversedModel != null) {
                            android.util.Log.d("AnkiApiRepository", "Found reversed model variant: ${reversedModel.value}")
                            return@withContext Result.success(reversedModel.key)
                        }
                        
                        // Log available models for debugging
                        android.util.Log.e("AnkiApiRepository", "Built-in model '$modelName' not found in AnkiDroid. Available models: ${models.values.toList()}")
                        Result.failure(AnkiError.ModelCreationFailed("Reversed card model not found. Please open AnkiDroid and ensure default note types are available, or try creating a card manually first."))
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
                    // Add audio files to AnkiDroid's media collection if present
                    val frontField = card.fields["Front"] ?: ""
                    val backField = card.fields["Back"] ?: ""
                    
                    // Add audio tags if audio files are provided
                    val frontWithAudio = addAudioTag(api, frontField, card.audioFiles["Front"])
                    val backWithAudio = addAudioTag(api, backField, card.audioFiles["Back"])
                    
                    arrayOf(frontWithAudio, backWithAudio)
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
     * Add audio file to AnkiDroid media collection and return the field with [sound:...] tag.
     * Returns the original text if audio file is null or copying fails.
     */
    private fun addAudioTag(api: AddContentApi, fieldText: String, audioFile: File?): String {
        if (audioFile == null || !audioFile.exists()) {
            return fieldText
        }
        
        return try {
            // Use FileProvider to create a content URI that AnkiDroid can access
            val authority = "${context.packageName}.fileprovider"
            val mediaUri = FileProvider.getUriForFile(context, authority, audioFile)
            
            // Grant read permission to AnkiDroid
            val ankiPackage = AddContentApi.getAnkiDroidPackageName(context)
            if (ankiPackage != null) {
                context.grantUriPermission(ankiPackage, mediaUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // Generate a unique preferred name based on the file name
            val preferredName = "glosdalen_${timeProvider.currentTimeMillis()}_${audioFileCounter.incrementAndGet()}"
            val soundTag = api.addMediaFromUri(mediaUri, preferredName, "audio")
            
            // Revoke the permission after the file is copied
            if (ankiPackage != null) {
                context.revokeUriPermission(mediaUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // addMediaFromUri returns the formatted [sound:...] tag directly, or null if it failed
            if (soundTag != null) {
                android.util.Log.d("AnkiApiRepository", "Successfully added audio: $soundTag")
                if (fieldText.isNotBlank()) {
                    "$fieldText $soundTag"
                } else {
                    soundTag
                }
            } else {
                android.util.Log.w("AnkiApiRepository", "addMediaFromUri returned null for: ${audioFile.name}")
                fieldText
            }
        } catch (e: Exception) {
            android.util.Log.w("AnkiApiRepository", "Failed to add audio file: ${e.message}", e)
            fieldText // Return original text if audio addition fails
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
}
