package org.opentripplanner.trakpi.testset

import org.opentripplanner.trakpi.common.TestsetVersion

/**
 * Prepares a new testset for [api]/[version]: loads raw requests from [source], deserializes each into the
 * planner's working form via [codec], applies the [transforms] in order (a transform that does not
 * apply returns the request unchanged), serializes each back to its stored form, then persists the result
 * via [store] and returns it. Requests that fail to deserialize or transform are skipped with a warning,
 * so a handful of unparseable requests in the sample don't crash the whole testset build.
 */
class TestsetBuilder<T>(
    private val source: TestsetSource,
    private val codec: RequestCodec<T>,
    private val transforms: List<RequestTransform<T>>,
    private val store: TestsetStore,
    private val targetSize: Int? = null,
) {
    fun prepare(api: String, version: TestsetVersion): Testset {
        println("Loading requests from the source…")
        val raw = source.load()
        println("Loaded ${raw.size} request(s); applying transforms…")
        val prepared = mutableListOf<T>()
        var skipped = 0
        for (request in raw) {
            if (targetSize != null && prepared.size >= targetSize) break
            try {
                val working = codec.deserialize(request)
                val transformed = transforms.fold(working) { current, transform -> transform.apply(current) }
                prepared.add(transformed)
            } catch (e: Exception) {
                skipped++
                println("  skipped request ${request.id} (${e.javaClass.simpleName}: ${e.message?.take(120)})")
                continue
            }
            if (prepared.size % PROGRESS_INTERVAL == 0) println("  transformed ${prepared.size}/${raw.size}")
        }
        if (prepared.size == raw.size || prepared.size == targetSize)
            println("  transformed ${prepared.size}/${raw.size}")
        if (skipped > 0) println("Skipped $skipped unparseable request(s).")
        println("Storing testset $api/$version (${prepared.size} request(s))…")
        return Testset(api, version, prepared.map(codec::serialize)).also(store::store)
    }

    private companion object {
        /** Emit a progress line every this many requests. */
        const val PROGRESS_INTERVAL = 100
    }
}
