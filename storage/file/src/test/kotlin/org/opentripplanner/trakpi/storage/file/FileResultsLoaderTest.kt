package org.opentripplanner.trakpi.storage.file

import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.opentripplanner.trakpi.tester.spi.Kpi
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult

class FileResultsLoaderTest {

    @Test
    fun `round-trips runs written by the storage`() {
        val dir = Files.createTempDirectory("results")
        val storage = FileResultsStorage(dir, Clock.fixed(Instant.parse("2026-07-01T04:00:00Z"), ZoneOffset.UTC))
        val run = RunMetadata.create(version = "dev", startedAt = Instant.parse("2026-07-01T04:00:00Z"))
        storage.store(run, TestCaseResult("request-1", "{}", listOf(Kpi("routingTimeMs", 100.0))))
        storage.store(run, TestCaseResult("request-2", "{}", listOf(Kpi("routingTimeMs", 300.0))))

        val runs = FileResultsLoader().load("--results-dir $dir")

        val loaded = runs.single()
        assertEquals(run.runId, loaded.runId)
        assertEquals("dev", loaded.version)
        assertEquals(2, loaded.results.size)
        assertEquals(listOf(100.0, 300.0), loaded.results.flatMap { it.kpis }.map { it.value }.sorted())
    }

    @Test
    fun `groups several runs and orders them by time`() {
        val dir = Files.createTempDirectory("results")
        write(dir, "dev_a", "dev", "2026-07-02T04:00:00Z", "request-1", 5.0)
        write(dir, "dev_b", "dev", "2026-07-01T04:00:00Z", "request-1", 9.0)

        val runs = FileResultsLoader().load("--results-dir=$dir")

        assertEquals(listOf("dev_b", "dev_a"), runs.map { it.runId })
    }

    @Test
    fun `falls back to directory name and unknown version for pre-runId files`() {
        val dir = Files.createTempDirectory("results")
        val legacy = dir.resolve("2026-06-23").createDirectories()
        legacy
            .resolve("request-1.json")
            .writeText("""{"requestId":"request-1","timestamp":"2026-06-23T04:00:00Z","kpis":{"itineraryCount":5}}""")

        val run = FileResultsLoader().load("--results-dir $dir").single()

        assertEquals("2026-06-23", run.runId)
        assertEquals("unknown", run.version)
        assertEquals(5.0, run.results.single().kpis.single().value)
    }

    @Test
    fun `fails when the results dir flag is missing`() {
        assertFailsWith<IllegalArgumentException> { FileResultsLoader().load("--wrong-flag foo") }
    }

    private fun write(root: java.nio.file.Path, runId: String, version: String, timestamp: String, requestId: String, kpi: Double) {
        val d = root.resolve(runId).createDirectories()
        d.resolve("$requestId.json")
            .writeText(
                """{"runId":"$runId","version":"$version","requestId":"$requestId","timestamp":"$timestamp","kpis":{"routingTimeMs":$kpi}}"""
            )
    }
}
