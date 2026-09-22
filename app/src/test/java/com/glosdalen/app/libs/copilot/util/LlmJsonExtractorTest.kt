package com.glosdalen.app.libs.copilot.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Contract of [LlmJsonExtractor.extractJsonObject]: given a raw LLM response,
 * return the JSON object embedded in it, or the trimmed input when no balanced
 * object exists (so the JSON parser downstream reports the error).
 */
class LlmJsonExtractorTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Parses the extraction result, proving it is usable JSON, not just a substring. */
    private fun extractAndParse(response: String): JsonObject =
        json.parseToJsonElement(LlmJsonExtractor.extractJsonObject(response)) as JsonObject

    @Nested
    @DisplayName("pure JSON responses")
    inner class PureJson {

        @Test
        fun `returns the whole object when the response is only JSON`() {
            val response = """{"answer":"hej","flashcards":[]}"""
            assertEquals(response, LlmJsonExtractor.extractJsonObject(response))
        }

        @Test
        fun `trims surrounding whitespace`() {
            assertEquals(
                """{"answer":"hej"}""",
                LlmJsonExtractor.extractJsonObject("\n\n  {\"answer\":\"hej\"}  \n")
            )
        }

        @Test
        fun `keeps nested objects intact`() {
            val response = """{"a":{"b":{"c":1}},"d":2}"""
            assertEquals(response, LlmJsonExtractor.extractJsonObject(response))
        }
    }

    @Nested
    @DisplayName("braces inside string values")
    inner class BracesInStrings {

        @Test
        fun `does not stop at a closing brace inside a string value`() {
            val response = """{"note":"use } to close","answer":"hej"}"""
            assertEquals(response, LlmJsonExtractor.extractJsonObject(response))
            assertEquals("hej", extractAndParse(response)["answer"]!!.jsonPrimitive.content)
        }

        @Test
        fun `handles an opening brace inside a string value`() {
            val response = """{"note":"an { opener","answer":"hej"}"""
            assertEquals(response, LlmJsonExtractor.extractJsonObject(response))
            assertEquals("hej", extractAndParse(response)["answer"]!!.jsonPrimitive.content)
        }

        @Test
        fun `handles both brace kinds inside a string value`() {
            val response = """{"answer":"text with { and } inside"}"""
            assertEquals(
                "text with { and } inside",
                extractAndParse(response)["answer"]!!.jsonPrimitive.content
            )
        }

        @Test
        fun `treats an escaped quote as part of the string, not its terminator`() {
            val response = """{"answer":"she said \"} done\"","explanation":"x"}"""
            assertEquals(response, LlmJsonExtractor.extractJsonObject(response))
            assertEquals("x", extractAndParse(response)["explanation"]!!.jsonPrimitive.content)
        }

        @Test
        fun `treats an escaped backslash before a quote as a literal backslash`() {
            val response = """{"answer":"path\\","explanation":"}"}"""
            assertEquals(response, LlmJsonExtractor.extractJsonObject(response))
            assertEquals("}", extractAndParse(response)["explanation"]!!.jsonPrimitive.content)
        }
    }

    @Nested
    @DisplayName("surrounding prose")
    inner class SurroundingProse {

        @Test
        fun `drops trailing text after the closing brace`() {
            val response = """{"answer":"hej"}

Hope that helps! Let me know if you need more."""
            assertEquals("""{"answer":"hej"}""", LlmJsonExtractor.extractJsonObject(response))
        }

        @Test
        fun `drops leading prose before the object`() {
            val response = """Sure, here is the JSON you asked for:
{"answer":"hej"}"""
            assertEquals("""{"answer":"hej"}""", LlmJsonExtractor.extractJsonObject(response))
        }

        @Test
        fun `skips unbalanced braces in prose and returns the real object`() {
            val response = """Use the { character carefully.
{"answer":"hej"}"""
            assertEquals("""{"answer":"hej"}""", LlmJsonExtractor.extractJsonObject(response))
        }
    }

    @Nested
    @DisplayName("markdown code fences")
    inner class CodeFences {

        @Test
        fun `extracts from a json-tagged fence`() {
            val response = "Here you go:\n```json\n{\"answer\":\"hej\"}\n```\nDone."
            assertEquals("""{"answer":"hej"}""", LlmJsonExtractor.extractJsonObject(response))
        }

        @Test
        fun `extracts from an untagged fence`() {
            val response = "```\n{\"answer\":\"hej\"}\n```"
            assertEquals("""{"answer":"hej"}""", LlmJsonExtractor.extractJsonObject(response))
        }

        @Test
        fun `prefers the fenced object over stray braces in the prose around it`() {
            val response = "A set is written {a, b}.\n```json\n{\"answer\":\"hej\"}\n```"
            assertEquals("""{"answer":"hej"}""", LlmJsonExtractor.extractJsonObject(response))
        }

        @Test
        fun `keeps fenced JSON whose string values contain braces`() {
            val response = "```json\n{\"answer\":\"use {curly} braces\"}\n```"
            assertEquals(
                "use {curly} braces",
                extractAndParse(response)["answer"]!!.jsonPrimitive.content
            )
        }
    }

    @Nested
    @DisplayName("multiple objects")
    inner class MultipleObjects {

        @Test
        fun `returns the first object instead of spanning both`() {
            val response = """{"answer":"first"}
{"answer":"second"}"""
            assertEquals("""{"answer":"first"}""", LlmJsonExtractor.extractJsonObject(response))
        }

        @Test
        fun `does not swallow trailing garbage between two objects`() {
            val response = """{"answer":"first"} ... and also {"answer":"second"} plus noise"""
            assertEquals("""{"answer":"first"}""", LlmJsonExtractor.extractJsonObject(response))
        }
    }

    @Nested
    @DisplayName("no extractable object")
    inner class NoJson {

        @Test
        fun `empty input yields empty output`() {
            assertEquals("", LlmJsonExtractor.extractJsonObject(""))
        }

        @Test
        fun `whitespace-only input yields empty output`() {
            assertEquals("", LlmJsonExtractor.extractJsonObject("   \n\t  "))
        }

        @Test
        fun `plain prose is returned trimmed for the parser to reject`() {
            assertEquals(
                "I could not answer that.",
                LlmJsonExtractor.extractJsonObject("  I could not answer that.  ")
            )
        }

        @Test
        fun `unclosed object is returned as-is for the parser to reject`() {
            val response = """{"answer":"hej","flashcards":["""
            assertEquals(response, LlmJsonExtractor.extractJsonObject(response))
        }

        @Test
        fun `object closed only inside a string is not treated as balanced`() {
            val response = """{"answer":"} not the end"""
            assertEquals(response, LlmJsonExtractor.extractJsonObject(response))
        }

        @Test
        fun `stray closing brace without an opener is returned as-is`() {
            assertEquals("no json here }", LlmJsonExtractor.extractJsonObject("no json here }"))
        }
    }
}
