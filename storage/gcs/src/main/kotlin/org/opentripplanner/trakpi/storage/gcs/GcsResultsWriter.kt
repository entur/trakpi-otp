package org.opentripplanner.trakpi.storage.gcs

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.opentripplanner.trakpi.tester.spi.ResultsWriter
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/**
 * Archives each result's raw request and its response in a GCS bucket so a later run can read a
 * reference run's response and compare against it. The request is stored raw; the response is stored
 * as a serialized [TravelPlannerResponse] (raw body + success + method + attributes) so it round-trips.
 * Authenticates with Application Default Credentials.
 */
class GcsResultsWriter(private val storage: Storage, private val bucket: String) : ResultsWriter {

    override fun store(run: RunMetadata, result: TestCaseResult) {
        write(requestObjectName(run, result.requestId), result.request)
        val response = TravelPlannerResponse(result.rawResponse, result.success, result.method, result.attributes)
        write(responseObjectName(run, result.requestId), ResponseJson.serialize(response))
    }

    private fun write(objectName: String, body: String) {
        val blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectName)).setContentType("application/json").build()
        storage.create(blobInfo, body.encodeToByteArray())
    }

    companion object {
        /** A store that writes to [bucket] using Application Default Credentials. */
        fun create(bucket: String): GcsResultsWriter = GcsResultsWriter(StorageOptions.getDefaultInstance().service, bucket)

        /** Key for a request */
        internal fun requestObjectName(run: RunMetadata, requestId: String): String =
            "requests/${run.testsetVersion}/$requestId"

        /** Object-name prefix holding one run's responses. */
        internal fun resultsPrefix(testsetVersion: String, runId: String): String = "results/$testsetVersion/$runId/"

        /** Key for a response (one per run). */
        internal fun responseObjectName(run: RunMetadata, requestId: String): String =
            resultsPrefix(run.testsetVersion, run.runId) + requestId
    }
}
