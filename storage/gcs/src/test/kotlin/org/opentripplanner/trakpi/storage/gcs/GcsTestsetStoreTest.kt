package org.opentripplanner.trakpi.storage.gcs

import kotlin.test.Test
import kotlin.test.assertEquals

class GcsTestsetStoreTest {
    @Test
    fun `keys a request by api, version and request id`() {
        assertEquals("testsets/transmodel/2026-07-20/request-001", GcsTestsetStore.objectName("transmodel", "2026-07-20", "request-001"))
    }

    @Test
    fun `versions live one directory under the api prefix`() {
        assertEquals("testsets/transmodel/", GcsTestsetStore.versionsPrefix("transmodel"))
    }
}
