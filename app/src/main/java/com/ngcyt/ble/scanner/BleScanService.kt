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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ngcyt.ble.domain.detection.DetectionEngine
import com.ngcyt.ble.domain.fingerprint.BleFingerprintEngineImpl
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@AndroidEntryPoint
class BleScanService : Service() {

    companion object {
        private const val TAG = "BleScanService"

        const val ACTION_START_SCAN = "com.ngcyt.ble.START_SCAN"
        const val ACTION_STOP_SCAN = "com.ngcyt.ble.STOP_SCAN"
        const val CHANNEL_SCAN_STATUS = "scan_status"
        const val CHANNEL_THREAT_ALERT = "threat_alert"
        const val NOTIFICATION_ID = 1
        const val SCAN_INTERVAL_MS = 60_000L
        const val BUCKET_ROTATION_MS = 120_000L // 2 minutes
        private const val MAX_SCAN_RETRIES = 3

        private val _isScanning = MutableStateFlow(false)
        val isScanning: StateFlow<Boolean> = _isScanning

        private val _scanStatus = MutableStateFlow(ScanStatus.IDLE)
        val scanStatus: StateFlow<ScanStatus> = _scanStatus

        private val _threatCount = MutableStateFlow(0)
        val threatCount: StateFlow<Int> = _threatCount

        // Reference to the active service instance for alarm-driven bucket rotation
        private var activeInstance: BleScanService? = null

        fun onBucketRotationAlarm() {
            activeInstance?.performBucketRotation()
        }
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    @Inject lateinit var detectionEngine: DetectionEngine
    @Inject lateinit var fingerprintEngine: BleFingerprintEngineImpl
    private var locationManager: LocationManager? = null
    private var currentLocation: Location? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var scanStartTime = 0L
    private var lastBucketRotation = 0L
    private var bucketGeneration = 0
    private var scanRetryCount = 0

    // ── Bluetooth State BroadcastReceiver ──────────────────────────────────────

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            when (state) {
                BluetoothAdapter.STATE_OFF -> {
                    Log.w(TAG, "Bluetooth turned OFF — pausing scan")
                    pauseScanning()
                    _scanStatus.value = ScanStatus.BLUETOOTH_OFF
                    _isScanning.value = false
                }
                BluetoothAdapter.STATE_ON -> {
                    Log.i(TAG, "Bluetooth turned ON — re-acquiring scanner and restarting")
                    bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
                    scanRetryCount = 0
                    startBleScan()
                }
            }
        }
    }

    // ── Scan Callback with failure recovery ────────────────────────────────────

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            processScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { processScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with errorCode=$errorCode (retryCount=$scanRetryCount)")
            when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> {
                    // Error code 1: stop then restart
                    Log.w(TAG, "Scan already started — stopping and restarting")
                    try { bluetoothLeScanner?.stopScan(this) } catch (_: SecurityException) {}
                    if (scanRetryCount < MAX_SCAN_RETRIES) {
                        scanRetryCount++
                        startBleScan()
                    } else {
                        _scanStatus.value = ScanStatus.SCAN_FAILED
                        _isScanning.value = false
                    }
                }
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> {
                    // Error code 2: wait 1s and retry once
                    if (scanRetryCount < MAX_SCAN_RETRIES) {
                        scanRetryCount++
                        serviceScope.launch {
                            delay(1000)
                            startBleScan()
                        }
                    } else {
                        _scanStatus.value = ScanStatus.SCAN_FAILED
                        _isScanning.value = false
                    }
                }
                else -> {
                    // Other errors: don't retry indefinitely
                    _scanStatus.value = ScanStatus.SCAN_FAILED
                    _isScanning.value = false
                }
            }
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
        activeInstance = this
        createNotificationChannels()

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        // Acquire partial wake lock to keep CPU alive during scan processing
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ngcytble:scan").apply {
            acquire()
        }

        // Register Bluetooth state receiver
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, filter)
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

        // ── Pre-checks: Bluetooth + Location ──────────────────────────────────

        // Check Bluetooth is enabled
        if (bluetoothAdapter?.isEnabled != true) {
            Log.w(TAG, "Bluetooth is not enabled — cannot start scan")
            _scanStatus.value = ScanStatus.BLUETOOTH_OFF
            _isScanning.value = false
            return
        }

        // On API < 31, location services are required for BLE scanning
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val lm = locationManager
            if (lm != null && !lm.isLocationEnabled) {
                Log.w(TAG, "Location services disabled — required for BLE scan on API < 31")
                _scanStatus.value = ScanStatus.LOCATION_REQUIRED
                _isScanning.value = false
                return
            }
        }

        // Start location updates (optional — app works without location permission on API 31+)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, SCAN_INTERVAL_MS, 0f, locationListener)
            } catch (_: Exception) {}
        }

        scanRetryCount = 0
        scanStartTime = System.currentTimeMillis()
        lastBucketRotation = scanStartTime
        startBleScan()
        scheduleBucketRotationAlarm()
    }

    /**
     * Starts the actual BLE scan. Separated from startScanning() so it can be
     * called independently for retries and Bluetooth-ON recovery.
     */
    private fun startBleScan() {
        if (bluetoothAdapter?.isEnabled != true) {
            _scanStatus.value = ScanStatus.BLUETOOTH_OFF
            _isScanning.value = false
            return
        }

        val scanner = bluetoothLeScanner ?: bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null — cannot start scan")
            _scanStatus.value = ScanStatus.SCAN_FAILED
            _isScanning.value = false
            return
        }
        bluetoothLeScanner = scanner

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
            _isScanning.value = true
            _scanStatus.value = ScanStatus.SCANNING
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting scan — missing permission", e)
            _scanStatus.value = ScanStatus.PERMISSION_DENIED
            _isScanning.value = false
            stopSelf()
        }
    }

    /**
     * Pauses the BLE scan without tearing down the entire service.
     * Used when Bluetooth is toggled off.
     */
    private fun pauseScanning() {
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {}
        cancelBucketRotationAlarm()
    }

    private fun stopScanning() {
        pauseScanning()

        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: Exception) {}

        _isScanning.value = false
        _scanStatus.value = ScanStatus.IDLE
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun processScanResult(result: ScanResult) {
        val parsed = ScanResultParser.parse(result)
        val now = System.currentTimeMillis() / 1000.0
        val loc = currentLocation

        // Feed into fingerprinting engine directly with parsed fields
        val cluster = fingerprintEngine.processAdvertisement(
            mac = parsed.mac,
            serviceUuids = parsed.serviceUuids,
            manufacturerData = parsed.manufacturerData,
            txPower = parsed.txPower,
            rssi = parsed.rssi,
            timestamp = now,
            deviceName = parsed.deviceName,
        )

        // Feed into detection engine — pass fingerprint correlation result
        val assessment = detectionEngine.analyzeDevice(
            mac = parsed.mac,
            deviceType = "BLE",
            timestamp = now,
            timeBucket = "bucket_$bucketGeneration",
            serviceUuid = parsed.serviceUuids.firstOrNull(),
            correlatedCluster = cluster,
            latitude = loc?.latitude,
            longitude = loc?.longitude,
            locationAccuracy = loc?.accuracy,
            locationProvider = loc?.provider,
        )

        // Update threat count
        val threats = detectionEngine.getAllThreats()
        _threatCount.value = threats.size
        updateNotification(threats.size)
    }

    // ── AlarmManager-based bucket rotation (survives Doze) ─────────────────────

    private fun scheduleBucketRotationAlarm() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, BucketRotationReceiver::class.java).apply {
            action = BucketRotationReceiver.ACTION_ROTATE_BUCKET
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = System.currentTimeMillis() + BUCKET_ROTATION_MS
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    private fun cancelBucketRotationAlarm() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, BucketRotationReceiver::class.java).apply {
            action = BucketRotationReceiver.ACTION_ROTATE_BUCKET
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Called from [BucketRotationReceiver] when the alarm fires.
     */
    private fun performBucketRotation() {
        detectionEngine.rotateTimeBuckets()
        lastBucketRotation = System.currentTimeMillis()
        bucketGeneration++
        if (_isScanning.value) {
            scheduleBucketRotationAlarm()
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
        // Unregister Bluetooth state receiver
        try { unregisterReceiver(bluetoothStateReceiver) } catch (_: Exception) {}

        // Cancel bucket rotation alarm
        cancelBucketRotationAlarm()

        // Release wake lock
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null

        activeInstance = null

        stopScanning()
        super.onDestroy()
    }
}
