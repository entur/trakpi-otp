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
import org.opentripplanner.trakpi.tester.spi.Kpi
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult

class FileResultsStorageTest {
    @Test
    fun `writes a json file per result under a run subdirectory`() {
        val dir = Files.createTempDirectory("results")
        val clock = Clock.fixed(Instant.parse("2026-06-23T04:00:00Z"), ZoneOffset.UTC)
        val storage = FileResultsStorage(dir, clock)
        val run =
            RunMetadata.create(
                version = "dev",
                application = "otp",
                startedAt = Instant.parse("2026-06-23T04:00:00Z"),
                referenceVersion = "dev",
                testsetVersion = "testset-1",
            )

        storage.store(
            run,
            TestCaseResult(
                requestId = "request-1",
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
}
