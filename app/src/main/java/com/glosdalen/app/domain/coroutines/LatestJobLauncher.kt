package com.glosdalen.app.domain.coroutines

import com.glosdalen.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.EnumMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs user-submitted work in the application scope so it outlives the screen's entry-scoped
 * ViewModel, with at most one job per [Slot]. Launching a new job, or calling [cancel], cancels
 * the previous job of that slot. A stale submission from a screen the user already left can
 * therefore never finish after, and overwrite, a newer submission or a clear.
 */
@Singleton
class LatestJobLauncher @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope
) {
    enum class Slot { DEEPL_API_KEY, ELEVENLABS_API_KEY }

    private val jobs = EnumMap<Slot, Job>(Slot::class.java)

    fun launch(slot: Slot, block: suspend CoroutineScope.() -> Unit): Job = synchronized(jobs) {
        jobs[slot]?.cancel()
        scope.launch(block = block).also { jobs[slot] = it }
    }

    fun cancel(slot: Slot) {
        synchronized(jobs) { jobs.remove(slot) }?.cancel()
    }
}
