package com.glosdalen.app.libs.copilot.auth

import android.content.ContextWrapper
import com.glosdalen.app.libs.copilot.CopilotException.AuthException
import com.glosdalen.app.libs.copilot.CopilotException.NetworkException
import com.glosdalen.app.libs.copilot.models.DeviceCodeResponse
import com.glosdalen.app.libs.copilot.models.OAuthToken
import com.glosdalen.app.libs.copilot.network.CopilotApiConstants
import com.glosdalen.app.libs.copilot.network.GitHubOAuthApiService
import com.glosdalen.app.libs.copilot.storage.CopilotStorage
import com.glosdalen.app.libs.copilot.util.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import retrofit2.Response
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Contract of the GitHub OAuth device-flow polling in [CopilotAuthManager]:
 * [CopilotAuthManager.pollForToken] classifies every poll outcome as retryable
 * ([AuthException.AuthorizationPending], any [NetworkException]) or terminal (other
 * [AuthException]s), and [CopilotAuthManager.pollForTokenWithRetry] keeps polling at the
 * device code's interval on retryable outcomes and stops on terminal ones.
 *
 * Only non-success outcomes are covered: [CopilotStorage] is a final, Keystore-backed class
 * that cannot run on the JVM, so the storage instance here is never reached (it is only
 * touched when a token is granted).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CopilotAuthManagerTest {

    private var now = START_MILLIS
    private val clock = object : TimeProvider {
        override fun currentTimeMillis(): Long = now
    }
    private val api = FakeOAuthApi()
    private val manager = CopilotAuthManager(
        oauthApiService = api,
        storage = CopilotStorage(ContextWrapper(null), clock),
        timeProvider = clock
    )

    /** Runs the device-code request so polling has a device code (created at [now]). */
    private suspend fun initiate(expiresIn: Int = EXPIRES_IN_SECONDS, interval: Int = INTERVAL_SECONDS) {
        api.deviceCode = DeviceCodeResponse(
            deviceCode = DEVICE_CODE,
            userCode = "ABCD-1234",
            verificationUri = "https://github.com/login/device",
            expiresIn = expiresIn,
            interval = interval
        )
        manager.initiateAuth().getOrThrow()
    }

    private suspend fun pollError(): Throwable? = manager.pollForToken().exceptionOrNull()

    @Nested
    @DisplayName("pollForToken: 200 response without an access token")
    inner class OkWithError {

        @Test
        fun `authorization_pending is retryable AuthorizationPending`() = runTest {
            initiate()
            api.replies += { tokenError("authorization_pending") }
            assertInstanceOf(AuthException.AuthorizationPending::class.java, pollError())
        }

        @Test
        fun `slow_down is retryable AuthorizationPending`() = runTest {
            initiate()
            api.replies += { tokenError("slow_down") }
            assertInstanceOf(AuthException.AuthorizationPending::class.java, pollError())
        }

        @Test
        fun `access_denied is terminal AccessDenied`() = runTest {
            initiate()
            api.replies += { tokenError("access_denied") }
            assertInstanceOf(AuthException.AccessDenied::class.java, pollError())
        }

        @Test
        fun `expired_token is terminal DeviceCodeExpired`() = runTest {
            initiate()
            api.replies += { tokenError("expired_token") }
            assertInstanceOf(AuthException.DeviceCodeExpired::class.java, pollError())
        }

        @Test
        fun `unknown error is terminal InvalidDeviceCode`() = runTest {
            initiate()
            api.replies += { tokenError("incorrect_device_code") }
            assertInstanceOf(AuthException.InvalidDeviceCode::class.java, pollError())
        }

        @Test
        fun `missing error is terminal InvalidDeviceCode`() = runTest {
            initiate()
            api.replies += { tokenError(null) }
            assertInstanceOf(AuthException.InvalidDeviceCode::class.java, pollError())
        }

        @Test
        fun `polls with the device code returned by initiateAuth`() = runTest {
            initiate()
            api.replies += { tokenError("authorization_pending") }
            manager.pollForToken()
            assertEquals(listOf(DEVICE_CODE), api.polledDeviceCodes)
        }
    }

    @Nested
    @DisplayName("pollForToken: HTTP error responses")
    inner class HttpErrors {

        @Test
        fun `400 with authorization_pending body is retryable AuthorizationPending`() = runTest {
            initiate()
            api.replies += { httpError(400, """{"error":"authorization_pending"}""") }
            assertInstanceOf(AuthException.AuthorizationPending::class.java, pollError())
        }

        @Test
        fun `400 with access_denied body is terminal AccessDenied`() = runTest {
            initiate()
            api.replies += { httpError(400, """{"error":"access_denied"}""") }
            assertInstanceOf(AuthException.AccessDenied::class.java, pollError())
        }

        @Test
        fun `400 with any other body is terminal InvalidDeviceCode`() = runTest {
            initiate()
            api.replies += { httpError(400, """{"error":"unsupported_grant_type"}""") }
            assertInstanceOf(AuthException.InvalidDeviceCode::class.java, pollError())
        }

        @Test
        fun `5xx is retryable ServerError carrying the status code`() = runTest {
            initiate()
            api.replies += { httpError(503, "unavailable") }
            val error = assertInstanceOf(NetworkException.ServerError::class.java, pollError())
            assertEquals(503, error.code)
        }

        @Test
        fun `unknown host is retryable NoConnection`() = runTest {
            initiate()
            api.replies += { throw UnknownHostException("github.com") }
            assertInstanceOf(NetworkException.NoConnection::class.java, pollError())
        }

        @Test
        fun `socket timeout is retryable Timeout`() = runTest {
            initiate()
            api.replies += { throw SocketTimeoutException() }
            assertInstanceOf(NetworkException.Timeout::class.java, pollError())
        }
    }

    @Nested
    @DisplayName("pollForToken: device code state")
    inner class DeviceCodeState {

        @Test
        fun `polling before initiateAuth is InvalidDeviceCode without a request`() = runTest {
            assertInstanceOf(AuthException.InvalidDeviceCode::class.java, pollError())
            assertTrue(api.polledDeviceCodes.isEmpty())
        }

        @Test
        fun `device code is still polled at exactly expiresIn`() = runTest {
            initiate()
            now = START_MILLIS + EXPIRES_IN_SECONDS * 1000L
            api.replies += { tokenError("authorization_pending") }
            assertInstanceOf(AuthException.AuthorizationPending::class.java, pollError())
            assertEquals(1, api.polledDeviceCodes.size)
        }

        @Test
        fun `device code past expiresIn is DeviceCodeExpired without a request`() = runTest {
            initiate()
            now = START_MILLIS + EXPIRES_IN_SECONDS * 1000L + 1
            assertInstanceOf(AuthException.DeviceCodeExpired::class.java, pollError())
            assertTrue(api.polledDeviceCodes.isEmpty())
        }
    }

    @Nested
    @DisplayName("pollForTokenWithRetry")
    inner class RetryLoop {

        @Test
        fun `before initiateAuth fails with InvalidDeviceCode without a request`() = runTest {
            val error = manager.pollForTokenWithRetry().exceptionOrNull()
            assertInstanceOf(AuthException.InvalidDeviceCode::class.java, error)
            assertTrue(api.polledDeviceCodes.isEmpty())
        }

        @Test
        fun `keeps polling at the device interval while authorization is pending`() = runTest {
            initiate(interval = 7)
            api.replies += { tokenError("authorization_pending") }
            api.replies += { httpError(400, """{"error":"authorization_pending"}""") }
            api.replies += { tokenError("slow_down") }
            api.replies += { tokenError("access_denied") }

            val error = manager.pollForTokenWithRetry().exceptionOrNull()

            assertInstanceOf(AuthException.AccessDenied::class.java, error)
            assertEquals(4, api.polledDeviceCodes.size)
            assertElapsed(3 * 7_000L)
        }

        @Test
        fun `retries network and server errors instead of aborting`() = runTest {
            initiate()
            api.replies += { httpError(502, "bad gateway") }
            api.replies += { throw UnknownHostException("github.com") }
            api.replies += { throw SocketTimeoutException() }
            api.replies += { tokenError("expired_token") }

            val error = manager.pollForTokenWithRetry().exceptionOrNull()

            assertInstanceOf(AuthException.DeviceCodeExpired::class.java, error)
            assertEquals(4, api.polledDeviceCodes.size)
            assertElapsed(3 * INTERVAL_SECONDS * 1000L)
        }

        @ParameterizedTest
        @ValueSource(strings = ["access_denied", "expired_token", "incorrect_device_code"])
        fun `stops after the first terminal error`(error: String) = runTest {
            initiate()
            api.replies += { tokenError(error) }

            val result = manager.pollForTokenWithRetry()

            assertInstanceOf(AuthException::class.java, result.exceptionOrNull())
            assertEquals(1, api.polledDeviceCodes.size)
            assertElapsed(0)
        }

        @Test
        fun `gives up with DeviceCodeExpired when the overall timeout elapses`() = runTest {
            // expiresIn far beyond the overall timeout (and a frozen clock) so the per-poll
            // expiry check never fires: only withTimeout can end the loop.
            initiate(expiresIn = 3600)
            api.whenEmpty = { tokenError("authorization_pending") }

            val error = manager.pollForTokenWithRetry().exceptionOrNull()

            assertInstanceOf(AuthException.DeviceCodeExpired::class.java, error)
            assertElapsed(CopilotApiConstants.DEVICE_CODE_TIMEOUT)
        }

        private fun TestScope.assertElapsed(millis: Long) = assertEquals(millis, currentTime)
    }

    private class FakeOAuthApi : GitHubOAuthApiService {
        lateinit var deviceCode: DeviceCodeResponse

        /** Replies to successive token polls, in order. */
        val replies = ArrayDeque<() -> Response<OAuthToken>>()

        /** Reply once [replies] is exhausted; null fails the test on an unexpected poll. */
        var whenEmpty: (() -> Response<OAuthToken>)? = null

        val polledDeviceCodes = mutableListOf<String>()

        override suspend fun getDeviceCode(
            accept: String,
            clientId: String,
            scope: String
        ): Response<DeviceCodeResponse> = Response.success(deviceCode)

        override suspend fun getAccessToken(
            accept: String,
            clientId: String,
            deviceCode: String,
            grantType: String
        ): Response<OAuthToken> {
            polledDeviceCodes += deviceCode
            val reply = replies.removeFirstOrNull() ?: whenEmpty ?: fail("unexpected token poll")
            return reply()
        }
    }

    private companion object {
        const val START_MILLIS = 1_700_000_000_000L
        const val EXPIRES_IN_SECONDS = 900
        const val INTERVAL_SECONDS = 5
        const val DEVICE_CODE = "device-code-123"

        fun tokenError(error: String?): Response<OAuthToken> =
            Response.success(OAuthToken(error = error))

        fun httpError(code: Int, body: String): Response<OAuthToken> =
            Response.error(code, body.toResponseBody("application/json".toMediaType()))
    }
}
