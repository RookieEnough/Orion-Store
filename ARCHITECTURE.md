# Orion-RE architecture

Native Jetpack Compose port of the Capacitor Orion Store (`Orion Legacy/`). Single activity, MVVM, Material 3 Expressive.

## Stack

| Layer | Choice |
|---|---|
| UI | Compose BOM 2026.09.00, material3 1.5.0-beta01 (`MaterialExpressiveTheme`, `MotionScheme.expressive()`) |
| Color | Tonal schemes from MCU spec 2025 (brand indigo + acid-lime tertiary), light / dark / dusk / OLED, Material You optional (`tools/gen-scheme.mjs`) |
| Icons | Material Symbols Rounded as generated vectors (`Sym.*`, `Sym.*Filled` for selected states, `tools/gen-symbols.mjs`); Font Awesome names from remote config mapped by `faIcon()` |
| Shapes | `MaterialShapes` polygons, live `Morph` via `MorphShape` / `MorphBlob`, `ButtonDefaults.shapes()` press morph |
| Progress | `LoadingIndicator`, `LinearWavyProgressIndicator`, `CircularWavyProgressIndicator` |
| Images | Coil 3 (DiceBear SVG avatars requested as PNG via `pngAvatar`) |
| Network | OkHttp 5 + kotlinx-serialization (`data/Net`) |
| Privileged install | Shizuku, Dhizuku (device owner), root, built-in wireless ADB |
| Ads | Unity Ads (rewarded, `Rewarded_Android`) |

## Flow

```
OrionActivity  splash, edge-to-edge, ResultHost, notification permission, deep links, refresh rate
  └─ OrionRoot(vm)                 ui/Root.kt      theme, gate, nav bar, tab host, overlay host
       ├─ routes = VM state        vm.tab, vm.selectedApp, vm.overlays
       ├─ CompositionLocals        LocalVm, LocalS (Settings), LocalD (DataState)
       └─ overlays = OrionSheet    ModalBottomSheet; LocalSheetClose for animated button dismiss
MainViewModel  ui/MainViewModel.kt  port of App.tsx orchestration
  ├─ Store.settings / Store.data    Persisted<T> (zustand persist equivalents, AtomicFile JSON)
  ├─ Repository / CatalogProcessor  catalog, config, mirrors, cache
  ├─ AppTracker                     port of AppTrackerPlugin.java (install, uninstall, package scan, network security)
  └─ Downloads / DownloadService    foreground download engine + notifications
```

## Feature map

| Web source | Native file | Status |
|---|---|---|
| App.tsx (orchestration, toasts, janitor, forced update, deep links) | ui/MainViewModel.kt | Ported |
| App.tsx shell, Header, BottomNav | ui/Root.kt | Ported (ShortNavigationBar, top app bar) |
| ModernAppList, ClassicAppList, AppCard, CompactAppCard, StoreFilters, ExpandedNewUpdatedGrid | ui/StoreScreen.kt | Ported |
| AppDetail, SelectedAppModalContainer | ui/Detail.kt | Ported |
| MyAppsView | ui/MyApps.kt | Ported |
| AboutView, AboutTabContainer, LeaderboardModal, LeaderboardSubmitModal | ui/About.kt | Ported |
| SettingsModal, ScannerTestUI, utils/catalogScanner.ts | ui/Settings.kt | Ported |
| ForcedStoreUpdateGate, MaintenanceMode | ui/Gate.kt | Ported |
| DinoGame, DinoGameModal, DinoCirclePreview | ui/Games.kt | Ported |
| PullToRefreshCharacter, PullToRefreshCharacterPickerSheet | ui/Pixel.kt, ui/PixelGrids.kt | Ported |
| ShizukuPowerModal, SentinelModal, DonationSheet | ui/Xtra.kt | Ported |
| NoticeModal, FAQModal, ReleaseNotesModal, ModernUITutorial, SpotlightOverlay | ui/Sheets.kt | Ported (spotlight tour becomes a guided sheet) |
| BundlePreviewModal, VirusTotalScanModal, AppChangeRequestModal | ui/Sheets.kt | Ported |
| AdDonationModal, ProfileStatsModal, CustomBundleModal, SubmissionModal | ui/Community.kt | Ported |
| SplashScreenPreview | core-splashscreen (themes.xml) | Replaced: the web preview only ran in browsers |
| utils/* (versioning, image links, signatures, submission helpers) | data/Logic.kt | Ported, covered by LogicTest |
| store/* (zustand) | data/Stores.kt | Ported |
| VirusTotal service | platform/VirusTotal.kt | Ported |

## Design system

- One storefront for every platform: icon-first `AppTile` (grids, swimlanes), `AppRow` (lists), `ShotCard` (first lane), app-colored `HeroCard` pager, numbered `Spotlight`, shape-blob categories, folder-style bundles.
- Icons and screenshots render as the image itself (no backing boxes); `Shot` keeps each screenshot at its natural aspect ratio.
- Motion: `springClick` press squash, `reveal` staggered entrance, shared-element icon hand-off card → detail (`LocalShared`), hero auto-advance with parallax, nav icon pop, image crossfade. "Disable Animations" swaps in an instant MotionScheme.
- Install state lives on the icon (`AppIcon(state = true)`): wavy download ring plus a cut-out corner badge; rows add `statusText()`.
- Lists of settings, app info and actions use segmented groups (24dp outer, 4dp inner, 2dp gaps); top-level settings icons are tonal circles via `tonal()`.
- Posters, category cards and bundles take scheme container colors so they follow Material You.

## Conventions

- Springs: spatial specs for movement and shape, effects specs for color and alpha.
- Corners on the M3 scale (4, 8, 12, 16, 20, 28, full). Abstract `MaterialShapes` only on icons, avatars and hero art.
- Emphasized type for headlines, selected states and primary actions only.
- Hero moments: tutorial blob morph and the ad quest VICTORY card.
- Sheet buttons close through `LocalSheetClose.current` so the hide animation runs before `onDismiss`.

## Checks

- `./gradlew :app:testDebugUnitTest --tests "*ShotsTest*" -Pshots=all` renders the real app with the live catalog under Robolectric and writes PNGs to `app/build/shots` (`-Pshots=tall` for long pages). Dev only, needs network.

- `./gradlew :app:testDebugUnitTest` runs `LogicTest` (versions, image links, repo parsing, submission and bundle issue bodies, profanity, levels).
- `./gradlew :app:assembleDebug` builds the debug APK.
