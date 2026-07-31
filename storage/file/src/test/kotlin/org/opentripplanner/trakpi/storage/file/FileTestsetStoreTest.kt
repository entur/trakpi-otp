package org.opentripplanner.trakpi.storage.file

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.opentripplanner.trakpi.common.TestsetVersion
import org.opentripplanner.trakpi.testset.Request
import org.opentripplanner.trakpi.testset.Testset

class FileTestsetStoreTest {
    @Test
    fun `stores requests as one file each and lists versions`() {
        val root = Files.createTempDirectory("testsets")
        val store = FileTestsetStore(root)

        store.store(Testset("transmodel", TestsetVersion("2026-07-16"), listOf(Request("r1", "q1"), Request("r2", "q2"))))

        assertEquals("q1", root.resolve("transmodel").resolve("2026-07-16").resolve("r1").readText())
        assertEquals("q2", root.resolve("transmodel").resolve("2026-07-16").resolve("r2").readText())
        assertEquals(listOf(TestsetVersion("2026-07-16")), store.versions("transmodel"))
        assertTrue(store.versions("missing").isEmpty())
    }
}
