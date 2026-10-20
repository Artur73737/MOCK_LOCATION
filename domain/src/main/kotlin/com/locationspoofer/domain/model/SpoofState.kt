package com.locationspoofer.domain.model

data class SpoofState(
    val active: Boolean = false,
    val coordinates: Coordinates = Coordinates(41.9028, 12.4964),
    val lastError: String? = null,
)
