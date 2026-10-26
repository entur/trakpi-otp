package org.opentripplanner.trakpi.storage.file

import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.opentripplanner.trakpi.common.PlannerVersion
import org.opentripplanner.trakpi.common.TestsetVersion
import org.opentripplanner.trakpi.tester.spi.TestCaseResult
import org.opentripplanner.trakpi.tester.spi.kpi.Kpi

class FileResultsReaderTest {
    private fun result(requestId: String, raw: String) =
        TestCaseResult(
            requestId = requestId,
            request = """{"query":"..."}""",
            method = "trip",
            success = true,
            rawResponse = raw,
            attributes = mapOf("http_status_code" to "200"),
            kpis = listOf(Kpi("itineraryCount", 3.0)),
        )

    @Test
    fun `reads back each version's responses from local result files`() {
        val dir = Files.createTempDirectory("results")
        val writer = FileResultsWriter(dir)
        val testset = TestsetVersion("testset-1")
        val baseRun = RunMetadataOf("base", testset, Instant.parse("2026-06-23T04:00:00Z"))
        val modRun = RunMetadataOf("mod", testset, Instant.parse("2026-06-23T05:00:00Z"))
        writer.store(baseRun, result("request-1", """{"data":{"trip":{"tripPatterns":[1,2]}}}"""))
        writer.store(modRun, result("request-1", """{"data":{"trip":{"tripPatterns":[1,2,3]}}}"""))

        val reader = FileResultsReader(dir)

        val cases = reader.comparisonCases(PlannerVersion("mod"), PlannerVersion("base"), testset).toList()
        assertEquals(listOf("request-1"), cases.map { it.request.id })
        assertEquals("""{"data":{"trip":{"tripPatterns":[1,2,3]}}}""", cases.single().subject.raw)
        assertEquals("""{"data":{"trip":{"tripPatterns":[1,2]}}}""", cases.single().reference?.raw)
        assertEquals("""{"query":"..."}""", cases.single().request.raw)
        assertNull(reader.comparisonCases(PlannerVersion("mod"), PlannerVersion("other"), testset).single().reference)

        val base = reader.response(PlannerVersion("base"), testset, "request-1")!!
        assertEquals("""{"data":{"trip":{"tripPatterns":[1,2]}}}""", base.raw)
        assertEquals("trip", base.method)
        assertEquals(true, base.success)
        assertEquals("200", base.attributes["http_status_code"])

        val mod = reader.response(PlannerVersion("mod"), testset, "request-1")!!
        assertEquals("""{"data":{"trip":{"tripPatterns":[1,2,3]}}}""", mod.raw)

        assertEquals(emptyList(), reader.comparisonCases(PlannerVersion("other"), PlannerVersion("base"), testset).toList())
        assertNull(reader.response(PlannerVersion("other"), testset, "request-1"))
    }

    private fun RunMetadataOf(version: String, testset: TestsetVersion, startedAt: Instant) =
        org.opentripplanner.trakpi.tester.spi.RunMetadata.create(
            version = PlannerVersion(version),
            application = "otp",
            startedAt = startedAt,
            testsetVersion = testset,
        )
}
