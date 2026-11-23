package com.glosdalen.app.backend.elevenlabs

import android.content.ContextWrapper
import android.media.MediaPlayer
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.glosdalen.app.domain.preferences.ElevenLabsPreferences
import com.glosdalen.app.libs.copilot.util.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import retrofit2.Response
import java.io.File
import java.io.IOException

/**
 * When playback of freshly fetched audio never starts, [ElevenLabsRepository.speakText] reports a
 * [ElevenLabsError.PlaybackError] and releases the [MediaPlayer] it created for that playback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ElevenLabsRepositoryPlaybackTest {

    private class InMemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    /** Returns a few audio bytes for every TTS request. */
    private object AudioApiService : ElevenLabsApiService {
        override suspend fun textToSpeech(
            voiceId: String,
            apiKey: String,
            request: TextToSpeechRequest
        ): Response<ResponseBody> = Response.success(byteArrayOf(1, 2, 3).toResponseBody())

        override suspend fun getVoices(apiKey: String, pageSize: Int, nextPageToken: String?): Response<VoicesResponse> =
            throw UnsupportedOperationException()

        override suspend fun getModels(apiKey: String): Response<List<ElevenLabsModel>> =
            throw UnsupportedOperationException()

        override suspend fun getUserSubscription(apiKey: String): Response<SubscriptionInfo> =
            throw UnsupportedOperationException()
    }

    /** Records [release]; every other call is a no-op of the mockable android.jar. */
    private open class RecordingMediaPlayer : MediaPlayer() {
        var released = false

        override fun release() {
            released = true
        }
    }

    /** The audio file cannot be opened. */
    private class UnreadableSourcePlayer : RecordingMediaPlayer() {
        override fun setDataSource(path: String) {
            throw IOException("cannot open $path")
        }
    }

    /** The audio file opens but cannot be decoded, so preparation reports an error. */
    private class UndecodableAudioPlayer : RecordingMediaPlayer() {
        private var errorListener: OnErrorListener? = null

        override fun setOnErrorListener(listener: OnErrorListener?) {
            errorListener = listener
        }

        override fun prepareAsync() {
            errorListener?.onError(this, MEDIA_ERROR_UNKNOWN, MEDIA_ERROR_MALFORMED)
        }
    }

    @TempDir
    lateinit var tempDir: File

    private val preferences = ElevenLabsPreferences(InMemoryDataStore())

    @BeforeEach
    fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        preferences.setApiKey("sk_test")
        preferences.setVoiceId("voice123")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository(player: MediaPlayer) = ElevenLabsRepository(
        context = object : ContextWrapper(null) {
            override fun getCacheDir(): File = tempDir
        },
        apiService = AudioApiService,
        preferences = preferences,
        timeProvider = object : TimeProvider {
            override fun currentTimeMillis(): Long = 0L
        },
        mediaPlayerFactory = { player }
    )

    private fun speakWith(player: MediaPlayer): List<ElevenLabsError> {
        val errors = mutableListOf<ElevenLabsError>()
        runBlocking { repository(player).speakText("Hund", onError = { errors += it }) }
        return errors
    }

    @Test
    fun `a player whose data source cannot be set is released`() {
        val player = UnreadableSourcePlayer()

        val errors = speakWith(player)

        assertInstanceOf(ElevenLabsError.PlaybackError::class.java, errors.single())
        assertTrue(player.released, "MediaPlayer was not released")
    }

    @Test
    fun `a player that fails to prepare is released`() {
        val player = UndecodableAudioPlayer()

        val errors = speakWith(player)

        assertInstanceOf(ElevenLabsError.PlaybackError::class.java, errors.single())
        assertTrue(player.released, "MediaPlayer was not released")
    }
}
