package com.locationspoofer.domain

import com.locationspoofer.domain.model.Coordinates
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoordinatesTest {
    @Test
    fun validCoordinatesAreAccepted() {
        assertTrue(Coordinates(41.9028, 12.4964).isValid)
        assertTrue(Coordinates(-90.0, 180.0).isValid)
    }

    @Test
    fun invalidCoordinatesAreRejected() {
        assertFalse(Coordinates(91.0, 0.0).isValid)
        assertFalse(Coordinates(0.0, 181.0).isValid)
    }

    @Test
    fun distanceIsZeroForSamePoint() {
        assertTrue(Coordinates(0.0, 0.0).distanceTo(Coordinates(0.0, 0.0)) < 0.01)
    }
}
