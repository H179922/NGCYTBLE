package com.ngcyt.ble.scanner

import android.Manifest
import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ngcyt.ble.domain.detection.DetectionEngine
import com.ngcyt.ble.domain.fingerprint.BleFingerprintEngineImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BleScanService : Service() {

    companion object {
        const val ACTION_START_SCAN = "com.ngcyt.ble.START_SCAN"
        const val ACTION_STOP_SCAN = "com.ngcyt.ble.STOP_SCAN"
        const val CHANNEL_SCAN_STATUS = "scan_status"
        const val CHANNEL_THREAT_ALERT = "threat_alert"
        const val NOTIFICATION_ID = 1
        const val SCAN_INTERVAL_MS = 60_000L
        const val BUCKET_ROTATION_MS = 300_000L // 5 minutes

        private val _isScanning = MutableStateFlow(false)
        val isScanning: StateFlow<Boolean> = _isScanning

        private val _threatCount = MutableStateFlow(0)
        val threatCount: StateFlow<Int> = _threatCount
    }

    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var detectionEngine: DetectionEngine? = null
    private var fingerprintEngine: BleFingerprintEngineImpl? = null
    private var locationManager: LocationManager? = null
    private var currentLocation: Location? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private var scanStartTime = 0L
    private var lastBucketRotation = 0L
    private var currentTimeBucket = "current"

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            processScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { processScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location
        }
        override fun onProviderDisabled(provider: String) {}
        override fun onProviderEnabled(provider: String) {}
        @Deprecated("Deprecated")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothLeScanner = bluetoothManager.adapter?.bluetoothLeScanner

        fingerprintEngine = BleFingerprintEngineImpl()
        detectionEngine = DetectionEngine(fingerprintEngine = fingerprintEngine)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SCAN -> startScanning()
            ACTION_STOP_SCAN -> stopScanning()
        }
        return START_STICKY
    }

    private fun startScanning() {
        val notification = buildNotification("Scanning for BLE devices...", 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Start location updates
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, SCAN_INTERVAL_MS, 0f, locationListener)
            } catch (_: Exception) {}
        }

        // Start BLE scanning
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        try {
            bluetoothLeScanner?.startScan(null, settings, scanCallback)
            _isScanning.value = true
            scanStartTime = System.currentTimeMillis()
            lastBucketRotation = scanStartTime
            scheduleTimeBucketRotation()
        } catch (e: SecurityException) {
            _isScanning.value = false
            stopSelf()
        }
    }

    private fun stopScanning() {
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {}

        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: Exception) {}

        _isScanning.value = false
        handler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun processScanResult(result: ScanResult) {
        val parsed = ScanResultParser.parse(result)
        val now = System.currentTimeMillis() / 1000.0
        val loc = currentLocation

        // Feed into fingerprinting engine directly with parsed fields
        fingerprintEngine?.processAdvertisement(
            mac = parsed.mac,
            serviceUuids = parsed.serviceUuids,
            manufacturerData = parsed.manufacturerData,
            txPower = parsed.txPower,
            rssi = parsed.rssi,
            timestamp = now,
            deviceName = parsed.deviceName,
        )

        // Feed into detection engine
        val assessment = detectionEngine?.analyzeDevice(
            mac = parsed.mac,
            deviceType = "BLE",
            timestamp = now,
            timeBucket = currentTimeBucket,
            serviceUuid = parsed.serviceUuids.firstOrNull(),
            latitude = loc?.latitude,
            longitude = loc?.longitude,
            locationAccuracy = loc?.accuracy,
            locationProvider = loc?.provider,
        )

        // Update threat count
        val threats = detectionEngine?.getAllThreats() ?: emptyList()
        _threatCount.value = threats.size
        updateNotification(threats.size)
    }

    private fun scheduleTimeBucketRotation() {
        handler.postDelayed({
            detectionEngine?.rotateTimeBuckets()
            lastBucketRotation = System.currentTimeMillis()
            updateTimeBucket()
            if (_isScanning.value) {
                scheduleTimeBucketRotation()
            }
        }, BUCKET_ROTATION_MS)
    }

    private fun updateTimeBucket() {
        val elapsed = System.currentTimeMillis() - scanStartTime
        currentTimeBucket = when {
            elapsed < 300_000 -> "current"
            elapsed < 600_000 -> "0-5min"
            else -> "current" // Reset after rotation
        }
    }

    private fun createNotificationChannels() {
        val scanChannel = NotificationChannel(
            CHANNEL_SCAN_STATUS,
            "Scan Status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "BLE scanning status" }

        val threatChannel = NotificationChannel(
            CHANNEL_THREAT_ALERT,
            "Threat Alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Surveillance threat alerts"
            enableVibration(true)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(scanChannel)
        manager.createNotificationChannel(threatChannel)
    }

    private fun buildNotification(text: String, threatCount: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_SCAN_STATUS)
            .setContentTitle("NGCYTBLE Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(threatCount: Int) {
        val text = if (threatCount > 0) "$threatCount threat(s) detected" else "Scanning..."
        val notification = buildNotification(text, threatCount)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        stopScanning()
        super.onDestroy()
    }
}
