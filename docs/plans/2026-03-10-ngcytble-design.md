# NGCYTBLE — BLE Surveillance Detection for Android

**Date:** 2026-03-10
**Status:** Approved
**Parent project:** NextGen_CYT

## Purpose

Port CYT's surveillance detection system to a standalone Android app using Bluetooth Low Energy instead of WiFi probe requests. Includes full fingerprinting engine, behavioral similarity search, threat scoring, Pi companion mode, and external API push — everything except WiFi monitor mode (which Android doesn't support).

## Architecture

```
┌─────────────────────────────────────────────────┐
│                   NGCYTBLE App                   │
├─────────────────────────────────────────────────┤
│  UI Layer (Jetpack Compose)                     │
│  ├── ThreatDashboard      - Main threat list    │
│  ├── DeviceDetailScreen   - Per-device deep dive│
│  ├── CompanionScreen      - Pi connection mgmt  │
│  └── SettingsScreen       - Config & ignore lists│
├─────────────────────────────────────────────────┤
│  Domain Layer                                    │
│  ├── DetectionEngine      - Threat scoring       │
│  ├── BleFingerprint       - MAC randomization    │
│  │                          defeat               │
│  ├── BehaviorSimilarity   - Vector similarity    │
│  │                          search               │
│  └── DeviceTracker        - Time-window bucketing│
├─────────────────────────────────────────────────┤
│  Data Layer                                      │
│  ├── BleScanner           - Android BLE scanning │
│  │                          service              │
│  ├── CompanionClient      - REST/WebSocket to Pi │
│  ├── ExternalApiClient    - Push data to user API│
│  ├── DeviceDatabase       - Room (local SQLite)  │
│  └── SettingsStore        - DataStore preferences│
├─────────────────────────────────────────────────┤
│  Platform                                        │
│  ├── ForegroundService    - Keeps scanning alive │
│  ├── NotificationManager  - Threat alerts        │
│  └── LocationProvider     - WiFi-derived default │
└─────────────────────────────────────────────────┘
```

### Key Platform Decisions

- **Room** for local device history (replaces Kismet SQLite).
- **Foreground service** for continuous BLE scanning — Android kills background BLE scanning after ~10 minutes; a foreground service with persistent notification is required.
- **On-device cosine similarity** instead of ChromaDB for behavioral similarity search. ChromaDB is Python-only with no JVM/Android port. For 11-dimensional vectors with <500 tracked devices, brute-force cosine similarity runs in microseconds. ChromaDB's value (approximate nearest neighbor at millions of vectors) doesn't apply at phone-scale device counts.
- **WiFi-derived location as default** — low battery, no GPS permission prompt on first launch. GPS available as opt-in for users wanting precision.
- **Jetpack Compose** for UI — modern, less boilerplate, good for real-time updating dashboards.

## BLE Fingerprinting Engine

Port of CYT's `DeviceFingerprintEngine`, mapping WiFi signals to BLE equivalents.

### Data Model

```
BleDeviceFingerprint
├── primary_id: String              (MAC or cluster ID)
├── associated_macs: Set<String>    (all MACs for this physical device)
├── service_uuids: Set<UUID>        (advertised BLE services)
├── manufacturer_data: Map<Int, ByteArray>  (company ID → payload)
├── device_name: String?            (broadcast name if available)
├── advertising: BleAdvertisingProfile
│   ├── ad_interval_ms: Float       (characteristic interval)
│   ├── interval_variance: Float    (consistency measure)
│   ├── connectable: Boolean
│   ├── tx_power: Int
│   └── phy_type: Int               (1M, 2M, Coded)
├── timing: TimingProfile           (port from CYT — burst/interval patterns)
├── rssi: RssiProfile               (signal strength over time)
└── cluster_confidence: Float
```

### Signal Mapping: WiFi → BLE

