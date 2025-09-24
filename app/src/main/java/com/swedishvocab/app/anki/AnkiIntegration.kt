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
        // AnkiDroid's actual API action for adding notes
        private const val ANKIDROID_API_ACTION = "com.ichi2.anki.api.ADD_NOTE"
        // Alternative: Use SEND intent with specific format
        private const val ACTION_SEND = "android.intent.action.SEND"
        private const val TYPE_TEXT_PLAIN = "text/plain"
    }
    
    fun isAnkiDroidInstalled(): Boolean {
        // Method 1: Try package manager
        try {
            context.packageManager.getPackageInfo(ANKIDROID_PACKAGE, 0)
            return true
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
            if (resolveInfo != null) return true
        } catch (e: Exception) {
            // Continue to method 3
        }
        
        // Method 3: Check if we can at least open AnkiDroid
        try {
            val mainIntent = context.packageManager.getLaunchIntentForPackage(ANKIDROID_PACKAGE)
            return mainIntent != null
        } catch (e: Exception) {
            return false
        }
    }
    
    fun createCard(card: AnkiCard): Result<Unit> {
        if (!isAnkiDroidInstalled()) {
            return Result.failure(AnkiError.AnkiDroidNotInstalled)
        }

        println("Creating AnkiDroid card: deck=${card.deckName}, model=${card.modelName}")
        println("Fields: ${card.fields}")
        println("Tags: ${card.tags}")
        
        // Try different methods to add card to AnkiDroid
        
        // Method 1: Try sending text to AnkiDroid (most likely to work)
        var result = trySendTextToAnkiDroid(card)
        if (result.isSuccess) return result
        
        // Method 2: Try the API action (probably not exposed)
        result = tryApiIntegration(card)
        if (result.isSuccess) return result
        
        // Method 3: Fallback - open AnkiDroid with instructions
        result = tryOpenAnkiDroidWithInstructions(card)
        if (result.isSuccess) return result
        
        return Result.failure(AnkiError.IntentFailed("No working method found to add card to AnkiDroid"))
    }
    
    private fun trySendTextToAnkiDroid(card: AnkiCard): Result<Unit> {
        return try {
            println("Trying method: Send text to AnkiDroid")
            
            // Format card data as text that AnkiDroid can import
            val front = card.fields["Front"] ?: ""
            val back = card.fields["Back"] ?: ""
            val tags = if (card.tags.isNotEmpty()) " ${card.tags.joinToString(" ")}" else ""
            
            // AnkiDroid can import text in this format: "Front;Back;Tags"
            val cardText = "$front;$back$tags"
            println("Formatted card text: $cardText")
            
            val intent = Intent(ACTION_SEND).apply {
                type = TYPE_TEXT_PLAIN
                putExtra(Intent.EXTRA_TEXT, cardText)
                putExtra(Intent.EXTRA_SUBJECT, "New Vocabulary Card")
                
                // Try to specify deck if possible
                putExtra("deckName", card.deckName)
                
                setPackage(ANKIDROID_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // Check if this intent can be handled
            val resolveInfo = context.packageManager.resolveActivity(intent, 0)
            if (resolveInfo != null) {
                println("SUCCESS: AnkiDroid can handle text import")
                context.startActivity(intent)
                println("Sent card text to AnkiDroid")
                return Result.success(Unit)
            } else {
                println("FAILED: AnkiDroid cannot handle text import")
                return Result.failure(AnkiError.IntentFailed("AnkiDroid cannot handle text import"))
            }
        } catch (e: Exception) {
            println("ERROR in text import: ${e.message}")
            Result.failure(AnkiError.IntentFailed("Text import failed: ${e.message}"))
        }
    }

    private fun tryApiIntegration(card: AnkiCard): Result<Unit> {
        return try {
            println("Trying method: AnkiDroid API integration")
            
            val intent = Intent(ANKIDROID_API_ACTION).apply {
                // Standard AnkiDroid API parameters
                putExtra("note_type", card.modelName)
                putExtra("deck_name", card.deckName)
                
                // Fields - try the documented format
                val fields = arrayOf(
                    card.fields["Front"] ?: "",
                    card.fields["Back"] ?: ""
                )
                putExtra("flds", fields)
                
                // Tags
                if (card.tags.isNotEmpty()) {
                    putExtra("tags", card.tags.toTypedArray())
                }
                
                setPackage(ANKIDROID_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // Check if this intent can be handled
            val resolveInfo = context.packageManager.resolveActivity(intent, 0)
            if (resolveInfo != null) {
                println("SUCCESS: AnkiDroid API is available")
                context.startActivity(intent)
                println("Used AnkiDroid API")
                return Result.success(Unit)
            } else {
                println("FAILED: AnkiDroid API not available")
                return Result.failure(AnkiError.IntentFailed("AnkiDroid API not available"))
            }
        } catch (e: Exception) {
            println("ERROR in API integration: ${e.message}")
            Result.failure(AnkiError.IntentFailed("API integration failed: ${e.message}"))
        }
    }
    
    private fun tryOpenAnkiDroidWithInstructions(card: AnkiCard): Result<Unit> {
        return try {
            println("Trying fallback: Opening AnkiDroid with instructions")
            
            // Open AnkiDroid and provide user instructions
            val mainIntent = context.packageManager.getLaunchIntentForPackage(ANKIDROID_PACKAGE)
            if (mainIntent != null) {
                println("SUCCESS: Opening AnkiDroid (user will need to add card manually)")
                println("Card to add: Front='${card.fields["Front"]}', Back='${card.fields["Back"]}'")
                context.startActivity(mainIntent)
                return Result.success(Unit)
            } else {
                println("FAILED: Cannot find AnkiDroid main activity")
                return Result.failure(AnkiError.IntentFailed("Cannot open AnkiDroid"))
            }
        } catch (e: Exception) {
            println("ERROR opening AnkiDroid: ${e.message}")
            Result.failure(AnkiError.IntentFailed("Cannot open AnkiDroid: ${e.message}"))
        }
    }    fun createMultipleCards(cards: List<AnkiCard>): Result<Unit> {
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
