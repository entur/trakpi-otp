package org.opentripplanner.trakpi.storage.gcs

import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors

/**
 * Lazily maps each element with [transform], running up to [maxInFlight] transforms at once on virtual threads, and
 * yields the non-null results as they complete, in no particular order. Meant for transforms that mostly wait on
 * storage: overlapping them hides the latency. Elements are pulled from the source only as slots free up, so at
 * most [maxInFlight] elements are being transformed or waiting to be consumed at any time. A transform's exception
 * is rethrown to the consumer when its result would have been yielded.
 */
internal fun <T, R : Any> Sequence<T>.mapConcurrently(maxInFlight: Int, transform: (T) -> R?): Sequence<R> {
    require(maxInFlight >= 1) { "maxInFlight must be at least 1, was $maxInFlight" }
    val source = this
    return sequence {
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val completed = ExecutorCompletionService<R?>(executor)
            var inFlight = 0
            suspend fun SequenceScope<R>.yieldNextCompleted() {
                val result =
                    try {
                        completed.take().get()
                    } catch (e: ExecutionException) {
                        throw e.cause ?: e
                    }
                inFlight--
                if (result != null) yield(result)
            }
            for (element in source) {
                if (inFlight == maxInFlight) yieldNextCompleted()
                completed.submit { transform(element) }
                inFlight++
            }
            while (inFlight > 0) yieldNextCompleted()
        }
    }
}
