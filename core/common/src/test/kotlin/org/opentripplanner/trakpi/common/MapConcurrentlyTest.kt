package org.opentripplanner.trakpi.common

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MapConcurrentlyTest {
    @Test
    fun `maps every element and drops null results`() {
        val results = (1..100).asSequence().mapConcurrently(maxInFlight = 8) { if (it % 10 == 0) null else it * 2 }.toSet()

        assertEquals((1..100).filter { it % 10 != 0 }.map { it * 2 }.toSet(), results)
    }

    @Test
    fun `never runs more than maxInFlight transforms at once`() {
        val running = AtomicInteger()
        val maxRunning = AtomicInteger()

        (1..200).asSequence()
            .mapConcurrently(maxInFlight = 4) {
                maxRunning.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                Thread.sleep(2)
                running.decrementAndGet()
                it
            }
            .count()

        assertTrue(maxRunning.get() in 2..4, "max concurrent transforms was ${maxRunning.get()}")
    }

    @Test
    fun `pulls only as far ahead of the consumer as maxInFlight allows`() {
        val pulled = AtomicInteger()
        val source = generateSequence(1) { it + 1 }.onEach { pulled.incrementAndGet() }

        source.mapConcurrently(maxInFlight = 4) { it }.take(10).toList()

        assertTrue(pulled.get() <= 10 + 4, "pulled ${pulled.get()} elements to yield 10")
    }

    @Test
    fun `rethrows a transform's exception to the consumer`() {
        val e = assertFailsWith<IllegalStateException> {
            (1..10).asSequence().mapConcurrently(maxInFlight = 3) { if (it == 5) error("read failed") else it }.toList()
        }
        assertEquals("read failed", e.message)
    }
}
