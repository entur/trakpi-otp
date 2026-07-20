package org.opentripplanner.trakpi.testset

/** A single request in a [Testset]: its [id] and its serialized [body]. */
data class Request(val id: String, val body: String)
