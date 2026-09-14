package com.example.targetgps

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Geographic calculation engine for estimating target GPS coordinates
 * using trigonometric camera depression angle, azimuth heading,
 * and WGS-84 spherical projection.
 */
object TargetGeoCalculator {

    /**
     * Equatorial radius of the Earth according to the WGS-84 ellipsoid standard (in meters).
     */
    const val EARTH_RADIUS_METERS: Double = 6378137.0

    /**
     * Result of target position calculation.
     *
     * @property groundDistance Planar horizontal ground distance from observer to target (meters).
     * @property northOffset Distance along the North-South axis in meters (positive = North, negative = South).
     * @property eastOffset Distance along the East-West axis in meters (positive = East, negative = West).
     * @property targetLat Estimated Latitude of target in decimal degrees.
     * @property targetLon Estimated Longitude of target in decimal degrees.
     */
    data class TargetResult(
        val groundDistance: Double,
        val northOffset: Double,
        val eastOffset: Double,
        val targetLat: Double,
        val targetLon: Double
    )

    /**
     * Calculates the estimated target GPS position.
     *
     * Formulas:
     * - Target Distance: d = h / tan(β)
     * - Offsets:
     *     North = d * cos(ψ)
     *     East  = d * sin(ψ)
     * - Earth Radius R = 6378137.0 m
     * - target_lat = current_lat + (North Offset / R) * (180 / π)
     * - target_lon = current_lon + (East Offset / (R * cos(current_lat))) * (180 / π)
     *
     * @param currentLat Observer's current latitude in decimal degrees.
     * @param currentLon Observer's current longitude in decimal degrees.
     * @param heightMeters Camera height above ground level (h) in meters.
     * @param headingDeg Target azimuth heading (ψ) in degrees [0, 360) clockwise from North.
     * @param depressionAngleDeg Camera depression angle (β) in degrees relative to the horizontal plane.
     * @return [TargetResult] containing ground distance, north/east offsets, and target coordinates.
     */
    fun calculate(
        currentLat: Double,
        currentLon: Double,
        heightMeters: Double,
        headingDeg: Double,
        depressionAngleDeg: Double
    ): TargetResult {
        require(heightMeters > 0.0) {
            "Camera height must be greater than 0 meters (received: $heightMeters m)."
        }

        require(depressionAngleDeg > 0.0) {
            "Depression angle must be greater than 0° to intersect the ground plane (received: $depressionAngleDeg°)."
        }

        // When depression angle reaches or exceeds 90°, the line of sight points straight down to observer's nadir
        if (depressionAngleDeg >= 89.9999) {
            return TargetResult(
                groundDistance = 0.0,
                northOffset = 0.0,
                eastOffset = 0.0,
                targetLat = currentLat,
                targetLon = currentLon
            )
        }

        // Convert angles from degrees to radians
        val betaRad = Math.toRadians(depressionAngleDeg)
        val psiRad = Math.toRadians(headingDeg)
        val currentLatRad = Math.toRadians(currentLat)

        // 1. Calculate Planar Ground Distance d = h / tan(β)
        val groundDistance = heightMeters / tan(betaRad)

        // 2. Calculate Cartesian Offsets:
        //    North = d * cos(ψ)
        //    East  = d * sin(ψ)
        val northOffset = groundDistance * cos(psiRad)
        val eastOffset = groundDistance * sin(psiRad)

        // 3. Convert Cartesian Offsets to Spherical Geographic Shifts
        //    ΔLat = (North / R) * (180 / π)
        //    ΔLon = (East / (R * cos(current_lat))) * (180 / π)
        val deltaLat = (northOffset / EARTH_RADIUS_METERS) * (180.0 / Math.PI)
        val cosCurrentLat = cos(currentLatRad).coerceAtLeast(1e-12)
        val deltaLon = (eastOffset / (EARTH_RADIUS_METERS * cosCurrentLat)) * (180.0 / Math.PI)

        val targetLat = currentLat + deltaLat
        val targetLon = currentLon + deltaLon

        return TargetResult(
            groundDistance = groundDistance,
            northOffset = northOffset,
            eastOffset = eastOffset,
            targetLat = targetLat,
            targetLon = targetLon
        )
    }
}
