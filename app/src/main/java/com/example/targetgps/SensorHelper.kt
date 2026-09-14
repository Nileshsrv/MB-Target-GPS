package com.example.targetgps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Helper class to monitor device rotation and compute:
 * - Device orientation angles: Azimuth, Pitch, Roll.
 * - Camera Line-of-Sight Heading (ψ) in degrees [0, 360) clockwise from North.
 * - Camera Depression Angle (β) in degrees relative to the horizontal plane.
 */
class SensorHelper(context: Context) : SensorEventListener {

    data class SensorData(
        val headingDeg: Double,
        val depressionAngleDeg: Double,
        val azimuthDeg: Double,
        val pitchDeg: Double,
        val rollDeg: Double
    )

    fun interface SensorCallback {
        fun onSensorUpdated(sensorData: SensorData)
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private var callback: SensorCallback? = null
    private var isListening = false

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    /**
     * Start listening to rotation vector sensor updates.
     */
    fun start(listener: SensorCallback): Boolean {
        if (rotationVectorSensor == null) {
            return false
        }
        if (!isListening) {
            this.callback = listener
            isListening = sensorManager.registerListener(
                this,
                rotationVectorSensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        return isListening
    }

    /**
     * Stop listening to sensor updates to conserve battery.
     */
    fun stop() {
        if (isListening) {
            sensorManager.unregisterListener(this)
            isListening = false
            callback = null
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        // 1. Convert rotation vector to 3x3 rotation matrix R
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // 2. Compute standard device orientation (Azimuth, Pitch, Roll)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        var azimuthDeg = Math.toDegrees(orientationAngles[0].toDouble())
        if (azimuthDeg < 0) azimuthDeg += 360.0
        val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble())
        val rollDeg = Math.toDegrees(orientationAngles[2].toDouble())

        // 3. Compute Rear Camera Optical Axis Vector in World Coordinates (East, North, Up)
        // In Android device coordinates:
        // X = Right, Y = Top, Z = Out of screen (towards face).
        // The rear camera optical axis points straight out of the back: [0, 0, -1].
        // Transforming by rotation matrix R: V_cam = R * [0, 0, -1]^T
        // V_cam_east  = -R[2]
        // V_cam_north = -R[5]
        // V_cam_up    = -R[8]
        val vEast = -rotationMatrix[2].toDouble()
        val vNorth = -rotationMatrix[5].toDouble()
        val vUp = -rotationMatrix[8].toDouble()

        // 4. Calculate Heading (ψ) in degrees [0, 360) clockwise from True/Magnetic North:
        // Heading is angle from North (Y) towards East (X) in horizontal plane.
        var headingDeg = Math.toDegrees(atan2(vEast, vNorth))
        if (headingDeg < 0.0) {
            headingDeg += 360.0
        }

        // 5. Calculate Camera Depression Angle (β) relative to horizontal plane:
        // Horizontal projection magnitude H = sqrt(vEast^2 + vNorth^2)
        // Downward vertical component = -vUp = R[8]
        // β = atan2(-vUp, H)
        val hProj = sqrt(vEast * vEast + vNorth * vNorth)
        val depressionAngleDeg = Math.toDegrees(atan2(-vUp, hProj))

        val data = SensorData(
            headingDeg = headingDeg,
            depressionAngleDeg = depressionAngleDeg,
            azimuthDeg = azimuthDeg,
            pitchDeg = pitchDeg,
            rollDeg = rollDeg
        )

        callback?.onSensorUpdated(data)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
