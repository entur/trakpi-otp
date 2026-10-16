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
 * the reader scans the run directories once per version and testset and indexes which file holds each
 * request's response; when a version and testset ran more than once, the most recent run's response wins
 * for each request. Both runs must live under the same [resultsDir] (each in its own run subdirectory).
 */
class FileResultsReader(private val resultsDir: Path) : ResultsReader {

    private val indexes = HashMap<Pair<PlannerVersion, TestsetVersion>, Map<String, Path>>()

    override fun responses(version: PlannerVersion, testsetVersion: TestsetVersion): Sequence<Pair<String, TravelPlannerResponse>> =
        index(version, testsetVersion).asSequence().map { (requestId, file) -> requestId to read(file) }

    override fun response(version: PlannerVersion, testsetVersion: TestsetVersion, requestId: String): TravelPlannerResponse? =
        index(version, testsetVersion)[requestId]?.let(::read)

    /** requestId -> the file holding its response, for the most recent run of [version] on [testsetVersion]. */
    private fun index(version: PlannerVersion, testsetVersion: TestsetVersion): Map<String, Path> =
        indexes.getOrPut(version to testsetVersion) {
            if (!resultsDir.isDirectory()) return@getOrPut emptyMap()
            // requestId -> (timestamp, file); a newer timestamp wins if the version/testset ran more than once.
            val latest = HashMap<String, Pair<String, Path>>()
            for (runDir in resultsDir.listDirectoryEntries().filter { it.isDirectory() }) {
                for (file in runDir.listDirectoryEntries("*.json")) {
                    val obj = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrNull() ?: continue
                    if (text(obj, "version") != version.value || text(obj, "testsetVersion") != testsetVersion.value) continue
                    val requestId = text(obj, "requestId") ?: continue
                    val timestamp = text(obj, "timestamp") ?: ""
                    val existing = latest[requestId]
                    if (existing == null || timestamp > existing.first) latest[requestId] = timestamp to file
                }
            }
            latest.mapValues { it.value.second }
        }

    private companion object {
        fun text(obj: JsonObject, key: String) = obj[key]?.jsonPrimitive?.contentOrNull

        fun read(file: Path) = toResponse(Json.parseToJsonElement(file.readText()).jsonObject)

        fun toResponse(obj: JsonObject) =
            TravelPlannerResponse(
                raw = text(obj, "rawResponse") ?: "",
                success = (obj["success"] as? JsonPrimitive)?.booleanOrNull ?: false,
                method = text(obj, "method") ?: "unknown",
                attributes = (obj["attributes"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap(),
            )
    }
}
