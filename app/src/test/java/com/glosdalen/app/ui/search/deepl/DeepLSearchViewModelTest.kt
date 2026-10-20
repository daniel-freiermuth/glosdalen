package com.glosdalen.app.ui.search.deepl

import com.glosdalen.app.backend.deepl.Language
import com.glosdalen.app.backend.deepl.Translation
import com.glosdalen.app.backend.deepl.VocabularyEntry
import com.glosdalen.app.domain.preferences.FrontPreference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream

/**
 * Contract of the DeepL card pipeline used by [DeepLSearchViewModel.createAnkiCard]:
 * pick the lookup target language, split the result into native/foreign words for forward
 * and reverse searches, and lay those words (plus the foreign-word TTS audio) onto the
 * Anki note according to the card direction and front preference.
 */
class DeepLSearchViewModelTest {

    private val native = Language.GERMAN
    private val foreign = Language.SWEDISH

    @Nested
    @DisplayName("lookup target language")
    inner class TargetLanguage {

        @Test
        fun `searching from the native language translates into the foreign language`() {
            assertEquals(foreign, deepLTargetLanguage(native, native, foreign))
        }

        @Test
        fun `searching from the foreign language translates into the native language`() {
            assertEquals(native, deepLTargetLanguage(foreign, native, foreign))
        }

        @Test
        fun `an unconfigured source language falls back to the foreign language`() {
            assertEquals(foreign, deepLTargetLanguage(Language.FRENCH, native, foreign))
        }
    }

    @Nested
    @DisplayName("native/foreign word assignment")
    inner class WordAssignment {

        private fun entry(source: Language, vararg translations: String) = VocabularyEntry(
            originalWord = "original",
            sourceLanguage = source,
            targetLanguage = if (source == native) foreign else native,
            translations = translations.map { Translation(it) }
        )

        @Test
        fun `forward search keeps the original word on the native side`() {
            assertEquals(
                "original" to "first",
                nativeAndForeignWords(entry(native, "first", "second"), null, native)
            )
        }

        @Test
        fun `reverse search puts the original word on the foreign side`() {
            assertEquals(
                "first" to "original",
                nativeAndForeignWords(entry(foreign, "first", "second"), null, native)
            )
        }

        @Test
        fun `selected translation wins over the first translation`() {
            assertEquals(
                "original" to "second",
                nativeAndForeignWords(entry(native, "first", "second"), "second", native)
            )
        }

        @Test
        fun `no translations and no selection yields a blank translation`() {
            assertEquals("original" to "", nativeAndForeignWords(entry(native), null, native))
            assertEquals("" to "original", nativeAndForeignWords(entry(foreign), null, native))
        }
    }

    @Nested
    @DisplayName("Anki note layout")
    inner class CardLayout {

        private val nativeWord = "Katze"
        private val foreignWord = "katt"
        private val audio = File("katt.mp3")

        private fun build(
            direction: DeepLCardDirection,
            frontPreference: FrontPreference,
            audioFile: File? = audio
        ) = buildDeepLAnkiCard(
            nativeWord = nativeWord,
            foreignWord = foreignWord,
            nativeLanguage = native,
            foreignLanguage = foreign,
            cardDirection = direction,
            frontPreference = frontPreference,
            deckName = "Deck::Svenska",
            foreignAudioFile = audioFile
        )

        @ParameterizedTest(name = "{0} with {1} front")
        @MethodSource("com.glosdalen.app.ui.search.deepl.DeepLSearchViewModelTest#layouts")
        fun `direction and front preference decide note type, sides, audio field and tags`(
            direction: DeepLCardDirection,
            frontPreference: FrontPreference,
            expectedModel: String,
            foreignOnFront: Boolean,
            directionTag: String?
        ) {
            val card = build(direction, frontPreference)

            val (front, back) = if (foreignOnFront) foreignWord to nativeWord else nativeWord to foreignWord
            val audioField = if (foreignOnFront) "Front" else "Back"
            assertEquals(expectedModel, card.modelName)
            assertEquals(mapOf("Front" to front, "Back" to back), card.fields)
            assertEquals(mapOf(audioField to audio), card.audioFiles)
            assertEquals(
                listOfNotNull("glosdalen", "vocab", native.code, foreign.code, directionTag),
                card.tags
            )
            assertEquals("Deck::Svenska", card.deckName)
        }

        @Test
        fun `no audio file leaves the note without audio`() {
            for (direction in DeepLCardDirection.entries) {
                for (pref in FrontPreference.entries) {
                    assertTrue(build(direction, pref, audioFile = null).audioFiles.isEmpty(), "$direction / $pref")
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun layouts(): Stream<Arguments> = Stream.of(
            // Fixed directions ignore the front preference.
            Arguments.of(DeepLCardDirection.NATIVE_TO_FOREIGN, FrontPreference.NATIVE, "Basic", false, "native-to-foreign"),
            Arguments.of(DeepLCardDirection.NATIVE_TO_FOREIGN, FrontPreference.FOREIGN, "Basic", false, "native-to-foreign"),
            Arguments.of(DeepLCardDirection.FOREIGN_TO_NATIVE, FrontPreference.NATIVE, "Basic", true, "foreign-to-native"),
            Arguments.of(DeepLCardDirection.FOREIGN_TO_NATIVE, FrontPreference.FOREIGN, "Basic", true, "foreign-to-native"),
            // Intent and bidirectional cards follow the front preference.
            Arguments.of(DeepLCardDirection.VIA_INTENT, FrontPreference.NATIVE, "Basic", false, null),
            Arguments.of(DeepLCardDirection.VIA_INTENT, FrontPreference.FOREIGN, "Basic", true, null),
            Arguments.of(DeepLCardDirection.BOTH_DIRECTIONS, FrontPreference.NATIVE, "Basic (and reversed card)", false, "bidirectional"),
            Arguments.of(DeepLCardDirection.BOTH_DIRECTIONS, FrontPreference.FOREIGN, "Basic (and reversed card)", true, "bidirectional"),
        )
    }
}
