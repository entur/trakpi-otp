package org.opentripplanner.trakpi.otp.drill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class ItineraryDiffTest {
    private fun pattern(vararg legs: String): JsonObject =
        Json.parseToJsonElement("""{"legs":[${legs.joinToString(",")}]}""").jsonObject

    private fun rail(code: String, sj: String, from: String, to: String, start: String, end: String) =
        """{"mode":"rail","aimedStartTime":"2026-09-01T$start:00+02:00","aimedEndTime":"2026-09-01T$end:00+02:00",""" +
            """"fromPlace":{"name":"$from"},"toPlace":{"name":"$to"},"line":{"publicCode":"$code"},"serviceJourney":{"id":"$sj"}}"""

    private val re10 = pattern(rail("RE10", "SJ:1", "Drammen", "Oslo S", "17:31", "18:02"))
    private val re11 = pattern(rail("RE11", "SJ:2", "Drammen", "Oslo S", "17:45", "18:20"))
    private val bus = pattern(rail("31", "SJ:9", "Drammen", "Oslo S", "17:50", "18:40"))

    @Test
    fun `identical lists produce only unchanged lines`() {
        val rows = computeItineraryDiff(listOf(re10, re11), listOf(re10, re11))
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.startsWith("  ") }, "all lines unchanged: $rows")
        assertTrue(rows[0].contains("RE10 from Drammen (17:31) -> Oslo S (18:02)"), rows[0])
    }

    @Test
    fun `an itinerary only in the candidate is marked +`() {
        val rows = computeItineraryDiff(listOf(re10, bus), listOf(re10))
        assertEquals(listOf(" ", "+"), rows.map { it.take(1) })
        assertTrue(rows[1].contains("31 from Drammen"), rows[1])
    }

    @Test
    fun `an itinerary only in the reference is marked -`() {
        val rows = computeItineraryDiff(listOf(re10), listOf(re10, bus))
        assertEquals(listOf(" ", "-"), rows.map { it.take(1) })
    }

    @Test
    fun `a reorder shows the moved itinerary as a minus and a plus, keeping the other in place`() {
        // candidate: re11, re10 ; reference: re10, re11 — a swap. LCS keeps one itinerary in place and
        // moves the other, which appears once as '-' and once as '+' (whichever one LCS chooses to keep).
        val rows = computeItineraryDiff(listOf(re11, re10), listOf(re10, re11))
        val markers = rows.map { it.take(1) }
        assertEquals(1, markers.count { it == " " })
        assertEquals(1, markers.count { it == "-" })
        assertEquals(1, markers.count { it == "+" })
        val movedOut = rows.first { it.startsWith("-") }.drop(1)
        val movedIn = rows.first { it.startsWith("+") }.drop(1)
        assertEquals(movedOut, movedIn, "the same itinerary is removed then re-added at its new position")
    }

    @Test
    fun `duration is derived from aimed times`() {
        val rows = computeItineraryDiff(listOf(re10), listOf(re10))
        assertTrue(rows[0].contains("(31m)"), rows[0])
    }

    @Test
    fun `a foot leg renders as walk`() {
        val walk =
            pattern(
                """{"mode":"foot","aimedStartTime":"2026-09-01T17:31:00+02:00","aimedEndTime":"2026-09-01T17:40:00+02:00","fromPlace":{"name":"A"},"toPlace":{"name":"B"}}"""
            )
        val rows = computeItineraryDiff(listOf(walk), listOf(walk))
        assertTrue(rows[0].contains("walk from A (17:31) -> B (17:40)"), rows[0])
    }

    @Test
    fun `search summary reads endpoints from the first itinerary`() {
        assertEquals("Drammen  ->  Oslo S   (first departs 17:31)", searchSummary(listOf(re10)))
        assertEquals(null, searchSummary(emptyList()))
    }
}
