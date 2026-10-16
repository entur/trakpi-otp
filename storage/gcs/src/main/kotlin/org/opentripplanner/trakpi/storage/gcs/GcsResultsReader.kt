package org.opentripplanner.trakpi.storage.gcs

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.StorageOptions
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import org.opentripplanner.trakpi.common.PlannerVersion
import org.opentripplanner.trakpi.common.TestsetVersion
import org.opentripplanner.trakpi.tester.spi.ResultsReader
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

/**
 * Reads a build's archived responses back from the GCS bucket written by [GcsResultsWriter],
 * keyed by requestId. The build version addresses the responses directly.
 * Authenticates with Application Default Credentials.
 */
class GcsResultsReader(private val storage: Storage, private val bucket: String) : ResultsReader {

    override fun responses(version: PlannerVersion, testsetVersion: TestsetVersion): Sequence<Pair<String, TravelPlannerResponse>> {
        val prefix = GcsResultsWriter.resultsPrefix(testsetVersion.value, version.value)
        return storage.list(bucket, Storage.BlobListOption.prefix(prefix)).iterateAll().asSequence()
            .map { blob -> blob.name.removePrefix(prefix) to blob }
            .filter { (requestId, _) -> requestId.isNotEmpty() && !requestId.contains('/') }
            .map { (requestId, blob) -> requestId to ResponseJson.parse(String(blob.getContent())) }
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
        /** A reader over [bucket] using Application Default Credentials. */
        fun create(bucket: String): GcsResultsReader = GcsResultsReader(StorageOptions.getDefaultInstance().service, bucket)
    }
}
