package org.opentripplanner.trakpi.testset

/** Loads requests for a new testset. */
fun interface TestsetSource {
    fun load(): List<Request>
}
