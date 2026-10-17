package com.glosdalen.app.libs.copilot.chat

import com.glosdalen.app.libs.copilot.CopilotException
import com.glosdalen.app.libs.copilot.asFailure
import com.glosdalen.app.libs.copilot.asSuccess
import com.glosdalen.app.libs.copilot.chat.CopilotChatManager.Companion.executeWithRetry
import com.glosdalen.app.libs.copilot.chat.CopilotChatManager.Companion.mapChatCompletionResponse
import com.glosdalen.app.libs.copilot.chat.CopilotChatManager.Companion.mapChatRequestException
import com.glosdalen.app.libs.copilot.models.ChatChoice
import com.glosdalen.app.libs.copilot.models.ChatResponse
import com.glosdalen.app.libs.copilot.models.Message
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Contract of the chat request pipeline in [CopilotChatManager]: HTTP responses and thrown
 * exceptions are classified into [CopilotException] subtypes, and [executeWithRetry] decides
 * from that classification how many attempts to make and how long to back off between them.
 *
 * Retry tests run on virtual time, so `currentTime` is the total backoff the caller waited.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CopilotChatManagerTest {

    /** Outcome of one [executeWithRetry] call plus the virtual time at which each attempt started. */
    private class RetryRun<T>(val result: Result<T>, val attemptTimes: List<Long>) {
        val gaps: List<Long> get() = attemptTimes.zipWithNext { a, b -> b - a }
    }

    private suspend fun <T> TestScope.retry(
        maxRetries: Int = 3,
        maxDelayMs: Long = 16000,
        jitterFactor: Double = 0.0,
        outcome: (attempt: Int) -> Result<T>
    ): RetryRun<T> {
        val attemptTimes = mutableListOf<Long>()
        val result = executeWithRetry(
            maxRetries = maxRetries,
            maxDelayMs = maxDelayMs,
            jitterFactor = jitterFactor
        ) {
            attemptTimes += currentTime
            outcome(attemptTimes.size - 1)
        }
        return RetryRun(result, attemptTimes)
    }

    private fun chatResponse(vararg contents: String) = ChatResponse(
        choices = contents.map { ChatChoice(message = Message(role = "assistant", content = it)) },
        id = "chatcmpl-1",
        model = "gpt-4o"
    )

    private fun errorResponse(code: Int, body: String = "") =
        Response.error<ChatResponse>(code, body.toResponseBody("application/json".toMediaType()))

    @Nested
    @DisplayName("attempt count")
    inner class AttemptCount {

        @Test
        fun `success on the first attempt returns without waiting`() = runTest {
            val run = retry { "ok".asSuccess() }

            assertEquals("ok", run.result.getOrThrow())
            assertEquals(listOf(0L), run.attemptTimes)
        }

        @Test
        fun `success after transient failures returns that success`() = runTest {
            val run = retry { attempt ->
                if (attempt < 2) CopilotException.NetworkException.ServerError(502).asFailure()
                else "ok".asSuccess()
            }

            assertEquals("ok", run.result.getOrThrow())
            assertEquals(3, run.attemptTimes.size)
        }

        @Test
        fun `persistent network failures make maxRetries plus one attempts and return the last error`() = runTest {
            val transient = listOf<() -> CopilotException>(
                { CopilotException.NetworkException.ServerError(503) },
                { CopilotException.NetworkException.NoConnection() },
                { CopilotException.NetworkException.Timeout() }
            )
            for (make in transient) {
                val last = make()
                val run = retry<String> { attempt -> if (attempt == 3) last.asFailure() else make().asFailure() }

                assertEquals(4, run.attemptTimes.size, "attempts for ${last::class.simpleName}")
                assertSame(last, run.result.exceptionOrNull())
            }
        }

        @Test
        fun `maxRetries zero makes exactly one attempt`() = runTest {
            val run = retry<String>(maxRetries = 0) { CopilotException.NetworkException.ServerError(500).asFailure() }

            assertEquals(listOf(0L), run.attemptTimes)
        }

        @Test
        fun `non-retryable errors return after a single attempt without waiting`() = runTest {
            val permanent = listOf<() -> CopilotException>(
                { CopilotException.ModelException.ModelNotFound("gpt-x") },
                { CopilotException.AuthException.TokenExpired() },
                { CopilotException.AuthException.InvalidToken() },
                { CopilotException.ChatException.EmptyMessage() },
                { CopilotException.ChatException.InvalidMessage("bad role") }
            )
            for (make in permanent) {
                val error = make()
                val run = retry<String> { error.asFailure() }

                assertEquals(listOf(0L), run.attemptTimes, "attempts for ${error::class.simpleName}")
                assertSame(error, run.result.exceptionOrNull())
            }
        }

        @Test
        fun `rate limiting is retried at most twice`() = runTest {
            val run = retry<String> { CopilotException.NetworkException.RateLimited().asFailure() }

            assertEquals(3, run.attemptTimes.size)
            assertInstanceOf(CopilotException.NetworkException.RateLimited::class.java, run.result.exceptionOrNull())
        }

        @Test
        fun `unclassified Copilot errors are retried once`() = runTest {
            val run = retry<String> { CopilotException.ChatException.ResponseParsingFailed().asFailure() }

            assertEquals(2, run.attemptTimes.size)
            assertInstanceOf(CopilotException.ChatException.ResponseParsingFailed::class.java, run.result.exceptionOrNull())
        }
    }

    @Nested
    @DisplayName("backoff")
    inner class Backoff {

        @Test
        fun `delay doubles between attempts and is not applied after the last one`() = runTest {
            val run = retry<String> { CopilotException.NetworkException.ServerError(500).asFailure() }

            assertEquals(listOf(1000L, 2000L, 4000L), run.gaps)
            assertEquals(7000L, currentTime)
        }

        @Test
        fun `delay is capped at maxDelayMs`() = runTest {
            val run = retry<String>(maxRetries = 5, maxDelayMs = 3000) {
                CopilotException.NetworkException.ServerError(500).asFailure()
            }

            assertEquals(listOf(1000L, 2000L, 3000L, 3000L, 3000L), run.gaps)
        }

        @Test
        fun `jitter keeps each delay within jitterFactor of the nominal backoff`() = runTest {
            repeat(20) {
                val run = retry<String>(jitterFactor = 0.1) { CopilotException.NetworkException.ServerError(500).asFailure() }

                listOf(1000L, 2000L, 4000L).zip(run.gaps).forEach { (nominal, gap) ->
                    assertTrue(gap in (nominal * 0.9).toLong()..(nominal * 1.1).toLong(), "gap $gap for nominal $nominal")
                }
            }
        }
    }

    @Nested
    @DisplayName("per-attempt timeout")
    inner class PerAttemptTimeout {

        @Test
        fun `an attempt that never completes is abandoned after 30 seconds and retried`() = runTest {
            var calls = 0
            val result = executeWithRetry(jitterFactor = 0.0) {
                calls++
                if (calls == 1) awaitCancellation()
                "ok".asSuccess()
            }

            assertEquals("ok", result.getOrThrow())
            assertEquals(2, calls)
            assertEquals(30_000L + 1000L, currentTime)
        }

        @Test
        fun `timing out on every attempt returns Timeout after the last one`() = runTest {
            var calls = 0
            val result = executeWithRetry<String>(jitterFactor = 0.0) {
                calls++
                awaitCancellation()
            }

            assertEquals(4, calls)
            assertInstanceOf(CopilotException.NetworkException.Timeout::class.java, result.exceptionOrNull())
            assertEquals(4 * 30_000L + 1000L + 2000L + 4000L, currentTime)
        }
    }

    @Nested
    @DisplayName("HTTP response mapping")
    inner class ResponseMapping {

        @Test
        fun `successful response with choices is returned as is`() {
            val body = chatResponse("hej")
            assertSame(body, mapChatCompletionResponse(Response.success(body), "gpt-4o").getOrThrow())
        }

        @Test
        fun `successful response without choices is a parsing failure`() {
            val result = mapChatCompletionResponse(Response.success(chatResponse()), "gpt-4o")
            assertInstanceOf(CopilotException.ChatException.ResponseParsingFailed::class.java, result.exceptionOrNull())
        }

        @Test
        fun `401 and 403 map to Unauthorized and Forbidden`() {
            assertInstanceOf(
                CopilotException.NetworkException.Unauthorized::class.java,
                mapChatCompletionResponse(errorResponse(401), "gpt-4o").exceptionOrNull()
            )
            assertInstanceOf(
                CopilotException.NetworkException.Forbidden::class.java,
                mapChatCompletionResponse(errorResponse(403), "gpt-4o").exceptionOrNull()
            )
        }

        @Test
        fun `404 maps to ModelNotFound for the requested model`() {
            val error = mapChatCompletionResponse(errorResponse(404), "claude-x").exceptionOrNull()
            assertInstanceOf(CopilotException.ModelException.ModelNotFound::class.java, error)
            assertEquals("claude-x", (error as CopilotException.ModelException.ModelNotFound).modelId)
        }

        @Test
        fun `429 carries the Retry-After header in seconds`() {
            val raw = okhttp3.Response.Builder()
                .request(Request.Builder().url("https://api.githubcopilot.com/chat/completions").build())
                .protocol(Protocol.HTTP_1_1)
                .code(429)
                .message("Too Many Requests")
                .header("Retry-After", "7")
                .build()
            val response = Response.error<ChatResponse>("".toResponseBody(null), raw)

            val error = mapChatCompletionResponse(response, "gpt-4o").exceptionOrNull()
            assertEquals(7L, (error as CopilotException.NetworkException.RateLimited).retryAfter)
        }

        @Test
        fun `429 without a numeric Retry-After has no retry hint`() {
            val error = mapChatCompletionResponse(errorResponse(429), "gpt-4o").exceptionOrNull()
            assertNull((error as CopilotException.NetworkException.RateLimited).retryAfter)
        }

        @Test
        fun `5xx maps to ServerError with status and body`() {
            for (code in listOf(500, 503, 599)) {
                val error = mapChatCompletionResponse(errorResponse(code, "down"), "gpt-4o").exceptionOrNull()
                val serverError = error as CopilotException.NetworkException.ServerError
                assertEquals(code, serverError.code)
                assertEquals("down", serverError.body)
            }
        }

        @Test
        fun `other client errors map to BadRequest with the error body`() {
            val error = mapChatCompletionResponse(errorResponse(400, """{"error":"bad"}"""), "gpt-4o").exceptionOrNull()
            assertEquals("""{"error":"bad"}""", (error as CopilotException.NetworkException.BadRequest).details)
        }

        @Test
        fun `mapped status classes drive the retry policy`() = runTest {
            var serverErrorCalls = 0
            executeWithRetry(jitterFactor = 0.0) {
                serverErrorCalls++
                mapChatCompletionResponse(errorResponse(503), "gpt-4o")
            }
            var notFoundCalls = 0
            executeWithRetry(jitterFactor = 0.0) {
                notFoundCalls++
                mapChatCompletionResponse(errorResponse(404), "gpt-4o")
            }

            assertEquals(4, serverErrorCalls)
            assertEquals(1, notFoundCalls)
        }
    }

    @Nested
    @DisplayName("exception mapping")
    inner class ExceptionMapping {

        @Test
        fun `socket timeout maps to Timeout`() {
            assertInstanceOf(
                CopilotException.NetworkException.Timeout::class.java,
                mapChatRequestException(SocketTimeoutException()).exceptionOrNull()
            )
        }

        @Test
        fun `unknown host maps to NoConnection`() {
            assertInstanceOf(
                CopilotException.NetworkException.NoConnection::class.java,
                mapChatRequestException(UnknownHostException("api.githubcopilot.com")).exceptionOrNull()
            )
        }

        @Test
        fun `Copilot exceptions pass through unchanged`() {
            val error = CopilotException.AuthException.TokenExpired()
            assertSame(error, mapChatRequestException(error).exceptionOrNull())
        }

        @Test
        fun `any other exception is a parsing failure keeping the cause`() {
            val cause = IOException("stream reset")
            val error = mapChatRequestException(cause).exceptionOrNull()
            assertInstanceOf(CopilotException.ChatException.ResponseParsingFailed::class.java, error)
            assertSame(cause, error!!.cause)
        }
    }
}
