package org.opentripplanner.trakpi.testset

import org.opentripplanner.trakpi.common.TestsetVersion

/**
 * Prepares a new testset for [api]/[version]: loads raw requests from [source], deserializes each into the
 * planner's working form via [codec], applies the [transforms] in order (a transform that does not
 * apply returns the request unchanged), serializes each back to its stored form, then persists the result
 * via [store] and returns it.
 */
class TestsetBuilder<T>(
    private val source: TestsetSource,
    private val codec: RequestCodec<T>,
    private val transforms: List<RequestTransform<T>>,
    private val store: TestsetStore,
) {
    fun prepare(api: String, version: TestsetVersion): Testset {
        println("Loading requests from the source…")
        val raw = source.load()
        println("Loaded ${raw.size} request(s); applying transforms…")
        val prepared =
            raw.mapIndexed { index, request ->
                val working = codec.deserialize(request)
                val transformed = transforms.fold(working) { current, transform -> transform.apply(current) }
                val done = index + 1
                if (done % PROGRESS_INTERVAL == 0 || done == raw.size) println("  transformed $done/${raw.size}")
                transformed
            }
        println("Storing testset $api/$version (${prepared.size} request(s))…")
        return Testset(api, version, prepared.map(codec::serialize)).also(store::store)
    }

    private companion object {
        /** Emit a progress line every this many requests. */
        const val PROGRESS_INTERVAL = 100
    }
}
