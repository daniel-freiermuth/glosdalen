package com.glosdalen.app.ui.search.copilot_knowledge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Contract of [parseKnowledgeResponse]: map the LLM's JSON answer (see the schema in
 * `CopilotKnowledgeViewModel.buildKnowledgePrompt`) to [ParsedKnowledgeResponse], and on any
 * parse failure show the error plus the raw response. The shared Copilot screen renders only
 * `parsedResponse`, so a null result would leave the screen blank.
 */
class CopilotKnowledgeViewModelTest {

    private fun parse(response: String): ParsedKnowledgeResponse =
        checkNotNull(parseKnowledgeResponse(response)) { "parser returned null for: $response" }

    @Test
    fun `maps every field of a valid response`() {
        val parsed = parse(
            """
            {
              "answer": "Paris",
              "flashcards": [{"front": "Capital of France?", "back": "Paris", "note": "geo"}],
              "explanation": "Since 508 AD",
              "suggestedDeck": "Geography"
            }
            """.trimIndent()
        )

        assertEquals("Paris", parsed.directAnswer)
        assertEquals(listOf(KnowledgeFlashCard("Capital of France?", "Paris", "geo")), parsed.cards)
        assertEquals("Since 508 AD", parsed.additionalInfo)
        assertEquals("Geography", parsed.suggestedDeck)
    }

    @Test
    fun `explicit null optional fields fall back to their defaults`() {
        val parsed = parseKnowledgeResponse(
            """{"answer": "Paris", "flashcards": [{"front": "Capital of France?", "back": "Paris",
                "note": null}], "explanation": null, "suggestedDeck": null}"""
        )

        assertEquals(
            ParsedKnowledgeResponse(
                directAnswer = "Paris",
                cards = listOf(KnowledgeFlashCard(frontSide = "Capital of France?", backSide = "Paris", note = "")),
                additionalInfo = "",
                suggestedDeck = "",
            ),
            parsed,
        )
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
            assertEquals(emptyList<KnowledgeFlashCard>(), parsed.cards)
            assertEquals("", parsed.additionalInfo)
            assertEquals("", parsed.suggestedDeck)
        }

        @Test
        fun `plain text with no JSON`() = assertFallback("Sorry, I cannot help with that.")

        @Test
        fun `truncated JSON`() = assertFallback("""{"answer": "cut off", "flashcards": [{"front": "a"""")

        @Test
        fun `valid JSON missing the required answer field`() =
            assertFallback("""{"flashcards": [], "suggestedDeck": "Geo"}""")

        @Test
        fun `explicit null for the required answer field`() = assertFallback("""{"answer": null}""")

        @Test
        fun `flashcard missing required back field`() =
            assertFallback("""{"answer": "x", "flashcards": [{"front": "a"}]}""")
    }
}
