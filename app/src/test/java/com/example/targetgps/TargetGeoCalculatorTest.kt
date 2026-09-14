package com.example.targetgps

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class TargetGeoCalculatorTest {

    private val epsilon = 1e-5

    @Test
    fun testDueNorthCalculation() {
        val lat = 37.7749
        val lon = -122.4194
        val height = 10.0
        val heading = 0.0 // North
        val depression = 45.0 // tan(45°) = 1.0

        val result = TargetGeoCalculator.calculate(lat, lon, height, heading, depression)

        assertEquals(10.0, result.groundDistance, 0.001)
        assertEquals(10.0, result.northOffset, 0.001)
        assertEquals(0.0, result.eastOffset, 0.001)

        val expectedDeltaLat = (10.0 / TargetGeoCalculator.EARTH_RADIUS_METERS) * (180.0 / Math.PI)
        assertEquals(lat + expectedDeltaLat, result.targetLat, epsilon)
        assertEquals(lon, result.targetLon, epsilon)
    }

    @Test
    fun testDueEastCalculation() {
        val lat = 0.0 // Equator
        val lon = 0.0
        val height = 100.0
        val heading = 90.0 // East
        val depression = 45.0

        val result = TargetGeoCalculator.calculate(lat, lon, height, heading, depression)

        assertEquals(100.0, result.groundDistance, 0.001)
        assertEquals(0.0, result.northOffset, 0.001)
        assertEquals(100.0, result.eastOffset, 0.001)

        val expectedDeltaLon = (100.0 / TargetGeoCalculator.EARTH_RADIUS_METERS) * (180.0 / Math.PI)
        assertEquals(lat, result.targetLat, epsilon)
        assertEquals(lon + expectedDeltaLon, result.targetLon, epsilon)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeDepressionAngleThrows() {
        TargetGeoCalculator.calculate(37.0, -122.0, 1.5, 0.0, -5.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testZeroHeightThrows() {
        TargetGeoCalculator.calculate(37.0, -122.0, 0.0, 0.0, 30.0)
    }
}
