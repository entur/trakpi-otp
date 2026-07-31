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
 * reference build's response and compare against it. The request is stored raw under the testset; the
 * response is stored as a serialized [TravelPlannerResponse] (raw body + success + method + attributes)
 * keyed by build version, so `--reference-version` addresses it directly. Re-running a build overwrites
 * its responses (latest wins). Authenticates with Application Default Credentials.
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
            "requests/${run.testsetVersion.value}/$requestId"

        /** Object-name prefix holding a build's responses for a testset. */
        internal fun resultsPrefix(testsetVersion: String, version: String): String = "results/$testsetVersion/$version/"

        /** Key for a response, addressed by the build [RunMetadata.version] so it's findable by reference version. */
        internal fun responseObjectName(run: RunMetadata, requestId: String): String =
            resultsPrefix(run.testsetVersion.value, run.version.value) + requestId
    }
}
