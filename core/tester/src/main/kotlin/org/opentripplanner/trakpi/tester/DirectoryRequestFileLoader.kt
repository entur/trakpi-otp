package org.opentripplanner.trakpi.tester

import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import org.opentripplanner.trakpi.common.TestsetVersion
import org.opentripplanner.trakpi.tester.spi.RequestFile
import org.opentripplanner.trakpi.tester.spi.RequestFileLoader

/**
 * A [RequestFileLoader] that reads request files from a local [dir], in filename order. The id of each
 * is its filename without extension. The directory holds one fixed set, so the testset version is ignored.
 */
class DirectoryRequestFileLoader(private val dir: Path) : RequestFileLoader {
    override fun loadAll(testsetVersion: TestsetVersion): List<RequestFile> =
        dir.listDirectoryEntries()
            .filter { it.isRegularFile() }
            .sorted()
            .map { RequestFile(id = it.nameWithoutExtension, body = it.readText()) }
}
