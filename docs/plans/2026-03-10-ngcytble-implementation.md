# NGCYTBLE Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a full-featured Android BLE surveillance detection app porting CYT's threat scoring, fingerprinting, and behavioral similarity engines from Python to Kotlin.

**Architecture:** Layered Android app — Compose UI → Domain (detection/fingerprint/similarity engines) → Data (Room DB, BLE scanner, network clients) → Platform (foreground service, notifications, location). All domain logic is pure Kotlin with no Android dependencies for testability.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Room, Hilt, Ktor Client, DataStore, WorkManager, Android BLE APIs (BluetoothLeScanner)

**Source reference:** Port from `/Users/auser/Documents/GitHub/NextGen_CYT/cyt/core/` — `detection.py`, `fingerprint.py`, `similarity.py`, `constants.py`

---

## Task 1: Android Project Scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (project-level)
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/ngcyt/ble/NgcytBleApplication.kt`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml` (version catalog)

**Step 1: Create project-level Gradle files**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolution {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "NGCYTBLE"
include(":app")
```

`build.gradle.kts` (project-level):
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
compose-bom = "2025.02.00"
material3 = "1.3.1"
hilt = "2.54"
room = "2.7.0"
ktor = "3.1.0"
datastore = "1.1.3"
work = "2.10.0"
ksp = "2.1.0-1.0.29"
lifecycle = "2.8.7"
navigation = "2.8.7"
coroutines = "1.10.1"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }

# Lifecycle
lifecycle-runtime = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-viewmodel = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }

# Navigation
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Ktor
ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { group = "io.ktor", name = "ktor-client-okhttp", version.ref = "ktor" }
ktor-client-content-negotiation = { group = "io.ktor", name = "ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { group = "io.ktor", name = "ktor-serialization-kotlinx-json", version.ref = "ktor" }

# DataStore
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

# WorkManager
work-runtime = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }

# Coroutines
coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# Serialization
kotlinx-serialization = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version = "1.7.3" }

# Testing
junit = { group = "junit", name = "junit", version = "4.13.2" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

**Step 2: Create app module build file**

`app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ngcyt.ble"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ngcyt.ble"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)

    // Lifecycle
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    // DataStore
    implementation(libs.datastore.preferences)

    // WorkManager
    implementation(libs.work.runtime)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization)

    // Core Android
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)
}
```

**Step 3: Create AndroidManifest.xml**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- BLE scanning -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

    <!-- Location (required for BLE scanning on Android) -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />

    <!-- Foreground service for continuous scanning -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Network for external API and companion mode -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- BLE hardware feature (not required, for Play Store filtering) -->
    <uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />

    <application
        android:name=".NgcytBleApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="NGCYTBLE"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Material3.DayNight.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".scanner.BleScanService"
            android:foregroundServiceType="connectedDevice"
            android:exported="false" />

    </application>
</manifest>
```

**Step 4: Create Application class**

`app/src/main/java/com/ngcyt/ble/NgcytBleApplication.kt`:
```kotlin
package com.ngcyt.ble

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NgcytBleApplication : Application()
```

**Step 5: Create gradle.properties**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

**Step 6: Verify build**

Run: `cd /Users/auser/Documents/GitHub/NGCYTBLE && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (may need Gradle wrapper setup first)

**Step 7: Commit**

```bash
git add -A
git commit -m "feat: scaffold Android project with Compose, Hilt, Room, Ktor"
```

---

## Task 2: Domain Models — Threat Assessment & Device Sighting

Port CYT's `ThreatLevel`, `DeviceSighting`, `ThreatAssessment` from `detection.py`.

**Files:**
- Create: `app/src/main/java/com/ngcyt/ble/domain/model/ThreatLevel.kt`
- Create: `app/src/main/java/com/ngcyt/ble/domain/model/DeviceSighting.kt`
- Create: `app/src/main/java/com/ngcyt/ble/domain/model/ThreatAssessment.kt`
- Test: `app/src/test/java/com/ngcyt/ble/domain/model/ThreatAssessmentTest.kt`

**Step 1: Write failing tests**

`app/src/test/java/com/ngcyt/ble/domain/model/ThreatAssessmentTest.kt`:
```kotlin
package com.ngcyt.ble.domain.model

import org.junit.Assert.*
import org.junit.Test

class ThreatAssessmentTest {

    @Test
    fun `threat level from score - minimal`() {
        assertEquals(ThreatLevel.MINIMAL, ThreatLevel.fromScore(0))
        assertEquals(ThreatLevel.MINIMAL, ThreatLevel.fromScore(24))
    }

    @Test
    fun `threat level from score - low`() {
        assertEquals(ThreatLevel.LOW, ThreatLevel.fromScore(25))
        assertEquals(ThreatLevel.LOW, ThreatLevel.fromScore(49))
    }

    @Test
    fun `threat level from score - medium`() {
        assertEquals(ThreatLevel.MEDIUM, ThreatLevel.fromScore(50))
        assertEquals(ThreatLevel.MEDIUM, ThreatLevel.fromScore(74))
    }

    @Test
    fun `threat level from score - high`() {
        assertEquals(ThreatLevel.HIGH, ThreatLevel.fromScore(75))
        assertEquals(ThreatLevel.HIGH, ThreatLevel.fromScore(89))
    }

    @Test
    fun `threat level from score - critical`() {
        assertEquals(ThreatLevel.CRITICAL, ThreatLevel.fromScore(90))
        assertEquals(ThreatLevel.CRITICAL, ThreatLevel.fromScore(100))
    }

    @Test
    fun `device sighting add sighting updates fields`() {
        val sighting = DeviceSighting(
            mac = "AA:BB:CC:DD:EE:FF",
            deviceType = "BLE",
            firstSeen = 1000.0,
            lastSeen = 1000.0,
        )

        sighting.addSighting("5-10min", 2000.0, serviceUuid = "0000180d-0000-1000-8000-00805f9b34fb")

        assertEquals(setOf("5-10min"), sighting.timeBuckets)
        assertEquals(2000.0, sighting.lastSeen, 0.01)
        assertEquals(2, sighting.sightingCount)
        assertTrue(sighting.serviceUuids.contains("0000180d-0000-1000-8000-00805f9b34fb"))
    }

