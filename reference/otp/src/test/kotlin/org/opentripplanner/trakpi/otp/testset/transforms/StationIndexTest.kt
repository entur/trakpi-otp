package org.opentripplanner.trakpi.otp.testset.transforms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StationIndexTest {
    @Test
    fun `snaps to the nearest station`() {
        val index = StationIndex(listOf(Coordinate(60.0, 10.0), Coordinate(59.0, 11.0), Coordinate(70.0, 20.0)))
        assertEquals(Coordinate(60.0, 10.0), index.snap(Coordinate(59.95, 10.1)))
    }

    @Test
    fun `requires at least one station`() {
        assertFailsWith<IllegalArgumentException> { StationIndex(emptyList()) }
    }
}
