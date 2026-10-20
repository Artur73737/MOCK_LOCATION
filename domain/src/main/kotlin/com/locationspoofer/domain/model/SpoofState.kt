package com.locationspoofer.domain.model

data class SpoofState(
    val active: Boolean = false,
    val coordinates: Coordinates = Coordinates(41.9028, 12.4964),
    val randomizeEarth: Boolean = false,
    val randomizeIntervalMillis: Long = 60_000L,
    val randomizerRegion: RandomizerRegion = RandomizerRegion.WORLD,
    val lastError: String? = null,
)
