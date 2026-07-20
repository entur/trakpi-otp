package org.opentripplanner.trakpi.testset

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
    fun prepare(api: String, version: String): Testset {
        val prepared =
            source.load().map { request ->
                val working = codec.deserialize(request)
                transforms.fold(working) { current, transform -> transform.apply(current) }
            }
        return Testset(api, version, prepared.map(codec::serialize)).also(store::store)
    }
}
