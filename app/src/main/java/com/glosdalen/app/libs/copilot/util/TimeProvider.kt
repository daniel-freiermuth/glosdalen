package com.glosdalen.app.libs.copilot.util

import android.annotation.SuppressLint
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Time provider interface for testability.
 * 
 * This abstraction allows us to:
 * - Mock time in unit tests
 * - Control time flow during testing
 * - Have consistent, reproducible test results
 * 
 * In production, uses System.currentTimeMillis().
 * In tests, can be mocked to return specific values.
 */
interface TimeProvider {
    /**
     * Get current time in milliseconds since epoch
     */
    fun currentTimeMillis(): Long
}

/**
 * Production implementation that uses system time
 */
@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    @SuppressLint("DirectSystemCurrentTimeMillisUsage")
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
