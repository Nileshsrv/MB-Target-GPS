package com.example.targetgps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Helper class wrapping Google Play Services FusedLocationProviderClient
 * to stream high-accuracy GPS fixes.
 */
class LocationHelper(private val context: Context) {

    data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val altitude: Double = 0.0
    )

    fun interface LocationUpdateListener {
        fun onLocationUpdated(locationData: LocationData)
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var locationCallback: LocationCallback? = null
    private var listener: LocationUpdateListener? = null
    private var isUpdating = false

    /**
     * Checks if location permissions are granted.
     */
    fun hasLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation || coarseLocation
    }

    /**
     * Starts high-accuracy GPS location streaming.
     */
    fun start(updateListener: LocationUpdateListener): Boolean {
        if (!hasLocationPermission()) {
            return false
        }

        this.listener = updateListener

        // Attempt to deliver last known location immediately
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    listener?.onLocationUpdated(
                        LocationData(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracy = location.accuracy,
                            altitude = location.altitude
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission check caught above
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        ).apply {
            setMinUpdateIntervalMillis(500L)
            setMinUpdateDistanceMeters(0.5f)
            setWaitForAccurateLocation(true)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (loc in result.locations) {
                    loc?.let {
                        listener?.onLocationUpdated(
                            LocationData(
                                latitude = it.latitude,
                                longitude = it.longitude,
                                accuracy = it.accuracy,
                                altitude = it.altitude
                            )
                        )
                    }
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback as LocationCallback,
                Looper.getMainLooper()
            )
            isUpdating = true
            return true
        } catch (_: SecurityException) {
            return false
        }
    }

    /**
     * Stops location updates to conserve device power.
     */
    fun stop() {
        if (isUpdating && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback as LocationCallback)
            locationCallback = null
            listener = null
            isUpdating = false
        }
    }
}
