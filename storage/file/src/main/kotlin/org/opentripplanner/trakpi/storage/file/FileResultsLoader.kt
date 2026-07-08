package org.opentripplanner.trakpi.storage.file

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.opentripplanner.trakpi.analyzer.KpiValue
import org.opentripplanner.trakpi.analyzer.ResultRecord
import org.opentripplanner.trakpi.analyzer.TestRun
import org.opentripplanner.trakpi.analyzer.spi.ResultsLoader

/**
 * Reads runs from a directory tree of result JSON files, as written by [FileResultsStorage] and as
 * assembled by downloading several runs' artifacts into one folder. The tree is walked recursively,
 * so any nesting works; results are grouped into runs by their embedded `runId`.
 *
 * Files written before runs carried a `runId`/`version` still load: the run id falls back to the
 * file's parent directory name and the version to `unknown`.
 *
 * Accepts one opaque argument, `--results-dir <path>` (or `--results-dir=<path>`).
 */
class FileResultsLoader : ResultsLoader {

    override fun load(args: String?): List<TestRun> {
        val dir = resultsDir(args)
        require(dir.isDirectory()) { "results directory does not exist: $dir" }

        val records =
            Files.walk(dir).use { paths ->
                paths.filter { it.isRegularFile() && it.extension == "json" }.map(::readRecord).toList()
            }

        return records
            .groupBy { it.runId }
            .map { (runId, recs) ->
                TestRun(
                    runId = runId,
                    version = recs.first().version,
                    // Median is robust to a stray leftover file with an unrelated timestamp.
                    timestamp = medianTimestamp(recs.map { it.timestamp }),
                    results = recs.map { ResultRecord(it.requestId, it.kpis) },
                )
            }
            .sortedBy { it.timestamp }
    }

    private fun medianTimestamp(timestamps: List<Instant>): Instant {
        val sorted = timestamps.sorted()
        return sorted[sorted.size / 2]
    }

    private fun readRecord(file: Path): RawRecord {
        val obj = Json.parseToJsonElement(file.readText()).jsonObject
        fun str(key: String) = obj[key]?.jsonPrimitive?.content
        val kpis =
            obj["kpis"]?.jsonObject?.mapNotNull { (name, value) ->
                value.jsonPrimitive.doubleOrNull?.let { KpiValue(name, it) }
            } ?: emptyList()
        return RawRecord(
            // Fall back to the parent directory name for pre-runId results.
            runId = str("runId") ?: file.parent.name,
            version = str("version") ?: "unknown",
            requestId = str("requestId") ?: file.name.removeSuffix(".json"),
            timestamp = str("timestamp")?.let(Instant::parse) ?: Instant.EPOCH,
            kpis = kpis,
        )
    }

    private fun resultsDir(args: String?): Path {
        val tokens = args?.trim()?.split(Regex("\\s+")).orEmpty().filter { it.isNotEmpty() }
        val value =
            tokens.mapNotNull { token ->
                when {
                    token.startsWith("$FLAG=") -> token.substringAfter('=')
                    else -> null
                }
            }.firstOrNull()
                ?: tokens.indexOf(FLAG).takeIf { it >= 0 && it + 1 < tokens.size }?.let { tokens[it + 1] }
                ?: throw IllegalArgumentException("results loader requires '$FLAG <path>' in --loaderargs")
        return Path.of(value)
    }

    private data class RawRecord(
        val runId: String,
        val version: String,
        val requestId: String,
        val timestamp: Instant,
        val kpis: List<KpiValue>,
    )

    private companion object {
        const val FLAG = "--results-dir"
    }
}
