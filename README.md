# V2IR — Smart Xray VPN Client for Android

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" height="96" alt="V2IR Logo"/>
</p>

<p align="center">
  <strong>امن · سریع · کاربردی</strong><br/>
  A high-performance Android VPN client powered by Xray-core with a modern Glassmorphism UI
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen?logo=android" />
  <img src="https://img.shields.io/badge/Min%20SDK-26-blue" />
  <img src="https://img.shields.io/badge/Kotlin-2.0.21-purple?logo=kotlin" />
  <img src="https://img.shields.io/badge/Xray--core-latest-orange" />
  <img src="https://img.shields.io/badge/License-GPL--3.0-red" />
</p>

---

## ✨ Features

| Feature | Status |
|---|---|
| VLESS / VMess / Trojan / Shadowsocks | ✅ |
| Reality / gRPC / WebSocket / TLS | ✅ |
| Smart Mode (Auto-select best server) | ✅ |
| Manual Mode (Full control) | ✅ |
| Subscription management & auto-update | ✅ |
| Parallel latency scanner (TLS Handshake) | ✅ |
| Load Balancer (LeastPing via Observatory) | ✅ |
| Cloudflare IP Scanner | ✅ |
| QR Code import / export | ✅ |
| IPv6 leak prevention | ✅ |
| DNS over HTTPS (DoH) with fallback | ✅ |
| Per-app routing (bypass list) | ✅ |
| Glassmorphism UI | ✅ |
| Persian (FA) / English (EN) language | ✅ |
| Dark theme | ✅ |

---

## 📋 Requirements

- **Android Studio** Hedgehog (2023.1.1) or newer
- **Android NDK** r25c or newer (for hev-socks5-tunnel)
- **JDK 17**
- Binaries are **not included** in the repo — see [Setup](#-setup) below

---

## 🚀 Setup

### 1. Clone the repository
```bash
git clone https://github.com/YOUR_USERNAME/SmartXrayClient.git
cd SmartXrayClient
```

### 2. Download Xray binaries + geo assets
```bash
./gradlew downloadBinaries
```
> This downloads `libxray.so` for all ABIs and `geoip.dat` / `geosite.dat` into the correct paths.
> Requires an internet connection (uses a local proxy at `127.0.0.1:12334` by default — edit `gradle.properties` if needed).

### 3. Build & run
```bash
./gradlew assembleDebug
```
> **hev-socks5-tunnel** is automatically pulled as an AAR from JitPack — no NDK or manual compilation needed.

---

## 🏗️ Architecture

```
com.v2ir/
├── data/
│   ├── local/          # Room database (v7), entities, DAOs
│   ├── model/          # Domain models (Config, Subscription, ...)
│   ├── remote/         # URI parser, subscription fetcher, share helper
│   ├── scanner/        # Cloudflare scanner engine
│   ├── repository/     # Single source of truth
│   └── xray/           # Xray binary manager, process runner, stats poller
├── domain/
│   ├── config/         # Config importer, parallel latency scanner
│   ├── LoadBalancer.kt
│   └── XrayConfigBuilder.kt   # Xray JSON config generator
├── service/
│   ├── worker/         # WorkManager workers (auto-update, scan)
│   └── V2irVpnService.kt      # VpnService (TUN interface)
├── ui/
│   ├── components/     # GlassCard, LiveTrafficGraph, ShareDialog
│   ├── screens/        # Home, Configs, Settings, Logs, Scanner
│   └── theme/          # Glassmorphism colors & typography
└── util/               # LocaleHelper
```

**Stack:** Kotlin · Jetpack Compose · Hilt · Room · Coroutines · WorkManager · OkHttp · CameraX · ZXing

---

## 📁 Binary Setup (Manual)

If `downloadBinaries` fails, place files manually:

| File | Path | Download |
|---|---|---|
| `libxray.so` (arm64-v8a) | `app/src/main/jniLibs/arm64-v8a/libxray.so` | [Xray-android-arm64-v8a.zip](https://github.com/XTLS/Xray-core/releases/latest) |
| `xray` binary (arm64-v8a) | `app/src/main/assets/xray/arm64-v8a/xray` | Same zip |
| `libxray.so` (x86_64) | `app/src/main/jniLibs/x86_64/libxray.so` | [Xray-android-amd64.zip](https://github.com/XTLS/Xray-core/releases/latest) |
| `xray` binary (x86_64) | `app/src/main/assets/xray/x86_64/xray` | Same zip |
| `geoip.dat` | `app/src/main/assets/geoip.dat` | [loyalsoldier/v2ray-rules-dat](https://github.com/loyalsoldier/v2ray-rules-dat/releases/latest) |
| `geosite.dat` | `app/src/main/assets/geosite.dat` | Same release |

> **Note:** `armeabi-v7a` and `x86` are no longer supported — Xray-core stopped publishing binaries for these architectures (pre-2017 devices). The minimum supported ABI is `arm64-v8a`.

> **Note:** `libhev-socks5-tunnel.so` is bundled inside the JitPack AAR — no manual setup needed.

---

## 🔒 Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | VPN traffic routing |
| `FOREGROUND_SERVICE` | VPN persistent service |
| `CAMERA` | QR code scanner |
| `POST_NOTIFICATIONS` | VPN status notification |
| `CHANGE_NETWORK_STATE` | Network reconnection handling |

---

## 🤝 Contributing

1. Fork the repo
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Commit your changes: `git commit -m 'feat: add my feature'`
4. Push: `git push origin feature/my-feature`
5. Open a Pull Request

---

## 📄 License

This project is licensed under the **GPL-3.0 License** — see [LICENSE](LICENSE) for details.

**Third-party components:**
- [Xray-core](https://github.com/XTLS/Xray-core) — MPL-2.0
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) — MIT
- [v2ray-rules-dat](https://github.com/loyalsoldier/v2ray-rules-dat) — GPL-3.0

---

## ⚠️ Disclaimer

This software is intended for legitimate privacy and security purposes only.
Users are responsible for complying with applicable laws in their jurisdiction.
