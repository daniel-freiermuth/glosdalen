package com.glosdalen.app.backend.anki

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for AnkiDroid integration.
 * Coordinates between API and Intent-based implementations.
 */
@Singleton
class AnkiRepository @Inject constructor(
    private val apiRepository: AnkiApiRepository,
    private val intentRepository: AnkiIntentRepository,
) {

    companion object {
        private const val TAG = "AnkiRepository"
    }

    /**
     * Determine the best available AnkiDroid integration method based on user preference
     */
    private suspend fun getBestAvailableRepository(): AnkiBackend = withContext(Dispatchers.IO) {
        // Check availability of both methods
        val apiAvailable = apiRepository.isAnkiDroidAvailable()
        val intentAvailable = intentRepository.isAnkiDroidAvailable()
        Log.d(TAG, "API available: $apiAvailable, Intent available: $intentAvailable")
        
        return@withContext tryApiFirst(apiAvailable, intentAvailable)
    }
    
    private suspend fun tryApiFirst(apiAvailable: Boolean, intentAvailable: Boolean): AnkiBackend {
        // Short-circuits: the permission request is only triggered when the API exists and the permission is missing.
        val apiPermitted = apiAvailable &&
            (apiRepository.hasApiPermission() || apiRepository.triggerPermissionRequest())
        Log.d(TAG, "API permitted: $apiPermitted")

        return when (chooseAnkiImplementation(apiAvailable, apiPermitted, intentAvailable)) {
            AnkiImplementationType.API -> apiRepository
            AnkiImplementationType.INTENT -> intentRepository
            AnkiImplementationType.UNAVAILABLE -> throw AnkiError.AnkiDroidNotInstalled
        }
    }

    /**
     * Check if AnkiDroid is available for use
     */
    suspend fun isAnkiDroidAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            getBestAvailableRepository()
            return@withContext true
        } catch (e: AnkiError.AnkiDroidNotInstalled) {
            return@withContext false
        } catch (e: AnkiError.PermissionDenied) {
            // Installed, just not permitted: card actions stay visible so the user gets the permission error.
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking AnkiDroid availability", e)
            return@withContext false
        }
    }

    /**
     * Check if the AnkiDroid API permission is granted (installed + permission)
     */
    suspend fun isApiPermissionGranted(): Boolean = withContext(Dispatchers.IO) {
        try {
            apiRepository.isAnkiDroidAvailable() && apiRepository.hasApiPermission()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking API permission", e)
            false
        }
    }

    /**
     * Check whether the AnkiDroid API endpoint is available (AnkiDroid installed and API constructible)
     */
    suspend fun isApiEndpointAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            apiRepository.isAnkiDroidAvailable()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking API endpoint availability", e)
            false
        }
    }

    /**
     * Create a single Anki card
     */
    suspend fun createCard(card: AnkiCard): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            getBestAvailableRepository().createCard(card)
        } catch (e: AnkiError) {
            Log.e(TAG, "AnkiDroid integration error", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during card creation", e)
            Result.failure(AnkiError.IntentFailed("Unexpected error: ${e.message}"))
        }
    }

    /**
     * Create multiple Anki cards in batch (when supported)
     */
    suspend fun createCards(cards: List<AnkiCard>): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            getBestAvailableRepository().createCards(cards)
        } catch (e: AnkiError) {
            Log.e(TAG, "AnkiDroid integration error during batch creation", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during batch card creation", e)
            Result.failure(AnkiError.IntentFailed("Unexpected batch error: ${e.message}"))
        }
    }
    
    /**
     * Create a card specifically via Intent method (bypasses user preference)
     * Useful for "Via Intent" option in card creation buttons
     */
    suspend fun createCardViaIntent(card: AnkiCard): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            intentRepository.createCard(card)
        } catch (e: AnkiError) {
            Log.e(TAG, "Intent-based card creation error", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during Intent card creation", e)
            Result.failure(AnkiError.IntentFailed("Intent error: ${e.message}"))
        }
    }

}

/**
 * Types of AnkiDroid integration implementations
 */
enum class AnkiImplementationType {
    API,        // Using AddContentApi 
    INTENT,     // Using ACTION_SEND intent
    UNAVAILABLE // AnkiDroid not available
}

/**
 * Pick the backend for automatic card creation.
 *
 * The intent backend drops deck, note type, tags and audio, so it is never used as a silent substitute
 * for an installed-but-unpermitted API: that case fails with [AnkiError.PermissionDenied]. Users who
 * want the intent path choose it explicitly via [AnkiRepository.createCardViaIntent].
 */
internal fun chooseAnkiImplementation(
    apiAvailable: Boolean,
    apiPermitted: Boolean,
    intentAvailable: Boolean,
): AnkiImplementationType = when {
    apiAvailable && apiPermitted -> AnkiImplementationType.API
    apiAvailable -> throw AnkiError.PermissionDenied("AnkiDroid API permission not granted")
    intentAvailable -> AnkiImplementationType.INTENT
    else -> AnkiImplementationType.UNAVAILABLE
}
