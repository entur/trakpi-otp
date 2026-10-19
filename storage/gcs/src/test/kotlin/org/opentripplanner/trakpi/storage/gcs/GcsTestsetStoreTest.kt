package org.opentripplanner.trakpi.storage.gcs

import com.google.cloud.storage.StorageException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.opentripplanner.trakpi.common.TestsetVersion
import org.opentripplanner.trakpi.testset.Request
import org.opentripplanner.trakpi.testset.Testset

class GcsTestsetStoreTest {
    private val version = TestsetVersion("2026-07-20")

    @Test
    fun `keys a request by api, version and request id`() {
        assertEquals("testsets/transmodel/2026-07-20/request-001", GcsTestsetStore.objectName("transmodel", "2026-07-20", "request-001"))
    }

    @Test
    fun `a version's requests live under its prefix`() {
        assertEquals("testsets/transmodel/2026-07-20/", GcsTestsetStore.versionPrefix("transmodel", "2026-07-20"))
    }

    @Test
    fun `versions live one directory under the api prefix`() {
        assertEquals("testsets/transmodel/", GcsTestsetStore.versionsPrefix("transmodel"))
    }

    @Test
    fun `uploads every request`() {
        val fake = InMemoryStorage()
        val requests = (1..500).map { Request("r$it", "q$it") }

        GcsTestsetStore(fake.storage, InMemoryStorage.BUCKET, concurrency = 8).store(Testset("transmodel", version, requests))

        assertEquals(requests.associate { "testsets/transmodel/2026-07-20/${it.id}" to it.body }, fake.objects.mapValues { String(it.value) })
    }

    @Test
    fun `replaces the requests of a version that is stored again and leaves other versions alone`() {
        val fake = InMemoryStorage()
        val store = GcsTestsetStore(fake.storage, InMemoryStorage.BUCKET, concurrency = 8)
        store.store(Testset("transmodel", TestsetVersion("2026-07-19"), listOf(Request("other", "q0"))))
        store.store(Testset("transmodel", version, (1..250).map { Request("old$it", "q$it") } + Request("kept", "q1")))

        store.store(Testset("transmodel", version, listOf(Request("kept", "q2"), Request("new", "q3"))))

        assertEquals(
            mapOf(
                "testsets/transmodel/2026-07-19/other" to "q0",
                "testsets/transmodel/2026-07-20/kept" to "q2",
                "testsets/transmodel/2026-07-20/new" to "q3",
            ),
            fake.objects.mapValues { String(it.value) },
        )
    }

    @Test
    fun `deletes the partial testset when an upload fails`() {
        val fake = InMemoryStorage(failCreate = { it.endsWith("/r250") })
        val requests = (1..500).map { Request("r$it", "q$it") }

        assertFailsWith<StorageException> {
            GcsTestsetStore(fake.storage, InMemoryStorage.BUCKET, concurrency = 8).store(Testset("transmodel", version, requests))
        }

        assertTrue(fake.objects.isEmpty(), "left ${fake.objects.size} object(s)")
    }
}
