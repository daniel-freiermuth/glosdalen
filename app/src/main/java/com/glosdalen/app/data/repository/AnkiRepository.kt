package com.glosdalen.app.data.repository

import com.glosdalen.app.data.model.AnkiCard

/**
 * Repository interface for AnkiDroid integration.
 * Provides a clean API for card creation regardless of underlying implementation.
 */
interface AnkiRepository {
    
    /**
     * Check if AnkiDroid is available for use
     */
    suspend fun isAnkiDroidAvailable(): Boolean
    
    /**
     * Create a single Anki card
     */
    suspend fun createCard(card: AnkiCard): Result<Unit>
    
    /**
     * Create multiple Anki cards in batch (when supported)
     */
    suspend fun createCards(cards: List<AnkiCard>): Result<Unit>
    
    /**
     * Check if the current implementation supports batch operations
     */
    fun supportsBatchOperations(): Boolean
    
    /**
     * Get the current implementation type for debugging/logging
     */
    fun getImplementationType(): AnkiImplementationType
}

/**
 * Types of AnkiDroid integration implementations
 */
enum class AnkiImplementationType {
    API,        // Using AddContentApi 
    INTENT,     // Using ACTION_SEND intent
    UNAVAILABLE // AnkiDroid not available
}