| WiFi Signal (CYT) | BLE Equivalent (NGCYTBLE) | Strength | Rationale |
|--------------------|---------------------------|----------|-----------|
| SSID probe lists | Service UUIDs advertised | Strong | Unique combo of services (fitness + audio + Find My) is identifying. Filter ubiquitous UUIDs. |
| Radio/HT/VHT capabilities | Ad flags + TX power + PHY type | Moderate | Hardware-dependent characteristics that don't change with MAC rotation. |
| Probe timing patterns | Advertisement interval patterns | Strong | Each device/OS has characteristic ad intervals and burst behavior. |
| Frame sequence numbers | *Dropped* | N/A | No BLE equivalent. |
| Signal strength (RSSI) | RSSI patterns | Same | Ports directly — consistent signal profile = same radio. |
| *N/A* | Manufacturer-specific data | Strong | BLE-unique signal. Apple/Google ad payloads contain device type bytes that persist across MAC rotations. |
| *N/A* | Device name (when broadcast) | Moderate | Some devices broadcast names that survive MAC rotation. |

### Correlation Scoring

| Signal | Max Points | Logic |
|--------|-----------|-------|
| Service UUID combination | 35 pts | Jaccard similarity on unique service sets. Filter out ubiquitous UUIDs (Generic Access, Generic Attribute, etc.). Scale by match count: 3+ matches = full score. |
| Manufacturer data match | 30 pts | Company ID + payload structure comparison. Apple/Google payloads contain device type bytes that persist across MAC rotations. Byte-level similarity with tolerance for changing fields. |
| Ad timing pattern | 15 pts | Mean interval and burst characteristic match (ported from CYT's `TimingProfile.similarity()`). Allow 20% variance. |
| TX power + PHY match | 10 pts | Exact match on hardware characteristics. |
| RSSI behavior pattern | 10 pts | Variance-based comparison — low variance across sightings suggests same device at consistent distance. |

**Clustering threshold:** 45 points to link MACs. Lower than CYT's WiFi threshold (50) because individual BLE signals are weaker than SSID probe lists, but we have more independent signals. High confidence at 70+.

## Threat Scoring Engine

Direct port of CYT's `DetectionEngine._calculate_threat_score`.

### Scoring Factors

| Factor | Points | Notes |
|--------|--------|-------|
| Time bucket presence | 20 pts per bucket (max 60) | Same 5-minute windows as CYT |
| Duration 15+ min | 10 pts | Continuous presence bonus |
| Duration 20+ min | 20 pts | Replaces 15+ min bonus |
| Service UUID match across windows | 10 pts | Replaces SSID probe matching |
| MAC correlation (fingerprinting linked) | 15 pts | Device using BLE MAC rotation to evade detection |

### Threat Levels

Matches CYT's `chasing_your_tail.py` thresholds:

| Score | Level |
|-------|-------|
| 0-24 | MINIMAL |
| 25-49 | LOW |
| 50-74 | MEDIUM |
| 75-89 | HIGH |
| 90-100 | CRITICAL |

### Alert Format

```
[HIGH] AA:BB:CC:DD:EE:FF (BLE) | Score: 75/100 | RSSI: -42dBm | Windows: 5-10min, 10-15min, 15-20min
  Correlated MACs: 3 linked via fingerprinting
  Location: 40.7128, -74.0060 (WiFi, ±50m)
```

## Behavioral Similarity Engine

Port of CYT's `BehaviorSimilarityEngine`. Runs on-device using Room-stored vectors and Kotlin cosine similarity.

### Behavior Vector (11 dimensions)

| # | CYT Dimension | NGCYTBLE Dimension | BLE Source |
|---|---------------|-------------------|-----------|
| 1 | probe_frequency | ad_frequency | How often device broadcasts (normalized to 0-1) |
| 2 | probe_regularity | ad_regularity | Coefficient of variation of ad intervals |
| 3 | burst_tendency | burst_tendency | Ratio of short (<500ms) intervals to total |
| 4 | ssid_count | service_count | Number of unique service UUIDs (normalized) |
| 5 | unique_ssid_ratio | unique_service_ratio | Ratio of uncommon to common services |
| 6 | ssid_entropy | service_entropy | Shannon entropy of service advertisement distribution |
| 7 | active_duration | active_duration | How long device present (normalized to 1hr max) |
| 8 | time_consistency | time_consistency | Consistency of appearance times |
| 9 | uses_randomization | uses_randomization | Detected BLE MAC rotation (0 or 1) |
| 10 | probes_hidden | minimal_advertisement | Device advertising with minimal/no payload (evasive behavior) |
| 11 | high_mobility | high_mobility | Appears/disappears frequently |

### Implementation

```kotlin
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    var dot = 0f; var normA = 0f; var normB = 0f
    for (i in a.indices) { dot += a[i] * b[i]; normA += a[i] * a[i]; normB += b[i] * b[i] }
    return dot / (sqrt(normA) * sqrt(normB))
}
```

- Vectors stored in Room `behavior_vectors` table as `BLOB` columns
- "Find similar" = scan all vectors, compute cosine similarity, return top N above threshold
- "Find suspicious" = compare against a predefined suspicious-pattern vector (same as CYT's `create_suspicious_pattern_vector()`)
- At <500 devices, full scan is sub-millisecond

## Location

### Strategy (layered, best-available)

| Source | Accuracy | Battery | Availability |
|--------|----------|---------|-------------|
| WiFi-derived (FLP) | ~30-100m | Low | **Default** |
| GPS/GNSS | ~3m | High | Opt-in via settings |
| Fused (GPS + WiFi + cell) | ~10-50m | Medium | Opt-in via settings |
| Cell tower only | ~300m-2km | Minimal | Fallback |

- **Default: WiFi-derived** — low battery, no GPS permission on first launch.
- Each `DeviceSighting` stamped with `lat/lng/accuracy/provider`.
- Location strengthens fingerprinting: two MACs at same GPS coordinates within seconds of each other = additional correlation evidence.

## External API

Push data from the device to a user-configured endpoint.

```
ExternalApiClient
├── endpoint_url: String          (user-configured)
├── api_key: String               (Authorization header)
├── mode: ALERTS_ONLY | FULL_TELEMETRY
├── batch_interval_seconds: Int   (default 30)
└── retry_policy: ExponentialBackoff
```

### Modes

- **ALERTS_ONLY** — POST `ThreatAssessment` JSON when score crosses configured threshold.
- **FULL_TELEMETRY** — All sightings, fingerprint correlations, similarity matches, location data. Batched on interval to avoid network hammering.

Both modes include WiFi-derived location and timestamps. Outbound data queued in `api_sync_queue` Room table for offline resilience.

## Pi Companion Mode

- Connects to CYT instance via REST (`http://<pi-ip>:5000/api/threats`)
- Polls on configurable interval (or WebSocket if CYT exposes one)
- Merges WiFi-based threats from Pi with local BLE threats into unified dashboard
- Each threat tagged with source: `BLE_LOCAL` or `WIFI_PI`

## Data Persistence (Room)

### Tables

| Table | Purpose |
|-------|---------|
| `device_sightings` | Every BLE sighting: timestamp, MAC, RSSI, service UUIDs, manufacturer data, location |
| `device_fingerprints` | Fingerprint data per MAC, cluster associations, correlation scores |
| `behavior_vectors` | 11-dim float vectors as BLOB, device ID, metadata |
| `threat_assessments` | Historical threat records with full assessment data |
| `api_sync_queue` | Outbound data waiting to push to external API |

**Retention:** Configurable, default 30 days. Stale data cleanup mirrors CYT's `cleanup_stale_devices` — removes devices not seen for configurable max age, caps total tracked devices.

## Android Permissions

| Permission | Why | Required |
|-----------|-----|----------|
| `BLUETOOTH_SCAN` | BLE scanning | Yes |
| `BLUETOOTH_CONNECT` | Device name resolution | Yes |
| `ACCESS_FINE_LOCATION` | Required by Android for BLE scanning | Yes |
| `ACCESS_COARSE_LOCATION` | WiFi-derived location | Yes |
| `FOREGROUND_SERVICE` | Keep scanning alive | Yes |
| `POST_NOTIFICATIONS` | Threat alerts | Yes |
| `INTERNET` | External API, companion mode | Yes |
| `ACCESS_WIFI_STATE` | WiFi-derived location | Yes |

## Tech Stack

- **Language:** Kotlin
- **Min SDK:** 26 (Android 8.0) — covers 95%+ of active devices
- **UI:** Jetpack Compose + Material 3
- **DI:** Hilt
- **Database:** Room
- **Preferences:** DataStore
- **Networking:** Ktor Client (lightweight, Kotlin-native)
- **Background:** Foreground Service + WorkManager for periodic cleanup
- **Build:** Gradle with Kotlin DSL
