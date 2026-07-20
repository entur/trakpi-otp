package org.opentripplanner.trakpi.storage.file

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText
import org.opentripplanner.trakpi.testset.Testset
import org.opentripplanner.trakpi.testset.TestsetStore

/**
 * A [TestsetStore] backed by the local filesystem: one file per request under
 * `<root>/<api>/<version>/<requestId>`.
 */
class FileTestsetStore(private val root: Path) : TestsetStore {
    override fun store(testset: Testset) {
        val dir = root.resolve(testset.api).resolve(testset.version)
        dir.createDirectories()
        testset.requests.forEach { dir.resolve(it.id).writeText(it.body) }
    }

    override fun versions(api: String): List<String> {
        val dir = root.resolve(api)
        if (!dir.exists()) return emptyList()
        return dir.listDirectoryEntries().filter { it.isDirectory() }.map { it.name }.sortedDescending()
    }
}
