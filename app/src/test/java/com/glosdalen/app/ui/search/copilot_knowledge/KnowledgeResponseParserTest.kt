package com.glosdalen.app.ui.search.copilot_knowledge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contract of [parseKnowledgeResponse]: the shared Copilot screen renders content only
 * from a non-null parsed response, so every LLM reply — well-formed or not — must
 * produce one the user can see.
 */
class KnowledgeResponseParserTest {

    @Test
    fun `parses a well-formed reply into answer and cards`() {
        val parsed = parseKnowledgeResponse(
            """{"answer":"Paris","flashcards":[{"front":"Capital of France?","back":"Paris"}],"explanation":"x","suggestedDeck":"Geo"}"""
        )

        assertEquals("Paris", parsed.directAnswer)
        assertEquals(listOf(KnowledgeFlashCard("Capital of France?", "Paris")), parsed.cards)
        assertEquals("Geo", parsed.suggestedDeck)
    }

    @Test
    fun `surfaces the raw reply when it is not JSON`() {
        val raw = "Sorry, I can't help with that."

        val parsed = parseKnowledgeResponse(raw)

        assertTrue(parsed.directAnswer.contains(raw), "answer should contain the raw reply: ${parsed.directAnswer}")
        assertTrue(parsed.cards.isEmpty())
    }

    @Test
    fun `surfaces the raw reply when a required field is missing`() {
        val raw = """{"flashcards":[{"front":"Q","back":"A"}]}"""

        val parsed = parseKnowledgeResponse(raw)

        assertTrue(parsed.directAnswer.contains(raw), "answer should contain the raw reply: ${parsed.directAnswer}")
        assertTrue(parsed.cards.isEmpty())
    }
}
