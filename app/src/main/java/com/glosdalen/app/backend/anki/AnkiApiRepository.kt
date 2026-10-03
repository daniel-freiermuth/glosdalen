package com.glosdalen.app.backend.anki

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.glosdalen.app.libs.copilot.util.TimeProvider
import com.ichi2.anki.api.AddContentApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for AnkiDroid AddContentApi implementation.
 * Handles API-specific functionality like permissions and deck management.
 */
@Singleton
class AnkiApiRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider
) : AnkiBackend {

    companion object {
        private const val TAG = "AnkiApiRepository"
        private const val PERMISSION_READ_WRITE_DATABASE = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    }

    private var cachedApi: AddContentApi? = null

    private fun getApi(): AddContentApi? {
        return try {
            if (cachedApi == null && AddContentApi.getAnkiDroidPackageName(context) != null) {
                cachedApi = AddContentApi(context)
            }
            cachedApi
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun isAnkiDroidAvailable(): Boolean = withContext(Dispatchers.IO) {
        val packageName = AddContentApi.getAnkiDroidPackageName(context)
        val apiAvailable = getApi() != null
        Log.d(TAG, "AnkiDroid package: $packageName, API available: $apiAvailable")
        return@withContext packageName != null && apiAvailable
    }

    /**
     * Check if the API permission is granted
     */
    suspend fun hasApiPermission(): Boolean = withContext(Dispatchers.IO) {
        val hasPermission = ContextCompat.checkSelfPermission(context, PERMISSION_READ_WRITE_DATABASE) == 
                PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "API permission check: $hasPermission")
        return@withContext hasPermission
    }

    /**
     * Trigger permission request by attempting a simple API operation
     */
    suspend fun triggerPermissionRequest(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val api = getApi() ?: return@withContext false
            // This should trigger the permission dialog if not already granted
            api.deckList
            Log.d(TAG, "Permission request triggered successfully")
            hasApiPermission()
        } catch (e: SecurityException) {
            Log.d(TAG, "Security exception during permission request: ${e.message}")
            false
        } catch (e: Exception) {
            Log.d(TAG, "Exception during permission request: ${e.message}")
            false
        }
    }

    /**
     * Ensure the required deck exists, create if necessary
     */
    suspend fun ensureDeckExists(deckName: String): Result<Long> = withContext(Dispatchers.IO) {
        return@withContext try {
            val api = getApi() ?: run {
                Log.e(TAG, "Failed to get API instance")
                return@withContext Result.failure(
                    AnkiError.ApiNotAvailable("AnkiDroid API not available")
                )
            }

            // Validate deck name - reject if it has whitespace around ::
            if (deckName.contains(Regex("\\s+::|::\\s+"))) {
                Log.e(TAG, "Invalid deck name with whitespace around '::': '$deckName'")
                return@withContext Result.failure(
                    AnkiError.DeckCreationFailed("Invalid deck name: whitespace not allowed around '::' separator. Please check your deck name template.")
                )
            }
            
            // Check if deck already exists (case-insensitive, as AnkiDroid treats deck names)
            val decks = api.deckList ?: return@withContext Result.failure(
                AnkiError.ApiError("Error managing deck: AnkiDroid returned no deck list")
            )
            val existingDeck = decks.entries.find { it.value.equals(deckName, ignoreCase = true) }
            
            if (existingDeck != null) {
                Result.success(existingDeck.key)
            } else {
                // Create new deck
                val deckId = api.addNewDeck(deckName)
                if (deckId != null) {
                    Result.success(deckId)
                } else {
                    Log.e(TAG, "Failed to create deck '$deckName' - API returned null")
                    Result.failure(AnkiError.DeckCreationFailed("Failed to create deck: $deckName"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in ensureDeckExists for '$deckName': ${e.message}", e)
            Result.failure(AnkiError.ApiError("Error managing deck: ${e.message}"))
        }
    }

    /**
     * Ensure the required note type/model exists, create if necessary
     */
    suspend fun ensureModelExists(noteType: AnkiNoteType): Result<Long> = withContext(Dispatchers.IO) {
        val modelName = noteType.modelName
        return@withContext try {
            val api = getApi() ?: run {
                Log.e(TAG, "Failed to get API instance")
                return@withContext Result.failure(
                    AnkiError.ApiNotAvailable("AnkiDroid API not available")
                )
            }

            // Check if model already exists
            val models = api.modelList ?: return@withContext Result.failure(
                AnkiError.ApiError("Error managing model: AnkiDroid returned no note type list")
            )
            Log.d(TAG, "Available models: ${models.values.toList()}")
            
            // First try exact match
            val existingModel = models.entries.find { it.value == modelName }
            
            if (existingModel != null) {
                Log.d(TAG, "Found exact model match: ${existingModel.value}")
                Result.success(existingModel.key)
            } else {
                // Handle built-in models vs custom models
                when (noteType) {
                    AnkiNoteType.BASIC_AND_REVERSED -> {
                        // Find the built-in reversed model, tolerating case and localized names
                        val reversedModel = findReversedModel(models)
                        if (reversedModel != null) {
                            Log.d(TAG, "Found reversed model variant: ${reversedModel.value}")
                            return@withContext Result.success(reversedModel.key)
                        }
                        
                        // Log available models for debugging
                        Log.e(TAG, "Built-in model '$modelName' not found in AnkiDroid. Available models: ${models.values.toList()}")
                        Result.failure(AnkiError.ModelCreationFailed("Reversed card model not found. Please open AnkiDroid and ensure default note types are available, or try creating a card manually first."))
                    }
                    AnkiNoteType.BASIC -> {
                        // Create new basic model with Front/Back fields
                        val modelId = api.addNewBasicModel(modelName)
                        if (modelId != null) {
                            Result.success(modelId)
                        } else {
                            Log.e(TAG, "Failed to create model '$modelName' - API returned null")
                            Result.failure(AnkiError.ModelCreationFailed("Failed to create model: $modelName"))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in ensureModelExists for '$modelName': ${e.message}", e)
            Result.failure(AnkiError.ApiError("Error managing model: ${e.message}"))
        }
    }

    override suspend fun createCard(card: AnkiCard): Result<Unit> {
        // Delegate to batch implementation for consistency and efficiency
        return createCards(listOf(card))
    }

    override suspend fun createCards(cards: List<AnkiCard>): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val api = getApi() ?: return@withContext Result.failure(
                AnkiError.ApiNotAvailable("AnkiDroid API not available")
            )

            // Group cards by deck and note type for batch operations
            val cardsByDeckAndNoteType = cards.groupBy {
                Pair(it.deckName, it.noteType)
            }

            for ((deckNoteTypePair, deckCards) in cardsByDeckAndNoteType) {
                val (deckName, noteType) = deckNoteTypePair
                
                // Ensure deck exists
                val deckResult = ensureDeckExists(deckName)
                val deckId = deckResult.getOrElse { 
                    return@withContext Result.failure(it)
                }

                // Ensure model exists - use the note type specified in the cards
                val modelResult = ensureModelExists(noteType)
                val modelId = modelResult.getOrElse { 
                    return@withContext Result.failure(it)
                }

                // Prepare notes for batch addition
                val notes = deckCards.map { card ->
                    // Add audio tags if audio files are provided
                    val frontWithAudio = addAudioTag(api, card.front, card.frontAudio)
                    val backWithAudio = addAudioTag(api, card.back, card.backAudio)
                    
                    arrayOf(frontWithAudio, backWithAudio)
                }

                val addResult = addNotesChecked(deckCards, notes) { fieldsList, tagsList ->
                    api.addNotes(modelId, deckId, fieldsList, tagsList)
                }
                if (addResult.isFailure) {
                    return@withContext addResult
                }
            }

            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(AnkiError.PermissionDenied("Permission denied: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(AnkiError.ApiError("Batch API error: ${e.message}"))
        }
    }

    /**
     * Add audio file to AnkiDroid media collection and return the field with [sound:...] tag.
     * Returns the original text if audio file is null or copying fails.
     */
    private fun addAudioTag(api: AddContentApi, fieldText: String, audioFile: File?): String {
        if (audioFile == null || !audioFile.exists()) {
            return fieldText
        }
        
        return try {
            // Use FileProvider to create a content URI that AnkiDroid can access
            val authority = "${context.packageName}.fileprovider"
            val mediaUri = FileProvider.getUriForFile(context, authority, audioFile)
            
            // Grant read permission to AnkiDroid
            val ankiPackage = AddContentApi.getAnkiDroidPackageName(context)
            if (ankiPackage != null) {
                context.grantUriPermission(ankiPackage, mediaUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // Generate a unique preferred name based on the file name
            val preferredName = "glosdalen_${timeProvider.currentTimeMillis()}"
            val soundTag = api.addMediaFromUri(mediaUri, preferredName, "audio")
            
            // Revoke the permission after the file is copied
            if (ankiPackage != null) {
                context.revokeUriPermission(mediaUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // addMediaFromUri returns the formatted [sound:...] tag directly, or null if it failed
            if (soundTag != null) {
                Log.d(TAG, "Successfully added audio: $soundTag")
                if (fieldText.isNotBlank()) {
                    "$fieldText $soundTag"
                } else {
                    soundTag
                }
            } else {
                Log.w(TAG, "addMediaFromUri returned null for: ${audioFile.name}")
                fieldText
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add audio file: ${e.message}", e)
            fieldText // Return original text if audio addition fails
        }
    }
    
    /**
     * Get list of available decks in AnkiDroid
     */
    suspend fun getAvailableDecks(): Result<Map<Long, String>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val api = getApi() ?: return@withContext Result.failure(
                AnkiError.ApiNotAvailable("AnkiDroid API not available")
            )
            
            api.deckList?.let { Result.success(it) }
                ?: Result.failure(AnkiError.ApiError("Error retrieving decks: AnkiDroid returned no deck list"))
        } catch (e: Exception) {
            Result.failure(AnkiError.ApiError("Error retrieving decks: ${e.message}"))
        }
    }

}

/**
 * Localized names of the stock "Basic (and reversed card)" note type, stored lowercase.
 *
 * Exact names only: substring heuristics matched the "optional reversed" note type (3 fields,
 * no reverse card unless the third field is filled) and unrelated user models, and missed most
 * real translations. Sources, current and historical values:
 * - ankitects/anki-core-i18n core/<lang>/notetypes.ftl, key notetypes-basic-reversed-name
 *   (names given by AnkiDroid's Rust backend and Anki desktop)
 * - ankidroid/Anki-Android res/values-<lang>, string forward_reverse_model_name
 *   (names given by the pre-Rust AnkiDroid libanki)
 * Names that either source also uses for another note type are left out.
 */
private val LOCALIZED_REVERSED_MODEL_NAMES: Set<String> = setOf(
    "Alap (mindkét irányban)",
    "Alapvető (és fordított kártya)",
    "Asas (dan kad terbalik)",
    "Bas (och omvänt kort)",
    "Basic (at may kabalikan na card)",
    "Basic (en omgekeerde kaart)",
    "Basic (裏表反転カード付き)",
    "Basiese (en omgekeerde kaart)",
    "Basilare (e carta inversa)",
    "Basique (et carte inversée)",
    "Basique (et envers de la carte)",
    "Basis (en omgekeerde kaart)",
    "Baza (en ambaŭ direktoj)",
    "Bunúsach (agus cárta aisiompaithe)",
    "Bunúsach (le malairt an chárta)",
    "Bàsic (i carta girada)",
    "Bàsica (i carta reversa)",
    "Bàsica (i targeta inversa)",
    "Básica (e tarxeta invertida)",
    "Básico (e card invertido)",
    "Básico (e carta invertida)",
    "Básico (e cartão invertido)",
    "Básico (e ficha invertida)",
    "Básico (y tarjeta invertida)",
    "Cơ bản (và thẻ đảo ngược)",
    "Cơ bản (với thẻ ngược)",
    "Dasar (dan kartu bolak-balik)",
    "Dasar (juga kartu terbalik)",
    "De base (e carta furriada)",
    "De bază (și cu card întors)",
    "Einfach (beide Richtungen)",
    "Einfach (und die umgekehrte Richtung)",
    "Enkel (och omvänt kort)",
    "Generalitats (dos senses)",
    "Grundlegende (und umgekehrte Karte)",
    "Grundlæggende (og kort baglæns)",
    "Grunnleggende (og motsatt kortet)",
    "Généralités (deux sens)",
    "malli (ja käänteinen kortti)",
    "Oddiy (va teskari karta)",
    "Oinarrizko (eta alderantzizko karta)",
    "Osnovna (i obratna kartica)",
    "Osnovna (in obrnjena kartica)",
    "Pamata (un apvērstā kartīte)",
    "Perusmalli (ja käännetty kortti)",
    "Perusmalli (ja käänteinen kortti)",
    "Podstawowy (z odwrotną kartą)",
    "Standard (e carta invertita)",
    "Standard (und umgekehrte Karte)",
    "Temel (ve ters kart)",
    "Temel (ve ters çevrilmiş kart)",
    "Základná (a obrátená kartička)",
    "Základní (a obrácená karta)",
    "Základní (plus obrácená karta)",
    "Základný (plus obrátená karta)",
    "Βασική (και αντίστροφη κάρτα)",
    "Βασική (και αντεστραμμένη κάρτα)",
    "Асноўная (з адваротнай карткай)",
    "Асноўны (і адваротная картка)",
    "Базавая (з адваротнай карткай)",
    "Базова (зі зворотною карткою)",
    "Базова (із зворотньою карткою)",
    "Базовий (зі зворотною карткою)",
    "Основна (и обратне карте)",
    "Основна (и обърната карта)",
    "Основная (с обратной карточкой)",
    "Основний (з оберненою карткою)",
    "Простая (с обратной карточкой)",
    "Қарапайым (және кері карта)",
    "Հիմնական (+ հետադարձ քարտերը)",
    "בסיסי (וכרטיס הופכי)",
    "בסיסי (וכרטיס רוורס)",
    "בסיסית  (וכרטיסייה הופכית)",
    "בסיסית (וכרטיסייה הופכית)",
    "עיקרדיק (און פֿאַרקערט קאַרטל)",
    "أساسي (وبطاقة معكوسة)",
    "ئاساسى (ئۆرۈلىدىغان كارتا)",
    "بطاقة أساسية وبطاقة معكوسة",
    "بنچینەیی (و کارتی پێچەوانە)",
    "بنیادی (اور الٹ کارڈ)",
    "پایه (و کارت معکوس شده)",
    "پایه (و کارت وارونه)",
    "मूल (और उलटा कार्ड)",
    "मूलभूत (आणि उलट कार्ड)",
    "मूळ (व उलट पत्ता)",
    "মৌলিক (এবং উল্টানো কার্ড)",
    "મૂળભૂત (અને રિવર્સ્ડ કાર્ડ)",
    "ମୌଳିକ (ଏବଂ ଓଲଟା ପତ୍ର)",
    "ప్రాథమిక (మరియు రివర్స్డ్ కార్డ్)",
    "അടിസ്ഥാന (വിപരീത കാർഡും)",
    "അടിസ്ഥാനം (വിപരീത കാർഡും)",
    "พื้นฐาน (และการ์ดแบบกลับด้าน)",
    "ზოგადი (და გადმობრუნებული ბარათი)",
    "ᱵᱮᱥᱤᱠ (ᱟᱨ ᱯᱟᱪᱹᱞᱟ ᱠᱟᱰ)",
    "基本 (表面と裏面を反転したカードも作成)",
    "基本 (裏表反転カード付き)",
    "基本型 (含反向卡片)",
    "基本型 (含反向的卡片)",
    "基本型(含反向的卡片)",
    "基本型（含反向卡片）",
    "基本型（含反向的卡片）",
    "基本型（含翻转的卡片）",
    "基础的（和相反的卡片）",
    "问答题（同时生成翻转的卡片）",
    "问答题（附翻转卡片）",
    "기본 (역방향 카드 포함)"
).mapTo(HashSet()) { it.lowercase() }

/**
 * Locate AnkiDroid's built-in "Basic (and reversed card)" model in [models] (id -> name).
 *
 * Tries a case-insensitive match on the English name first, then on the stock note type's
 * localized names ([LOCALIZED_REVERSED_MODEL_NAMES]).
 */
internal fun findReversedModel(models: Map<Long, String>): Map.Entry<Long, String>? {
    models.entries.find { it.value.equals(AnkiNoteType.BASIC_AND_REVERSED.modelName, ignoreCase = true) }
        ?.let { return it }

    return models.entries.find { it.value.lowercase() in LOCALIZED_REVERSED_MODEL_NAMES }
}

/**
 * Submits [notes] (one per entry of [cards], same order) through [addNotes] together with each
 * card's tags, and fails unless every note was added.
 *
 * [addNotes] mirrors `AddContentApi.addNotes`, which returns the number of notes added
 * (< 0 means there was a problem).
 */
internal fun addNotesChecked(
    cards: List<AnkiCard>,
    notes: List<Array<String>>,
    addNotes: (fieldsList: List<Array<String>>, tagsList: List<Set<String>>) -> Int
): Result<Unit> {
    val added = addNotes(notes, cards.map { it.tags.toSet() })
    return if (added == notes.size) {
        Result.success(Unit)
    } else {
        Result.failure(AnkiError.CardCreationFailed("AnkiDroid added $added of ${notes.size} notes"))
    }
}
