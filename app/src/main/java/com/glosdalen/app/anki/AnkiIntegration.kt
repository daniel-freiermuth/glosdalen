package com.glosdalen.app.anki

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.glosdalen.app.data.model.AnkiCard
import com.glosdalen.app.data.model.AnkiError
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
                putExtra(Intent.EXTRA_TEXT, note.fields["Back"])
                putExtra(Intent.EXTRA_SUBJECT, note.fields["Front"])
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
