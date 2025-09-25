package com.swedishvocab.app.data.repository

import com.swedishvocab.app.data.model.AnkiCard

/**
 * Repository interface specifically for AnkiDroid AddContentApi implementation.
 * Handles API-specific functionality like permissions and deck management.
 */
interface AnkiApiRepository : AnkiRepository {
    
    /**
     * Check if the API permission is granted
     */
    suspend fun hasApiPermission(): Boolean
    
    /**
     * Request API permission from the user
     */
    suspend fun requestApiPermission(): Boolean
    
    /**
     * Ensure the required deck exists, create if necessary
     */
    suspend fun ensureDeckExists(deckName: String): Result<Long>
    
    /**
     * Ensure the required note type/model exists, create if necessary  
     */
    suspend fun ensureModelExists(modelName: String): Result<Long>
    
    /**
     * Get or create the basic two-field model for vocabulary cards
     */
    suspend fun getOrCreateBasicModel(): Result<Long>
    
    /**
     * Get list of available decks in AnkiDroid
     */
    suspend fun getAvailableDecks(): Result<Map<Long, String>>
    
    /**
     * Get list of available note types/models in AnkiDroid
     */
    suspend fun getAvailableModels(): Result<Map<Long, String>>
}
