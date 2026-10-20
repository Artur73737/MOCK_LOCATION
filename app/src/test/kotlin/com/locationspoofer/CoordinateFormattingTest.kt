package com.locationspoofer

import kotlin.test.Test
import kotlin.test.assertEquals

class CoordinateFormattingTest {
    @Test
    fun coordinatesKeepSixDecimalPlaces() {
        assertEquals("41.902800", String.format(java.util.Locale.US, "%.6f", 41.9028))
    }
}
