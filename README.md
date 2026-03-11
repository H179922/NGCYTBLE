# NGCYTBLE — BLE Surveillance Detection for Android

Detect if you're being followed using Bluetooth Low Energy. NGCYTBLE is a full port of [Chasing Your Tail (CYT)](https://github.com/H179922/CYT) from Raspberry Pi WiFi probe request analysis to Android BLE scanning. It monitors nearby BLE devices over time, defeats MAC address randomization through multi-signal fingerprinting, and scores threats on a 0-100 scale.

Released under the MIT License - https://opensource.org/licenses/MIT

## Relationship to CYT

CYT runs on a Raspberry Pi with an external WiFi adapter in monitor mode, capturing 802.11 probe requests via Kismet. NGCYTBLE brings the same detection logic to any Android phone using the BLE radio that's already built in — no special hardware, no root, no monitor mode.

Use NGCYTBLE standalone or connect it to a running CYT instance via **Pi Companion Mode** for a merged WiFi + BLE threat view.

## Features

- **Threat Scoring (0-100)** - Five threat levels from MINIMAL to CRITICAL, matching CYT's scoring engine
- **BLE MAC Randomization Defeat** - Fingerprints physical devices across MAC rotations using 5 independent signals (service UUIDs, manufacturer data, ad timing, TX power/PHY, RSSI patterns)
- **Behavioral Similarity Search** - 11-dimensional behavior vectors with on-device cosine similarity to find devices that act like known threats
- **Real-time Threat Dashboard** - Jetpack Compose UI with color-coded threat cards and live updates
- **Device Detail View** - Per-device deep dive with fingerprint data, correlated MACs, and similarity matches
- **Pi Companion Mode** - Connect to a CYT Raspberry Pi instance for merged WiFi + BLE threat assessment
- **External API Push** - Forward threat data to your own endpoint in ALERTS_ONLY or FULL_TELEMETRY mode
- **Foreground Service** - Continuous background BLE scanning that survives Android's background execution limits
- **Runtime Permission Handling** - Guided permission flow for BLE, location, and notification access
- **Configurable Settings** - Alert threshold, scan interval, location mode, data retention period, device ignore lists

## How Detection Works

### Threat Scoring

NGCYTBLE calculates a threat score (0-100) for each detected BLE device based on four factors:

| Factor | Points | Notes |
|--------|--------|-------|
| Time bucket presence | 20 pts per bucket (max 60) | Same 5-minute windows as CYT |
| Duration 15+ min | 10 pts | Continuous presence bonus |
| Duration 20+ min | 20 pts | Replaces the 15+ min bonus |
| Service UUID match across windows | 10 pts | BLE equivalent of SSID probe matching |
| MAC correlation (fingerprint-linked) | 15 pts | Device using BLE MAC rotation to evade detection |

Alerts include signal strength, threat level, and which time windows the device was seen in:
```
[HIGH] AA:BB:CC:DD:EE:FF (BLE) | Score: 75/100 | RSSI: -42dBm | Windows: 5-10min, 10-15min, 15-20min
  Correlated MACs: 3 linked via fingerprinting
  Location: 40.7128, -74.0060 (WiFi, +/-50m)
```

### Threat Levels

| Score | Level | Meaning |
|-------|-------|---------|
| 0-24 | MINIMAL | Single brief sighting, likely passerby |
| 25-49 | LOW | Seen in 2 time periods |
| 50-74 | MEDIUM | Seen in 3 time periods, moderate signal consistency |
| 75-89 | HIGH | Seen in 4+ time periods, consistent signal strength |
| 90-100 | CRITICAL | Persistent presence, strong pattern, steady RSSI |

### BLE Fingerprinting (MAC Randomization Defeat)

Modern phones rotate their BLE MAC address to avoid tracking. NGCYTBLE's fingerprinting engine defeats this by correlating devices across five signals, mapped from CYT's WiFi equivalents:

| WiFi Signal (CYT) | BLE Equivalent (NGCYTBLE) | Max Points | Rationale |
|--------------------|---------------------------|------------|-----------|
| SSID probe lists | Service UUIDs advertised | 35 pts | Unique combo of services (fitness + audio + Find My) is identifying. Ubiquitous UUIDs filtered out. |
| Radio/HT/VHT capabilities | TX power + PHY type | 10 pts | Hardware-dependent characteristics that don't change with MAC rotation. |
| Probe timing patterns | Advertisement interval patterns | 15 pts | Each device/OS has characteristic ad intervals and burst behavior. |
| Frame sequence numbers | *Dropped* | N/A | No BLE equivalent exists. |
| Signal strength (RSSI) | RSSI behavior patterns | 10 pts | Consistent signal profile = same radio at consistent distance. |
| *N/A (BLE-only)* | Manufacturer-specific data | 30 pts | Apple/Google ad payloads contain device type bytes that persist across MAC rotations. Byte-level similarity with tolerance for changing fields. |

