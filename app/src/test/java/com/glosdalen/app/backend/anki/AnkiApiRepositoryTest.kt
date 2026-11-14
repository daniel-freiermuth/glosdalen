package com.glosdalen.app.backend.anki

import android.content.ContextWrapper
import com.glosdalen.app.libs.copilot.util.TimeProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Contract of [AnkiApiRepository.createCards]: group cards by (deck, model), make sure each
 * deck and model exists, add each group's notes in one batch, embed audio as `[sound:...]`
 * tags, and map AnkiDroid exceptions to [AnkiError]s. Also covers [findReversedModel].
 */
class AnkiApiRepositoryTest {

    private class AddNotesCall(val modelId: Long, val deckId: Long, val notes: List<List<String>>)

    private class FakeAnkiContentApi(
        decks: Map<Long, String> = emptyMap(),
        models: Map<Long, String> = mapOf(BASIC_MODEL_ID to BASIC_MODEL)
    ) : AnkiContentApi {
        val decks = decks.toMutableMap()
        val models = models.toMutableMap()
        val addNotesCalls = mutableListOf<AddNotesCall>()
        val createdDecks = mutableListOf<String>()
        var nextId = 1000L
        var failDeckCreation = emptySet<String>()
        var addNotesError: Exception? = null
        var audioResult: (File) -> String? = { "[sound:${it.name}]" }
        val audioPreferredNames = mutableListOf<String>()

        override val deckList: Map<Long, String> get() = decks.toMap()
        override val modelList: Map<Long, String> get() = models.toMap()

        override fun addNewDeck(deckName: String): Long? {
            if (deckName in failDeckCreation) return null
            createdDecks += deckName
            return nextId++.also { decks[it] = deckName }
        }

        override fun addNewBasicModel(modelName: String): Long? = nextId++.also { models[it] = modelName }

        override fun addNotes(
            modelId: Long,
            deckId: Long,
            fieldsList: List<Array<String>>,
            tagsList: List<Set<String>>
        ): Int {
            addNotesError?.let { throw it }
            addNotesCalls += AddNotesCall(modelId, deckId, fieldsList.map { it.toList() })
            return fieldsList.size
        }

        override fun addAudio(audioFile: File, preferredName: String): String? {
            audioPreferredNames += preferredName
            return audioResult(audioFile)
        }
    }

    private fun repository(api: AnkiContentApi?) = AnkiApiRepository(
        context = ContextWrapper(null),
        timeProvider = object : TimeProvider {
            override fun currentTimeMillis(): Long = 42L
        },
        apiFactory = { api }
    )

    private fun card(
        front: String? = "Hund",
        back: String? = "dog",
        deck: String = "German",
        model: String = BASIC_MODEL,
        audio: Map<String, File> = emptyMap()
    ) = AnkiCard(
        modelName = model,
        fields = buildMap {
            front?.let { put("Front", it) }
            back?.let { put("Back", it) }
        },
        deckName = deck,
        audioFiles = audio
    )

    private fun createCards(api: AnkiContentApi?, vararg cards: AnkiCard): Result<Unit> =
        runBlocking { repository(api).createCards(cards.toList()) }

    @Nested
    inner class Grouping {

        @Test
        fun `empty card list succeeds without creating decks or notes`() {
            val api = FakeAnkiContentApi()

            val result = createCards(api)

            assertTrue(result.isSuccess, "$result")
            assertTrue(api.createdDecks.isEmpty(), "${api.createdDecks}")
            assertTrue(api.addNotesCalls.isEmpty())
        }

        @Test
        fun `cards sharing deck and model are added in one batch in input order`() {
            val api = FakeAnkiContentApi(decks = mapOf(7L to "German"))

            val result = createCards(api, card("Hund", "dog"), card("Katze", "cat"))

            assertTrue(result.isSuccess, "$result")
            assertEquals(1, api.addNotesCalls.size)
            val call = api.addNotesCalls.single()
            assertEquals(BASIC_MODEL_ID, call.modelId)
            assertEquals(7L, call.deckId)
            assertEquals(listOf(listOf("Hund", "dog"), listOf("Katze", "cat")), call.notes)
        }

        @Test
        fun `cards in different decks get one batch per deck, reusing existing decks case-insensitively`() {
            val api = FakeAnkiContentApi(decks = mapOf(7L to "german"))

            val result = createCards(
                api,
                card("Hund", "dog", deck = "German"),
                card("chien", "dog", deck = "French"),
                card("Katze", "cat", deck = "German")
            )

            assertTrue(result.isSuccess, "$result")
            assertEquals(listOf("French"), api.createdDecks)
            val frenchId = api.decks.entries.single { it.value == "French" }.key
            val notesByDeck = api.addNotesCalls.associate { it.deckId to it.notes }
            assertEquals(
                mapOf(
                    7L to listOf(listOf("Hund", "dog"), listOf("Katze", "cat")),
                    frenchId to listOf(listOf("chien", "dog"))
                ),
                notesByDeck
            )
        }

        @Test
        fun `missing Front and Back fields are sent as empty strings`() {
            val api = FakeAnkiContentApi()

            createCards(api, card(front = null, back = null))

            assertEquals(listOf(listOf("", "")), api.addNotesCalls.single().notes)
        }
    }

