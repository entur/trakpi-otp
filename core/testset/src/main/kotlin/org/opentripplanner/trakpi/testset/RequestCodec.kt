package org.opentripplanner.trakpi.testset

/**
 * Converts between a stored [Request] and the planner's in-memory working form [T]. Working in [T]
 * lets a chain of transforms share a single parse: [deserialize] parses once, transforms edit [T],
 * and [serialize] renders once.
 */
interface RequestCodec<T> {
    fun deserialize(request: Request): T

    fun serialize(request: T): Request
}
