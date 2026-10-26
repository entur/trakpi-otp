package org.opentripplanner.trakpi.tester

import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import org.opentripplanner.trakpi.common.TestsetVersion
import org.opentripplanner.trakpi.tester.spi.RequestFileLoader
import org.opentripplanner.trakpi.tester.spi.TravelPlannerRequest

/**
 * A [RequestFileLoader] that reads request files from a local [dir], in filename order. The id of each
 * is its filename without extension. The directory holds one fixed set, so the testset version is ignored.
 */
class DirectoryRequestFileLoader(private val dir: Path) : RequestFileLoader {
    override fun loadAll(testsetVersion: TestsetVersion): List<TravelPlannerRequest> =
        dir.listDirectoryEntries()
            .filter { it.isRegularFile() }
            .sorted()
            .map { TravelPlannerRequest(id = it.nameWithoutExtension, raw = it.readText()) }
}