    @Nested
    inner class Failures {

        @Test
        fun `unavailable AnkiDroid fails with ApiNotAvailable`() {
            val result = createCards(null, card())

            assertTrue(result.exceptionOrNull() is AnkiError.ApiNotAvailable, "$result")
        }

        @Test
        fun `deck creation failure is reported and that group's notes are not added`() {
            val api = FakeAnkiContentApi().apply { failDeckCreation = setOf("Broken") }

            val result = createCards(api, card(deck = "Broken"))

            assertTrue(result.exceptionOrNull() is AnkiError.DeckCreationFailed, "$result")
            assertTrue(api.addNotesCalls.isEmpty())
        }

        @Test
        fun `deck name with whitespace around separator is rejected before reaching AnkiDroid`() {
            val api = FakeAnkiContentApi()

            val result = createCards(api, card(deck = "German :: Verbs"))

            assertTrue(result.exceptionOrNull() is AnkiError.DeckCreationFailed, "$result")
            assertTrue(api.createdDecks.isEmpty(), "${api.createdDecks}")
            assertTrue(api.addNotesCalls.isEmpty())
        }

        @Test
        fun `missing reversed note type fails with ModelCreationFailed and adds no notes`() {
            val api = FakeAnkiContentApi()

            val result = createCards(api, card(model = "Basic (and reversed card)"))

            assertTrue(result.exceptionOrNull() is AnkiError.ModelCreationFailed, "$result")
            assertTrue(api.addNotesCalls.isEmpty())
        }

        @Test
        fun `SecurityException from AnkiDroid maps to PermissionDenied`() {
            val api = FakeAnkiContentApi().apply { addNotesError = SecurityException("no access") }

            val result = createCards(api, card())

            assertEquals(AnkiError.PermissionDenied("Permission denied: no access"), result.exceptionOrNull())
        }

        @Test
        fun `other exceptions from AnkiDroid map to ApiError`() {
            val api = FakeAnkiContentApi().apply { addNotesError = IllegalStateException("db locked") }

            val result = createCards(api, card())

            assertEquals(AnkiError.ApiError("Batch API error: db locked"), result.exceptionOrNull())
        }
    }

    @Nested
    inner class Audio {

        @TempDir
        lateinit var dir: File

        private fun audioFile(name: String) = File(dir, name).apply { writeText("mp3") }

        @Test
        fun `sound tags are appended to each field with a space`() {
            val api = FakeAnkiContentApi()
            val audio = mapOf("Front" to audioFile("front.mp3"), "Back" to audioFile("back.mp3"))

            createCards(api, card("Hund", "dog", audio = audio))

            assertEquals(
                listOf(listOf("Hund [sound:front.mp3]", "dog [sound:back.mp3]")),
                api.addNotesCalls.single().notes
            )
        }

        @Test
        fun `front and back audio added in the same millisecond get distinct media names`() {
            val api = FakeAnkiContentApi()
            val audio = mapOf("Front" to audioFile("front.mp3"), "Back" to audioFile("back.mp3"))

            createCards(api, card("Hund", "dog", audio = audio))

            assertEquals(2, api.audioPreferredNames.toSet().size, "${api.audioPreferredNames}")
        }

        @Test
        fun `sound tag replaces a blank field instead of being appended`() {
            val api = FakeAnkiContentApi()

            createCards(api, card(front = " ", audio = mapOf("Front" to audioFile("front.mp3"))))

            assertEquals(listOf(listOf("[sound:front.mp3]", "dog")), api.addNotesCalls.single().notes)
        }

        @Test
        fun `audio rejected by AnkiDroid keeps the original text`() {
            val api = FakeAnkiContentApi().apply { audioResult = { null } }

            createCards(api, card(audio = mapOf("Front" to audioFile("front.mp3"))))

            assertEquals(listOf(listOf("Hund", "dog")), api.addNotesCalls.single().notes)
        }

        @Test
        fun `audio copy failure keeps the original text and still adds the note`() {
            val api = FakeAnkiContentApi().apply { audioResult = { throw IllegalArgumentException("no provider") } }

            val result = createCards(api, card(audio = mapOf("Front" to audioFile("front.mp3"))))

            assertTrue(result.isSuccess, "$result")
            assertEquals(listOf(listOf("Hund", "dog")), api.addNotesCalls.single().notes)
        }

        @Test
        fun `audio file that no longer exists is skipped`() {
            val api = FakeAnkiContentApi()

            createCards(api, card(audio = mapOf("Front" to File(dir, "deleted.mp3"))))

            assertEquals(listOf(listOf("Hund", "dog")), api.addNotesCalls.single().notes)
        }
    }

