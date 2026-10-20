package com.locationspoofer.domain.model

import kotlin.random.Random

class EarthLocationGenerator(private val random: Random = Random.Default) {
    fun next(): Coordinates = Coordinates(
        latitude = random.nextDouble(-90.0, 90.0),
        longitude = random.nextDouble(-180.0, 180.0),
    )
}
