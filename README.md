# Mobile-Based Target GPS Estimation Android Application

A production-ready Android application developed in Kotlin that transforms an Android smartphone into a handheld target GPS estimator. By fusing device rotation sensor data, camera feed reticle alignment, and GPS coordinates from `FusedLocationProviderClient`, the application computes the real-world geographic coordinates (Latitude, Longitude) of a distant point on the ground.

---

## 1. Theoretical Working Principle & Mathematical Formulation

The targeting model treats the ground as a local tangent plane on the WGS-84 reference ellipsoid and resolves the target's distance and relative geographic offsets using basic trigonometry and spherical geodesy.

```
       Camera (Observer: current_lat, current_lon, height h)
          * \
          |  \
          |   \  Line of Sight (LOS)
        h |    \  Depression Angle β
          |     \
          |______\________________ Target (target_lat, target_lon)
              d (Ground Distance)
```

### 1.1 Ground Distance Calculation
Given:
- $h$: Optical axis height of the mobile camera above the ground (meters)
- $\beta$: Depression angle of the camera below the horizontal ground plane ($0^\circ < \beta < 90^\circ$)

The planar horizontal ground distance $d$ from the observer's ground nadir to the target is:
$$d = \frac{h}{\tan(\beta)}$$

### 1.2 Cartesian Geodetic Offsets
Given:
- $\psi$: Azimuth heading of the camera optical axis in degrees $[0^\circ, 360^\circ)$ clockwise from True/Magnetic North

The North-South and East-West linear displacement offsets are:
$$\text{North Offset} = d \cdot \cos(\psi)$$
$$\text{East Offset} = d \cdot \sin(\psi)$$

### 1.3 Target Coordinate Geodesic Projection

Using the WGS-84 Earth equatorial radius **$R = 6{,}378{,}137.0\ \text{m}$**:

- The angular latitude shift **$\Delta\text{Lat}$** (in degrees) is:

  $$\Delta\text{Lat} = \left(\frac{\text{North Offset}}{R}\right) \times \left(\frac{180}{\pi}\right)$$

- The angular longitude shift **$\Delta\text{Lon}$** (in degrees) scales with the parallel circumference at the observer's latitude `current_lat`:

  $$\Delta\text{Lon} = \left(\frac{\text{East Offset}}{R \cdot \cos(\text{current\_lat})}\right) \times \left(\frac{180}{\pi}\right)$$

Final estimated target coordinates:

$$\text{target\_lat} = \text{current\_lat} + \Delta\text{Lat}$$
$$\text{target\_lon} = \text{current\_lon} + \Delta\text{Lon}$$

---

## 2. Android Hardware & Sensor Architecture

| Subsystem | API / Component | Purpose |
|---|---|---|
| **Camera Feed** | `androidx.camera:camera-camera2`, `camera-view` (`PreviewView`) | Live visual viewfinder displaying target crosshairs |
| **Orientation Sensing** | `android.hardware.Sensor.TYPE_ROTATION_VECTOR` | Computes 3D rotation matrix $R$, isolating camera optical vector $(0, 0, -1)$ in world coordinates |
| **Heading ($\psi$)** | Vector projection: $\text{atan2}(-R[2], -R[5])$ | Continuous, gimbal-lock-free line-of-sight heading $[0, 360^\circ)$ |
| **Depression Angle ($\beta$)** | Vector projection: $\text{atan2}(-V_{\text{up}}, H_{\text{proj}})$ | Accurate tilt angle below the horizon |
| **Position Fixes** | `com.google.android.gms.location.FusedLocationProviderClient` | Provides high-accuracy latitude, longitude, and accuracy radius |
| **Data Logging** | `java.io.FileWriter` (Synchronized thread-safe logger) | Logs test trials to CSV in `getExternalFilesDir(null)` |

---

## 3. Project Structure

