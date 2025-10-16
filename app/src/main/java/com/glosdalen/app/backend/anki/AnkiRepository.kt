package com.glosdalen.app.backend.anki

import android.content.Context
import android.util.Log
import com.ichi2.anki.api.AddContentApi
import com.glosdalen.app.domain.preferences.UserPreferences
import com.glosdalen.app.domain.preferences.AnkiMethodPreference
import kotlinx.coroutines.flow.first
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for AnkiDroid integration.
 * Coordinates between API and Intent-based implementations.
 */
@Singleton
class AnkiRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiRepository: AnkiApiRepository,
    private val intentRepository: AnkiIntentRepository,
    private val userPreferences: UserPreferences
) {

    companion object {
        private const val TAG = "AnkiRepository"
    }

    /**
     * Determine the best available AnkiDroid integration method based on user preference
     */
    private suspend fun getBestAvailableRepository(): AnkiBackend = withContext(Dispatchers.IO) {
        val userPreference = userPreferences.getPreferredAnkiMethod().first()
        Log.d(TAG, "User preference: $userPreference")
        
        // Check availability of both methods
        val apiAvailable = apiRepository.isAnkiDroidAvailable()
        val intentAvailable = intentRepository.isAnkiDroidAvailable()
        Log.d(TAG, "API available: $apiAvailable, Intent available: $intentAvailable")
        
        when (userPreference) {
            AnkiMethodPreference.API -> {
                return@withContext tryApiFirst(apiAvailable, intentAvailable)
            }
            AnkiMethodPreference.INTENT -> {
                return@withContext tryIntentFirst(apiAvailable, intentAvailable)
            }
            AnkiMethodPreference.AUTO -> {
                // Default behavior: prefer API, fall back to Intent
                return@withContext tryApiFirst(apiAvailable, intentAvailable)
            }
        }
    }
    
    private suspend fun tryApiFirst(apiAvailable: Boolean, intentAvailable: Boolean): AnkiBackend {
        if (apiAvailable) {
            Log.d(TAG, "Trying API implementation...")
            val hasPermission = apiRepository.hasApiPermission()
            Log.d(TAG, "Has API permission: $hasPermission")
            
            if (hasPermission) {
                Log.d(TAG, "API permission granted, using API implementation")
                return apiRepository
            } else {
                Log.d(TAG, "API permission not granted, attempting to trigger permission request...")
                val permissionGranted = apiRepository.triggerPermissionRequest()
                if (permissionGranted) {
                    Log.d(TAG, "Permission granted after request, using API implementation")
                    return apiRepository
                } else {
                    Log.d(TAG, "Permission request failed or denied")
                }
            }
        }
        
        // Fall back to intent if available
        if (intentAvailable) {
            Log.d(TAG, "Falling back to intent implementation")
            return intentRepository
        }
        
        Log.w(TAG, "No AnkiDroid integration available")
        throw AnkiError.AnkiDroidNotInstalled
    }
    
    private suspend fun tryIntentFirst(apiAvailable: Boolean, intentAvailable: Boolean): AnkiBackend {
        if (intentAvailable) {
            Log.d(TAG, "Using intent implementation (user preference)")
            return intentRepository
        }
        
        // Fall back to API if available
        if (apiAvailable) {
            Log.d(TAG, "Intent not available, trying API implementation...")
            val hasPermission = apiRepository.hasApiPermission()
            if (hasPermission) {
                Log.d(TAG, "Using API implementation as fallback")
                return apiRepository
            } else {
                Log.d(TAG, "API available but no permission, and intent unavailable")
            }
        }
        
        Log.w(TAG, "No AnkiDroid integration available")
        throw AnkiError.AnkiDroidNotInstalled
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
        } catch (e: Exception) {
            Log.e(TAG, "Error checking AnkiDroid availability", e)
            return@withContext false
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
     * Get the current implementation type for debugging/logging
     */
    fun getImplementationType(): AnkiImplementationType {
        return runBlocking {
            try {
                getBestAvailableRepository().getImplementationType()
            } catch (e: Exception) {
                Log.w(TAG, "Error determining implementation type", e)
                AnkiImplementationType.UNAVAILABLE
            }
        }
    }

    /**
     * Get install AnkiDroid intent for when AnkiDroid is not available
     */
    suspend fun getInstallAnkiDroidIntent(): android.content.Intent? {
        return intentRepository.getInstallAnkiDroidIntent()
    }

    /**
     * Force refresh of available repositories (useful after app updates or permission changes)
     */
    suspend fun refreshAvailability(): AnkiImplementationType = withContext(Dispatchers.IO) {
        return@withContext try {
            getBestAvailableRepository().getImplementationType()
        } catch (e: Exception) {
            Log.w(TAG, "Error refreshing availability", e)
            AnkiImplementationType.UNAVAILABLE
        }
    }
    
    /**
     * Check if both API and Intent methods are available
     */
    suspend fun areBothMethodsAvailable(): Boolean = withContext(Dispatchers.IO) {
        val apiAvailable = apiRepository.isAnkiDroidAvailable()
        val intentAvailable = intentRepository.isAnkiDroidAvailable()
        return@withContext apiAvailable && intentAvailable
    }
    
    /**
     * Get list of available methods for user selection
     */
    suspend fun getAvailableMethods(): List<AnkiImplementationType> = withContext(Dispatchers.IO) {
        val methods = mutableListOf<AnkiImplementationType>()
        
        if (apiRepository.isAnkiDroidAvailable()) {
            methods.add(AnkiImplementationType.API)
        }
        
        if (intentRepository.isAnkiDroidAvailable()) {
            methods.add(AnkiImplementationType.INTENT)
        }
        
        return@withContext methods
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
