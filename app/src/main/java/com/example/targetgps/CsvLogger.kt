package com.example.targetgps

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Module responsible for persisting sensor, GPS, and calculated target data
 * into a standardized CSV file stored in the application's external files directory.
 */
class CsvLogger(private val context: Context) {

    companion object {
        private const val TAG = "CsvLogger"
        private const val CSV_FILE_NAME = "gps_target_tests.csv"
        private const val CSV_HEADER =
            "Timestamp,CurrentLat,CurrentLon,Accuracy,Heading,Pitch,Roll,DepressionAngle,Height,GroundDistance,NorthOffset,EastOffset,TargetLat,TargetLon\n"
    }

    private val lock = Any()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

    /**
     * Retrieves the target CSV file handle.
     */
    fun getLogFile(): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(baseDir, CSV_FILE_NAME)
    }

    /**
     * Appends a test result entry to the CSV log.
     *
     * @return True if logged successfully, false otherwise.
     */
    fun logTest(
        currentLat: Double,
        currentLon: Double,
        accuracy: Float,
        heading: Double,
        pitch: Double,
        roll: Double,
        depressionAngle: Double,
        height: Double,
        groundDistance: Double,
        northOffset: Double,
        eastOffset: Double,
        targetLat: Double,
        targetLon: Double
    ): Boolean {
        synchronized(lock) {
            val file = getLogFile()
            val writeHeader = !file.exists() || file.length() == 0L

            return try {
                FileWriter(file, true).use { writer ->
                    if (writeHeader) {
                        writer.write(CSV_HEADER)
                    }

                    val timestamp = dateFormat.format(Date())
                    val record = String.format(
                        Locale.US,
                        "%s,%.7f,%.7f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.7f,%.7f\n",
                        timestamp,
                        currentLat,
                        currentLon,
                        accuracy,
                        heading,
                        pitch,
                        roll,
                        depressionAngle,
                        height,
                        groundDistance,
                        northOffset,
                        eastOffset,
                        targetLat,
                        targetLon
                    )
                    writer.write(record)
                    writer.flush()
                }
                Log.d(TAG, "Test successfully logged to ${file.absolutePath}")
                true
            } catch (e: IOException) {
                Log.e(TAG, "Failed to log test entry to CSV file", e)
                false
            }
        }
    }
}
