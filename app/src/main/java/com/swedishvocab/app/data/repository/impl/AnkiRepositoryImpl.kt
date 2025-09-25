package com.swedishvocab.app.data.repository.impl

import android.content.Context
import android.util.Log
import com.ichi2.anki.api.AddContentApi
import com.swedishvocab.app.data.model.AnkiCard
import com.swedishvocab.app.data.model.AnkiError
import com.swedishvocab.app.data.repository.AnkiImplementationType
import com.swedishvocab.app.data.repository.AnkiRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnkiRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiRepository: AnkiApiRepositoryImpl,
    private val intentRepository: AnkiIntentRepositoryImpl
) : AnkiRepository {

    companion object {
        private const val TAG = "AnkiRepositoryImpl"
    }

    /**
     * Determine the best available AnkiDroid integration method
     */
    private suspend fun getBestAvailableRepository(): AnkiRepository = withContext(Dispatchers.IO) {
        // 1. Try API first (recommended approach)
        if (apiRepository.isAnkiDroidAvailable()) {
            Log.d(TAG, "API is available, checking permissions...")
            
            if (apiRepository.hasApiPermission()) {
                Log.d(TAG, "API permission granted, using API implementation")
                return@withContext apiRepository
            } else {
                Log.d(TAG, "API permission not granted, will fall back to intent")
                // Don't attempt to create test cards - this was causing phantom "success" toasts
                // Permission will be requested when user first tries to create a real card
            }
        }
        
        // 2. Fall back to intent-based approach
        if (intentRepository.isAnkiDroidAvailable()) {
            Log.d(TAG, "API not available, falling back to intent implementation")
            return@withContext intentRepository
        }
        
        // 3. No AnkiDroid integration available
        Log.w(TAG, "No AnkiDroid integration available")
        throw AnkiError.AnkiDroidNotInstalled
    }

    override suspend fun isAnkiDroidAvailable(): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun createCard(card: AnkiCard): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val repository = getBestAvailableRepository()
            val result = repository.createCard(card)
            
            Log.d(TAG, "Card creation ${if (result.isSuccess) "successful" else "failed"} " +
                      "using ${repository.getImplementationType()} implementation")
            
            result
        } catch (e: AnkiError) {
            Log.e(TAG, "AnkiDroid integration error", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during card creation", e)
            Result.failure(AnkiError.IntentFailed("Unexpected error: ${e.message}"))
        }
    }

    override suspend fun createCards(cards: List<AnkiCard>): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val repository = getBestAvailableRepository()
            val result = repository.createCards(cards)
            
            Log.d(TAG, "Batch card creation ${if (result.isSuccess) "successful" else "failed"} " +
                      "using ${repository.getImplementationType()} implementation " +
                      "(${cards.size} cards, batch supported: ${repository.supportsBatchOperations()})")
            
            result
        } catch (e: AnkiError) {
            Log.e(TAG, "AnkiDroid integration error during batch creation", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during batch card creation", e)
            Result.failure(AnkiError.IntentFailed("Unexpected batch error: ${e.message}"))
        }
    }

    override fun supportsBatchOperations(): Boolean {
        // Return true if we might use the API (which supports batch), false otherwise
        // This is a best-guess since we can't easily check async here
        return AddContentApi.getAnkiDroidPackageName(context) != null
    }

    override fun getImplementationType(): AnkiImplementationType {
        return runBlocking {
            try {
                val repository = getBestAvailableRepository()
                repository.getImplementationType()
            } catch (e: Exception) {
                Log.w(TAG, "Error determining implementation type", e)
                AnkiImplementationType.INTENT
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
            val repository = getBestAvailableRepository()
            repository.getImplementationType()
        } catch (e: Exception) {
            Log.w(TAG, "Error refreshing availability", e)
            AnkiImplementationType.UNAVAILABLE
        }
    }
}
