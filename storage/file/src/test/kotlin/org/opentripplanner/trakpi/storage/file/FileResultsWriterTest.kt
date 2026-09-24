package org.opentripplanner.trakpi.storage.file

import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.opentripplanner.trakpi.common.PlannerVersion
import org.opentripplanner.trakpi.common.TestsetVersion
import org.opentripplanner.trakpi.tester.spi.kpi.Kpi
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult

class FileResultsWriterTest {
    @Test
    fun `writes a json file per result under a run subdirectory`() {
        val dir = Files.createTempDirectory("results")
        val clock = Clock.fixed(Instant.parse("2026-06-23T04:00:00Z"), ZoneOffset.UTC)
        val storage = FileResultsWriter(dir, clock)
        val run =
            RunMetadata.create(
                version = PlannerVersion("dev"),
                application = "otp",
                startedAt = Instant.parse("2026-06-23T04:00:00Z"),
                referenceVersion = PlannerVersion("dev"),
                testsetVersion = TestsetVersion("testset-1"),
            )

        storage.store(
            run,
            TestCaseResult(
                requestId = "request-1",
                request = """{"query":"{ trip { tripPatterns } }"}""",
                method = "trip",
                success = true,
                rawResponse = """{"data":{"trip":{"tripPatterns":[]}}}""",
                attributes = mapOf("http_status_code" to "200", "http_status_class" to "2xx"),
                kpis = listOf(Kpi("itineraryCount", 5.0)),
            ),
        )

        val file = dir.resolve(run.runId).resolve("request-1.json")
        assertTrue(file.exists())
        val obj = Json.parseToJsonElement(file.readText()).jsonObject
        assertEquals(run.runId, obj["runId"]!!.jsonPrimitive.content)
        assertEquals("dev", obj["version"]!!.jsonPrimitive.content)
        assertEquals("otp", obj["application"]!!.jsonPrimitive.content)
        assertEquals(true, obj["isReferenceVersion"]!!.jsonPrimitive.boolean)
        assertEquals("dev", obj["referenceVersion"]!!.jsonPrimitive.content)
        assertEquals("testset-1", obj["testsetVersion"]!!.jsonPrimitive.content)
        assertEquals("request-1", obj["requestId"]!!.jsonPrimitive.content)
        assertEquals("""{"query":"{ trip { tripPatterns } }"}""", obj["request"]!!.jsonPrimitive.content)
        assertEquals("trip", obj["method"]!!.jsonPrimitive.content)
        assertEquals(true, obj["success"]!!.jsonPrimitive.boolean)
        assertEquals("2026-06-23T04:00:00Z", obj["timestamp"]!!.jsonPrimitive.content)
        assertEquals(5.0, obj["kpis"]!!.jsonObject["itineraryCount"]!!.jsonPrimitive.double)
        assertEquals("200", obj["attributes"]!!.jsonObject["http_status_code"]!!.jsonPrimitive.content)
        assertEquals("2xx", obj["attributes"]!!.jsonObject["http_status_class"]!!.jsonPrimitive.content)
        assertEquals(
            """{"data":{"trip":{"tripPatterns":[]}}}""",
            obj["rawResponse"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `merges KPIs into an existing result rather than overwriting it`() {
        val dir = Files.createTempDirectory("results")
        val storage = FileResultsWriter(dir)
        val run =
            RunMetadata.create(
                version = PlannerVersion("dev"),
                application = "otp",
                startedAt = Instant.parse("2026-06-23T04:00:00Z"),
                testsetVersion = TestsetVersion("testset-1"),
            )

        // The test pass writes the per-run KPIs, request and response.
        storage.store(
            run,
            TestCaseResult(
                requestId = "request-1",
                request = """{"query":"..."}""",
                method = "trip",
                success = true,
                rawResponse = """{"data":{"trip":{}}}""",
                attributes = mapOf("http_status_code" to "200"),
                kpis = listOf(Kpi("itineraryCount", 5.0)),
            ),
        )
        // A second pass (compare) adds a comparative KPI with no request body of its own.
        storage.store(
            run,
            TestCaseResult(
                requestId = "request-1",
                request = "",
                method = "trip",
                success = true,
                rawResponse = "",
                attributes = emptyMap(),
                kpis = listOf(Kpi("itinerariesMatchReference", 0.0)),
            ),
        )

        val obj = Json.parseToJsonElement(dir.resolve(run.runId).resolve("request-1.json").readText()).jsonObject
        // Both KPIs are present.
        assertEquals(5.0, obj["kpis"]!!.jsonObject["itineraryCount"]!!.jsonPrimitive.double)
        assertEquals(0.0, obj["kpis"]!!.jsonObject["itinerariesMatchReference"]!!.jsonPrimitive.double)
        // The original request and response are kept, not overwritten by the empty ones.
        assertEquals("""{"query":"..."}""", obj["request"]!!.jsonPrimitive.content)
        assertEquals("""{"data":{"trip":{}}}""", obj["rawResponse"]!!.jsonPrimitive.content)
        assertEquals("200", obj["attributes"]!!.jsonObject["http_status_code"]!!.jsonPrimitive.content)
    }
}
