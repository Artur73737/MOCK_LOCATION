package com.locationspoofer.domain.model

import kotlin.random.Random

/** Chooses from an offline catalogue of inland points, avoiding ocean samples. */
class EarthLocationGenerator(private val random: Random = Random.Default) {
    fun next(region: RandomizerRegion = RandomizerRegion.WORLD): Coordinates {
        val point = pointsByRegion.getValue(region).random(random)
        val jitter = 0.02
        return Coordinates(
            latitude = (point.latitude + random.nextDouble(-jitter, jitter)).coerceIn(-90.0, 90.0),
            longitude = (point.longitude + random.nextDouble(-jitter, jitter)).coerceIn(-180.0, 180.0),
        )
    }

    private companion object {
        private val europe = listOf(
            p(48.8566, 2.3522), p(52.5200, 13.4050), p(50.0755, 14.4378), p(48.2082, 16.3738),
            p(47.4979, 19.0402), p(52.2297, 21.0122), p(45.4642, 9.1900), p(41.9028, 12.4964),
            p(40.4168, -3.7038), p(46.2044, 6.1432), p(59.3293, 18.0686), p(60.1699, 24.9384),
            p(55.6761, 12.5683), p(53.3498, -6.2603), p(51.5074, -0.1278), p(42.6977, 23.3219),
            p(44.4268, 26.1025), p(37.9838, 23.7275), p(43.8563, 18.4131), p(44.7866, 20.4489),
            p(50.4501, 30.5234), p(55.7558, 37.6173), p(64.1466, -21.9426),
        )

        private val asia = listOf(
            p(43.2389, 76.8897), p(39.9334, 32.8597), p(35.6892, 51.3890), p(34.5553, 69.2075),
            p(24.7136, 46.6753), p(25.2048, 55.2708), p(35.6762, 139.6503), p(37.5665, 126.9780),
            p(39.9042, 116.4074), p(31.2304, 121.4737), p(23.1291, 113.2644), p(28.6139, 77.2090),
            p(19.0760, 72.8777), p(13.7563, 100.5018), p(21.0278, 105.8342), p(14.5995, 120.9842),
            p(3.1390, 101.6869), p(1.3521, 103.8198), p(27.7172, 85.3240), p(47.8864, 106.9057),
            p(30.0444, 31.2357), p(39.0392, 125.7625),
        )

        private val africa = listOf(
            p(30.0444, 31.2357), p(31.6295, -7.9811), p(36.7538, 3.0588), p(36.8065, 10.1815),
            p(12.3714, -1.5197), p(13.5127, 2.1125), p(12.6392, -8.0029), p(9.0765, 7.3986),
            p(5.6037, -0.1870), p(0.3476, 32.5825), p(-1.2921, 36.8219), p(-1.9441, 30.0619),
            p(-6.7924, 39.2083), p(-15.3875, 28.3228), p(-17.8252, 31.0335), p(-24.6282, 25.9231),
            p(-25.7479, 28.2293), p(-30.5595, 22.9375), p(-26.2041, 28.0473),
        )

        private val americas = listOf(
            p(39.7392, -104.9903), p(41.8781, -87.6298), p(32.7767, -96.7970), p(39.0997, -94.5786),
            p(36.1627, -86.7816), p(29.7604, -95.3698), p(51.0447, -114.0719), p(53.5461, -113.4938),
            p(45.4215, -75.6972), p(43.6532, -79.3832), p(49.2827, -123.1207), p(19.4326, -99.1332),
            p(20.6597, -103.3496), p(14.6349, -90.5069), p(9.9281, -84.0907), p(4.7110, -74.0721),
            p(0.1807, -78.4678), p(-16.4897, -68.1193), p(-15.7942, -47.8822), p(-23.5505, -46.6333),
            p(-25.2637, -57.5759), p(-34.6037, -58.3816), p(-33.4489, -70.6693), p(10.4806, -66.9036),
        )

        private val oceania = listOf(
            p(-35.2809, 149.1300), p(-33.8688, 151.2093), p(-37.8136, 144.9631), p(-27.4698, 153.0251),
            p(-31.9505, 115.8605), p(-34.9285, 138.6007), p(-12.4634, 130.8456), p(-23.6980, 133.8807),
            p(-42.8821, 147.3272), p(-36.8509, 174.7645), p(-41.2866, 174.7756), p(-43.5321, 172.6362),
            p(-17.7134, 178.0650), p(-9.4438, 147.1803),
        )

        private val pointsByRegion = mapOf(
            RandomizerRegion.WORLD to (europe + asia + africa + americas + oceania),
            RandomizerRegion.EUROPE to europe,
            RandomizerRegion.ASIA to asia,
            RandomizerRegion.AFRICA to africa,
            RandomizerRegion.AMERICAS to americas,
            RandomizerRegion.OCEANIA to oceania,
        )

        private fun p(latitude: Double, longitude: Double) = Coordinates(latitude, longitude)
    }
}
