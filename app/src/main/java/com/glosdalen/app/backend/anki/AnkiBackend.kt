package com.glosdalen.app.backend.anki

/**
 * Common interface for AnkiDroid backend implementations.
 * Defines the core card operations that all backends must support.
 */
interface AnkiBackend {
    /**
     * Check if this backend is available
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
     * Get the implementation type
     */
    fun getImplementationType(): AnkiImplementationType
}
