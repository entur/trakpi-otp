package org.opentripplanner.trakpi.storage.gcs

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.StorageOptions
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import org.opentripplanner.trakpi.common.PlannerVersion
import org.opentripplanner.trakpi.common.TestsetVersion
import org.opentripplanner.trakpi.common.mapConcurrently
import org.opentripplanner.trakpi.tester.spi.ComparisonCase
import org.opentripplanner.trakpi.tester.spi.ResultsReader
import org.opentripplanner.trakpi.tester.spi.TravelPlannerRequest
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/**
 * Reads a build's archived responses back from the GCS bucket written by [GcsResultsWriter],
 * keyed by requestId, together with the request each answers. The build version addresses the responses directly.
 * Authenticates with Application Default Credentials.
 */
class GcsResultsReader(
    private val storage: Storage,
    private val bucket: String,
    private val concurrency: Int = DEFAULT_CONCURRENCY,
) : ResultsReader {

    override fun comparisonCases(subject: PlannerVersion, reference: PlannerVersion, testsetVersion: TestsetVersion): Sequence<ComparisonCase> {
        val prefix = GcsResultsWriter.resultsPrefix(testsetVersion.value, subject.value)
        // iterateAll fetches the listing a page at a time as the sequence is consumed. Each case is three reads that
        // mostly wait on GCS, so several cases are read at once.
        return storage.list(bucket, Storage.BlobListOption.prefix(prefix), Storage.BlobListOption.fields(Storage.BlobField.NAME))
            .iterateAll()
            .asSequence()
            .map { it.name.removePrefix(prefix) }
            .filter { it.isNotEmpty() && !it.contains('/') }
            .mapConcurrently(concurrency) { requestId ->
                // A subject response deleted since the listing is skipped.
                response(subject, testsetVersion, requestId)?.let {
                    ComparisonCase(request(testsetVersion, requestId), it, response(reference, testsetVersion, requestId))
                }
            }
    }

    override fun response(version: PlannerVersion, testsetVersion: TestsetVersion, requestId: String): TravelPlannerResponse? =
        read(GcsResultsWriter.resultsPrefix(testsetVersion.value, version.value) + requestId)?.let(ResponseJson::parse)

    /** The request archived for [requestId] in [testsetVersion]; it is archived with every response. */
    private fun request(testsetVersion: TestsetVersion, requestId: String): TravelPlannerRequest {
        val name = GcsResultsWriter.requestObjectName(testsetVersion.value, requestId)
        return TravelPlannerRequest(requestId, checkNotNull(read(name)) { "No archived request $name for the response to $requestId" })
    }

    /** The content of object [name], or null when it does not exist. */
    private fun read(name: String): String? =
        try {
            String(storage.readAllBytes(BlobId.of(bucket, name)))
        } catch (e: StorageException) {
            if (e.code == HTTP_NOT_FOUND) null else throw e
        }

    companion object {
        /** How many comparison cases are read at once; enough to hide GCS latency without flooding it. */
        private const val DEFAULT_CONCURRENCY = 32

        /** A reader over [bucket] using Application Default Credentials. */
        fun create(bucket: String): GcsResultsReader = GcsResultsReader(StorageOptions.getDefaultInstance().service, bucket)
    }
}
