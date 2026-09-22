package com.glosdalen.app.libs.copilot.util

/**
 * Extracts the JSON object out of a raw LLM response.
 *
 * LLMs frequently wrap the requested JSON in prose ("Sure, here you go:"), in a
 * markdown code fence, or append a closing remark after the object. This helper
 * recovers the object in all of those shapes.
 *
 * The scan is string-aware: braces and quotes that appear *inside* JSON string
 * values are ignored, so a response such as `{"note": "use { and } here"}` is
 * extracted intact instead of being cut at the first unbalanced brace.
 *
 * Strategy, in order:
 *  1. The response itself starts with `{` -> take its balanced object.
 *  2. A markdown code fence contains a balanced object -> take that (preferred
 *     over stray braces in the surrounding prose).
 *  3. The first balanced object anywhere in the response.
 *  4. Nothing balanced -> return the trimmed input and let the JSON parser
 *     produce the error.
 */
internal object LlmJsonExtractor {

    private val CODE_FENCE_REGEX = "```[a-zA-Z]*[ \\t]*\\r?\\n?([\\s\\S]*?)```".toRegex()

    fun extractJsonObject(response: String): String {
        val trimmed = response.trim()
        if (trimmed.isEmpty()) return trimmed

        if (trimmed.startsWith("{")) {
            balancedObjectAt(trimmed, 0)?.let { return it }
        }

        for (fence in CODE_FENCE_REGEX.findAll(trimmed)) {
            firstBalancedObject(fence.groupValues[1])?.let { return it }
        }

        firstBalancedObject(trimmed)?.let { return it }

        return trimmed
    }

    /** First balanced `{...}` object in [text], or null if none balances. */
    private fun firstBalancedObject(text: String): String? {
        var index = text.indexOf('{')
        while (index != -1) {
            balancedObjectAt(text, index)?.let { return it }
            index = text.indexOf('{', index + 1)
        }
        return null
    }

    /**
     * Balanced object starting exactly at [start], or null when [start] is not
     * an opening brace or the object never closes.
     */
    private fun balancedObjectAt(text: String, start: Int): String? {
        if (start !in text.indices || text[start] != '{') return null

        var depth = 0
        var inString = false
        var escaped = false

        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
