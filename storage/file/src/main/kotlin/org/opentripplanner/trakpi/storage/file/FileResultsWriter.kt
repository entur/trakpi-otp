package org.opentripplanner.trakpi.storage.file

import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.opentripplanner.trakpi.tester.spi.ResultsWriter
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult

/**
 * Writes each result as a JSON file `<runId>/<requestId>.json` under [outputDir]. When a file for the
 * request already exists — for example a second-pass compare adding comparative KPIs to a run the test
 * pass already wrote — the new KPIs and attributes are merged in rather than overwriting the file, and
 * the existing request body and raw response are kept. So a request keeps all of its KPIs.
 */
class FileResultsWriter(private val outputDir: Path, private val clock: Clock = Clock.systemUTC()) : ResultsWriter {
    private val json = Json { prettyPrint = true }

    override fun store(run: RunMetadata, result: TestCaseResult) {
        val runDir = outputDir.resolve(run.runId)
        runDir.createDirectories()
        val file = runDir.resolve("${result.requestId}.json")
        val existing = if (file.exists()) runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrNull() else null
        fun keptText(field: String, incoming: String) =
            existing?.get(field)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() } ?: incoming
        val payload = buildJsonObject {
            put("runId", run.runId)
            put("version", run.version.value)
            put("application", run.application)
            put("isReferenceVersion", run.isReferenceVersion)
            run.referenceVersion?.let { put("referenceVersion", it.value) }
            put("testsetVersion", run.testsetVersion.value)
            put("requestId", result.requestId)
            put("request", keptText("request", result.request))
            put("method", result.method)
            put("success", result.success)
            put("timestamp", Instant.now(clock).toString())
            putJsonObject("kpis") {
                (existing?.get("kpis") as? JsonObject)?.forEach { (name, value) -> put(name, value) }
                result.kpis.forEach { put(it.name, it.value) }
            }
            putJsonObject("attributes") {
                (existing?.get("attributes") as? JsonObject)?.forEach { (name, value) -> put(name, value) }
                result.attributes.forEach { (name, value) -> put(name, value) }
            }
            put("rawResponse", keptText("rawResponse", result.rawResponse))
        }
        file.writeText(json.encodeToString(JsonObject.serializer(), payload))
    }
}
