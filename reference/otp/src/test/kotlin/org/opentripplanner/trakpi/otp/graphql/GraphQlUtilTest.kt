package org.opentripplanner.trakpi.otp.graphql

import graphql.language.AstPrinter
import graphql.parser.Parser
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphQlUtilTest {
    private val tripFields = "{ tripPatterns { duration legs { mode } } debugOutput { totalTime } }"

    private fun merged(query: String, roots: Set<String>, required: String): String =
        AstPrinter.printAst(GraphQlUtil.mergeFields(Parser().parseDocument(query), roots, required))

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
}