    @Test
    fun `threat assessment toMap includes fingerprint data when present`() {
        val assessment = ThreatAssessment(
            mac = "AA:BB:CC:DD:EE:FF",
            deviceType = "BLE",
            threatScore = 75,
            threatLevel = ThreatLevel.HIGH,
            timeBucketsPresent = listOf("current", "5-10min", "10-15min"),
            durationMinutes = 18.5,
            serviceUuids = listOf("Heart Rate"),
            reasoning = "Device seen in 3 time periods; Present for 18 minutes",
            physicalDeviceId = "device_1_12345",
            associatedMacs = listOf("11:22:33:44:55:66"),
            fingerprintConfidence = 72.5,
            isMacRandomized = true,
        )

        val map = assessment.toMap()
        assertEquals("AA:BB:CC:DD:EE:FF", map["mac"])
        assertEquals(75, map["threat_score"])
        assertEquals("high", map["threat_level"])
        assertEquals("device_1_12345", map["physical_device_id"])
        assertEquals(true, map["is_mac_randomized"])
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.ngcyt.ble.domain.model.ThreatAssessmentTest"`
Expected: FAIL — classes don't exist yet

**Step 3: Implement domain models**

`app/src/main/java/com/ngcyt/ble/domain/model/ThreatLevel.kt`:
```kotlin
package com.ngcyt.ble.domain.model

enum class ThreatLevel(val value: String) {
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical");

    companion object {
        fun fromScore(score: Int): ThreatLevel = when {
            score >= 90 -> CRITICAL
            score >= 75 -> HIGH
            score >= 50 -> MEDIUM
            score >= 25 -> LOW
            else -> MINIMAL
        }
    }
}
```

`app/src/main/java/com/ngcyt/ble/domain/model/DeviceSighting.kt`:
```kotlin
package com.ngcyt.ble.domain.model

data class DeviceSighting(
    val mac: String,
    val deviceType: String,
    val firstSeen: Double,
    var lastSeen: Double,
    val timeBuckets: MutableSet<String> = mutableSetOf(),
    val serviceUuids: MutableSet<String> = mutableSetOf(),
    var sightingCount: Int = 1,
    var latitude: Double? = null,
    var longitude: Double? = null,
    var locationAccuracy: Float? = null,
    var locationProvider: String? = null,
) {
    fun addSighting(
        timeBucket: String,
        timestamp: Double,
        serviceUuid: String? = null,
        lat: Double? = null,
        lng: Double? = null,
        accuracy: Float? = null,
        provider: String? = null,
    ) {
        timeBuckets.add(timeBucket)
        lastSeen = maxOf(lastSeen, timestamp)
        sightingCount++
        serviceUuid?.let { serviceUuids.add(it) }
        lat?.let { latitude = it }
        lng?.let { longitude = it }
        accuracy?.let { locationAccuracy = it }
        provider?.let { locationProvider = it }
    }
}
```

`app/src/main/java/com/ngcyt/ble/domain/model/ThreatAssessment.kt`:
```kotlin
package com.ngcyt.ble.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ThreatAssessment(
    val mac: String,
    val deviceType: String,
    val threatScore: Int,
    val threatLevel: ThreatLevel,
    val timeBucketsPresent: List<String>,
    val durationMinutes: Double,
    val serviceUuids: List<String>,
    val reasoning: String,
    val physicalDeviceId: String? = null,
    val associatedMacs: List<String> = emptyList(),
    val fingerprintConfidence: Double = 0.0,
    val isMacRandomized: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracy: Float? = null,
    val source: ThreatSource = ThreatSource.BLE_LOCAL,
) {
    fun toMap(): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>(
            "mac" to mac,
            "device_type" to deviceType,
            "threat_score" to threatScore,
            "threat_level" to threatLevel.value,
            "time_buckets" to timeBucketsPresent,
            "duration_minutes" to durationMinutes,
            "service_uuids" to serviceUuids,
            "reasoning" to reasoning,
            "source" to source.value,
        )
        if (latitude != null) {
            result["latitude"] = latitude
            result["longitude"] = longitude
            result["location_accuracy"] = locationAccuracy
        }
        if (physicalDeviceId != null) {
            result["physical_device_id"] = physicalDeviceId
            result["associated_macs"] = associatedMacs
            result["fingerprint_confidence"] = fingerprintConfidence
            result["is_mac_randomized"] = isMacRandomized
        }
        return result
    }
}

enum class ThreatSource(val value: String) {
    BLE_LOCAL("ble_local"),
    WIFI_PI("wifi_pi"),
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.ngcyt.ble.domain.model.ThreatAssessmentTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/ngcyt/ble/domain/model/ app/src/test/java/com/ngcyt/ble/domain/model/
git commit -m "feat: add ThreatLevel, DeviceSighting, ThreatAssessment domain models"
```

---

## Task 3: Threat Scoring Engine (DetectionEngine)

Port CYT's `DetectionEngine._calculate_threat_score`, `_get_threat_level`, `_generate_reasoning`, time bucket rotation.

**Files:**
- Create: `app/src/main/java/com/ngcyt/ble/domain/detection/DetectionEngine.kt`
- Test: `app/src/test/java/com/ngcyt/ble/domain/detection/DetectionEngineTest.kt`

**Step 1: Write failing tests**

```kotlin
package com.ngcyt.ble.domain.detection

import com.ngcyt.ble.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DetectionEngineTest {

    private lateinit var engine: DetectionEngine

    @Before
    fun setup() {
        engine = DetectionEngine(ignoreMacs = setOf("IGNORED:MAC"))
    }

    @Test
    fun `single bucket scores zero`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, 1000.0)
        sighting.timeBuckets.add("current")
        assertEquals(0, engine.calculateThreatScore(sighting))
    }

    @Test
    fun `two buckets scores 20`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, 1300.0)
        sighting.timeBuckets.addAll(listOf("current", "5-10min"))
        assertEquals(20, engine.calculateThreatScore(sighting))
    }

    @Test
    fun `four buckets scores 60`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, 2200.0)
        sighting.timeBuckets.addAll(listOf("current", "5-10min", "10-15min", "15-20min"))
        assertEquals(60, engine.calculateThreatScore(sighting))
    }

    @Test
    fun `duration 15 min adds 10 points`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 0.0, 900.0) // 15 min
        sighting.timeBuckets.addAll(listOf("current", "5-10min"))
        assertEquals(30, engine.calculateThreatScore(sighting)) // 20 + 10
    }

    @Test
    fun `duration 20 min adds 20 points`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 0.0, 1200.0) // 20 min
        sighting.timeBuckets.addAll(listOf("current", "5-10min"))
        assertEquals(40, engine.calculateThreatScore(sighting)) // 20 + 20
    }

    @Test
    fun `service uuid match adds 10 points`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, 1300.0)
        sighting.timeBuckets.addAll(listOf("current", "5-10min"))
        sighting.serviceUuids.add("0000180d-0000-1000-8000-00805f9b34fb")
        assertEquals(30, engine.calculateThreatScore(sighting)) // 20 + 10
    }

    @Test
    fun `mac correlation adds 15 points`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, 1300.0)
        sighting.timeBuckets.addAll(listOf("current", "5-10min"))
        assertEquals(35, engine.calculateThreatScore(sighting, isCorrelated = true)) // 20 + 15
    }

    @Test
    fun `score capped at 100`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 0.0, 1200.0) // 20 min
        sighting.timeBuckets.addAll(listOf("current", "0-5min", "5-10min", "10-15min", "15-20min"))
        sighting.serviceUuids.add("some-uuid")
        // 80 (buckets, capped 60) + 20 (duration) + 10 (uuid) + 15 (correlated) = 105, capped 100
        assertEquals(100, engine.calculateThreatScore(sighting, isCorrelated = true))
    }

    @Test
    fun `ignored mac returns null assessment`() {
        val result = engine.analyzeDevice("IGNORED:MAC", "BLE", 1000.0, "current")
        assertNull(result)
    }

    @Test
    fun `reasoning describes multiple time periods`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 0.0, 1200.0)
        sighting.timeBuckets.addAll(listOf("current", "5-10min", "10-15min"))
        val reasoning = engine.generateReasoning(sighting, 60)
        assertTrue(reasoning.contains("3 time periods"))
        assertTrue(reasoning.contains("20 minutes"))
    }

    @Test
    fun `rotate time buckets ages labels`() {
        engine.analyzeDevice("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, "current")
        engine.rotateTimeBuckets()

        val sighting = engine.deviceHistory["AA:BB:CC:DD:EE:FF"]
        assertNotNull(sighting)
        assertTrue(sighting!!.timeBuckets.contains("0-5min"))
        assertFalse(sighting.timeBuckets.contains("current"))
    }

    @Test
    fun `rotate removes devices only in oldest bucket`() {
        engine.analyzeDevice("OLD:DEVICE", "BLE", 1000.0, "15-20min")
        engine.rotateTimeBuckets()
        assertNull(engine.deviceHistory["OLD:DEVICE"])
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.ngcyt.ble.domain.detection.DetectionEngineTest"`
Expected: FAIL

**Step 3: Implement DetectionEngine**

`app/src/main/java/com/ngcyt/ble/domain/detection/DetectionEngine.kt`:
```kotlin
package com.ngcyt.ble.domain.detection

import com.ngcyt.ble.domain.model.*

class DetectionEngine(
    private val ignoreMacs: Set<String> = emptySet(),
    private val fingerprintEngine: BleFingerprintEngine? = null,
    private val similarityEngine: BehaviorSimilarityEngine? = null,
) {
    companion object {
        val TIME_BUCKETS = mapOf(
            "current" to (0 to 2),
            "0-5min" to (0 to 5),
            "5-10min" to (5 to 10),
            "10-15min" to (10 to 15),
            "15-20min" to (15 to 20),
        )

        const val BUCKET_PRESENCE_POINTS = 20
        const val MAX_BUCKET_POINTS = 60
        const val DURATION_BONUS = 10
        const val LONG_DURATION_BONUS = 20
        const val SERVICE_UUID_BONUS = 10
        const val MAC_CORRELATION_BONUS = 15
    }

    val deviceHistory = mutableMapOf<String, DeviceSighting>()
    private val alertCallbacks = mutableListOf<(ThreatAssessment) -> Unit>()

    fun registerAlertCallback(callback: (ThreatAssessment) -> Unit) {
        alertCallbacks.add(callback)
    }

    fun calculateThreatScore(sighting: DeviceSighting, isCorrelated: Boolean = false): Int {
        var score = 0

        // Base score from number of time buckets (max 60 points)
        val bucketCount = sighting.timeBuckets.size
        if (bucketCount > 1) {
            score += minOf((bucketCount - 1) * BUCKET_PRESENCE_POINTS, MAX_BUCKET_POINTS)
        }

        // Duration bonus
        val durationMins = (sighting.lastSeen - sighting.firstSeen) / 60.0
        if (durationMins >= 20) {
            score += LONG_DURATION_BONUS
        } else if (durationMins >= 15) {
            score += DURATION_BONUS
        }

        // Service UUID match bonus
        if (sighting.serviceUuids.isNotEmpty()) {
            score += SERVICE_UUID_BONUS
        }

        // MAC correlation bonus
        if (isCorrelated) {
            score += MAC_CORRELATION_BONUS
        }

        return score.coerceIn(0, 100)
    }

    fun generateReasoning(sighting: DeviceSighting, score: Int, correlatedCluster: String? = null): String {
        val reasons = mutableListOf<String>()

        val bucketCount = sighting.timeBuckets.size
        if (bucketCount > 1) {
            reasons.add("Device seen in $bucketCount time periods")
        }

        val durationMins = (sighting.lastSeen - sighting.firstSeen) / 60.0
        if (durationMins >= 15) {
            reasons.add("Present for ${durationMins.toInt()} minutes")
        }

        if (sighting.serviceUuids.isNotEmpty()) {
            reasons.add("Advertising ${sighting.serviceUuids.size} service(s)")
        }

        if (correlatedCluster != null && fingerprintEngine != null) {
            val allMacs = fingerprintEngine.getAllMacsForDevice(sighting.mac)
            if (allMacs.size > 1) {
                reasons.add("MAC randomization detected (${allMacs.size} MACs linked)")
            }
        }

        return if (reasons.isEmpty()) {
            "Single brief sighting - likely passerby"
        } else {
            reasons.joinToString("; ")
        }
    }

    fun analyzeDevice(
        mac: String,
        deviceType: String,
        timestamp: Double,
        timeBucket: String,
        serviceUuid: String? = null,
        scanRecord: ByteArray? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        locationAccuracy: Float? = null,
        locationProvider: String? = null,
    ): ThreatAssessment? {
        if (mac in ignoreMacs) return null

        // Process through fingerprinting engine if available
        var correlatedCluster: String? = null
        if (fingerprintEngine != null && scanRecord != null) {
            correlatedCluster = fingerprintEngine.processAdvertisement(
                mac = mac,
                scanRecord = scanRecord,
                timestamp = timestamp,
            )
        }

        // Record behavior for similarity search
        if (similarityEngine != null) {
            val deviceId = correlatedCluster ?: mac
            similarityEngine.recordDeviceBehavior(
                deviceId = deviceId,
                timestamp = timestamp,
                serviceUuid = serviceUuid,
                usesRandomization = correlatedCluster != null,
            )
        }

        val effectiveId = correlatedCluster ?: mac

        // Update or create sighting
        val existing = deviceHistory[effectiveId]
        if (existing != null) {
            existing.addSighting(
                timeBucket, timestamp, serviceUuid,
                latitude, longitude, locationAccuracy, locationProvider,
            )
        } else {
            deviceHistory[effectiveId] = DeviceSighting(
                mac = effectiveId,
                deviceType = deviceType,
                firstSeen = timestamp,
                lastSeen = timestamp,
                latitude = latitude,
                longitude = longitude,
                locationAccuracy = locationAccuracy,
                locationProvider = locationProvider,
            ).apply {
                timeBuckets.add(timeBucket)
                serviceUuid?.let { serviceUuids.add(it) }
            }
        }

        val sighting = deviceHistory[effectiveId]!!
        val isCorrelated = correlatedCluster != null
        val score = calculateThreatScore(sighting, isCorrelated)

        if (score > 0) {
            val assessment = ThreatAssessment(
                mac = mac,
                deviceType = deviceType,
                threatScore = score,
                threatLevel = ThreatLevel.fromScore(score),
                timeBucketsPresent = sighting.timeBuckets.sorted(),
                durationMinutes = (sighting.lastSeen - sighting.firstSeen) / 60.0,
                serviceUuids = sighting.serviceUuids.sorted(),
                reasoning = generateReasoning(sighting, score, correlatedCluster),
                physicalDeviceId = correlatedCluster,
                associatedMacs = if (isCorrelated && fingerprintEngine != null) {
                    fingerprintEngine.getAllMacsForDevice(mac).filter { it != mac }.sorted()
                } else emptyList(),
                fingerprintConfidence = if (isCorrelated && fingerprintEngine != null) {
                    fingerprintEngine.getDeviceForMac(mac)?.clusterConfidence ?: 0.0
                } else 0.0,
                isMacRandomized = isCorrelated,
                latitude = sighting.latitude,
                longitude = sighting.longitude,
                locationAccuracy = sighting.locationAccuracy,
            )

            if (score >= 20) {
                alertCallbacks.forEach { it(assessment) }
            }

            return assessment
        }

        return null
    }

    fun rotateTimeBuckets() {
        // Remove devices only seen in oldest bucket
        val toRemove = deviceHistory.entries
            .filter { it.value.timeBuckets == mutableSetOf("15-20min") }
            .map { it.key }
        toRemove.forEach { deviceHistory.remove(it) }

        // Age the bucket labels
        val bucketMap = mapOf(
            "10-15min" to "15-20min",
            "5-10min" to "10-15min",
            "0-5min" to "5-10min",
            "current" to "0-5min",
        )

        for (sighting in deviceHistory.values) {
            val newBuckets = mutableSetOf<String>()
            for (bucket in sighting.timeBuckets) {
                val mapped = bucketMap[bucket]
                if (mapped != null) {
                    newBuckets.add(mapped)
                } else if (bucket == "15-20min") {
                    newBuckets.add(bucket)
                }
            }
            sighting.timeBuckets.clear()
            sighting.timeBuckets.addAll(newBuckets)
        }
    }

    fun getAllThreats(minScore: Int = 20): List<ThreatAssessment> {
        return deviceHistory.map { (mac, sighting) ->
            val score = calculateThreatScore(sighting)
            ThreatAssessment(
                mac = mac,
                deviceType = sighting.deviceType,
                threatScore = score,
                threatLevel = ThreatLevel.fromScore(score),
                timeBucketsPresent = sighting.timeBuckets.sorted(),
                durationMinutes = (sighting.lastSeen - sighting.firstSeen) / 60.0,
                serviceUuids = sighting.serviceUuids.sorted(),
                reasoning = generateReasoning(sighting, score),
                latitude = sighting.latitude,
                longitude = sighting.longitude,
                locationAccuracy = sighting.locationAccuracy,
            )
        }.filter { it.threatScore >= minScore }
            .sortedByDescending { it.threatScore }
    }

    fun cleanupStaleDevices(maxAgeSeconds: Double = 3600.0, maxDevices: Int = 10000) {
        val now = System.currentTimeMillis() / 1000.0

        val stale = deviceHistory.filter { now - it.value.lastSeen > maxAgeSeconds }.keys.toList()
        stale.forEach { deviceHistory.remove(it) }

        if (deviceHistory.size > maxDevices) {
            val sorted = deviceHistory.entries.sortedBy { it.value.lastSeen }
            val excess = sorted.take(sorted.size - maxDevices)
            excess.forEach { deviceHistory.remove(it.key) }
        }
    }

    fun clearHistory() {
        deviceHistory.clear()
    }
}

// Forward declarations — implemented in Tasks 4 and 5
interface BleFingerprintEngine {
    fun processAdvertisement(mac: String, scanRecord: ByteArray, timestamp: Double): String?
    fun getAllMacsForDevice(mac: String): Set<String>
    fun getDeviceForMac(mac: String): BleDeviceFingerprint?
}

interface BehaviorSimilarityEngine {
    fun recordDeviceBehavior(deviceId: String, timestamp: Double, serviceUuid: String?, usesRandomization: Boolean)
}

data class BleDeviceFingerprint(
    val primaryId: String,
    val associatedMacs: Set<String>,
    val clusterConfidence: Double,
)
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.ngcyt.ble.domain.detection.DetectionEngineTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/ngcyt/ble/domain/detection/ app/src/test/java/com/ngcyt/ble/domain/detection/
git commit -m "feat: port DetectionEngine with threat scoring and time bucket rotation"
```

---

## Task 4: BLE Fingerprinting Engine

Port CYT's `DeviceFingerprintEngine` — adapted for BLE signals (service UUIDs, manufacturer data, timing, TX power, RSSI).

**Files:**
- Create: `app/src/main/java/com/ngcyt/ble/domain/fingerprint/BleConstants.kt`
- Create: `app/src/main/java/com/ngcyt/ble/domain/fingerprint/TimingProfile.kt`
- Create: `app/src/main/java/com/ngcyt/ble/domain/fingerprint/BleDeviceFingerprintData.kt`
- Create: `app/src/main/java/com/ngcyt/ble/domain/fingerprint/BleFingerprintEngineImpl.kt`
- Test: `app/src/test/java/com/ngcyt/ble/domain/fingerprint/BleFingerprintEngineTest.kt`
- Test: `app/src/test/java/com/ngcyt/ble/domain/fingerprint/TimingProfileTest.kt`

**Step 1: Write failing tests**

`app/src/test/java/com/ngcyt/ble/domain/fingerprint/TimingProfileTest.kt`:
```kotlin
package com.ngcyt.ble.domain.fingerprint

import org.junit.Assert.*
import org.junit.Test

class TimingProfileTest {

    @Test
    fun `similarity of identical profiles is high`() {
        val p1 = TimingProfile()
        val p2 = TimingProfile()
        // Simulate identical timing: probes at 0, 1.0, 2.0, 3.0 seconds
        listOf(0.0, 1.0, 2.0, 3.0).forEach { p1.addTimestamp(it); p2.addTimestamp(it) }
        assertTrue(p1.similarity(p2) > 0.7)
    }

    @Test
    fun `similarity of different profiles is low`() {
        val p1 = TimingProfile()
        val p2 = TimingProfile()
        listOf(0.0, 1.0, 2.0, 3.0).forEach { p1.addTimestamp(it) }
        listOf(0.0, 5.0, 15.0, 45.0).forEach { p2.addTimestamp(it) }
        assertTrue(p1.similarity(p2) < 0.3)
    }

    @Test
    fun `burst detection identifies short intervals`() {
        val p = TimingProfile()
        // Burst: 3 probes in 200ms, then 5s gap, then 3 more
        listOf(0.0, 0.1, 0.2, 5.0, 5.1, 5.2).forEach { p.addTimestamp(it) }
        assertTrue(p.burstSize > 1)
    }
}
```

`app/src/test/java/com/ngcyt/ble/domain/fingerprint/BleFingerprintEngineTest.kt`:
```kotlin
package com.ngcyt.ble.domain.fingerprint

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BleFingerprintEngineTest {

    private lateinit var engine: BleFingerprintEngineImpl

    @Before
    fun setup() {
        engine = BleFingerprintEngineImpl()
    }

    @Test
    fun `new mac creates fingerprint`() {
        engine.processAdvertisement(
            mac = "AA:BB:CC:DD:EE:FF",
            serviceUuids = setOf("0000180d-0000-1000-8000-00805f9b34fb"),
            manufacturerData = mapOf(76 to byteArrayOf(0x01, 0x02)),
            txPower = -59,
            rssi = -45,
            timestamp = 1000.0,
        )
        val fp = engine.getFingerprint("AA:BB:CC:DD:EE:FF")
        assertNotNull(fp)
        assertEquals(1, fp!!.serviceUuids.size)
    }

    @Test
    fun `same service uuids correlate two macs`() {
        val sharedUuids = setOf(
            "0000180d-0000-1000-8000-00805f9b34fb", // Heart Rate
            "0000180f-0000-1000-8000-00805f9b34fb", // Battery
            "custom-uuid-12345",                      // Custom service
        )

        // First MAC
        engine.processAdvertisement(
            mac = "AA:BB:CC:DD:EE:01",
            serviceUuids = sharedUuids,
            manufacturerData = mapOf(76 to byteArrayOf(0x01, 0x02, 0x03)),
            txPower = -59,
            rssi = -45,
            timestamp = 1000.0,
        )

        // Second MAC with same services + manufacturer data
        val cluster = engine.processAdvertisement(
            mac = "AA:BB:CC:DD:EE:02",
            serviceUuids = sharedUuids,
            manufacturerData = mapOf(76 to byteArrayOf(0x01, 0x02, 0x03)),
            txPower = -59,
            rssi = -43,
            timestamp = 1005.0,
        )

        assertNotNull(cluster)
    }

    @Test
    fun `completely different devices do not correlate`() {
        engine.processAdvertisement(
            mac = "AA:BB:CC:DD:EE:01",
            serviceUuids = setOf("0000180d-0000-1000-8000-00805f9b34fb"),
            manufacturerData = mapOf(76 to byteArrayOf(0x01)),
            txPower = -59,
            rssi = -45,
            timestamp = 1000.0,
        )

        val cluster = engine.processAdvertisement(
            mac = "AA:BB:CC:DD:EE:02",
            serviceUuids = setOf("0000fff0-0000-1000-8000-00805f9b34fb"),
            manufacturerData = mapOf(6 to byteArrayOf(0x99)),
            txPower = -30,
            rssi = -80,
            timestamp = 1005.0,
        )

        assertNull(cluster)
    }

    @Test
    fun `get all macs for clustered device`() {
        val sharedUuids = setOf("custom-a", "custom-b", "custom-c")
        val sharedMfr = mapOf(76 to byteArrayOf(0x01, 0x02, 0x03, 0x04))

        engine.processAdvertisement("MAC:01", sharedUuids, sharedMfr, -59, -45, 1000.0)
        engine.processAdvertisement("MAC:02", sharedUuids, sharedMfr, -59, -43, 1005.0)

        val allMacs = engine.getAllMacsForDevice("MAC:01")
        assertTrue(allMacs.contains("MAC:01"))
        assertTrue(allMacs.contains("MAC:02"))
    }

    @Test
    fun `ubiquitous uuids filtered from scoring`() {
        // Generic Access and Generic Attribute are ubiquitous — should not count
        engine.processAdvertisement(
            mac = "MAC:01",
            serviceUuids = setOf("00001800-0000-1000-8000-00805f9b34fb", "00001801-0000-1000-8000-00805f9b34fb"),
            manufacturerData = emptyMap(),
            txPower = -59,
            rssi = -45,
            timestamp = 1000.0,
        )

        engine.processAdvertisement(
            mac = "MAC:02",
            serviceUuids = setOf("00001800-0000-1000-8000-00805f9b34fb", "00001801-0000-1000-8000-00805f9b34fb"),
            manufacturerData = emptyMap(),
            txPower = -30,
            rssi = -80,
            timestamp = 1005.0,
        )

        // Should NOT correlate based on ubiquitous UUIDs alone
        val allMacs = engine.getAllMacsForDevice("MAC:01")
        assertEquals(1, allMacs.size) // Just itself
    }

    @Test
    fun `cleanup removes stale fingerprints`() {
        engine.processAdvertisement("MAC:01", emptySet(), emptyMap(), 0, -45, 1000.0)
        engine.processAdvertisement("MAC:02", emptySet(), emptyMap(), 0, -45, 9999.0)

        // Cleanup with 100s max age, "now" at 10000
        engine.cleanupStaleData(maxAgeSeconds = 100.0, now = 10000.0)

        assertNull(engine.getFingerprint("MAC:01"))
        assertNotNull(engine.getFingerprint("MAC:02"))
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.ngcyt.ble.domain.fingerprint.*"`
Expected: FAIL

**Step 3: Implement fingerprinting engine**

`app/src/main/java/com/ngcyt/ble/domain/fingerprint/BleConstants.kt`:
```kotlin
package com.ngcyt.ble.domain.fingerprint

/** BLE Service UUIDs that are too common to be useful for fingerprinting. */
val UBIQUITOUS_SERVICE_UUIDS = setOf(
    "00001800-0000-1000-8000-00805f9b34fb", // Generic Access
    "00001801-0000-1000-8000-00805f9b34fb", // Generic Attribute
    "0000fe8f-0000-1000-8000-00805f9b34fb", // Google Nearby
    "0000fea0-0000-1000-8000-00805f9b34fb", // Google
    "0000fd6f-0000-1000-8000-00805f9b34fb", // Exposure Notification (COVID)
)

fun isUbiquitousUuid(uuid: String): Boolean = uuid.lowercase() in UBIQUITOUS_SERVICE_UUIDS
```

`app/src/main/java/com/ngcyt/ble/domain/fingerprint/TimingProfile.kt`:
```kotlin
package com.ngcyt.ble.domain.fingerprint

import kotlin.math.abs
import kotlin.math.sqrt

class TimingProfile {
    private val timestamps = mutableListOf<Double>()
    var meanInterval: Double = 0.0; private set
    var stdInterval: Double = 0.0; private set
    var burstSize: Int = 0; private set
    var burstInterval: Double = 0.0; private set

    fun addTimestamp(timestamp: Double) {
        timestamps.add(timestamp)
        if (timestamps.size > 100) {
            timestamps.removeAt(0)
        }
        recalculate()
    }

    private fun recalculate() {
        if (timestamps.size < 3) return
        val sorted = timestamps.sorted()
        val intervals = (0 until sorted.size - 1).map { sorted[it + 1] - sorted[it] }
        if (intervals.isEmpty()) return

        val burstIntervals = intervals.filter { it < 0.5 }
        val mainIntervals = intervals.filter { it >= 0.5 }

        if (mainIntervals.isNotEmpty()) {
            meanInterval = mainIntervals.average()
            stdInterval = if (mainIntervals.size > 1) {
                val mean = mainIntervals.average()
                sqrt(mainIntervals.map { (it - mean) * (it - mean) }.average())
            } else 0.0
        }

        if (burstIntervals.isNotEmpty()) {
            burstInterval = burstIntervals.average()
            var burstCount = 0
            var current = 1
            for (interval in intervals) {
                if (interval < 0.5) {
                    current++
                } else {
                    if (current > 1) burstCount++
                    current = 1
                }
            }
            burstSize = if (burstCount == 0) current
                else (burstIntervals.size / maxOf(burstCount, 1)) + 1
        }
    }

    fun similarity(other: TimingProfile): Double {
        if (meanInterval == 0.0 || other.meanInterval == 0.0) return 0.0

        var score = 0.0

        // Mean interval similarity (allow 20% variance)
        val ratio = minOf(meanInterval, other.meanInterval) / maxOf(meanInterval, other.meanInterval)
        score += when {
            ratio > 0.8 -> 0.5
            ratio > 0.6 -> 0.25
            else -> 0.0
        }

        // Burst characteristics
        if (burstSize > 0 && other.burstSize > 0) {
            score += when {
                burstSize == other.burstSize -> 0.3
                abs(burstSize - other.burstSize) == 1 -> 0.15
                else -> 0.0
            }
        }

        // Burst interval similarity
        if (burstInterval > 0 && other.burstInterval > 0) {
            val burstRatio = minOf(burstInterval, other.burstInterval) / maxOf(burstInterval, other.burstInterval)
            if (burstRatio > 0.8) score += 0.2
        }

        return minOf(score, 1.0)
    }
}
```

`app/src/main/java/com/ngcyt/ble/domain/fingerprint/BleDeviceFingerprintData.kt`:
```kotlin
package com.ngcyt.ble.domain.fingerprint

data class BleDeviceFingerprintData(
    var primaryId: String,
    val associatedMacs: MutableSet<String> = mutableSetOf(),
    val serviceUuids: MutableSet<String> = mutableSetOf(),
    val uniqueServiceUuids: MutableSet<String> = mutableSetOf(),
    val manufacturerData: MutableMap<Int, ByteArray> = mutableMapOf(),
    var deviceName: String? = null,
    var txPower: Int = 0,
    var connectable: Boolean = true,
    val timing: TimingProfile = TimingProfile(),
    val rssiHistory: MutableList<Int> = mutableListOf(),
    var firstSeen: Double = 0.0,
    var lastSeen: Double = 0.0,
    var totalAdvertisements: Int = 0,
    var clusterConfidence: Double = 0.0,
) {
    fun addServiceUuid(uuid: String) {
        serviceUuids.add(uuid)
        if (!isUbiquitousUuid(uuid)) {
            uniqueServiceUuids.add(uuid)
        }
    }

    fun getUuidFingerprintStrength(): Double {
        val count = uniqueServiceUuids.size
        return when {
            count >= 4 -> 0.9
            count == 3 -> 0.7
            count == 2 -> 0.5
            count == 1 -> 0.3
            else -> 0.0
        }
    }

    fun mergeFrom(other: BleDeviceFingerprintData) {
        associatedMacs.addAll(other.associatedMacs)
        serviceUuids.addAll(other.serviceUuids)
        uniqueServiceUuids.addAll(other.uniqueServiceUuids)
        other.manufacturerData.forEach { (k, v) -> manufacturerData.putIfAbsent(k, v) }
        if (other.firstSeen < firstSeen || firstSeen == 0.0) firstSeen = other.firstSeen
        if (other.lastSeen > lastSeen) lastSeen = other.lastSeen
        totalAdvertisements += other.totalAdvertisements
        other.timing // Timing merge would need probe_times exposed; skip for now
    }
}
```

`app/src/main/java/com/ngcyt/ble/domain/fingerprint/BleFingerprintEngineImpl.kt`:
```kotlin
package com.ngcyt.ble.domain.fingerprint

import com.ngcyt.ble.domain.detection.BleDeviceFingerprint
import com.ngcyt.ble.domain.detection.BleFingerprintEngine
import kotlin.math.sqrt

class BleFingerprintEngineImpl : BleFingerprintEngine {

    companion object {
        const val CLUSTER_THRESHOLD = 45.0
        const val HIGH_CONFIDENCE_THRESHOLD = 70.0

        const val WEIGHT_SERVICE_UUID = 35.0
        const val WEIGHT_MANUFACTURER_DATA = 30.0
        const val WEIGHT_TIMING = 15.0
        const val WEIGHT_TX_POWER = 10.0
        const val WEIGHT_RSSI = 10.0
    }

    private val macFingerprints = mutableMapOf<String, BleDeviceFingerprintData>()
    private val deviceClusters = mutableMapOf<String, BleDeviceFingerprintData>()
    private val macToCluster = mutableMapOf<String, String>()

    fun processAdvertisement(
        mac: String,
        serviceUuids: Set<String>,
        manufacturerData: Map<Int, ByteArray>,
        txPower: Int,
        rssi: Int,
        timestamp: Double,
        deviceName: String? = null,
    ): String? {
        // Get or create fingerprint
        val fp = macFingerprints.getOrPut(mac) {
            BleDeviceFingerprintData(
                primaryId = mac,
                associatedMacs = mutableSetOf(mac),
                firstSeen = timestamp,
            )
        }

        fp.lastSeen = timestamp
        fp.totalAdvertisements++
        fp.txPower = txPower
        fp.timing.addTimestamp(timestamp)
        fp.rssiHistory.add(rssi)
        if (fp.rssiHistory.size > 50) fp.rssiHistory.removeAt(0)
        deviceName?.let { fp.deviceName = it }

        serviceUuids.forEach { fp.addServiceUuid(it) }
        manufacturerData.forEach { (k, v) -> fp.manufacturerData[k] = v }

        // Try to correlate
        return findCorrelation(mac)
    }

    // Implement BleFingerprintEngine interface (used by DetectionEngine)
    override fun processAdvertisement(mac: String, scanRecord: ByteArray, timestamp: Double): String? {
        // Simplified — real implementation would parse scan record
        // The full version is called directly from BleScanner with parsed fields
        return null
    }

    override fun getAllMacsForDevice(mac: String): Set<String> {
        val clusterId = macToCluster[mac]
        if (clusterId != null && clusterId in deviceClusters) {
            return deviceClusters[clusterId]!!.associatedMacs.toSet()
        }
        return setOf(mac)
    }

    override fun getDeviceForMac(mac: String): BleDeviceFingerprint? {
        val clusterId = macToCluster[mac]
        val fp = if (clusterId != null) deviceClusters[clusterId] else macFingerprints[mac]
        return fp?.let {
            BleDeviceFingerprint(
                primaryId = it.primaryId,
                associatedMacs = it.associatedMacs.toSet(),
                clusterConfidence = it.clusterConfidence,
            )
        }
    }

    fun getFingerprint(mac: String): BleDeviceFingerprintData? = macFingerprints[mac]

    private fun findCorrelation(mac: String): String? {
        val fp = macFingerprints[mac] ?: return null
        if (mac in macToCluster) return macToCluster[mac]

        var bestMatch: String? = null
        var bestScore = 0.0

        for ((otherMac, otherFp) in macFingerprints) {
            if (otherMac == mac) continue
            val score = calculateCorrelationScore(fp, otherFp)
            if (score > bestScore && score >= CLUSTER_THRESHOLD) {
                bestScore = score
                bestMatch = otherMac
            }
        }

        if (bestMatch != null) {
            return mergeIntoCluster(mac, bestMatch, bestScore)
        }

        return null
    }

    private fun calculateCorrelationScore(fp1: BleDeviceFingerprintData, fp2: BleDeviceFingerprintData): Double {
        var score = 0.0

        // 1. Service UUID similarity (max 35 pts)
        score += scoreServiceUuidSimilarity(fp1, fp2)

        // 2. Manufacturer data similarity (max 30 pts)
        score += scoreManufacturerDataSimilarity(fp1, fp2)

        // 3. Timing pattern similarity (max 15 pts)
        score += fp1.timing.similarity(fp2.timing) * WEIGHT_TIMING

        // 4. TX power match (max 10 pts)
        if (fp1.txPower != 0 && fp2.txPower != 0 && fp1.txPower == fp2.txPower) {
            score += WEIGHT_TX_POWER
        }

        // 5. RSSI behavior similarity (max 10 pts)
        score += scoreRssiSimilarity(fp1, fp2)

        return minOf(score, 100.0)
    }

    private fun scoreServiceUuidSimilarity(fp1: BleDeviceFingerprintData, fp2: BleDeviceFingerprintData): Double {
        // Use unique (non-ubiquitous) UUIDs
        if (fp1.uniqueServiceUuids.isNotEmpty() && fp2.uniqueServiceUuids.isNotEmpty()) {
            val intersection = fp1.uniqueServiceUuids.intersect(fp2.uniqueServiceUuids)
            val union = fp1.uniqueServiceUuids.union(fp2.uniqueServiceUuids)

            if (intersection.isNotEmpty()) {
                val jaccard = intersection.size.toDouble() / union.size
                val matchCount = intersection.size
                return when {
                    matchCount >= 3 -> WEIGHT_SERVICE_UUID * minOf(jaccard + 0.3, 1.0)
                    matchCount == 2 -> WEIGHT_SERVICE_UUID * jaccard * 0.8
                    else -> WEIGHT_SERVICE_UUID * jaccard * 0.6
                }
            }
        }
        return 0.0
    }

    private fun scoreManufacturerDataSimilarity(fp1: BleDeviceFingerprintData, fp2: BleDeviceFingerprintData): Double {
        if (fp1.manufacturerData.isEmpty() || fp2.manufacturerData.isEmpty()) return 0.0

        // Check for matching company IDs
        val sharedKeys = fp1.manufacturerData.keys.intersect(fp2.manufacturerData.keys)
        if (sharedKeys.isEmpty()) return 0.0

        var totalSimilarity = 0.0
        for (key in sharedKeys) {
            val d1 = fp1.manufacturerData[key] ?: continue
            val d2 = fp2.manufacturerData[key] ?: continue

            // Compare payload bytes
            val minLen = minOf(d1.size, d2.size)
            if (minLen == 0) continue

            var matching = 0
            for (i in 0 until minLen) {
                if (d1[i] == d2[i]) matching++
            }
            totalSimilarity += matching.toDouble() / minLen
        }

        val avgSimilarity = totalSimilarity / sharedKeys.size
        return WEIGHT_MANUFACTURER_DATA * avgSimilarity
    }

    private fun scoreRssiSimilarity(fp1: BleDeviceFingerprintData, fp2: BleDeviceFingerprintData): Double {
        if (fp1.rssiHistory.size < 2 || fp2.rssiHistory.size < 2) return 0.0

        val var1 = calculateVariance(fp1.rssiHistory.map { it.toDouble() })
        val var2 = calculateVariance(fp2.rssiHistory.map { it.toDouble() })

        // Similar variance suggests similar radio behavior
        val ratio = if (var1 > 0 && var2 > 0) {
            minOf(var1, var2) / maxOf(var1, var2)
        } else if (var1 == 0.0 && var2 == 0.0) {
            1.0
        } else {
            0.0
        }

        return WEIGHT_RSSI * ratio
    }

    private fun calculateVariance(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average()
    }

    private fun mergeIntoCluster(mac1: String, mac2: String, confidence: Double): String {
        val cluster1 = macToCluster[mac1]
        val cluster2 = macToCluster[mac2]

        val clusterId: String

        when {
            cluster1 != null && cluster2 != null && cluster1 != cluster2 -> {
                // Merge cluster2 into cluster1
                val c2fp = deviceClusters.remove(cluster2)
                if (c2fp != null) {
                    deviceClusters[cluster1]?.mergeFrom(c2fp)
                    for (mac in c2fp.associatedMacs) {
                        macToCluster[mac] = cluster1
                    }
                }
                clusterId = cluster1
            }
            cluster1 != null -> {
                macToCluster[mac2] = cluster1
                deviceClusters[cluster1]?.associatedMacs?.add(mac2)
                macFingerprints[mac2]?.let { deviceClusters[cluster1]?.mergeFrom(it) }
                clusterId = cluster1
            }
            cluster2 != null -> {
                macToCluster[mac1] = cluster2
                deviceClusters[cluster2]?.associatedMacs?.add(mac1)
                macFingerprints[mac1]?.let { deviceClusters[cluster2]?.mergeFrom(it) }
                clusterId = cluster2
            }
            else -> {
                clusterId = "device_${deviceClusters.size + 1}_${System.currentTimeMillis()}"
                val merged = BleDeviceFingerprintData(
                    primaryId = clusterId,
                    associatedMacs = mutableSetOf(mac1, mac2),
                    firstSeen = minOf(
                        macFingerprints[mac1]?.firstSeen ?: Double.MAX_VALUE,
                        macFingerprints[mac2]?.firstSeen ?: Double.MAX_VALUE,
                    ),
                    clusterConfidence = confidence,
                )
                macFingerprints[mac1]?.let { merged.mergeFrom(it) }
                macFingerprints[mac2]?.let { merged.mergeFrom(it) }
                deviceClusters[clusterId] = merged
                macToCluster[mac1] = clusterId
                macToCluster[mac2] = clusterId
            }
        }

        deviceClusters[clusterId]?.clusterConfidence = maxOf(
            deviceClusters[clusterId]?.clusterConfidence ?: 0.0,
            confidence,
        )

        return clusterId
    }

    fun cleanupStaleData(maxAgeSeconds: Double = 3600.0, maxFingerprints: Int = 5000, now: Double = System.currentTimeMillis() / 1000.0) {
        val stale = macFingerprints.filter { now - it.value.lastSeen > maxAgeSeconds }.keys.toList()
        for (mac in stale) {
            macFingerprints.remove(mac)
            macToCluster.remove(mac)
        }

        if (macFingerprints.size > maxFingerprints) {
            val sorted = macFingerprints.entries.sortedBy { it.value.lastSeen }
            val excess = sorted.take(sorted.size - maxFingerprints)
            for ((mac, _) in excess) {
                macFingerprints.remove(mac)
                macToCluster.remove(mac)
            }
        }

        // Clean orphaned clusters
        val activeClusters = macToCluster.values.toSet()
        val orphaned = deviceClusters.keys.filter { it !in activeClusters }
        orphaned.forEach { deviceClusters.remove(it) }
    }

    fun getClusterStats(): Map<String, Any> = mapOf(
        "total_macs" to macFingerprints.size,
        "total_clusters" to deviceClusters.size,
        "macs_in_clusters" to macToCluster.size,
        "unclustered_macs" to (macFingerprints.size - macToCluster.size),
    )
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.ngcyt.ble.domain.fingerprint.*"`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/ngcyt/ble/domain/fingerprint/ app/src/test/java/com/ngcyt/ble/domain/fingerprint/
git commit -m "feat: port BLE fingerprinting engine with MAC randomization defeat"
```

---

## Task 5: Behavioral Similarity Engine

Port CYT's `BehaviorAnalyzer` and `BehaviorSimilarityEngine` — using Room + cosine similarity instead of ChromaDB.

**Files:**
- Create: `app/src/main/java/com/ngcyt/ble/domain/similarity/BehaviorVector.kt`
- Create: `app/src/main/java/com/ngcyt/ble/domain/similarity/BehaviorAnalyzer.kt`
- Create: `app/src/main/java/com/ngcyt/ble/domain/similarity/BehaviorSimilarityEngineImpl.kt`
- Test: `app/src/test/java/com/ngcyt/ble/domain/similarity/BehaviorSimilarityTest.kt`

**Step 1: Write failing tests**

```kotlin
package com.ngcyt.ble.domain.similarity

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BehaviorSimilarityTest {

    @Test
    fun `cosine similarity of identical vectors is 1`() {
        val a = floatArrayOf(0.5f, 0.8f, 0.3f, 0.1f, 0.9f, 0.4f, 0.7f, 0.2f, 1.0f, 0.0f, 0.5f)
        assertEquals(1.0f, cosineSimilarity(a, a), 0.001f)
    }

    @Test
    fun `cosine similarity of orthogonal vectors is 0`() {
        val a = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(0.0f, cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun `behavior analyzer creates vector from advertisement history`() {
        val analyzer = BehaviorAnalyzer()
        // Simulate 10 advertisements over 5 minutes
        for (i in 0 until 10) {
            analyzer.recordAdvertisement("device1", 1000.0 + i * 30.0, "service-a")
        }
        val vector = analyzer.createBehaviorVector("device1")
        assertTrue(vector.adFrequency > 0)
        assertTrue(vector.serviceCount > 0)
        assertEquals(10, vector.rawAdCount)
    }

    @Test
    fun `suspicious pattern vector has expected characteristics`() {
        val engine = BehaviorSimilarityEngineImpl()
        val suspicious = engine.createSuspiciousPatternVector()
        assertTrue(suspicious.adFrequency > 0.5f)
        assertTrue(suspicious.usesRandomization > 0.5f)
        assertTrue(suspicious.activeDuration > 0.5f)
    }

    @Test
    fun `similar devices found by cosine similarity`() {
        val engine = BehaviorSimilarityEngineImpl()

        // Device A: regular, high frequency
        for (i in 0 until 20) {
            engine.recordDeviceBehavior("deviceA", 1000.0 + i * 30.0, "svc-1")
        }
        engine.updateDeviceVector("deviceA")

        // Device B: similar pattern
        for (i in 0 until 20) {
            engine.recordDeviceBehavior("deviceB", 2000.0 + i * 30.0, "svc-1")
        }
        engine.updateDeviceVector("deviceB")

        // Device C: very different pattern
        for (i in 0 until 3) {
            engine.recordDeviceBehavior("deviceC", 5000.0 + i * 300.0, "svc-x")
        }
        engine.updateDeviceVector("deviceC")

        val similar = engine.findSimilarDevices("deviceA", nResults = 5, minSimilarity = 0.5f)
        assertTrue(similar.any { it["device_id"] == "deviceB" })
    }
}
```

**Step 2: Run tests, verify they fail**

**Step 3: Implement**

`app/src/main/java/com/ngcyt/ble/domain/similarity/BehaviorVector.kt`:
```kotlin
package com.ngcyt.ble.domain.similarity

import kotlin.math.sqrt

data class BehaviorVector(
    val deviceId: String,
    val adFrequency: Float = 0f,
    val adRegularity: Float = 0f,
    val burstTendency: Float = 0f,
    val serviceCount: Float = 0f,
    val uniqueServiceRatio: Float = 0f,
    val serviceEntropy: Float = 0f,
    val activeDuration: Float = 0f,
    val timeConsistency: Float = 0f,
    val usesRandomization: Float = 0f,
    val minimalAdvertisement: Float = 0f,
    val highMobility: Float = 0f,
    val rawAdCount: Int = 0,
    val rawServiceList: List<String> = emptyList(),
    val timestamp: Double = 0.0,
) {
    fun toFloatArray(): FloatArray = floatArrayOf(
        adFrequency, adRegularity, burstTendency,
        serviceCount, uniqueServiceRatio, serviceEntropy,
        activeDuration, timeConsistency,
        usesRandomization, minimalAdvertisement, highMobility,
    )

    companion object {
        const val DIMENSIONS = 11
    }
}

fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size) { "Vectors must have same dimension" }
    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    val denom = sqrt(normA) * sqrt(normB)
    return if (denom == 0f) 0f else dot / denom
}
```

`app/src/main/java/com/ngcyt/ble/domain/similarity/BehaviorAnalyzer.kt`:
```kotlin
package com.ngcyt.ble.domain.similarity

import com.ngcyt.ble.domain.fingerprint.isUbiquitousUuid
import kotlin.math.ln
import kotlin.math.min

class BehaviorAnalyzer {
    companion object {
        const val MAX_AD_FREQUENCY = 10.0   // 10 ads/min is high
        const val MAX_SERVICE_COUNT = 20.0
        const val MAX_DURATION_MINS = 60.0
    }

    private val adHistory = mutableMapOf<String, MutableList<Double>>()
    private val serviceHistory = mutableMapOf<String, MutableMap<String, Int>>()
    private val firstSeen = mutableMapOf<String, Double>()
    private val lastSeen = mutableMapOf<String, Double>()

    fun recordAdvertisement(deviceId: String, timestamp: Double, serviceUuid: String? = null) {
        val history = adHistory.getOrPut(deviceId) { mutableListOf() }
        history.add(timestamp)
        if (history.size > 100) history.removeAt(0)

        if (serviceUuid != null) {
            val services = serviceHistory.getOrPut(deviceId) { mutableMapOf() }
            services[serviceUuid] = (services[serviceUuid] ?: 0) + 1
        }

        if (deviceId !in firstSeen) firstSeen[deviceId] = timestamp
        lastSeen[deviceId] = timestamp
    }

    fun createBehaviorVector(deviceId: String, usesRandomization: Boolean = false): BehaviorVector {
        val ads = adHistory[deviceId] ?: emptyList()
        val services = serviceHistory[deviceId] ?: emptyMap()

        var vector = BehaviorVector(
            deviceId = deviceId,
            timestamp = System.currentTimeMillis() / 1000.0,
            rawAdCount = ads.size,
            rawServiceList = services.keys.toList(),
            usesRandomization = if (usesRandomization) 1f else 0f,
        )

        if (ads.size < 2) return vector

        // Ad frequency
        val durationMins = (ads.max() - ads.min()) / 60.0
        val adFrequency = if (durationMins > 0) {
            min(ads.size / durationMins / MAX_AD_FREQUENCY, 1.0).toFloat()
        } else 0f

        // Ad regularity
        val intervals = (0 until ads.size - 1).map { ads[it + 1] - ads[it] }
        var adRegularity = 0f
        if (intervals.size > 1) {
            val mean = intervals.average()
            val std = kotlin.math.sqrt(intervals.map { (it - mean) * (it - mean) }.average())
            if (mean > 0) {
                val cv = std / mean
                adRegularity = maxOf(0f, (1.0 - minOf(cv, 2.0) / 2.0).toFloat())
            }
        }

        // Burst tendency
        val burstTendency = if (intervals.isNotEmpty()) {
            intervals.count { it < 1.0 }.toFloat() / intervals.size
        } else 0f

        // Service features
        var serviceCount = 0f
        var uniqueServiceRatio = 0f
        var serviceEntropy = 0f

        if (services.isNotEmpty()) {
            val total = services.size
            val unique = services.keys.count { !isUbiquitousUuid(it) }

            serviceCount = min(total.toDouble() / MAX_SERVICE_COUNT, 1.0).toFloat()
            uniqueServiceRatio = if (total > 0) unique.toFloat() / total else 0f

            val totalAds = services.values.sum()
            if (totalAds > 0) {
                var entropy = 0.0
                for (count in services.values) {
                    val p = count.toDouble() / totalAds
                    if (p > 0) entropy -= p * ln(p)
                }
                serviceEntropy = min(entropy / 3.0, 1.0).toFloat()
            }
        }

        // Duration
        val first = firstSeen[deviceId] ?: 0.0
        val last = lastSeen[deviceId] ?: 0.0
        val activeDuration = if (first > 0 && last > 0) {
            min((last - first) / 60.0 / MAX_DURATION_MINS, 1.0).toFloat()
        } else 0f

        // Minimal advertisement (no services, no name = evasive)
        val minimalAd = if (services.isEmpty()) 1f else 0f

        return vector.copy(
            adFrequency = adFrequency,
            adRegularity = adRegularity,
            burstTendency = burstTendency,
            serviceCount = serviceCount,
            uniqueServiceRatio = uniqueServiceRatio,
            serviceEntropy = serviceEntropy,
            activeDuration = activeDuration,
            minimalAdvertisement = minimalAd,
        )
    }
}
```

`app/src/main/java/com/ngcyt/ble/domain/similarity/BehaviorSimilarityEngineImpl.kt`:
```kotlin
package com.ngcyt.ble.domain.similarity

import com.ngcyt.ble.domain.detection.BehaviorSimilarityEngine

class BehaviorSimilarityEngineImpl : BehaviorSimilarityEngine {

    private val analyzer = BehaviorAnalyzer()
    private val storedVectors = mutableMapOf<String, FloatArray>()
    private val storedMetadata = mutableMapOf<String, Map<String, Any>>()

    override fun recordDeviceBehavior(deviceId: String, timestamp: Double, serviceUuid: String?, usesRandomization: Boolean) {
        analyzer.recordAdvertisement(deviceId, timestamp, serviceUuid)
    }

    fun updateDeviceVector(deviceId: String, usesRandomization: Boolean = false): BehaviorVector {
        val vector = analyzer.createBehaviorVector(deviceId, usesRandomization)
        storedVectors[deviceId] = vector.toFloatArray()
        storedMetadata[deviceId] = mapOf(
            "device_id" to deviceId,
            "ad_count" to vector.rawAdCount,
            "service_list" to vector.rawServiceList.take(10).joinToString(","),
            "uses_randomization" to usesRandomization,
        )
        return vector
    }

    fun findSimilarDevices(deviceId: String, nResults: Int = 5, minSimilarity: Float = 0.7f): List<Map<String, Any>> {
        val targetVector = analyzer.createBehaviorVector(deviceId)
        if (targetVector.rawAdCount < 2) return emptyList()
        val target = targetVector.toFloatArray()

        return storedVectors
            .filter { it.key != deviceId }
            .map { (id, vec) ->
                val similarity = cosineSimilarity(target, vec)
                Triple(id, similarity, storedMetadata[id] ?: emptyMap())
            }
            .filter { it.second >= minSimilarity }
            .sortedByDescending { it.second }
            .take(nResults)
            .map { (id, similarity, metadata) ->
                mapOf(
                    "device_id" to id,
                    "similarity" to similarity,
                    "ad_count" to (metadata["ad_count"] ?: 0),
                    "uses_randomization" to (metadata["uses_randomization"] ?: false),
                )
            }
    }

    fun createSuspiciousPatternVector(): BehaviorVector = BehaviorVector(
        deviceId = "__suspicious_pattern__",
        adFrequency = 0.8f,
        adRegularity = 0.7f,
        burstTendency = 0.3f,
        serviceCount = 0.7f,
        uniqueServiceRatio = 0.8f,
        serviceEntropy = 0.6f,
        activeDuration = 0.8f,
        timeConsistency = 0.7f,
        usesRandomization = 1.0f,
        minimalAdvertisement = 0.5f,
        highMobility = 0.3f,
    )

    fun findSuspiciousDevices(nResults: Int = 10): List<Map<String, Any>> {
        val suspicious = createSuspiciousPatternVector().toFloatArray()
        return storedVectors
            .map { (id, vec) ->
                val similarity = cosineSimilarity(suspicious, vec)
                Triple(id, similarity, storedMetadata[id] ?: emptyMap())
            }
            .sortedByDescending { it.second }
            .take(nResults)
            .map { (id, similarity, metadata) ->
                mapOf(
                    "device_id" to id,
                    "similarity" to similarity,
                    "uses_randomization" to (metadata["uses_randomization"] ?: false),
                )
            }
    }

    fun getBehaviorClusters(): Map<String, List<String>> {
        val clusters = mutableMapOf<String, MutableList<String>>()
        for ((id, vec) in storedVectors) {
            val label = when {
                vec[8] > 0.5f -> "randomizing"
                vec[0] > 0.7f -> "high_frequency"
                vec[4] > 0.7f -> "unique_services"
                vec[6] > 0.7f -> "persistent"
                else -> "normal"
            }
            clusters.getOrPut(label) { mutableListOf() }.add(id)
        }
        return clusters
    }

    fun clear() {
        storedVectors.clear()
        storedMetadata.clear()
    }
}
```

**Step 4: Run tests, verify they pass**

Run: `./gradlew test --tests "com.ngcyt.ble.domain.similarity.*"`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/ngcyt/ble/domain/similarity/ app/src/test/java/com/ngcyt/ble/domain/similarity/
git commit -m "feat: port behavioral similarity engine with on-device cosine similarity"
```

---

## Task 6: Room Database

**Files:**
- Create: `app/src/main/java/com/ngcyt/ble/data/db/AppDatabase.kt`
- Create: `app/src/main/java/com/ngcyt/ble/data/db/entity/` (5 entity files)
- Create: `app/src/main/java/com/ngcyt/ble/data/db/dao/` (5 DAO files)
- Create: `app/src/main/java/com/ngcyt/ble/data/db/Converters.kt`

Entities: `DeviceSightingEntity`, `DeviceFingerprintEntity`, `BehaviorVectorEntity`, `ThreatAssessmentEntity`, `ApiSyncQueueEntity`

Each entity maps to one Room table per the design doc. DAOs provide insert, query by MAC, query by time range, delete stale records.

**This task is implementation-only (no tests for Room DAOs in unit tests — they require instrumented tests). Write the entities and DAOs, verify compilation.**

**Step 1: Create Converters, entities, DAOs, and AppDatabase**

*(Full implementation code for each file — entities mirror domain models with `@Entity` annotations, DAOs provide `@Insert(onConflict = REPLACE)`, `@Query` for time-range lookups, `@Delete` for cleanup.)*

**Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/ngcyt/ble/data/db/
git commit -m "feat: add Room database with entities and DAOs for all data tables"
```

---

## Task 7: BLE Scanner Service

**Files:**
- Create: `app/src/main/java/com/ngcyt/ble/scanner/BleScanService.kt`
- Create: `app/src/main/java/com/ngcyt/ble/scanner/ScanResultParser.kt`

**BleScanService** is an Android foreground service that:
1. Starts BLE scanning via `BluetoothLeScanner.startScan()`
2. Parses each `ScanResult` into service UUIDs, manufacturer data, TX power, RSSI, device name
3. Feeds parsed data into `DetectionEngine.analyzeDevice()`
4. Feeds parsed data into `BleFingerprintEngineImpl.processAdvertisement()`
5. Runs on 60-second scan cycles with 5-minute bucket rotation
6. Shows persistent notification with current threat count
7. Acquires WiFi-derived location for each scan cycle

**ScanResultParser** extracts fingerprinting signals from Android's `ScanResult`:
- `scanResult.scanRecord.serviceUuids`
- `scanResult.scanRecord.manufacturerSpecificData`
- `scanResult.scanRecord.txPowerLevel`
- `scanResult.rssi`
- `scanResult.device.name`

**Step 1: Implement both files**

**Step 2: Verify build**

**Step 3: Commit**

```bash
git add app/src/main/java/com/ngcyt/ble/scanner/
git commit -m "feat: add BLE foreground scan service with result parsing"
```

---

## Task 8: External API Client

**Files:**
- Create: `app/src/main/java/com/ngcyt/ble/data/api/ExternalApiClient.kt`
- Create: `app/src/main/java/com/ngcyt/ble/data/api/ApiSyncWorker.kt`
- Test: `app/src/test/java/com/ngcyt/ble/data/api/ExternalApiClientTest.kt`

**ExternalApiClient** uses Ktor to POST threat data to user-configured endpoint:
- `ALERTS_ONLY` mode: sends `ThreatAssessment.toMap()` when score crosses threshold
- `FULL_TELEMETRY` mode: batches all sightings, fingerprint events, similarity data
- Failed sends queued in `api_sync_queue` Room table
- `ApiSyncWorker` (WorkManager) retries failed sends with exponential backoff

**Step 1: Write tests (mock HTTP client)**
**Step 2: Implement**
**Step 3: Verify tests pass**
**Step 4: Commit**

```bash
git add app/src/main/java/com/ngcyt/ble/data/api/ app/src/test/java/com/ngcyt/ble/data/api/
git commit -m "feat: add external API client with alerts and telemetry modes"
```

---

## Task 9: Pi Companion Client

**Files:**
- Create: `app/src/main/java/com/ngcyt/ble/data/companion/CompanionClient.kt`
- Test: `app/src/test/java/com/ngcyt/ble/data/companion/CompanionClientTest.kt`

**CompanionClient** connects to CYT Pi instance:
- REST polling: `GET http://<pi-ip>:5000/api/threats`
- Parses CYT `ThreatAssessment` JSON (Python format) into Kotlin `ThreatAssessment` with `source = WIFI_PI`
- Configurable poll interval
- Connection status tracking (connected/disconnected/error)

**Step 1-4: Test, implement, verify, commit**

```bash
git add app/src/main/java/com/ngcyt/ble/data/companion/ app/src/test/java/com/ngcyt/ble/data/companion/
git commit -m "feat: add Pi companion client for WiFi threat integration"
```

---

## Task 10: Settings & DataStore

**Files:**
- Create: `app/src/main/java/com/ngcyt/ble/data/settings/AppSettings.kt`

**AppSettings** wraps DataStore preferences:
- `ignoreMacs: Set<String>`
- `minAlertScore: Int` (default 40)
- `scanIntervalSeconds: Int` (default 60)
- `retentionDays: Int` (default 30)
- `locationMode: LocationMode` (WIFI_DERIVED, GPS, FUSED, PASSIVE)
- `externalApiUrl: String?`
- `externalApiKey: String?`
- `externalApiMode: ApiMode` (ALERTS_ONLY, FULL_TELEMETRY)
- `companionPiUrl: String?`
- `companionPollIntervalSeconds: Int` (default 30)

**Step 1: Implement**
**Step 2: Commit**

```bash
git add app/src/main/java/com/ngcyt/ble/data/settings/
git commit -m "feat: add DataStore-backed app settings"
```

---

## Task 11: Hilt Dependency Injection Module

**Files:**
- Create: `app/src/main/java/com/ngcyt/ble/di/AppModule.kt`

Provides singletons for: `AppDatabase`, `DetectionEngine`, `BleFingerprintEngineImpl`, `BehaviorSimilarityEngineImpl`, `ExternalApiClient`, `CompanionClient`, `AppSettings`, Ktor `HttpClient`.

**Step 1: Implement**
**Step 2: Verify build**
**Step 3: Commit**

```bash
git add app/src/main/java/com/ngcyt/ble/di/
git commit -m "feat: add Hilt DI module wiring all components"
```

---

## Task 12: UI — Main Activity & Navigation

**Files:**
- Create: `app/src/main/java/com/ngcyt/ble/ui/MainActivity.kt`
- Create: `app/src/main/java/com/ngcyt/ble/ui/NavGraph.kt`
- Create: `app/src/main/java/com/ngcyt/ble/ui/theme/Theme.kt`

Bottom navigation with 4 destinations: Dashboard, Devices, Companion, Settings.

**Step 1: Implement**
**Step 2: Verify build**
**Step 3: Commit**

```bash
git add app/src/main/java/com/ngcyt/ble/ui/
git commit -m "feat: add main activity, navigation, and Material 3 theme"
```

---

## Task 13: UI — Threat Dashboard Screen

**Files:**
- Create: `app/src/main/java/com/ngcyt/ble/ui/dashboard/ThreatDashboardScreen.kt`
- Create: `app/src/main/java/com/ngcyt/ble/ui/dashboard/ThreatDashboardViewModel.kt`
- Create: `app/src/main/java/com/ngcyt/ble/ui/dashboard/ThreatCard.kt`

Dashboard shows:
- Scan status (active/paused, device count)
- Start/Stop scan button
- List of `ThreatAssessment` cards sorted by score
- Each card: MAC, threat level color-coded, score, time windows, RSSI, location
- HIGH/CRITICAL threats have warning styling
- Pull-to-refresh

**Step 1: Implement**
**Step 2: Verify build and manual test**
**Step 3: Commit**

```bash
git add app/src/main/java/com/ngcyt/ble/ui/dashboard/
git commit -m "feat: add threat dashboard with real-time threat cards"
```

---

## Task 14: UI — Device Detail Screen

**Files:**
- Create: `app/src/main/java/com/ngcyt/ble/ui/detail/DeviceDetailScreen.kt`
- Create: `app/src/main/java/com/ngcyt/ble/ui/detail/DeviceDetailViewModel.kt`

Shows for a selected device:
- Full threat assessment details
- Fingerprint info (associated MACs, cluster confidence, service UUIDs, manufacturer data)
- Behavioral similarity matches ("devices behaving similarly")
- Sighting timeline
- Location history (if GPS enabled)
- "Add to ignore list" button

**Step 1: Implement**
**Step 2: Commit**

```bash
git add app/src/main/java/com/ngcyt/ble/ui/detail/
git commit -m "feat: add device detail screen with fingerprint and similarity data"
```

---

## Task 15: UI — Companion & Settings Screens

**Files:**
- Create: `app/src/main/java/com/ngcyt/ble/ui/companion/CompanionScreen.kt`
- Create: `app/src/main/java/com/ngcyt/ble/ui/companion/CompanionViewModel.kt`
- Create: `app/src/main/java/com/ngcyt/ble/ui/settings/SettingsScreen.kt`
- Create: `app/src/main/java/com/ngcyt/ble/ui/settings/SettingsViewModel.kt`

**CompanionScreen:** Pi URL input, connection status, WiFi threats from Pi displayed.
**SettingsScreen:** All settings from Task 10 with UI controls, external API config, ignore list management, location mode picker, data retention, about section.

**Step 1: Implement**
**Step 2: Commit**

```bash
git add app/src/main/java/com/ngcyt/ble/ui/companion/ app/src/main/java/com/ngcyt/ble/ui/settings/
git commit -m "feat: add companion and settings screens"
```

---

## Task 16: Permission Handling

**Files:**
- Create: `app/src/main/java/com/ngcyt/ble/ui/permissions/PermissionScreen.kt`

On first launch, request permissions in order:
1. Bluetooth (BLUETOOTH_SCAN, BLUETOOTH_CONNECT)
2. Location (ACCESS_FINE_LOCATION)
3. Notifications (POST_NOTIFICATIONS, Android 13+)

Show explanation for each permission before requesting. Handle denial gracefully with re-request option.

**Step 1: Implement**
**Step 2: Manual test on device/emulator**
**Step 3: Commit**

```bash
git add app/src/main/java/com/ngcyt/ble/ui/permissions/
git commit -m "feat: add runtime permission request flow"
```

---

## Task 17: Notification Channels & Threat Alerts

**Files:**
- Modify: `app/src/main/java/com/ngcyt/ble/NgcytBleApplication.kt` — create notification channels
- Create: `app/src/main/java/com/ngcyt/ble/notification/ThreatNotificationManager.kt`

Two notification channels:
1. **Scan Status** (low priority) — persistent foreground service notification
2. **Threat Alerts** (high priority) — fires for HIGH/CRITICAL threats

`ThreatNotificationManager` registered as alert callback on `DetectionEngine`.

**Step 1: Implement**
**Step 2: Commit**

```bash
git add app/src/main/java/com/ngcyt/ble/notification/ app/src/main/java/com/ngcyt/ble/NgcytBleApplication.kt
git commit -m "feat: add notification channels and threat alert notifications"
```

---

## Task 18: Integration Test & Final Wiring

**Files:**
- Verify all Hilt wiring compiles
- Test: `app/src/test/java/com/ngcyt/ble/integration/FullPipelineTest.kt`

Write an integration test that simulates:
1. 5 BLE advertisements from MAC-A across time windows
2. 3 BLE advertisements from MAC-B with same service UUIDs (should correlate via fingerprinting)
3. Verify threat score reflects correlated device
4. Verify similarity engine finds MAC-A and MAC-B as similar
5. Verify `ThreatAssessment.toMap()` produces valid JSON for external API

**Step 1: Write test**
**Step 2: Run and verify**
**Step 3: Commit**

```bash
git add app/src/test/java/com/ngcyt/ble/integration/
git commit -m "test: add full pipeline integration test"
```

---

## Task 19: README

**Files:**
- Create: `README.md`

Cover: what it does, how it relates to CYT, install/build instructions, permissions explained, architecture overview, BLE vs WiFi comparison table, external API setup, Pi companion setup.

**Step 1: Write**
**Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add project README"
```
