package com.glosdalen.app.domain.coroutines

import com.glosdalen.app.domain.coroutines.LatestJobLauncher.Slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A stale "validate & save" from a settings page the user already left must never persist after a
 * newer submission or a clear. Each job waits on a gate standing in for the network round trip.
 */
class LatestJobLauncherTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val launcher = LatestJobLauncher(scope)
    private val saved = mutableListOf<String>()

    @AfterEach
    fun tearDown() = scope.cancel()

    private fun submit(slot: Slot, key: String, network: CompletableDeferred<Unit>) =
        launcher.launch(slot) {
            network.await()
            saved += key
        }

    @Test
    fun `a newer submission supersedes an older one whose network call finishes later`() = runBlocking {
        val slowA = CompletableDeferred<Unit>()
        val fastB = CompletableDeferred<Unit>()
        val jobA = submit(Slot.DEEPL_API_KEY, "A", slowA)
        val jobB = submit(Slot.DEEPL_API_KEY, "B", fastB)

        fastB.complete(Unit)
        slowA.complete(Unit)
        jobB.join()
        jobA.join()

        assertEquals(listOf("B"), saved)
        assertTrue(jobA.isCancelled)
    }

    @Test
    fun `cancel stops a pending submission so a clear is not undone`() = runBlocking {
        val network = CompletableDeferred<Unit>()
        val job = submit(Slot.ELEVENLABS_API_KEY, "A", network)

        launcher.cancel(Slot.ELEVENLABS_API_KEY)
        network.complete(Unit)
        job.join()

        assertEquals(emptyList<String>(), saved)
    }

    @Test
    fun `slots are independent`() = runBlocking {
        val deepL = CompletableDeferred<Unit>()
        val elevenLabs = CompletableDeferred<Unit>()
        val deepLJob = submit(Slot.DEEPL_API_KEY, "deepl", deepL)
        val elevenLabsJob = submit(Slot.ELEVENLABS_API_KEY, "elevenlabs", elevenLabs)
        launcher.cancel(Slot.ELEVENLABS_API_KEY)

        deepL.complete(Unit)
        elevenLabs.complete(Unit)
        deepLJob.join()
        elevenLabsJob.join()

        assertEquals(listOf("deepl"), saved)
    }
}