**Clustering threshold:** 45 points to link MACs as the same physical device. Lower than CYT's WiFi threshold (50) because individual BLE signals are weaker than SSID probe lists, but more independent signals are available. High confidence at 70+.

### Behavioral Similarity Search

Each tracked device gets an 11-dimensional behavior vector. NGCYTBLE computes cosine similarity on-device (no server needed) to answer queries like "find all devices behaving like this suspicious one" or "show me devices matching a surveillance pattern."

| # | Dimension | BLE Source |
|---|-----------|-----------|
| 1 | ad_frequency | How often device broadcasts (normalized to 0-1) |
| 2 | ad_regularity | Coefficient of variation of ad intervals |
| 3 | burst_tendency | Ratio of short (<500ms) intervals to total |
| 4 | service_count | Number of unique service UUIDs (normalized) |
| 5 | unique_service_ratio | Ratio of uncommon to common services |
| 6 | service_entropy | Shannon entropy of service advertisement distribution |
| 7 | active_duration | How long device present (normalized to 1hr max) |
| 8 | time_consistency | Consistency of appearance times |
| 9 | uses_randomization | Detected BLE MAC rotation (0 or 1) |
| 10 | minimal_advertisement | Device advertising with minimal/no payload (evasive behavior) |
| 11 | high_mobility | Appears/disappears frequently |

Vectors are stored in Room as BLOBs. At <500 tracked devices, a full similarity scan runs in sub-millisecond time — no need for ChromaDB or approximate nearest neighbor indexing.

## BLE vs WiFi Comparison

| Feature | CYT (WiFi) | NGCYTBLE (BLE) |
|---------|------------|----------------|
| Platform | Raspberry Pi (Linux) | Any Android phone |
| Signal source | WiFi probe requests (802.11) | BLE advertisements |
| Capture method | Kismet + monitor mode adapter | Android BLE scanner API |
| MAC defeat signals | SSIDs, radio caps, timing, frame seq, RSSI | Service UUIDs, manufacturer data, ad timing, TX/PHY, RSSI |
| Similarity engine | ChromaDB (Python, server-side) | On-device cosine similarity (Kotlin) |
| Location | GPS/manual | WiFi-derived (default), GPS opt-in |
| UI | Web dashboard (Flask) | Native Android (Jetpack Compose) |
| Background operation | systemd service | Android foreground service |
| Notifications | Pushover / Telegram | Android notifications |
| Root/special hardware | Monitor mode WiFi adapter required | No root, no special hardware |

## Architecture

```
+---------------------------------------------------+
|                   NGCYTBLE App                     |
+---------------------------------------------------+
|  UI Layer (Jetpack Compose)                        |
|  +-- ThreatDashboard      - Main threat list       |
|  +-- DeviceDetailScreen   - Per-device deep dive   |
|  +-- CompanionScreen      - Pi connection mgmt     |
|  +-- SettingsScreen       - Config & ignore lists   |
+---------------------------------------------------+
|  Domain Layer                                      |
|  +-- DetectionEngine      - Threat scoring          |
|  +-- BleFingerprint       - MAC randomization       |
|  |                          defeat                  |
|  +-- BehaviorSimilarity   - Vector similarity       |
|  |                          search                  |
|  +-- DeviceTracker        - Time-window bucketing   |
+---------------------------------------------------+
|  Data Layer                                        |
|  +-- BleScanner           - Android BLE scanning    |
|  |                          service                 |
|  +-- CompanionClient      - REST/WebSocket to Pi    |
|  +-- ExternalApiClient    - Push data to user API   |
|  +-- DeviceDatabase       - Room (local SQLite)     |
|  +-- SettingsStore        - DataStore preferences   |
+---------------------------------------------------+
|  Platform                                          |
|  +-- ForegroundService    - Keeps scanning alive    |
|  +-- NotificationManager  - Threat alerts           |
|  +-- LocationProvider     - WiFi-derived default    |
+---------------------------------------------------+
```

### Room Database Tables

| Table | Purpose |
|-------|---------|
| `device_sightings` | Every BLE sighting: timestamp, MAC, RSSI, service UUIDs, manufacturer data, location |
| `device_fingerprints` | Fingerprint data per MAC, cluster associations, correlation scores |
| `behavior_vectors` | 11-dim float vectors as BLOB, device ID, metadata |
| `threat_assessments` | Historical threat records with full assessment data |
| `api_sync_queue` | Outbound data waiting to push to external API |

