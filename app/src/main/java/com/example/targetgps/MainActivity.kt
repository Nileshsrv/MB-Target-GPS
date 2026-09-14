package com.example.targetgps

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.targetgps.databinding.ActivityMainBinding
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Main Activity integrating CameraX live feed, reticle HUD overlay,
 * sensor orientation processing, FusedLocationProvider GPS fixes,
 * and geographic target estimation calculations with CSV logging.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorHelper: SensorHelper
    private lateinit var locationHelper: LocationHelper
    private lateinit var csvLogger: CsvLogger
    private lateinit var cameraExecutor: ExecutorService

    // Active Sensor Telemetry State
    private var currentHeading: Double = 0.0
    private var currentDepression: Double = 0.0
    private var currentPitch: Double = 0.0
    private var currentRoll: Double = 0.0

    // Active GPS Telemetry State
    private var currentLat: Double? = null
    private var currentLon: Double? = null
    private var currentAccuracy: Float = 0.0f

    // Last Calculated Target State
    private var lastTargetResult: TargetGeoCalculator.TargetResult? = null
    private var lastCalculatedHeight: Double? = null

    // Permission Launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (cameraGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required for targeting.", Toast.LENGTH_LONG).show()
        }

        if (fineLocationGranted || coarseLocationGranted) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Location permission is required for GPS estimation.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        sensorHelper = SensorHelper(this)
        locationHelper = LocationHelper(this)
        csvLogger = CsvLogger(this)

        setupListeners()
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            startCamera()
            startLocationUpdates()
        }
    }

    private fun setupListeners() {
        binding.btnCalculate.setOnClickListener {
            performTargetCalculation()
        }

        binding.btnSaveData.setOnClickListener {
            saveTestData()
        }
    }

    private fun startLocationUpdates() {
        locationHelper.start { locationData ->
            currentLat = locationData.latitude
            currentLon = locationData.longitude
            currentAccuracy = locationData.accuracy

            runOnUiThread {
                binding.tvCurrentLocation.text = String.format(
                    Locale.US,
                    "Current Lat/Lon: %.6f, %.6f",
                    locationData.latitude,
                    locationData.longitude
                )
                binding.tvGpsAccuracy.text = String.format(
                    Locale.US,
                    "GPS Accuracy: ±%.1f m",
                    locationData.accuracy
                )
            }
        }
    }

    private fun startSensors() {
        sensorHelper.start { data ->
            currentHeading = data.headingDeg
            currentDepression = data.depressionAngleDeg
            currentPitch = data.pitchDeg
            currentRoll = data.rollDeg

            runOnUiThread {
                binding.tvHeading.text = String.format(
                    Locale.US,
                    "Heading (ψ): %.2f°",
                    data.headingDeg
                )
                binding.tvPitchRoll.text = String.format(
                    Locale.US,
                    "Pitch: %.2f° | Roll: %.2f°",
                    data.pitchDeg,
                    data.rollDeg
                )
                binding.tvDepressionAngle.text = String.format(
                    Locale.US,
                    "Depression Angle (β): %.2f°",
                    data.depressionAngleDeg
                )
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview
                )
            } catch (exc: Exception) {
                Log.e(TAG, "CameraX initialization failed", exc)
                Toast.makeText(this, "Failed to start camera preview: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun parseCameraHeight(): Double? {
        val inputStr = binding.etCameraHeight.text.toString().trim()
        val height = inputStr.toDoubleOrNull()
        if (height == null || height <= 0.0) {
            Toast.makeText(this, "Please enter a valid camera height > 0 m.", Toast.LENGTH_SHORT).show()
            return null
        }
        return height
    }

    private fun performTargetCalculation(): Boolean {
        val lat = currentLat
        val lon = currentLon
        if (lat == null || lon == null) {
            Toast.makeText(this, "Awaiting GPS fix before calculating target.", Toast.LENGTH_SHORT).show()
            return false
        }

        val height = parseCameraHeight() ?: return false

        if (currentDepression <= 0.0) {
            Toast.makeText(
                this,
                "Aim reticle at a ground target below the horizon (Depression angle must be > 0°).",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        try {
            val result = TargetGeoCalculator.calculate(
                currentLat = lat,
                currentLon = lon,
                heightMeters = height,
                headingDeg = currentHeading,
                depressionAngleDeg = currentDepression
            )

            lastTargetResult = result
            lastCalculatedHeight = height

            // Update UI with calculated target metrics
            binding.tvGroundDistance.text = String.format(
                Locale.US,
                "Ground Distance (d): %.2f m",
                result.groundDistance
            )
            binding.tvOffsets.text = String.format(
                Locale.US,
                "Offsets: North: %+.2f m | East: %+.2f m",
                result.northOffset,
                result.eastOffset
            )
            binding.tvTargetLocation.text = String.format(
                Locale.US,
                "Estimated Target: %.7f, %.7f",
                result.targetLat,
                result.targetLon
            )

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Target calculation error", e)
            Toast.makeText(this, "Calculation error: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    private fun saveTestData() {
        // If calculation hasn't been performed yet, execute it now
        if (lastTargetResult == null) {
            val success = performTargetCalculation()
            if (!success) return
        }

        val lat = currentLat ?: return
        val lon = currentLon ?: return
        val result = lastTargetResult ?: return
        val height = lastCalculatedHeight ?: return

        val logged = csvLogger.logTest(
            currentLat = lat,
            currentLon = lon,
            accuracy = currentAccuracy,
            heading = currentHeading,
            pitch = currentPitch,
            roll = currentRoll,
            depressionAngle = currentDepression,
            height = height,
            groundDistance = result.groundDistance,
            northOffset = result.northOffset,
            eastOffset = result.eastOffset,
            targetLat = result.targetLat,
            targetLon = result.targetLon
        )

        if (logged) {
            val path = csvLogger.getLogFile().absolutePath
            Toast.makeText(this, "Test data logged!\nSaved to: $path", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Error logging test data to CSV.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        startSensors()
        if (locationHelper.hasLocationPermission()) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorHelper.stop()
        locationHelper.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
