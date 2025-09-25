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

        println("Sharing vocabulary card with AnkiDroid: ${card.fields["Front"]} -> ${card.fields["Back"]}")
        
        // AnkiDroid only supports text sharing - send formatted card data with clear instructions
        return shareFormattedCardData(card)
    }
    
    private fun shareFormattedCardData(card: AnkiCard): Result<Unit> {
        return try {
            println("Sharing clean card data with AnkiDroid")
            
            val front = card.fields["Front"] ?: ""
            val back = card.fields["Back"] ?: ""
            
            // Send ONLY the clean card data - no instructions or formatting
            // AnkiDroid will put this in the first field, user copies to correct fields
            val cardText = "$front\t$back"
            
            println("Clean card data: $cardText")
            
            val intent = Intent(ACTION_SEND).apply {
                type = TYPE_TEXT_PLAIN
                putExtra(Intent.EXTRA_TEXT, front)
                putExtra(Intent.EXTRA_SUBJECT, cardText) // Just the word, no prefix
                setPackage(ANKIDROID_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            println("SUCCESS: Card data shared with AnkiDroid")
            return Result.success(Unit)
            
        } catch (e: Exception) {
            println("ERROR sharing card data: ${e.message}")
            Result.failure(AnkiError.IntentFailed("Failed to share card data: ${e.message}"))
        }
    }

    private fun tryEnhancedTextImport(card: AnkiCard): Result<Unit> {
        return try {
            println("Trying method: Enhanced text import to AnkiDroid")
            
            // Format card data in AnkiDroid's expected import format
            val front = card.fields["Front"] ?: ""
            val back = card.fields["Back"] ?: ""
            val tags = if (card.tags.isNotEmpty()) card.tags.joinToString(" ") else ""
            
            // AnkiDroid expects tab-separated format: "Front\tBack\tTags"
            // Or semicolon-separated: "Front;Back;Tags"  
            val cardText = if (tags.isNotEmpty()) {
                "$front\t$back\t$tags"
            } else {
                "$front\t$back"
            }
            
            println("Enhanced card text (tab-separated): $cardText")
            
            val intent = Intent(ACTION_SEND).apply {
                type = TYPE_TEXT_PLAIN
                putExtra(Intent.EXTRA_TEXT, cardText)
                putExtra(Intent.EXTRA_SUBJECT, front) // Use front as subject, not "Anki Card: ..."
                
                // Try AnkiDroid-specific extras that might work
                putExtra("deckName", card.deckName)
                putExtra("deck_name", card.deckName)
                putExtra("modelName", card.modelName)
                putExtra("note_type", card.modelName)
                putExtra("notetype", card.modelName)
                
                setPackage(ANKIDROID_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // Check if this intent can be handled
            val resolveInfo = context.packageManager.resolveActivity(intent, 0)
            if (resolveInfo != null) {
                println("SUCCESS: AnkiDroid can handle enhanced text import")
                context.startActivity(intent)
                println("Sent enhanced card text to AnkiDroid")
                return Result.success(Unit)
            } else {
                println("FAILED: AnkiDroid cannot handle enhanced text import")
                return Result.failure(AnkiError.IntentFailed("AnkiDroid cannot handle enhanced text import"))
            }
        } catch (e: Exception) {
            println("ERROR in enhanced text import: ${e.message}")
            Result.failure(AnkiError.IntentFailed("Enhanced text import failed: ${e.message}"))
        }
    }

    private fun tryApiIntegration(card: AnkiCard): Result<Unit> {
        return try {
            println("Trying method: AnkiDroid API integration")
            
            // Try multiple API action names that AnkiDroid might support
            val apiActions = listOf(
                "com.ichi2.anki.api.ADD_NOTE",
                "com.ichi2.anki.ADD_NOTE", 
                "com.ichi2.anki.intent.ADD_NOTE"
            )
            
            for (action in apiActions) {
                val intent = Intent(action).apply {
                    // Multiple parameter formats that AnkiDroid API might accept
                    putExtra("note_type", card.modelName)
                    putExtra("modelName", card.modelName) 
                    putExtra("deck_name", card.deckName)
                    putExtra("deckName", card.deckName)
                    
                    // Field formats
                    val front = card.fields["Front"] ?: ""
                    val back = card.fields["Back"] ?: ""
                    
                    // Array format
                    putExtra("flds", arrayOf(front, back))
                    putExtra("fields", arrayOf(front, back))
                    
                    // Individual field format
                    putExtra("Front", front)
                    putExtra("Back", back)
                    putExtra("fld0", front)
                    putExtra("fld1", back)
                    
                    // Tags
                    if (card.tags.isNotEmpty()) {
                        putExtra("tags", card.tags.toTypedArray())
                        putExtra("tag", card.tags.joinToString(" "))
                    }
                    
                    setPackage(ANKIDROID_PACKAGE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                // Check if this intent can be handled
                val resolveInfo = context.packageManager.resolveActivity(intent, 0)
                if (resolveInfo != null) {
                    println("SUCCESS: Found working API action: $action")
                    context.startActivity(intent)
                    println("Used AnkiDroid API with action: $action")
                    return Result.success(Unit)
                } else {
                    println("FAILED: Action not supported: $action")
                }
            }
            
            return Result.failure(AnkiError.IntentFailed("No AnkiDroid API actions are supported"))
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
    }
    
    fun createAnkiCards(note: AnkiCard): Result<Unit> {
        if (!isAnkiDroidInstalled()) {
            return Result.failure(AnkiError.AnkiDroidNotInstalled)
        }
        
        return try {
            val intent = Intent(ACTION_SEND).apply {
                type = TYPE_TEXT_PLAIN
                putExtra(Intent.EXTRA_TEXT, note.fields["Front"])
                putExtra(Intent.EXTRA_SUBJECT, note.fields["Back"])
                setPackage(ANKIDROID_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(AnkiError.IntentFailed("Failed to share multiple cards: ${e.message}"))
        }
    }
    
    fun getAnkiDroidPlayStoreIntent(): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$ANKIDROID_PACKAGE")
        }
    }
}
