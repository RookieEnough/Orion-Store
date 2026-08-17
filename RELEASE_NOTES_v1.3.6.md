# 🌌 Orion Store v1.3.6 Release

![Orion Store Banner](https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/assets/banner.png)

> **Orion Store 1.3.6** brings under-the-hood engine optimizations, lifecycle & memory stability fixes, smoother UI scrolling, and full app system setting version alignment (`versionCode 12`).

---

## ⚡ What's New in v1.3.6

### 🚀 Performance & Rendering Boosts
- **Zero-Flicker Card Rendering**: Optimized card state selectors in `CompactAppCard` and `ModernAppList` to minimize unnecessary React re-renders during high-speed list scrolling.
- **Image Decoding Optimization**: Priority decoding (`sync`/`async`) and lazy image prefetching tuned for fast network handoffs on modern Android displays.
- **Memory Lifecycle Cleanups**: Cleaned up unmounted gesture listeners, backdrop timer references, and background polling callbacks to maintain maximum FPS during prolonged sessions.

### 🛠️ Bug Fixes & Logic Improvements
- **System Settings Alignment**: Version bumped across `package.json`, `android/app/build.gradle` (`versionCode 12`, `versionName 1.3.6`), and `App.tsx` (`CURRENT_STORE_VERSION = 1.3.6`) so Android System Settings accurately reflects the release version.
- **Gesture Physics & Lightbox Stability**: Hardened pinch-to-zoom bounds and snap-back mechanics in `AppDetail` image lightboxes to avoid layout jitter on high refresh rate displays.
- **Store Update Pipeline Hardening**: Enhanced forced self-update snapshot validation and update checks.

### 🧹 Code Cleanup
- Refactored component state hooks and primitive selectors.
- Removed unused local variables and imports.
- Ensured strict alignment with TypeScript non-emitting checks (`tsc --noEmit`).

---

## 📦 Installation & Download Guide

### 📱 Android Devices
1. Download `OrionStore_1.3.6.apk` below.
2. If updating from an earlier version, install directly over your existing app (no need to uninstall).
3. If using **Shizuku**, silent background updates will automatically utilize the new version code `12`.

---

## 🛡️ Security & Verification

| Metric | Details |
| :--- | :--- |
| **Package Name** | `com.orion.store` |
| **Version Code** | `12` |
| **Version Name** | `1.3.6` |
| **Target SDK** | Android 14+ (API 34/35 compatible) |
| **Min SDK** | Android 7.0 (API 24) |
| **Java Version** | Java 21 / Capacitor 7 |

---

<p align="center">
  <b>Built with ❤️ by the Orion Store Team</b><br/>
  <i>Serverless • Open Source • Privacy-First App Store</i>
</p>
