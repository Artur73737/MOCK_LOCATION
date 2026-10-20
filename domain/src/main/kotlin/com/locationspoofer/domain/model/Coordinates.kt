package com.locationspoofer.domain.model

import kotlin.math.abs
import java.util.Locale

data class Coordinates(
    val latitude: Double,
    val longitude: Double,
) {
    val isValid: Boolean
        get() = latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            latitude.isFinite() && longitude.isFinite()

    fun distanceTo(other: Coordinates): Double {
        val earthRadiusMeters = 6_371_000.0
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return earthRadiusMeters * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }

    override fun toString(): String = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
}