    @Nested
    inner class ReversedModelLookup {

        private fun reversedModelId(vararg models: Pair<Long, String>): Long? =
            findReversedModel(linkedMapOf(*models))?.key

        // --- English name ---

        @Test
        fun `exact English name is found`() {
            assertEquals(2L, reversedModelId(1L to "Basic", 2L to "Basic (and reversed card)", 3L to "Cloze"))
        }

        @Test
        fun `English name is matched case-insensitively`() {
            assertEquals(2L, reversedModelId(1L to "Basic", 2L to "basic (and reversed card)"))
        }

        @Test
        fun `English name wins over earlier localized-looking variants`() {
            // "optional reversed" also contains "reversed" but must not shadow the real model
            assertEquals(
                2L,
                reversedModelId(1L to "Basic (optional reversed card)", 2L to "BASIC (AND REVERSED CARD)")
            )
        }

        // --- Localized names ---

        @Test
        fun `German name is found`() {
            assertEquals(2L, reversedModelId(1L to "Einfach", 2L to "Einfach (beide Richtungen)"))
        }

        @Test
        fun `localized name is matched case-insensitively`() {
            assertEquals(2L, reversedModelId(1L to "Basique", 2L to "BASIQUE (ET CARTE INVERSÉE)"))
        }

        @Test
        fun `Dutch omgekeerd name is found`() {
            assertEquals(2L, reversedModelId(1L to "Basis", 2L to "Basis (en omgekeerde kaart)"))
        }

        // --- Real stock names from Anki's translations (anki-core-i18n core/<lang>/notetypes.ftl) ---

        @Test
        fun `real Spanish Italian and Portuguese names are found`() {
            assertEquals(2L, reversedModelId(1L to "Básico", 2L to "Básico (y tarjeta invertida)"))
            assertEquals(2L, reversedModelId(1L to "Basilare", 2L to "Basilare (e carta inversa)"))
            assertEquals(2L, reversedModelId(1L to "Básico", 2L to "Básico (e cartão invertido)"))
        }

        @Test
        fun `real names without latin keywords are found`() {
            assertEquals(2L, reversedModelId(1L to "Enkel", 2L to "Enkel (och omvänt kort)"))
            assertEquals(2L, reversedModelId(1L to "Простая", 2L to "Простая (с обратной карточкой)"))
            assertEquals(2L, reversedModelId(1L to "基本", 2L to "基本 (表面と裏面を反転したカードも作成)"))
            assertEquals(2L, reversedModelId(1L to "问答题", 2L to "问答题（附翻转卡片）"))
        }

        @Test
        fun `French reversed model is found and the optional reversed model is not taken for it`() {
            assertEquals(
                3L,
                reversedModelId(
                    1L to "Basique",
                    2L to "Basique (carte inversée optionnelle)",
                    3L to "Généralités (deux sens)"
                )
            )
            assertNull(reversedModelId(1L to "Basique", 2L to "Basique (carte inversée optionnelle)"))
        }

        @Test
        fun `German and Dutch optional reversed models listed first are not picked`() {
            assertEquals(
                2L,
                reversedModelId(
                    1L to "Einfach (und wahlweise die umgekehrte Richtung)",
                    2L to "Einfach (und die umgekehrte Richtung)"
                )
            )
            assertEquals(
                2L,
                reversedModelId(1L to "Basis (omgekeerde kaart optioneel)", 2L to "Basis (en omgekeerde kaart)")
            )
        }

        @Test
        fun `user model containing reversed listed first is not picked`() {
            assertEquals(
                2L,
                reversedModelId(1L to "Reversed Engineering", 2L to "Einfach (und die umgekehrte Richtung)")
            )
            assertNull(reversedModelId(1L to "Basic", 2L to "Reversed Engineering"))
        }

        // --- No match ---

        @Test
        fun `empty model list yields no match`() {
            assertNull(reversedModelId())
        }

        @Test
        fun `only non-reversed built-in models yield no match`() {
            assertNull(reversedModelId(1L to "Basic", 2L to "Basic (type in the answer)", 3L to "Cloze"))
        }

        @Test
        fun `unrelated name containing reverse is not matched`() {
            assertNull(reversedModelId(1L to "Basic", 2L to "My Reverse Engineering Notes"))
        }
    }

    private companion object {
        const val BASIC_MODEL = "Basic"
        const val BASIC_MODEL_ID = 1L
    }
}
