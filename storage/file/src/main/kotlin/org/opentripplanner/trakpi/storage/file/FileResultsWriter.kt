package org.opentripplanner.trakpi.storage.file

import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.opentripplanner.trakpi.tester.spi.ResultsWriter
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult

/**
 * Writes each result as a JSON file `<runId>/<requestId>.json` under [outputDir].
 */
class FileResultsWriter(private val outputDir: Path, private val clock: Clock = Clock.systemUTC()) : ResultsWriter {
    private val json = Json { prettyPrint = true }

    override fun store(run: RunMetadata, result: TestCaseResult) {
        val runDir = outputDir.resolve(run.runId)
        runDir.createDirectories()
        val payload = buildJsonObject {
            put("runId", run.runId)
            put("version", run.version.value)
            put("application", run.application)
            put("isReferenceVersion", run.isReferenceVersion)
            run.referenceVersion?.let { put("referenceVersion", it.value) }
            put("testsetVersion", run.testsetVersion.value)
            put("requestId", result.requestId)
            put("request", result.request)
            put("method", result.method)
            put("success", result.success)
            put("timestamp", Instant.now(clock).toString())
            putJsonObject("kpis") { result.kpis.forEach { put(it.name, it.value) } }
            putJsonObject("attributes") { result.attributes.forEach { (name, value) -> put(name, value) } }
            put("rawResponse", result.rawResponse)
        }
        runDir.resolve("${result.requestId}.json").writeText(json.encodeToString(JsonObject.serializer(), payload))
    }
}
