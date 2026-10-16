package org.opentripplanner.trakpi.tester

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.opentripplanner.trakpi.common.PlannerVersion
import org.opentripplanner.trakpi.common.TestsetVersion
import org.opentripplanner.trakpi.tester.spi.ResponsePair
import org.opentripplanner.trakpi.tester.spi.ResultsReader
import org.opentripplanner.trakpi.tester.spi.ResultsWriter
import org.opentripplanner.trakpi.tester.spi.RunMetadata
import org.opentripplanner.trakpi.tester.spi.TestCaseResult
import org.opentripplanner.trakpi.tester.spi.TravelPlannerResponse
import org.opentripplanner.trakpi.tester.spi.kpi.ComparativeKPICalculator
import org.opentripplanner.trakpi.tester.spi.kpi.Kpi

class ComparatorTest {
    private val testset = TestsetVersion("testset-1")
    private val subject = PlannerVersion("subject")
    private val reference = PlannerVersion("reference")
    private val run =
        RunMetadata.create(
            version = subject,
            application = "otp",
            startedAt = Instant.parse("2026-10-16T05:00:00Z"),
            testsetVersion = testset,
            referenceVersion = reference,
            runId = "run-1",
        )

    private class MapResultsReader(private val byVersion: Map<PlannerVersion, Map<String, String>>) : ResultsReader {
        override fun responsePairs(subject: PlannerVersion, reference: PlannerVersion, testsetVersion: TestsetVersion) =
            byVersion[subject].orEmpty().keys.asSequence().map { requestId ->
                ResponsePair(requestId, response(subject, testsetVersion, requestId)!!, response(reference, testsetVersion, requestId))
            }

        override fun response(version: PlannerVersion, testsetVersion: TestsetVersion, requestId: String) =
            byVersion[version]?.get(requestId)?.let { TravelPlannerResponse(raw = it, success = true, method = "trip") }
    }

    private class RecordingResultsWriter : ResultsWriter {
        val stored = HashMap<String, List<Kpi>>()

        override fun store(run: RunMetadata, result: TestCaseResult) {
            stored[result.requestId] = result.kpis
        }
    }

    /** 1.0 when both raw responses are equal, otherwise 0.0. */
    private val sameRaw =
        object : ComparativeKPICalculator {
            override fun calculate(subject: TravelPlannerResponse, reference: TravelPlannerResponse) =
                Kpi("sameRaw", if (subject.raw == reference.raw) 1.0 else 0.0)
        }

    @Test
    fun `scores every subject response that has a reference response`() {
        val ids = (1..10).map { "request-$it" }
        val reader =
            MapResultsReader(
                mapOf(
                    subject to ids.associateWith { if (it == "request-7") "changed" else "same" },
                    // request-10 has no reference response.
                    reference to ids.dropLast(1).associateWith { "same" },
                )
            )
        val writer = RecordingResultsWriter()

        Comparator(run, reference, reader, listOf(sameRaw), writer).run()

        assertEquals(9, writer.stored.size)
        assertEquals(listOf(Kpi("sameRaw", 0.0)), writer.stored["request-7"])
        assertEquals(listOf(Kpi("sameRaw", 1.0)), writer.stored["request-8"])
        assertEquals(null, writer.stored["request-10"])
    }

    @Test
    fun `a calculator failure fails the comparison`() {
        val reader = MapResultsReader(mapOf(subject to mapOf("request-1" to "a"), reference to mapOf("request-1" to "a")))
        val failing =
            object : ComparativeKPICalculator {
                override fun calculate(subject: TravelPlannerResponse, reference: TravelPlannerResponse): Kpi? =
                    error("calculator failed")
            }

        val e = assertFailsWith<IllegalStateException> {
            Comparator(run, reference, reader, listOf(failing), RecordingResultsWriter()).run()
        }
        assertEquals("calculator failed", e.message)
    }
}
