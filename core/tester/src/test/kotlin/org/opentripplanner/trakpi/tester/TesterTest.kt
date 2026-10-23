package org.opentripplanner.trakpi.tester

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.opentripplanner.trakpi.common.PlannerVersion
import org.opentripplanner.trakpi.common.TestsetVersion
import org.opentripplanner.trakpi.tester.spi.RequestFile
import org.opentripplanner.trakpi.tester.spi.RequestFileLoader
import org.opentripplanner.trakpi.tester.spi.RequestLoader
import org.opentripplanner.trakpi.tester.spi.ResultsWriter
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult
import org.opentripplanner.trakpi.tester.spi.TravelPlanner
import org.opentripplanner.trakpi.tester.spi.TravelPlannerRequest
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse

class TesterTest {
    private val run =
        RunMetadata.create(
            version = PlannerVersion("subject"),
            application = "otp",
            startedAt = Instant.parse("2026-10-19T05:00:00Z"),
            testsetVersion = TestsetVersion("testset-1"),
        )

    private data class Request(val id: String) : TravelPlannerRequest

    private val files = (1..100).map { RequestFile(id = "request-$it", body = "body-$it") }
    private val fileLoader =
        object : RequestFileLoader {
            override fun loadAll(testsetVersion: TestsetVersion) = files
        }
    private val requestLoader =
        object : RequestLoader<Request> {
            override fun load(file: RequestFile) = Request(file.id)
        }

    /** Answers after a short delay, recording how many calls overlapped; fails the requests in [failing]. */
    private class SlowPlanner(private val failing: Set<String> = emptySet()) : TravelPlanner<Request> {
        private val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()

        override fun execute(request: Request): TravelPlannerResponse {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            try {
                Thread.sleep(5)
                if (request.id in failing) error("planner failed")
                return TravelPlannerResponse(raw = request.id, success = true, method = "trip")
            } finally {
                inFlight.decrementAndGet()
            }
        }
    }

    /** Records stored request ids in order, and fails if two stores ever overlap. */
    private class RecordingResultsWriter : ResultsWriter {
        private val storing = AtomicInteger()
        val stored = mutableListOf<String>()

        override fun store(run: RunMetadata, result: TestCaseResult) {
            check(storing.incrementAndGet() == 1) { "stores overlapped" }
            stored += result.requestId
            storing.decrementAndGet()
        }
    }

    private fun tester(planner: SlowPlanner, writer: ResultsWriter, concurrency: Int) =
        Tester(run, fileLoader, requestLoader, planner, kpiCalculators = emptyList(), resultsWriter = writer, concurrency = concurrency)

    @Test
    fun `runs requests one at a time and in order by default`() {
        val planner = SlowPlanner()
        val writer = RecordingResultsWriter()

        Tester(run, fileLoader, requestLoader, planner, kpiCalculators = emptyList(), resultsWriter = writer).run()

        assertEquals(1, planner.maxInFlight.get())
        assertEquals(files.map { it.id }, writer.stored)
    }

    @Test
    fun `keeps up to concurrency requests in flight and stores each result once`() {
        val planner = SlowPlanner()
        val writer = RecordingResultsWriter()

        tester(planner, writer, concurrency = 4).run()

        assertTrue(planner.maxInFlight.get() in 2..4, "max in flight was ${planner.maxInFlight.get()}")
        assertEquals(files.map { it.id }.toSet(), writer.stored.toSet())
        assertEquals(files.size, writer.stored.size)
    }

    @Test
    fun `a failing request is skipped without stopping the run`() {
        val planner = SlowPlanner(failing = setOf("request-7", "request-42"))
        val writer = RecordingResultsWriter()

        tester(planner, writer, concurrency = 4).run()

        assertEquals(files.map { it.id }.toSet() - setOf("request-7", "request-42"), writer.stored.toSet())
    }
}
