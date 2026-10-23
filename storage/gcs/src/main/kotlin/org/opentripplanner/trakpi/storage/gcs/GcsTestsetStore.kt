package org.opentripplanner.trakpi.storage.gcs

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.opentripplanner.trakpi.common.TestsetVersion
import org.opentripplanner.trakpi.common.mapConcurrently
import org.opentripplanner.trakpi.testset.Request
import org.opentripplanner.trakpi.testset.Testset
import org.opentripplanner.trakpi.testset.TestsetStore

/**
 * A [TestsetStore] backed by a GCS bucket: one object per request at
 * `testsets/<api>/<version>/<requestId>`. Versions are the "directories" one level under
 * `testsets/<api>/`. Authenticates with Application Default Credentials.
 *
 * GCS can't replace a version's objects atomically, so [store] deletes the old ones before uploading the
 * new ones, and deletes what it uploaded if the upload fails. A reader listing the version during the
 * upload sees a partial testset, but never one that mixes old and new requests.
 *
 * Uploads and deletes mostly wait on GCS, so up to [concurrency] of them run at once.
 */
class GcsTestsetStore(
    private val storage: Storage,
    private val bucket: String,
    private val concurrency: Int = DEFAULT_CONCURRENCY,
) : TestsetStore {
    override fun store(testset: Testset) {
        val prefix = versionPrefix(testset.api, testset.version.value)
        val deleted = deleteAll(prefix)
        if (deleted > 0) println("  deleted $deleted request(s) of the testset being replaced")
        try {
            // A failed upload is rethrown only once the uploads still in flight have finished, so the cleanup
            // below can't race them.
            testset.requests
                .asSequence()
                .mapConcurrently(concurrency) { request -> write(objectName(testset.api, testset.version.value, request), request.body) }
                .forEachIndexed { index, _ ->
                    val done = index + 1
                    if (done % PROGRESS_INTERVAL == 0 || done == testset.requests.size) println("  uploaded $done/${testset.requests.size}")
                }
        } catch (e: Exception) {
            println("  upload failed; deleting the partial testset")
            runCatching { deleteAll(prefix) }.onFailure(e::addSuppressed)
            throw e
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

    /** Deletes every object under [prefix] and returns how many there were. */
    private fun deleteAll(prefix: String): Int {
        val blobIds = storage.list(bucket, Storage.BlobListOption.prefix(prefix)).iterateAll().map { it.blobId }
        blobIds.chunked(DELETE_BATCH_SIZE).asSequence().mapConcurrently(concurrency) { storage.delete(it) }.count()
        return blobIds.size
    }

    private fun write(objectName: String, body: String) {
        val blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectName)).setContentType("application/json").build()
        storage.create(blobInfo, body.encodeToByteArray())
    }

    companion object {
        /** Emit a progress line every this many uploads. */
        private const val PROGRESS_INTERVAL = 1000

        /** How many uploads, or batches of deletes, run at once; enough to hide GCS latency without flooding it. */
        private const val DEFAULT_CONCURRENCY = 32

        /** The most deletes GCS accepts in one batch request. */
        private const val DELETE_BATCH_SIZE = 100

        /** A store that writes to [bucket] using Application Default Credentials. */
        fun create(bucket: String): GcsTestsetStore = GcsTestsetStore(StorageOptions.getDefaultInstance().service, bucket)

        internal fun objectName(api: String, version: String, requestId: String): String = versionPrefix(api, version) + requestId

        /** Object-name prefix under which a testset version's requests live. */
        internal fun versionPrefix(api: String, version: String): String = versionsPrefix(api) + "$version/"

        /** Object-name prefix under which a planner api's testset versions live. */
        internal fun versionsPrefix(api: String): String = "testsets/$api/"

        private fun objectName(api: String, version: String, request: Request): String = objectName(api, version, request.id)
    }
}
