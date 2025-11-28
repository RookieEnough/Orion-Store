
# Nexus OpenStore

A modern, open-source app store for Android and PC, built with React and Tailwind CSS.

## Features
- **Material You 3 Design**: Vibrant, Gen Z aesthetic with Acid/Neon accents.
- **Dark Mode**: Built-in theme toggling.
- **Dual Platform**: Sections for Android and PC software.
- **Native Bridge**: Detects installed apps and provides updates when running as an APK.
- **Static Configuration**: Simple JSON-based app management.

## Managing Apps

You do not need to edit code to add apps. The store is driven by the `apps.json` file. When you update `apps.json` in your repository, the store automatically updates.

### Adding an App

Simply add a new entry to `apps.json` with the direct download link to your APK file.

```json
{
  "id": "spotube",
  "name": "Spotube",
  "description": "A great music app.",
  "icon": "https://link-to-icon.png",
  "version": "3.4.0",
  "latestVersion": "3.4.0",
  "downloadUrl": "https://github.com/user/repo/releases/download/v3.4.0/app.apk",
  "repoUrl": "https://github.com/user/repo",
  "category": "Media",
  "platform": "Android",
  "size": "25 MB",
  "author": "Developer Name",
  "screenshots": [
    "https://link-to-screenshot1.jpg",
    "https://link-to-screenshot2.jpg"
  ]
}
```

## 🚀 Auto-Update Mirroring

The store includes a sophisticated workflow (`.github/workflows/auto_mirror.yml`) that can automatically check external websites for updates, download the APK, extract the version number, and publish a new release if it's an update.

### Setup Auto-Updates

1.  Open `mirror_config.json` in your repository.
2.  Add apps you want to track using **Direct Download Links** (Permlinks).

```json
[
  {
    "id": "telegram-premium",
    "name": "Telegram",
    "downloadUrl": "https://telegram.org/dl/android/apk"
  }
]
```

3.  The workflow runs automatically at **00:00 UTC** every day.
4.  It detects the version inside the downloaded APK.
5.  If that version doesn't exist in your Releases, it creates a new Release tagged `telegram-premium-v10.1.1`.

### Manual Mirroring

If you just want to mirror a single file once:
1.  Go to **Actions** -> **Mirror APK (Manual)**.
2.  Enter the `App ID` (e.g., `spotify-mod`).
3.  Enter the `Download URL`.
4.  Run Workflow. It will auto-detect the version and publish it.

### Connecting to apps.json

Once setup, ensure your `apps.json` points to your own repo:

```json
{
  "id": "telegram-premium",
  "githubRepo": "YourUsername/YourStoreRepo",
  "releaseKeyword": "telegram-premium",
  ...
}
```

## Project Structure
- `index.html`: Entry point with Tailwind CSS (CDN) and Import Maps.
- `App.tsx`: Main application logic and routing.
- `apps.json`: The database of apps.
- `mirror_config.json`: Configuration for the auto-updater.
- `components/`: UI components (AppCard, AppDetail).

## How to Host on GitHub Pages
1. Create a new repository on GitHub.
2. Upload all these files to the repository.
3. Go to **Settings > Pages**.
4. Select the `main` branch and save.
5. Your store will be live at `https://yourusername.github.io/your-repo`.

## Troubleshooting

### Build Error: "Dependency 'androidx.core:core:1.17.0' requires compileSdk 36"
If you see a build error stating that `androidx.core:core` requires `compileSdk 36` (Android 15) while you are on `android-35`, it is because a dependency (likely from Capacitor or a transitive library) pulled in a very new version of the core library.

**Fix:**
Open your `android/app/build.gradle` file and add this code block at the very bottom of the file to force a compatible version:

```gradle
configurations.all {
    resolutionStrategy {
        force 'androidx.core:core:1.15.0'
        force 'androidx.core:core-ktx:1.15.0'
    }
}
```
