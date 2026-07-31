package org.opentripplanner.trakpi.storage.gcs

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.opentripplanner.trakpi.common.TestsetVersion
import org.opentripplanner.trakpi.testset.Request
import org.opentripplanner.trakpi.testset.Testset
import org.opentripplanner.trakpi.testset.TestsetStore

/**
 * A [TestsetStore] backed by a GCS bucket: one object per request at
 * `testsets/<api>/<version>/<requestId>`. Versions are the "directories" one level under
 * `testsets/<api>/`. Authenticates with Application Default Credentials.
 */
class GcsTestsetStore(private val storage: Storage, private val bucket: String) : TestsetStore {
    override fun store(testset: Testset) {
        testset.requests.forEachIndexed { index, request ->
            write(objectName(testset.api, testset.version.value, request), request.body)
            val done = index + 1
            if (done % PROGRESS_INTERVAL == 0 || done == testset.requests.size) println("  uploaded $done/${testset.requests.size}")
        }
    }

    override fun versions(api: String): List<TestsetVersion> {
        val prefix = versionsPrefix(api)
        return storage
            .list(bucket, Storage.BlobListOption.prefix(prefix), Storage.BlobListOption.currentDirectory())
            .iterateAll()
            .map { it.name }
            .filter { it.endsWith("/") && it != prefix }
            .map { TestsetVersion(it.removePrefix(prefix).removeSuffix("/")) }
            .sortedByDescending { it.value }
    }

    private fun write(objectName: String, body: String) {
        val blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectName)).setContentType("application/json").build()
        storage.create(blobInfo, body.encodeToByteArray())
    }

    companion object {
        /** Emit a progress line every this many uploads. */
        private const val PROGRESS_INTERVAL = 100

        /** A store that writes to [bucket] using Application Default Credentials. */
        fun create(bucket: String): GcsTestsetStore = GcsTestsetStore(StorageOptions.getDefaultInstance().service, bucket)

        internal fun objectName(api: String, version: String, requestId: String): String = "testsets/$api/$version/$requestId"

        /** Object-name prefix under which a planner api's testset versions live. */
        internal fun versionsPrefix(api: String): String = "testsets/$api/"

        private fun objectName(api: String, version: String, request: Request): String = objectName(api, version, request.id)
    }
}
