package org.opentripplanner.trakpi.tester.spi

import org.opentripplanner.trakpi.common.TestsetVersion

/**
 * Loads the request files that make up the testset identified by [testsetVersion].
 */
interface RequestFileLoader {
    fun loadAll(testsetVersion: TestsetVersion): List<RequestFile>
}
