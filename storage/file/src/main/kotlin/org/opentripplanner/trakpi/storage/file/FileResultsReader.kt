package org.opentripplanner.trakpi.storage.file

import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.opentripplanner.trakpi.common.PlannerVersion
import org.opentripplanner.trakpi.common.TestsetVersion
import org.opentripplanner.trakpi.tester.spi.ResultsReader
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/**
 * Reads archived responses back from the result files [FileResultsWriter] wrote under [resultsDir],
 * addressed by version and testset. Each result file records the version and testset it belongs to, so
 * the reader scans the run directories and returns the responses of the matching runs; when a version
 * and testset ran more than once, the most recent run's response wins for each request. This is the
 * local counterpart of the GCS results reader, so `compare` can score two runs from local files without
 * a cloud archive. Both runs must live under the same [resultsDir] (each in its own run subdirectory).
 */
class FileResultsReader(private val resultsDir: Path) : ResultsReader {

    override fun responses(version: PlannerVersion, testsetVersion: TestsetVersion): Map<String, TravelPlannerResponse> {
        if (!resultsDir.isDirectory()) return emptyMap()
        // requestId -> (timestamp, response); a newer timestamp wins if the version/testset ran more than once.
        val latest = HashMap<String, Pair<String, TravelPlannerResponse>>()
        for (runDir in resultsDir.listDirectoryEntries().filter { it.isDirectory() }) {
            for (file in runDir.listDirectoryEntries("*.json")) {
                val obj = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrNull() ?: continue
                if (text(obj, "version") != version.value || text(obj, "testsetVersion") != testsetVersion.value) continue
                val requestId = text(obj, "requestId") ?: continue
                val timestamp = text(obj, "timestamp") ?: ""
                val existing = latest[requestId]
                if (existing == null || timestamp > existing.first) latest[requestId] = timestamp to toResponse(obj)
            }
        }
        return latest.mapValues { it.value.second }
    }

    override fun response(version: PlannerVersion, testsetVersion: TestsetVersion, requestId: String): TravelPlannerResponse? =
        responses(version, testsetVersion)[requestId]

    private companion object {
        fun text(obj: JsonObject, key: String) = obj[key]?.jsonPrimitive?.contentOrNull

        fun toResponse(obj: JsonObject) =
            TravelPlannerResponse(
                raw = text(obj, "rawResponse") ?: "",
                success = (obj["success"] as? JsonPrimitive)?.booleanOrNull ?: false,
                method = text(obj, "method") ?: "unknown",
                attributes = (obj["attributes"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap(),
            )
    }
}
