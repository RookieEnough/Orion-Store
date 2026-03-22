# 🚀 Orion Store v1.2.4 — The "Polish & Performance" Update

> A focused release tackling user-reported bugs, device-specific performance issues, and UX improvements across the lightbox, variant picker, and submission flow.

---

## ✨ New Features

- **Mobile Lightbox Navigation Arrows** — Visible prev/next buttons now appear below the dot indicators on mobile, providing an explicit alternative to swipe gestures.
- **Screenshot Thumbnails in Submissions** — Added image previews in the submission form so you can verify screenshots before submitting.
- **Auto-saving Submission Drafts** — Your submission form progress is now saved automatically. Switching apps or accidentally closing the modal won't lose your work.

## ⚡ Performance Improvements

- **Instant Scroll-to-Top** — The button now jumps to the top instantly instead of using the slow smooth scroll animation that caused visible lag on long lists.
- **Debounced Scroll Listener** — Scroll event handler now uses `requestAnimationFrame` throttling, reducing unnecessary re-renders and improving scroll performance.
- **Settings Modal Preloading** — The Settings chunk is preloaded 2 seconds after launch, eliminating the delay when opening Settings on Android for the first time.
- **Faster Post-Splash Loading** — App data fetching starts immediately on launch instead of waiting 500ms, reducing the blank screen time after the splash animation.

## 🐛 Bug Fixes

- **Lightbox Swipe Reliability** — Fixed screenshot swiping failing to complete on some devices by adding a timeout fallback for missed CSS `transitionend` events.
- **Duplicate Category Tabs** — Fixed "utility" and "Utility" appearing as separate filter categories by normalizing all categories to title-case in the data worker.
- **Duplicate Variant Entries** — Fixed the variant/architecture picker showing multiple identical entries (e.g. "Universal" × 3) by deduplicating by arch name.
- **Scroll Button Overlap** — Repositioned the scroll-to-top button higher to avoid being hidden behind 3-button navigation bars.
- **Variant Picker Overflow** — Made the architecture picker scrollable when many options are available, preventing content from being cut off.
- **Android Version Info** — Bumped `versionCode` to 5 and `versionName` to `1.2.4` so Android app info correctly displays the new version after installation.

## 📦 Version Changes

| File | Field | Old | New |
|------|-------|-----|-----|
| `package.json` | `version` | 1.2.3 | 1.2.4 |
| `App.tsx` | `CURRENT_STORE_VERSION` | 1.2.3 | 1.2.4 |
| `build.gradle` | `versionCode` | 4 | 5 |
| `build.gradle` | `versionName` | 1.2.3 | 1.2.4 |

---

**Full Changelog**: v1.2.3...v1.2.4
