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
import org.opentripplanner.trakpi.tester.spi.ResultsStorage
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult

/**
 * Writes each result as a JSON file `<runId>/<requestId>.json` under [outputDir].
 */
class FileResultsStorage(private val outputDir: Path, private val clock: Clock = Clock.systemUTC()) : ResultsStorage {
    private val json = Json { prettyPrint = true }

    override fun store(run: RunMetadata, result: TestCaseResult) {
        val runDir = outputDir.resolve(run.runId)
        runDir.createDirectories()
        val payload = buildJsonObject {
            put("runId", run.runId)
            put("version", run.version)
            put("requestId", result.requestId)
            put("timestamp", Instant.now(clock).toString())
            putJsonObject("kpis") { result.kpis.forEach { put(it.name, it.value) } }
            put("rawResponse", result.rawResponse)
        }
        runDir.resolve("${result.requestId}.json").writeText(json.encodeToString(JsonObject.serializer(), payload))
    }
}
