package org.opentripplanner.trakpi.storage.gcs

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.opentripplanner.trakpi.common.TestsetVersion
import org.opentripplanner.trakpi.common.mapConcurrently
import org.opentripplanner.trakpi.tester.spi.RequestFile
import org.opentripplanner.trakpi.tester.spi.RequestFileLoader

/**
 * A [RequestFileLoader] that reads a testset's request files from a GCS bucket.
 * Each object's name (after the prefix) is the request id and its content is the body. Up to
 * [concurrency] files are downloaded at once, and the files are returned sorted by id.
 * Authenticates with Application Default Credentials.
 */
class GcsRequestFileLoader(
    private val storage: Storage,
    private val bucket: String,
    private val api: String,
    private val concurrency: Int = DEFAULT_CONCURRENCY,
) : RequestFileLoader {
    override fun loadAll(testsetVersion: TestsetVersion): List<RequestFile> {
        val prefix = "testsets/$api/${testsetVersion.value}/"
        // Downloads complete in no particular order. Sorting by id restores the order GCS lists the objects in.
        return storage
            .list(bucket, Storage.BlobListOption.prefix(prefix), Storage.BlobListOption.fields(Storage.BlobField.NAME))
            .iterateAll()
            .asSequence()
            .map { it.name.removePrefix(prefix) }
            .filter { it.isNotEmpty() && !it.contains('/') }
            .mapConcurrently(concurrency) { id -> RequestFile(id = id, body = String(storage.readAllBytes(BlobId.of(bucket, prefix + id)))) }
            .onEachIndexed { index, _ -> if ((index + 1) % PROGRESS_INTERVAL == 0) println("  downloaded ${index + 1} request(s)") }
            .sortedBy { it.id }
            .toList()
    }

    companion object {
        /** How many files are downloaded at once; enough to hide GCS latency without flooding it. */
        private const val DEFAULT_CONCURRENCY = 32

        /** Emit a progress line every this many downloads. */
        private const val PROGRESS_INTERVAL = 1000

        /** A loader over [bucket] for the given planner [api], using Application Default Credentials. */
        fun create(bucket: String, api: String): GcsRequestFileLoader = GcsRequestFileLoader(StorageOptions.getDefaultInstance().service, bucket, api)
    }
}
