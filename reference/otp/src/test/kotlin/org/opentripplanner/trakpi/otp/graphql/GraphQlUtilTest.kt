package org.opentripplanner.trakpi.otp.graphql

import graphql.language.AstPrinter
import graphql.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphQlUtilTest {
    private val tripFields = "{ tripPatterns { duration legs { mode } } debugOutput { totalTime } }"

    private fun merged(query: String, roots: Set<String>, required: String): String =
        AstPrinter.printAst(GraphQlUtil.mergeFields(Parser().parseDocument(query), roots, required))

    private fun count(haystack: String, needle: String): Int = haystack.split(needle).size - 1

    @Test
    fun `appends the required selection to a named root field, staying valid GraphQL`() {
        val out = merged("{ trip(from: {place: \"A\"}) { tripPatterns { startTime } } }", setOf("trip"), tripFields)
        Parser().parseDocument(out)
        listOf("debugOutput", "totalTime", "duration", "legs", "mode").forEach { assertTrue(it in out, "expected '$it' in:\n$out") }
    }

    @Test
    fun `merges into any of the named roots`() {
        val out = merged("{ stopPlace(id: \"1\") { name } }", setOf("stopPlace", "quay"), "{ estimatedCalls { realtime } }")
        assertTrue("estimatedCalls" in out, out)
    }

    @Test
    fun `leaves fields not named in rootFields untouched`() {
        // stopPlace is not in rootFields, so the trip selection must not be injected
        val out = merged("{ stopPlace(id: \"1\") { name } }", setOf("trip"), tripFields)
        assertFalse("debugOutput" in out, out)
    }

    @Test
    fun `de-aliases a merged root so its response key matches`() {
        val out = merged("{ x: trip(from: {place: \"A\"}) { tripPatterns { startTime } } }", setOf("trip"), tripFields)
        assertTrue("trip" in out, out)
        assertFalse("x:" in out || "x :" in out, "alias should be gone:\n$out")
    }

    @Test
    fun `handles a variable-based query`() {
        val out = merged("query(\$from: Location!) { trip(from: \$from) { tripPatterns { startTime } } }", setOf("trip"), tripFields)
        assertTrue("debugOutput" in out, out)
    }

    @Test
    fun `merges the missing leaves into an existing field instead of adding a conflicting twin`() {
        val out =
            merged("{ quay(id: \"1\") { estimatedCalls(numberOfDepartures: 10) { aimedDepartureTime } } }", setOf("quay"), "{ estimatedCalls { realtime } }")
        Parser().parseDocument(out)
        assertEquals(1, count(out, "estimatedCalls"), "expected exactly one estimatedCalls in:\n$out")
        assertTrue("numberOfDepartures" in out, out) // the original arguments are preserved
        assertTrue("realtime" in out, out)
    }

    @Test
    fun `does not duplicate a field already present`() {
        val out = merged("{ trip(from: {place: \"A\"}) { tripPatterns { startTime } } }", setOf("trip"), tripFields)
        assertEquals(1, count(out, "tripPatterns"), "expected exactly one tripPatterns in:\n$out")
        listOf("startTime", "duration", "legs").forEach { assertTrue(it in out, "expected '$it' in:\n$out") }
    }

    @Test
    fun `refuses to merge into a field hidden under an inline-fragment type condition`() {
        val query = "{ quay(id: \"1\") { ... on Quay { estimatedCalls { aimedDepartureTime } } } }"
        assertFailsWith<IllegalArgumentException> {
            GraphQlUtil.mergeFields(Parser().parseDocument(query), setOf("quay"), "{ estimatedCalls { realtime } }")
        }
    }
}