**Retention:** Configurable, default 30 days. Stale data cleanup mirrors CYT's `cleanup_stale_devices`.

## Build Instructions

### Requirements

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 35 (target)
- Android SDK 26 (minimum — Android 8.0)

### Build

```bash
git clone https://github.com/H179922/NGCYTBLE.git
cd NGCYTBLE
```

Open in Android Studio, sync Gradle, and build. Or from the command line:

```bash
./gradlew assembleDebug
```

Install on a connected device:

```bash
./gradlew installDebug
```

**Min SDK 26** (Android 8.0) covers 95%+ of active Android devices.

## Permissions

| Permission | Why | Required |
|------------|-----|----------|
| `BLUETOOTH_SCAN` | BLE scanning | Yes |
| `BLUETOOTH_CONNECT` | Device name resolution | Yes |
| `ACCESS_FINE_LOCATION` | Required by Android for BLE scan results | Yes |
| `ACCESS_COARSE_LOCATION` | WiFi-derived location | Yes |
| `FOREGROUND_SERVICE` | Keep scanning alive in background | Yes |
| `POST_NOTIFICATIONS` | Threat alert notifications | Yes |
| `INTERNET` | External API push, Pi companion mode | Yes |
| `ACCESS_WIFI_STATE` | WiFi-derived location | Yes |

NGCYTBLE requests permissions at runtime with explanations. BLE scanning on Android requires location permission by OS policy — NGCYTBLE uses WiFi-derived location by default (low battery, ~30-100m accuracy). GPS is available as an opt-in for users wanting precision.

## Pi Companion Setup

Connect NGCYTBLE to a running CYT instance on your Raspberry Pi for merged WiFi + BLE threat detection.

1. Ensure CYT is running with the web dashboard enabled on your Pi
2. Note your Pi's IP address (e.g., `192.168.1.100`)
3. In NGCYTBLE, open **Settings > Pi Companion**
4. Enter the Pi URL: `http://192.168.1.100:5000`
5. Set the poll interval (default: 30 seconds)

NGCYTBLE will poll `http://<pi-ip>:5000/api/threats` and merge WiFi-based threats from the Pi with local BLE threats into a unified dashboard. Each threat is tagged with its source: `BLE_LOCAL` or `WIFI_PI`.

Both devices should be on the same network. If connecting over the internet, use a VPN or SSH tunnel — CYT's API has no authentication.

## External API Setup

Push threat data from NGCYTBLE to your own endpoint for logging, alerting, or integration with other systems.

### Configuration

In **Settings > External API**:

- **Endpoint URL** — Your HTTPS endpoint that accepts POST requests
- **API Key** — Sent as `Authorization: Bearer <key>` header
- **Mode** — `ALERTS_ONLY` or `FULL_TELEMETRY`
- **Batch Interval** — Seconds between telemetry pushes (default: 30)

### Modes

**ALERTS_ONLY** — POSTs a `ThreatAssessment` JSON payload when a device's score crosses your configured alert threshold:

```json
{
  "device_mac": "AA:BB:CC:DD:EE:FF",
  "threat_score": 75,
  "threat_level": "HIGH",
  "time_windows": ["5-10min", "10-15min", "15-20min"],
  "correlated_macs": ["11:22:33:44:55:66", "77:88:99:AA:BB:CC"],
  "rssi_avg": -42,
  "location": {"lat": 40.7128, "lng": -74.0060, "accuracy_m": 50, "provider": "wifi"},
  "timestamp": "2026-03-10T14:30:00Z"
}
```

**FULL_TELEMETRY** — All sightings, fingerprint correlations, similarity matches, and location data. Batched on interval to avoid network hammering.

Both modes queue outbound data in a Room table (`api_sync_queue`) for offline resilience — data syncs when connectivity returns.

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Dependency Injection | Hilt |
| Database | Room (SQLite) |
| Preferences | DataStore |
| Networking | Ktor Client |
| Background Work | Foreground Service + WorkManager |
| Build | Gradle with Kotlin DSL |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |

## Credits

- [Chasing Your Tail (CYT)](https://github.com/H179922/CYT) — the parent project this app ports from
- Original [Chasing Your Tail](https://github.com/azmatt/chasing_your_tail) concept by @matt0177
- [Kismet](https://www.kismetwireless.net/) wireless detection (used by CYT)

## License

MIT License - See LICENSE file for details.
