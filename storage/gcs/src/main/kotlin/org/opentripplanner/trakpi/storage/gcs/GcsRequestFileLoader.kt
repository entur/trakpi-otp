package org.opentripplanner.trakpi.storage.gcs

import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.opentripplanner.trakpi.tester.spi.RequestFile
import org.opentripplanner.trakpi.tester.spi.RequestFileLoader

/**
 * A [RequestFileLoader] that reads a testset's request files from a GCS bucket.
 * Each object's name (after the prefix) is the request id and its content is the body.
 * Authenticates with Application Default Credentials.
 */
class GcsRequestFileLoader(private val storage: Storage, private val bucket: String, private val api: String) : RequestFileLoader {
    override fun loadAll(testsetVersion: String): List<RequestFile> {
        val prefix = "testsets/$api/$testsetVersion/"
        return storage
            .list(bucket, Storage.BlobListOption.prefix(prefix))
            .iterateAll()
            .mapNotNull { blob ->
                val id = blob.name.removePrefix(prefix)
                if (id.isEmpty() || id.contains('/')) null else RequestFile(id = id, body = String(blob.getContent()))
            }
    }

    companion object {
        /** A loader over [bucket] for the given planner [api], using Application Default Credentials. */
        fun create(bucket: String, api: String): GcsRequestFileLoader = GcsRequestFileLoader(StorageOptions.getDefaultInstance().service, bucket, api)
    }
}
