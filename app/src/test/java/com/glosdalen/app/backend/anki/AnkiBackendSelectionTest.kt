package com.glosdalen.app.backend.anki

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AnkiBackendSelectionTest {

    @Test
    fun `permitted API is used`() {
        assertEquals(
            AnkiImplementationType.API,
            chooseAnkiImplementation(apiAvailable = true, apiPermitted = true, intentAvailable = true)
        )
    }

    @Test
    fun `installed but unpermitted API fails instead of falling back to the lossy intent`() {
        assertThrows<AnkiError.PermissionDenied> {
            chooseAnkiImplementation(apiAvailable = true, apiPermitted = false, intentAvailable = true)
        }
    }

    @Test
    fun `intent is used only when the API itself is unavailable`() {
        assertEquals(
            AnkiImplementationType.INTENT,
            chooseAnkiImplementation(apiAvailable = false, apiPermitted = false, intentAvailable = true)
        )
    }

    @Test
    fun `nothing available is reported as unavailable`() {
        assertEquals(
            AnkiImplementationType.UNAVAILABLE,
            chooseAnkiImplementation(apiAvailable = false, apiPermitted = false, intentAvailable = false)
        )
    }
}
