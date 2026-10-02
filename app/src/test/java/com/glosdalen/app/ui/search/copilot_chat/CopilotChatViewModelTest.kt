package com.glosdalen.app.ui.search.copilot_chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Contract of [parseCopilotResponse]: map the LLM's JSON answer (see the schema in
 * `CopilotChatViewModel.buildPrompt`) to [ParsedCopilotResponse], turning blank language
 * codes into null, and on any parse failure show the error plus the raw response.
 */
class CopilotChatViewModelTest {

    private fun parse(response: String): ParsedCopilotResponse =
        checkNotNull(parseCopilotResponse(response)) { "parser returned null for: $response" }

    @Nested
    @DisplayName("valid JSON")
    inner class ValidJson {

        @Test
        fun `maps every field of a fully populated response`() {
            val parsed = parse(
                """
                {
                  "answer": "Hund means dog",
                  "answer_language": "en",
                  "flashcards": [
                    {"front": "der Hund", "front_language": "de", "back": "the dog", "back_language": "en", "note": "masculine"},
                    {"front": "die Katze", "front_language": "de", "back": "the cat", "back_language": "en", "note": ""}
                  ],
                  "explanation": "Plural: Hunde"
                }
                """.trimIndent()
            )

            assertEquals(
                ParsedCopilotResponse(
                    directAnswer = "Hund means dog",
                    directAnswerLanguageCode = "en",
                    cards = listOf(
                        FlashCard("der Hund", "de", "the dog", "en", "masculine"),
                        FlashCard("die Katze", "de", "the cat", "en", ""),
                    ),
                    additionalInfo = "Plural: Hunde",
                ),
                parsed,
            )
        }

        @Test
        fun `empty flashcards array yields no cards`() {
            val parsed = parse("""{"answer": "No cards needed", "answer_language": "en", "flashcards": [], "explanation": ""}""")

            assertEquals("No cards needed", parsed.directAnswer)
            assertEquals(emptyList<FlashCard>(), parsed.cards)
        }

        @Test
        fun `only the required answer field is needed and optional fields default`() {
            val parsed = parse("""{"answer": "Just an answer"}""")

            assertEquals(
                ParsedCopilotResponse(
                    directAnswer = "Just an answer",
                    directAnswerLanguageCode = null,
                    cards = emptyList(),
                    additionalInfo = "",
                ),
                parsed,
            )
        }

        @Test
        fun `unknown keys are ignored`() {
            val parsed = parse(
                """
                {
                  "answer": "ok",
                  "confidence": 0.9,
                  "flashcards": [{"front": "a", "back": "b", "difficulty": "easy"}],
                  "meta": {"model": "x"}
                }
                """.trimIndent()
            )

            assertEquals("ok", parsed.directAnswer)
            assertEquals(listOf(FlashCard(frontSide = "a", backSide = "b")), parsed.cards)
        }

        @Test
        fun `JSON wrapped in prose and code fences is still parsed`() {
            val parsed = parse("Here you go:\n```json\n{\"answer\": \"wrapped\", \"answer_language\": \"sv\"}\n```\nHope it helps!")

            assertEquals("wrapped", parsed.directAnswer)
            assertEquals("sv", parsed.directAnswerLanguageCode)
        }
    }

    @Nested
    @DisplayName("blank language codes")
    inner class BlankLanguageCodes {

        @Test
        fun `empty answer_language maps to null`() {
            assertNull(parse("""{"answer": "x", "answer_language": ""}""").directAnswerLanguageCode)
        }

        @Test
        fun `whitespace-only answer_language maps to null`() {
            assertNull(parse("""{"answer": "x", "answer_language": "   "}""").directAnswerLanguageCode)
        }

        @Test
        fun `blank card language codes map to null independently per side`() {
            val parsed = parse(
                """
                {"answer": "x", "flashcards": [
                  {"front": "f1", "front_language": "", "back": "b1", "back_language": "en"},
                  {"front": "f2", "front_language": "de", "back": "b2", "back_language": " "}
                ]}
                """.trimIndent()
            )

            assertEquals(
                listOf(
                    FlashCard(frontSide = "f1", frontLanguageCode = null, backSide = "b1", backLanguageCode = "en"),
                    FlashCard(frontSide = "f2", frontLanguageCode = "de", backSide = "b2", backLanguageCode = null),
                ),
                parsed.cards,
            )
        }
    }

    @Nested
    @DisplayName("parse failures fall back to an error answer")
    inner class Fallback {

        private fun assertFallback(raw: String) {
            val parsed = parse(raw)

            assertTrue(
                parsed.directAnswer.startsWith("Error parsing response: "),
                "expected error prefix, got: ${parsed.directAnswer}",
            )
            assertTrue(
                parsed.directAnswer.endsWith("\n\nRaw response:\n$raw"),
                "expected raw response appended verbatim, got: ${parsed.directAnswer}",
            )
            assertNull(parsed.directAnswerLanguageCode)
            assertEquals(emptyList<FlashCard>(), parsed.cards)
            assertEquals("", parsed.additionalInfo)
        }

        @Test
        fun `plain text with no JSON`() = assertFallback("Sorry, I cannot help with that.")

        @Test
        fun `truncated JSON`() = assertFallback("""{"answer": "cut off", "flashcards": [{"front": "a"""")

        @Test
        fun `valid JSON missing the required answer field`() =
            assertFallback("""{"answer_language": "en", "flashcards": []}""")

        @Test
        fun `flashcard missing required back field`() =
            assertFallback("""{"answer": "x", "flashcards": [{"front": "a"}]}""")

        @Test
        fun `empty response`() = assertFallback("")
    }
}
