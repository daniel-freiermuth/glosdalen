package com.swedishvocab.app.anki

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.swedishvocab.app.data.model.AnkiCard
import com.swedishvocab.app.data.model.AnkiError
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnkiIntegration @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val ANKIDROID_PACKAGE = "com.ichi2.anki"
        private const val ANKIDROID_ACTION_ADD_NOTE = "com.ichi2.anki.api.ADD_NOTE"
    }
    
    fun isAnkiDroidInstalled(): Boolean {
        // Method 1: Try package manager
        try {
            context.packageManager.getPackageInfo(ANKIDROID_PACKAGE, 0)
            return true
        } catch (e: PackageManager.NameNotFoundException) {
            // Continue to method 2
        }
        
        // Method 2: Check if intent can be resolved
        try {
            val intent = Intent(ANKIDROID_ACTION_ADD_NOTE).apply {
                setPackage(ANKIDROID_PACKAGE)
            }
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolveInfo != null) return true
        } catch (e: Exception) {
            // Continue to method 3
        }
        
        // Method 3: Try to query activities that handle our intent
        try {
            val intent = Intent(ANKIDROID_ACTION_ADD_NOTE)
            val activities = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            return activities.any { it.activityInfo.packageName == ANKIDROID_PACKAGE }
        } catch (e: Exception) {
            return false
        }
    }
    
    fun createCard(card: AnkiCard): Result<Unit> {
        if (!isAnkiDroidInstalled()) {
            return Result.failure(AnkiError.AnkiDroidNotInstalled)
        }
        
        return try {
            val intent = Intent(ANKIDROID_ACTION_ADD_NOTE).apply {
                putExtra("deck", card.deckName)
                putExtra("model", card.modelName)
                
                // Add fields
                card.fields.forEach { (key, value) ->
                    putExtra("fld_$key", value)
                }
                
                // Add tags
                if (card.tags.isNotEmpty()) {
                    putExtra("tags", card.tags.joinToString(" "))
                }
                
                // Make sure AnkiDroid handles this
                setPackage(ANKIDROID_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AnkiError.IntentFailed(e.message))
        }
    }
    
    fun createMultipleCards(cards: List<AnkiCard>): Result<Unit> {
        if (!isAnkiDroidInstalled()) {
            return Result.failure(AnkiError.AnkiDroidNotInstalled)
        }
        
        // Create cards sequentially for now
        cards.forEach { card ->
            val result = createCard(card)
            if (result.isFailure) {
                return result
            }
        }
        
        return Result.success(Unit)
    }
    
    fun getAnkiDroidPlayStoreIntent(): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$ANKIDROID_PACKAGE")
        }
    }
}
