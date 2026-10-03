# Orion Store 1.5.0 — Personalization, Level-Ups & Visual Polish 🚀

Welcome to **Orion Store 1.5.0**! This release brings a brand-new hub for your library, deeper personalization, sleek visual refinements, and a gamified update request system.

---

## ✨ What's New

* **The All-New "My Apps" Hub:** Your personal app library, organized into dedicated sections for **Downloaded**, **Installed**, **Ready-to-Install**, and **Available Updates**. Take immediate action—**Install**, **Update**, **Download**, or **Delete APKs**—or jump straight to your Download Queue in one tap.
* **Custom Font Support & Reliable Switching:** Full typography customization with support for `.ttf`, `.otf`, or `.woff2` files and popular built-in Google Fonts, now applying immediately and reliably across native Android APK and Web builds.
* **Android 13 Themed Icon:** Native monochrome adaptive icon support (API 33+) with centered vector geometry that blends seamlessly into your dynamic system theme.
* **Gamified App Update Requests:** Request updates across **Android, PC, and TV** apps. Submissions generate machine-readable GitHub issues for automated processing *and* score you **+50 XP** toward leaderboard progression. 🎮

---

## 🎨 Design & Experience

* **Streamlined Font Picker:** Completely redesigned the typography picker into a clean, minimal interface with real-time live preview, distraction-free rows, and seamless frameless styling.
* **Responsive Layouts & Controls:** Fixed element clipping and squished toggles across compact, narrow, and tall screens so modals and controls fit naturally on all device displays.
* **Material You 3 Refresh:** Cards in *My Apps* now sport softer, organic surfaces and modernized icon shapes.
* **Nav Dock Size Slider:** Fine-tune your bottom navigation dock height with a real-time, live-themed preview.
* **AMOLED-Optimized Logo:** The updated Orion header logo features a clean, unbordered look that pops—even in deep dark mode.

---

## ⚡ Improvements

* **Universal Font Rendering:** Preloaded supported Google Fonts and direct CSS variable inheritance ensure fonts switch smoothly and persist instantly upon launch.
* **Balanced Navigation:** Adjusted bottom dock proportions to perfectly accommodate the expanded 5-tab layout.
* **Frictionless Requests:** Simplified the App Update Request form so you can fire off requests quickly, while keeping evidence links, current values, and advanced parameters readily accessible.
* **Tooling Upgrades:** Android build configuration updated for **JDK 25** compatibility in Android Studio.

---

## 🤖 Automation

* **Zero-Setup Pipelines:** No new `Orion-Data` workflow required. App Update requests seamlessly pass through the existing `approved` label and update workflows.