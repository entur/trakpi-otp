package org.opentripplanner.trakpi.otp.graphql

import graphql.language.AstPrinter
import graphql.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphQlUtilInlineFragmentsTest {
    private fun inlined(query: String): String = AstPrinter.printAst(GraphQlUtil.inlineFragments(Parser().parseDocument(query)))

    @Test
    fun `inlines a spread and drops the fragment definition`() {
        val out = inlined("{ quay(id: \"1\") { ...q } } fragment q on Quay { estimatedCalls { realtime } }")
        Parser().parseDocument(out)
        assertTrue("estimatedCalls" in out && "realtime" in out, out)
        assertFalse("...q" in out || "fragment q" in out, "spread and definition should be gone:\n$out")
    }

    @Test
    fun `inlines a root field reached through a spread`() {
        val out = inlined("query { ...roots } fragment roots on Query { trip(from: {place: \"A\"}) { tripPatterns { startTime } } }")
        assertTrue("trip" in out && "tripPatterns" in out, out)
        assertFalse("...roots" in out || "fragment roots" in out, out)
    }

    @Test
    fun `resolves nested spreads`() {
        val out = inlined("{ quay(id: \"1\") { ...a } } fragment a on Quay { ...b } fragment b on Quay { estimatedCalls { realtime } }")
        assertTrue("estimatedCalls" in out, out)
        assertFalse("..." in out || "fragment" in out, "no spreads or definitions should remain:\n$out")
    }

    @Test
    fun `keeps inline fragments while inlining named ones`() {
        val out = inlined("{ quay(id: \"1\") { ...q ... on Quay { name } } } fragment q on Quay { id }")
        assertTrue("on Quay" in out, "inline fragment should be preserved:\n$out")
        assertFalse("...q" in out || "fragment q" in out, out)
    }

    @Test
    fun `leaves a fragment-free document unchanged`() {
        val query = "{ trip(from: {place: \"A\"}) { tripPatterns { startTime } } }"
        assertEquals(AstPrinter.printAst(Parser().parseDocument(query)), inlined(query))
    }

    @Test
    fun `refuses a spread carrying directives`() {
        val query = "query(\$b: Boolean!) { quay(id: \"1\") { ...q @include(if: \$b) } } fragment q on Quay { estimatedCalls { realtime } }"
        assertFailsWith<IllegalArgumentException> { GraphQlUtil.inlineFragments(Parser().parseDocument(query)) }
    }

    @Test
    fun `refuses an unknown fragment`() {
        val query = "{ quay(id: \"1\") { ...missing } } fragment other on Quay { name }"
        assertFailsWith<IllegalStateException> { GraphQlUtil.inlineFragments(Parser().parseDocument(query)) }
    }
}
