package org.opentripplanner.trakpi.storage.gcs

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.opentripplanner.trakpi.tester.spi.ResultsWriter
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult

/**
 * Archives each result's raw request and response as objects in a GCS bucket so a later run can read
 * a reference run's request/response and compare against it.
 * Authenticates with Application Default Credentials.
 */
class GcsResultsWriter(private val storage: Storage, private val bucket: String) : ResultsWriter {

    override fun store(run: RunMetadata, result: TestCaseResult) {
        write(requestObjectName(run, result.requestId), result.request)
        write(responseObjectName(run, result.requestId), result.rawResponse)
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

        /** Key for a response (one per run). */
        internal fun responseObjectName(run: RunMetadata, requestId: String): String =
            "results/${run.testsetVersion}/${run.runId}/$requestId"
    }
}
