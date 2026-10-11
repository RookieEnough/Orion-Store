# Orion Store #123: "App details in store" on the Android App info page

Issue: https://github.com/RookieEnough/Orion-Store/issues (#123)
Version analysed: 1.5.0

## Problem

In Android system settings → Apps → *[app]* → App info, there is a row
"App details in store" ("Detalji o apl. u prodavnici"). When an app was installed
through Droid-ify this row is active and opens that app's page in Droid-ify. When
an app was installed through Orion Store the row says "Downloaded from Orion Store"
but is **greyed out** and does nothing.

## Root cause

Android shows this row as tappable only if the installer app has an activity that
handles the intent:

```
action:  android.intent.action.SHOW_APP_INFO
extra:   android.intent.extra.PACKAGE_NAME  (String, the package being inspected)
```

Orion Store already is recognised as the installer (the text "Downloaded from Orion Store"
comes from the install source), but its `AndroidManifest.xml` has no intent-filter for
`SHOW_APP_INFO`, so the system finds no handler and disables the row.

Orion Store does already have an `orionstore://app/{appId}` deep link
(handled in `App.tsx` via Capacitor's `appUrlOpen`), but:

1. it is only wired to the custom scheme, not to `SHOW_APP_INFO`;
2. it looks apps up by catalog **`id`**, while Android sends the **package name**.

## Solution

Three small changes.

### 1. `AndroidManifest.xml`: declare the handler

An extra intent-filter on `MainActivity`:

```xml
<intent-filter>
    <action android:name="android.intent.action.SHOW_APP_INFO" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```

No `exported` change is needed (`MainActivity` is already exported, `singleTask`).

### 2. `MainActivity.java`: translate the intent for Capacitor

Capacitor's App plugin only forwards `ACTION_VIEW` intents with a data URI to JS as
`appUrlOpen`. So `SHOW_APP_INFO` is rewritten into
`orionstore://package/{packageName}` (action → `ACTION_VIEW`, data → that URI):

- in `onCreate`, before `super.onCreate` (cold start),
- in `onNewIntent`, before `super.onNewIntent` (app already running, `singleTask`).

New helper: `handleShowAppInfo(Intent)`. Added imports: `android.content.Intent`, `android.net.Uri`.

### 3. `App.tsx`: resolve by package name

The deep link handler now understands two forms:

| URL | Lookup |
|---|---|
| `orionstore://app/{appId}` (existing) | `appLookup.get(appId)` |
| `orionstore://package/{packageName}` (new) | `allKnownApps.find(a => a.packageName === pkg)` |

The shared logic is in a new `openDeepLinkTarget(kind, value)`. The "queued until the
catalog has loaded" mechanism (`pendingDeepLinkRef`) now stores `"app:<id>"` or
`"pkg:<name>"`, so a cold start from App info also works before data is loaded.
Behaviour for existing `orionstore://app/...` links is unchanged.

## Changed files

- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/com/orion/store/MainActivity.java`
- `App.tsx`

## Limitations / things to verify

- **`packageName` is optional** in `AppData` (`types.ts`). Apps in the catalog without
  it cannot be matched, and the user gets the "not found in Orion Store" toast. Ideally
  the catalog JSON should fill `packageName` for all apps (or the installed-app tracker
  `AppTrackerPlugin` could map installed package → catalog id).
- Not built or run on a device here; please test: install an app via Orion Store →
  Settings → App info → tap "App details in store" → the app's page should open
  (both with Orion Store closed and in the background).
- The row can also be enabled by Android only when the installer is recorded as Orion
  Store. This works already (screenshot shows "Downloaded from Orion Store"), so no
  change is needed on the installer side.
- Cold-start delivery relies on Capacitor retaining the `appUrlOpen` event until a
  listener is registered, same as the existing `orionstore://` deep link.
