# Laws & Project Evolution Report - SmartXrayClient (English)

## ⚠️ Vital Documentation Laws
1. **Law 1: Never Delete History.** Existing results and logs must never be deleted. This file serves as the project's technical memory.
2. **Law 2: Detail Everything.** Every change must include exact file paths, error descriptions, and the technical reason for the change.
3. **Law 3: State the Final Result.** Clearly state if the change was successful or if issues persist.
4. **Law 4: Future Perspective.** Mention the long-term goal or desired outcome for each modification.

---

## 1. Project Overview
A high-performance Android VPN client using Xray-core with a Glassmorphism UI.

---

## 2. Technical Log & Troubleshooting (Standardized)

### **[LOG-001] Protocol Structure Fix in Xray Config**
- **Affected Files:** `XrayConfigBuilder.kt`
- **Error/Observation:** Connected status showed, but no data was passing through.
- **Technical Reason:** Trojan and Shadowsocks protocols were incorrectly using the `vnext` structure, which is specific to VLESS/VMess.
- **Changes Made:** Refactored JSON generation logic to use the correct `servers` array structure for these protocols.
- **Final Result:** Successfully tested traffic flow for Trojan and Shadowsocks.
- **Future Perspective:** Add support for newer protocols like Reality in future versions.

### **[LOG-002] VPN Tunnel Infinite Loop Fix (SOCKS Loop)**
- **Affected Files:** `SmartXrayVpnService.kt`
- **Error/Observation:** Immediate disconnect or 100% CPU usage upon connection.
- **Technical Reason:** The `VpnService` was capturing the traffic of the Xray process itself, sending it back into the tunnel and creating a loop.
- **Changes Made:** Added `builder.addDisallowedApplication(packageName)` to exempt the app from its own VPN tunnel.
- **Final Result:** Connection stability achieved; loop issue resolved.
- **Future Perspective:** Implement Per-App Proxy features for granular traffic control.

### **[LOG-003] Binary Execution Fix (Permission Denied - Error 13)**
- **Affected Files:** `build.gradle.kts`, `XrayBinaryManager.kt`, `XrayProcessRunner.kt`
- **Error/Observation:** `eror =13 permission denied` on Android 10 and above.
- **Technical Reason:** Android 10+ restricts executing binaries from `filesDir`. Binaries must reside in the `nativeLibraryDir` and follow the `lib*.so` naming convention.
- **Changes Made:** Moved Xray binary to `jniLibs`, renamed it to `libxray.so`, and updated execution paths to use `nativeLibraryDir`.
- **Final Result:** Xray core now executes successfully on all modern Android versions (up to Android 14).
- **Future Perspective:** Transition fully to the Native Bridge (JNI) to eliminate external process execution.

### **[LOG-004] UI Rendering & Navigation Bar Optimization**
- **Affected Files:** `AppNavigation.kt`, `MainActivity.kt`, `GlassComponents.kt`
- **Error/Observation:** Blank screen on startup or cut-off navigation buttons.
- **Technical Reason:** Infinite `recreate()` calls in `MainActivity` due to language checks and layout constraints in `GlassCard`.
- **Changes Made:** Optimized lifecycle in `MainActivity` and increased Bottom Navigation height to 80dp with corrected padding.
- **Final Result:** UI renders correctly across all screen resolutions.
- **Future Perspective:** Optimize battery consumption related to background blur animations.

### **[LOG-005] Share System Fix & Scanner Isolation**
- **Affected Files:** `ConfigShareHelper.kt`, `ConfigsViewModel.kt`, `ConfigDomainFacade.kt`
- **Error/Observation:** Shared links were not recognized by other apps; scanner interfered with core settings.
- **Technical Reason:** Non-standard link formats and tight coupling between the Cloudflare scanner and Xray core.
- **Changes Made:** Standardized URI generation (VLESS/SS) and completely decoupled the Cloudflare scanner module.
- **Final Result:** Flawless sharing and complete isolation of the scanner module.
- **Future Perspective:** Add "Config Poster" generation for more aesthetic sharing.

### **[LOG-006] 16 KB Page Alignment Fix & Navigation Bar Refinement**
- **Affected Files:** `CMakeLists.txt`, `AppNavigation.kt`, `XrayBinaryManager.kt`
- **Error/Observation:** App crashed on newer emulators with "LOAD segment not aligned" and bottom buttons were cramped.
- **Technical Reason:** Newer Android versions require 16 KB page alignment for native libraries to improve performance. Navigation bar height was insufficient for labels.
- **Changes Made:** Added `-Wl,-z,max-page-size=16384` link flag to CMake and increased Bottom Navigation height to 80dp. Refined binary lookup logic to prioritize JNI paths.
- **Final Result:** App runs successfully on modern environments; UI elements are properly displayed.
- **Future Perspective:** Monitor core performance across different CPU architectures.

### **[LOG-007] DNS Resolution Fix & Core Crash Prevention (Invalid UUID)**
- **Affected Files:** `XrayConfigBuilder.kt`, `ConfigUriParser.kt`, `XrayStatsPoller.kt`, `XrayConstants.kt`
- **Error/Observation:** VPN connected but no internet access (DNS resolution error); Core crashed during parallel scanning with Exit Code 23.
- **Technical Reason:** 1. DNS Loop: Port 53 rules were capturing Xray's internal DNS queries, creating a deadlock. 2. Invalid UUIDs: Some VMess configs had malformed IDs, causing Xray core to terminate.
- **Changes Made:** 1. Added `inboundTag: ["socks-in"]` to DNS routing rules to break the loop. 2. Implemented UUID validation in `ConfigUriParser.kt` and filtered out malformed configs in `XrayConfigBuilder.kt`. 3. Reduced MTU to 1400 for better mobile network stability. 4. Integrated multiple DNS servers (Google/Cloudflare) and DoH support.
- **Final Result:** Stable parallel scanning and successful internet access in manual mode.
- **Future Perspective:** Implement real-time DNS health monitoring and automatic fallback between DNS providers.

### **[LOG-008] UI Copy Bug Fix & Robust DNS Strategy**
- **Affected Files:** `ShareConfigDialog.kt`, `ConfigsScreen.kt`, `XrayConfigBuilder.kt`
- **Error/Observation:** "Logs copied" message appeared when copying a config; Internet disconnects if public DNS servers are filtered.
- **Technical Reason:** Incorrect string resource usage in UI components; Lack of a multi-tier fallback mechanism in DNS configuration.
- **Changes Made:** 1. Fixed labels and Toast messages in the share/export sections. 2. Re-engineered DNS hierarchy: Implemented DoH (DNS over HTTPS) as primary, Public DNS as secondary, and System DNS (localhost) as the ultimate fallback for 100% availability. 3. Added domain-specific DNS rules for Iranian vs Global domains.
- **Final Result:** UI bug resolved; DNS resolution remains active even under severe network restrictions.
- **Future Perspective:** Add live DNS latency testing and manual DNS provider selection.

---
*Generated by AI Assistant — All changes recorded according to the new standard.*
