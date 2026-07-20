# Changelog

All notable changes to V2IR are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [1.0.0] — 2026-07-20

### First public release

#### Core Features
- **Smart Mode** — automatic subscription fetch, parallel scan, best-node selection
- **Manual Mode** — direct config selection with load balancing support
- **Protocols** — VLESS, VMess, Trojan, Shadowsocks, Hysteria2, TUIC
- **Transport** — WebSocket, gRPC, HTTP/2, QUIC, httpUpgrade, TCP
- **TLS/Reality** — full TLS + uTLS fingerprinting, Reality support
- **Xray-core v26.7.11** — latest engine with XTLS Vision support

#### VPN Engine
- TUN-based VPN via hev-socks5-tunnel (from JitPack AAR)
- IPv6 leak prevention (dual-stack TUN)
- DNS: FakeDNS + DoH (1.1.1.1) + bypass chain
- Routing: bypass Iran, bypass LAN, full routing table
- Load balancer: random / leastPing / roundRobin / leastConn strategies
- Real-time traffic statistics (download/upload speed + total)

#### Smart Connection
- Parallel latency scanner with configurable concurrency (1–64 workers)
- Health-check filter before saving public subscriptions
- Auto-reconnect on network change
- Connection duration timer
- Public IP + ISP + country detection

#### Cloudflare Scanner
- Discovery phase: scan all Cloudflare CIDR ranges
- Validation phase: Xray transport test with configurable speed test
- Real-time results table with sortable columns (IP, ping, speed, score)
- Apply best clean IP to selected configs
- Export results (TXT / CSV / JSON)

#### Config Management
- Import: URI paste, QR scan, gallery QR, bulk text/base64
- Export: copy URI, QR code, share sheet
- Subscription management: public + private repos, auto-update
- Config editor: manual add/edit with all protocol fields

#### UI
- Glassmorphism design, dark Navy theme
- RTL support (Persian / Farsi)
- Real-time traffic graph
- Log viewer with level filter (INFO / WARNING / ERROR)

#### Technical
- Architecture: MVVM + Clean Architecture (data → domain → ui)
- DI: Hilt + KSP
- Database: Room 2.6.1 with migration chain (v1→v7)
- Networking: OkHttp 4.x with 30s call timeout
- Coroutines: structured concurrency, no GlobalScope
- Supported ABI: arm64-v8a, x86_64

---

## Release Notes

### Minimum Requirements
- Android 8.0 (API 26)
- ~120 MB storage (including Xray binary + geo assets)
- Internet permission

### Known Limitations
- Xray-core no longer publishes armeabi-v7a / x86 binaries (devices pre-2017)
- Speed Test requires an active VPN connection
- Hysteria2 and TUIC require Xray-core support on the server side

### Building from Source
See `README.md` for full build instructions including `./gradlew downloadBinaries`.
