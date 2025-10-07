package com.glosdalen.app.data.repository

import com.glosdalen.app.data.model.AnkiCard

/**
 * Repository interface specifically for AnkiDroid intent-based implementation.
 * Handles ACTION_SEND intent functionality.
 */
interface AnkiIntentRepository : AnkiRepository {
    
    /**
     * Check if AnkiDroid can handle ACTION_SEND intents
     */
    suspend fun canHandleActionSend(): Boolean
    
    /**
     * Create card using ACTION_SEND intent with subject/text fields
     */
    suspend fun createCardViaIntent(card: AnkiCard): Result<Unit>
    
    /**
     * Get intent for installing AnkiDroid from Play Store
     */
    suspend fun getInstallAnkiDroidIntent(): android.content.Intent?
}
