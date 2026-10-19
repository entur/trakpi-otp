package org.opentripplanner.trakpi.storage.bigquery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BigQueryTestsetSourceTest {
    @Test
    fun `renders the sample percent as a plain decimal literal`() {
        assertEquals("1", BigQueryTestsetSource.samplePercentLiteral(1.0))
        assertEquals("5", BigQueryTestsetSource.samplePercentLiteral(5.0))
        assertEquals("0.5", BigQueryTestsetSource.samplePercentLiteral(0.5))
        assertEquals("0.00001", BigQueryTestsetSource.samplePercentLiteral(0.00001))
        assertEquals("100", BigQueryTestsetSource.samplePercentLiteral(100.0))
    }

    @Test
    fun `rejects a sample percent that is not above 0 and at most 100`() {
        for (percent in listOf(0.0, -1.0, 100.5, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { BigQueryTestsetSource.samplePercentLiteral(percent) }
        }
    }

    @Test
    fun `samples the given percent of the table`() {
        val query = BigQueryTestsetSource.query("project.dataset.table", 5.0)

        assertTrue("FROM `project.dataset.table` TABLESAMPLE SYSTEM (5 PERCENT)" in query, query)
    }

    @Test
    fun `keeps one request per id before ranking each hour`() {
        val query = BigQueryTestsetSource.query("project.dataset.table", 1.0)

        val dedupe = query.indexOf("QUALIFY ROW_NUMBER() OVER (PARTITION BY JSON_VALUE(attributes[\"X-Big-Daddy-External-Correlation-Id\"])")
        val rank = query.indexOf("ROW_NUMBER() OVER (PARTITION BY hour ORDER BY RAND()) AS rank")
        assertTrue(dedupe in 0 until rank, query)
        assertTrue(query.trimEnd().endsWith("ORDER BY rank, hour"), query)
    }
}
