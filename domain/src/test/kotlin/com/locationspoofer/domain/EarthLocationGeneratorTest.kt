package com.locationspoofer.domain

import com.locationspoofer.domain.model.EarthLocationGenerator
import com.locationspoofer.domain.model.RandomizerRegion
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class EarthLocationGeneratorTest {
    @Test
    fun generatedCoordinatesAlwaysStayOnEarth() {
        val generator = EarthLocationGenerator(Random(7))
        repeat(500) {
            assertTrue(generator.next().isValid)
        }
    }

    @Test
    fun generatorProducesDifferentLocations() {
        val generator = EarthLocationGenerator(Random(11))
        val locations = (1..5).map { generator.next() }.toSet()
        assertTrue(locations.size > 1)
    }

    @Test
    fun everyRegionProducesValidCoordinates() {
        val generator = EarthLocationGenerator(Random(19))
        RandomizerRegion.entries.forEach { region ->
            repeat(20) { assertTrue(generator.next(region).isValid) }
        }
    }
}
