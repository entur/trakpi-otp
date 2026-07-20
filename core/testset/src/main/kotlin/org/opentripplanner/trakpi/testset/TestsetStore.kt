package org.opentripplanner.trakpi.testset

/** Persists prepared testsets and lists their versions, keyed by [Testset.api] and [Testset.version]. */
interface TestsetStore {
    fun store(testset: Testset)

    /** The versions available for [api] */
    fun versions(api: String): List<String>
}
