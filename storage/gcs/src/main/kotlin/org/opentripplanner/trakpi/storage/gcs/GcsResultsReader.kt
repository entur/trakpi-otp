package org.opentripplanner.trakpi.storage.gcs

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.StorageOptions
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import org.opentripplanner.trakpi.common.PlannerVersion
import org.opentripplanner.trakpi.common.TestsetVersion
import org.opentripplanner.trakpi.tester.spi.ResponsePair
import org.opentripplanner.trakpi.tester.spi.ResultsReader
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/**
 * Reads a build's archived responses back from the GCS bucket written by [GcsResultsWriter],
 * keyed by requestId. The build version addresses the responses directly.
 * Authenticates with Application Default Credentials.
 */
class GcsResultsReader(
    private val storage: Storage,
    private val bucket: String,
    private val concurrency: Int = DEFAULT_CONCURRENCY,
) : ResultsReader {

    override fun responsePairs(subject: PlannerVersion, reference: PlannerVersion, testsetVersion: TestsetVersion): Sequence<ResponsePair> {
        val prefix = GcsResultsWriter.resultsPrefix(testsetVersion.value, subject.value)
        // iterateAll fetches the listing a page at a time as the sequence is consumed. Each pair is two reads that
        // mostly wait on GCS, so several pairs are read at once.
        return storage.list(bucket, Storage.BlobListOption.prefix(prefix), Storage.BlobListOption.fields(Storage.BlobField.NAME))
            .iterateAll()
            .asSequence()
            .map { it.name.removePrefix(prefix) }
            .filter { it.isNotEmpty() && !it.contains('/') }
            .mapConcurrently(concurrency) { requestId ->
                // A subject response deleted since the listing is skipped.
                response(subject, testsetVersion, requestId)?.let { ResponsePair(requestId, it, response(reference, testsetVersion, requestId)) }
            }
    }

    override fun response(version: PlannerVersion, testsetVersion: TestsetVersion, requestId: String): TravelPlannerResponse? {
        val name = GcsResultsWriter.resultsPrefix(testsetVersion.value, version.value) + requestId
        val content =
            try {
                storage.readAllBytes(BlobId.of(bucket, name))
            } catch (e: StorageException) {
                if (e.code == HTTP_NOT_FOUND) return null
                throw e
            }
        return ResponseJson.parse(String(content))
    }

    companion object {
        /** How many response pairs are read at once; enough to hide GCS latency without flooding it. */
        private const val DEFAULT_CONCURRENCY = 32

        /** A reader over [bucket] using Application Default Credentials. */
        fun create(bucket: String): GcsResultsReader = GcsResultsReader(StorageOptions.getDefaultInstance().service, bucket)
    }
}
