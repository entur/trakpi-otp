package org.opentripplanner.trakpi.tester.spi

/**
 * Loads the request files that make up the testset identified by [testsetVersion].
 */
interface RequestFileLoader {
    fun loadAll(testsetVersion: String): List<RequestFile>
}
