package com.glosdalen.app.data.repository.impl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.glosdalen.app.data.model.AnkiCard
import com.glosdalen.app.data.model.AnkiError
import com.glosdalen.app.data.repository.AnkiImplementationType
import com.glosdalen.app.data.repository.AnkiIntentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnkiIntentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AnkiIntentRepository {

    companion object {
        private const val ANKIDROID_PACKAGE = "com.ichi2.anki"
        private const val ACTION_SEND = "android.intent.action.SEND"
        private const val TYPE_TEXT_PLAIN = "text/plain"
    }

    override suspend fun isAnkiDroidAvailable(): Boolean = withContext(Dispatchers.IO) {
        // Method 1: Try package manager
        try {
            context.packageManager.getPackageInfo(ANKIDROID_PACKAGE, 0)
            return@withContext true
        } catch (e: PackageManager.NameNotFoundException) {
            // Continue to method 2
        }
        
        // Method 2: Check if we can send text to AnkiDroid
        try {
            val intent = Intent(ACTION_SEND).apply {
                type = TYPE_TEXT_PLAIN
                setPackage(ANKIDROID_PACKAGE)
            }
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolveInfo != null) return@withContext true
        } catch (e: Exception) {
            // Continue to method 3
        }
        
        // Method 3: Check if we can at least open AnkiDroid
        try {
            val mainIntent = context.packageManager.getLaunchIntentForPackage(ANKIDROID_PACKAGE)
            return@withContext mainIntent != null
        } catch (e: Exception) {
            return@withContext false
        }
    }

    override suspend fun canHandleActionSend(): Boolean = withContext(Dispatchers.IO) {
        try {
            val intent = Intent(ACTION_SEND).apply {
                type = TYPE_TEXT_PLAIN
                setPackage(ANKIDROID_PACKAGE)
            }
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            return@withContext resolveInfo != null
        } catch (e: Exception) {
            return@withContext false
        }
    }

    override suspend fun createCard(card: AnkiCard): Result<Unit> {
        return createCardViaIntent(card)
    }

    override suspend fun createCardViaIntent(card: AnkiCard): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isAnkiDroidAvailable()) {
            return@withContext Result.failure(AnkiError.AnkiDroidNotInstalled)
        }
        
        return@withContext try {
            val intent = Intent(ACTION_SEND).apply {
                type = TYPE_TEXT_PLAIN
                putExtra(Intent.EXTRA_TEXT, card.fields["Back"] ?: "")
                putExtra(Intent.EXTRA_SUBJECT, card.fields["Front"] ?: "")
                setPackage(ANKIDROID_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            android.util.Log.d("AnkiIntentRepository", "Starting AnkiDroid intent with Front: ${card.fields["Front"]}, Back: ${card.fields["Back"]}")
            context.startActivity(intent)
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(AnkiError.IntentFailed("Failed to create card via intent: ${e.message}"))
        }
    }

    override suspend fun createCards(cards: List<AnkiCard>): Result<Unit> = withContext(Dispatchers.IO) {
        // Intent-based approach doesn't support batch operations efficiently
        // We'll create cards one by one
        for (card in cards) {
            val result = createCard(card)
            if (result.isFailure) {
                return@withContext result
            }
            
            // Small delay between cards to avoid overwhelming the intent system
            kotlinx.coroutines.delay(100)
        }
        
        Result.success(Unit)
    }

    override suspend fun getInstallAnkiDroidIntent(): Intent? = withContext(Dispatchers.IO) {
        return@withContext try {
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$ANKIDROID_PACKAGE")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun supportsBatchOperations(): Boolean = false

    override fun getImplementationType(): AnkiImplementationType = AnkiImplementationType.INTENT
}
