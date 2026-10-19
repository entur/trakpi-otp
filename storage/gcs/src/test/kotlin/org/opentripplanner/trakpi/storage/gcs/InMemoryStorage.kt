package org.opentripplanner.trakpi.storage.gcs

import com.google.api.gax.paging.Page
import com.google.cloud.NoCredentials
import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.StorageOptions
import com.google.cloud.storage.testBlob
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap

/**
 * An in-memory fake of the few [Storage] calls the GCS adapters make: `list` (honoring a prefix option),
 * `create`, `readAllBytes` and batch `delete`. Objects are keyed by name within a single bucket. [failCreate]
 * lets a test make an upload fail.
 */
internal class InMemoryStorage(private val failCreate: (String) -> Boolean = { false }) {
    /** The stored objects by name, sorted like a GCS listing. */
    val objects = ConcurrentSkipListMap<String, ByteArray>()

    val storage: Storage =
        Proxy.newProxyInstance(Storage::class.java.classLoader, arrayOf(Storage::class.java)) { _, method, args ->
            when (method.name) {
                "list" -> page(prefix(args[1] as Array<*>))
                "create" -> {
                    val name = (args[0] as BlobInfo).name
                    if (failCreate(name)) throw StorageException(503, "injected failure for $name")
                    objects[name] = args[1] as ByteArray
                    null
                }
                "readAllBytes" -> objects[(args[0] as BlobId).name] ?: throw StorageException(404, "not found")
                "delete" -> (args[0] as Iterable<*>).map { objects.remove((it as BlobId).name) != null }
                "getOptions" -> options
                else -> throw UnsupportedOperationException(method.name)
            }
        } as Storage

    private val options = StorageOptions.newBuilder().setProjectId("test").setCredentials(NoCredentials.getInstance()).build()

    private val blobs = ConcurrentHashMap<String, Blob>()

    /**
     * The prefix among [options], matched against every "directory" of the stored names; null when none matches,
     * since then no stored object is under it. Every adapter lists with a prefix.
     */
    private fun prefix(options: Array<*>): String? =
        objects.keys
            .flatMap { name -> name.indices.filter { name[it] == '/' }.map { name.substring(0, it + 1) } }
            .firstOrNull { Storage.BlobListOption.prefix(it) in options }

    private fun page(prefix: String?): Page<Blob> {
        val values = objects.keys.filter { prefix != null && it.startsWith(prefix) }.map { name -> blobs.getOrPut(name) { testBlob(storage, BlobId.of(BUCKET, name)) } }
        return object : Page<Blob> {
            override fun hasNextPage() = false
            override fun getNextPageToken(): String? = null
            override fun getNextPage(): Page<Blob>? = null
            override fun iterateAll(): Iterable<Blob> = values
            override fun getValues(): Iterable<Blob> = values
        }
    }

    companion object {
        const val BUCKET = "bucket"
    }
}
