package com.glosdalen.app.backend.elevenlabs

import android.media.MediaPlayer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates the [MediaPlayer] for each [ElevenLabsRepository] playback.
 * This seam lets the repository run against a fake player in JVM tests.
 */
fun interface MediaPlayerFactory {
    fun create(): MediaPlayer
}

@Singleton
class AndroidMediaPlayerFactory @Inject constructor() : MediaPlayerFactory {
    override fun create(): MediaPlayer = MediaPlayer()
}