```
ace proj/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/targetgps/
│       │   │   ├── MainActivity.kt           // UI & subsystem lifecycle orchestrator
│       │   │   ├── SensorHelper.kt           // RotationVector & angle extraction
│       │   │   ├── LocationHelper.kt         // FusedLocationProviderClient manager
│       │   │   ├── TargetGeoCalculator.kt    // Core mathematical geodetic engine
│       │   │   └── CsvLogger.kt              // Thread-safe CSV test dataset logger
│       │   └── res/
│       │       ├── drawable/
│       │       │   ├── bg_edittext.xml
│       │       │   └── ic_crosshair.xml      // Tactical crosshairs reticle
│       │       ├── layout/
│       │       │   └── activity_main.xml     // Fullscreen preview, HUD overlay, action buttons
│       │       └── values/
│       │           ├── colors.xml
│       │           ├── strings.xml
│       │           └── themes.xml
│       └── test/java/com/example/targetgps/
│           └── TargetGeoCalculatorTest.kt    // Unit tests verifying calculation engine
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── README.md
```

---

## 4. How to Build and Run

### Prerequisites
- **Android Studio**: Android Studio Hedgehog (2023.1.1) or newer.
- **JDK**: Java Development Kit 17 or 21.
- **Android SDK**: Build-tools 34.0.0, compileSdk 34, minSdk 24.
- **Physical Device**: Required for realistic Camera, GPS, and Gyroscope/Magnetometer testing (emulators lack 6-DOF sensor fusion).

### Build Commands
To run unit tests:
```bash
./gradlew test
```

To build Debug APK:
```bash
./gradlew assembleDebug
```

To install directly onto a connected physical Android device:
```bash
./gradlew installDebug
```

---

## 5. Standard Test Protocol & 10 Sample CSV Entries

The data logger appends test captures to `gps_target_tests.csv` in `context.getExternalFilesDir(null)` (`/Android/data/com.example.targetgps/files/gps_target_tests.csv`).

### CSV Header & Sample 10 Structural Test Entries:

```csv
Timestamp,CurrentLat,CurrentLon,Accuracy,Heading,Pitch,Roll,DepressionAngle,Height,GroundDistance,NorthOffset,EastOffset,TargetLat,TargetLon
2026-09-14T11:45:01.120+0530,12.9715987,77.5945627,2.10,0.00,-30.00,0.20,30.00,1.50,2.60,2.60,0.00,12.9716220,77.5945627
2026-09-14T11:45:15.340+0530,12.9715987,77.5945627,1.80,90.00,-45.00,-0.10,45.00,1.50,1.50,0.00,1.50,12.9715987,77.5945765
2026-09-14T11:45:32.450+0530,12.9715987,77.5945627,2.40,180.00,-20.00,0.05,20.00,1.80,4.95,-4.95,0.00,12.9715543,77.5945627
2026-09-14T11:45:48.890+0530,12.9715987,77.5945627,2.20,270.00,-15.00,-0.30,15.00,2.00,7.46,0.00,-7.46,12.9715987,77.5944939
2026-09-14T11:46:05.110+0530,12.9715987,77.5945627,1.90,45.00,-45.00,0.15,45.00,1.60,1.60,1.13,1.13,12.9716089,77.5945731
2026-09-14T11:46:22.560+0530,12.9715987,77.5945627,2.00,135.00,-60.00,-0.25,60.00,1.70,0.98,-0.69,0.69,12.9715925,77.5945691
2026-09-14T11:46:39.780+0530,12.9715987,77.5945627,1.70,315.00,-10.00,0.10,10.00,1.50,8.51,6.02,-6.02,12.9716527,77.5945072
2026-09-14T11:47:01.320+0530,12.9715987,77.5945627,3.10,210.00,-25.00,0.40,25.00,10.00,21.45,-18.57,-10.72,12.9714319,77.5944638
2026-09-14T11:47:25.640+0530,12.9715987,77.5945627,2.80,60.00,-8.00,0.00,8.00,25.00,177.89,88.94,154.05,12.9723976,77.5959834
2026-09-14T11:47:49.020+0530,12.9715987,77.5945627,1.60,15.00,-80.00,-0.12,80.00,1.40,0.25,0.24,0.06,12.9716008,77.5945633
```
