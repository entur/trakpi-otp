package org.opentripplanner.trakpi.testset

/**
 * A transformation applied to a request while preparing a testset, e.g. obfuscating
 * coordinates or ensuring the fields trakpi's KPIs read are present.
 * A transform that should not apply must leave the [request] unchanged.
 */
fun interface RequestTransform<T> {
    fun apply(request: T): T
}
