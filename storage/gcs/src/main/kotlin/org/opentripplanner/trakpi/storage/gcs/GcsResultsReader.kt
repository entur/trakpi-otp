package org.opentripplanner.trakpi.storage.gcs

import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.opentripplanner.trakpi.tester.spi.ResultsReader
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/**
 * Reads a build's archived responses back from the GCS bucket written by [GcsResultsWriter],
 * keyed by requestId. The build version addresses the responses directly.
 * Authenticates with Application Default Credentials.
 */
class GcsResultsReader(private val storage: Storage, private val bucket: String) : ResultsReader {

    override fun responses(version: String, testsetVersion: String): Map<String, TravelPlannerResponse> {
        val prefix = GcsResultsWriter.resultsPrefix(testsetVersion, version)
        val byRequest = HashMap<String, TravelPlannerResponse>()
        for (blob in storage.list(bucket, Storage.BlobListOption.prefix(prefix)).iterateAll()) {
            val requestId = blob.name.removePrefix(prefix)
            if (requestId.isEmpty() || requestId.contains('/')) continue
            byRequest[requestId] = ResponseJson.parse(String(blob.getContent()))
        }
        return byRequest
    }

    companion object {
        /** A reader over [bucket] using Application Default Credentials. */
        fun create(bucket: String): GcsResultsReader = GcsResultsReader(StorageOptions.getDefaultInstance().service, bucket)
    }
}
