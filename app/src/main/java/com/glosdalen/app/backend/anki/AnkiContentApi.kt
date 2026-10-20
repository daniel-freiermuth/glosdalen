package com.glosdalen.app.backend.anki

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ichi2.anki.api.AddContentApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The subset of AnkiDroid's [AddContentApi] used by [AnkiApiRepository].
 * [AddContentApi] is final, so this seam lets the repository run against a fake in JVM tests.
 */
interface AnkiContentApi {
    val deckList: Map<Long, String>
    val modelList: Map<Long, String>
    fun addNewDeck(deckName: String): Long?
    fun addNewBasicModel(modelName: String): Long?

    /** Adds one note per entry of [fieldsList]; returns the number of notes AnkiDroid actually added. */
    fun addNotes(modelId: Long, deckId: Long, fieldsList: List<Array<String>>): Int

    /** Copies [audioFile] into AnkiDroid's media collection; returns its `[sound:...]` tag, or null on failure. */
    fun addAudio(audioFile: File, preferredName: String): String?
}

/** Creates an [AnkiContentApi], or returns null when AnkiDroid is not installed. */
fun interface AnkiContentApiFactory {
    fun create(): AnkiContentApi?
}

@Singleton
class AnkiDroidContentApiFactory @Inject constructor(
    @ApplicationContext private val context: Context
) : AnkiContentApiFactory {
    override fun create(): AnkiContentApi? =
        if (AddContentApi.getAnkiDroidPackageName(context) != null) {
            AnkiDroidContentApi(context, AddContentApi(context))
        } else {
            null
        }
}

private class AnkiDroidContentApi(
    private val context: Context,
    private val api: AddContentApi
) : AnkiContentApi {
    override val deckList: Map<Long, String> get() = api.deckList
    override val modelList: Map<Long, String> get() = api.modelList
    override fun addNewDeck(deckName: String): Long? = api.addNewDeck(deckName)
    override fun addNewBasicModel(modelName: String): Long? = api.addNewBasicModel(modelName)
    override fun addNotes(modelId: Long, deckId: Long, fieldsList: List<Array<String>>): Int =
        api.addNotes(modelId, deckId, fieldsList, null)

    override fun addAudio(audioFile: File, preferredName: String): String? {
        // Use FileProvider to create a content URI that AnkiDroid can access
        val authority = "${context.packageName}.fileprovider"
        val mediaUri = FileProvider.getUriForFile(context, authority, audioFile)

        // Grant read permission to AnkiDroid
        val ankiPackage = AddContentApi.getAnkiDroidPackageName(context)
        if (ankiPackage != null) {
            context.grantUriPermission(ankiPackage, mediaUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val soundTag = api.addMediaFromUri(mediaUri, preferredName, "audio")

        // Revoke the permission after the file is copied
        if (ankiPackage != null) {
            context.revokeUriPermission(mediaUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return soundTag
    }
}
