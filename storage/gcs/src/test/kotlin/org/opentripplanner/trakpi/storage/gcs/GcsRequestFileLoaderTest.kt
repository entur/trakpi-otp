package org.opentripplanner.trakpi.storage.gcs

import kotlin.test.Test
import kotlin.test.assertEquals
import org.opentripplanner.trakpi.common.TestsetVersion
import org.opentripplanner.trakpi.tester.spi.RequestFile

class GcsRequestFileLoaderTest {
    @Test
    fun `loads a version's request files sorted by id, skipping nested objects and other versions`() {
        val fake = InMemoryStorage()
        val ids = (1..300).map { "r%03d".format(it) }.shuffled()
        ids.forEach { fake.objects["testsets/transmodel/2026-07-20/$it"] = "body-$it".encodeToByteArray() }
        fake.objects["testsets/transmodel/2026-07-20/nested/r999"] = "nested".encodeToByteArray()
        fake.objects["testsets/transmodel/2026-07-19/r001"] = "older".encodeToByteArray()

        val files = GcsRequestFileLoader(fake.storage, InMemoryStorage.BUCKET, api = "transmodel", concurrency = 8).loadAll(TestsetVersion("2026-07-20"))

        assertEquals(ids.sorted().map { RequestFile(it, "body-$it") }, files)
    }
}
