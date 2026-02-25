import { AppItem, AppCategory, Platform } from './types';

/**
 * Local fallback data for Orion Store
 * Used when remote API is unavailable or in offline mode
 */
export const localAppsData: AppItem[] = [
  {
    id: '1',
    name: 'Aurora Store',
    description: 'An open-source alternative to the Google Play Store with privacy-focused features.',
    icon: 'https://gitlab.com/AuroraOSS/aurorastore/-/raw/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png',
    version: '4.3.1',
    latestVersion: '4.3.1',
    downloadUrl: 'https://github.com/AuroraOSS/AppWarden/releases/download/v1.0.0-RC-2/AuroraStore_4.3.1_Release.apk',
    githubRepo: 'AuroraOSS/AuroraStore',
    category: AppCategory.PRIVACY,
    platform: Platform.ANDROID,
    size: '8.2 MB',
    author: 'AuroraOSS',
    screenshots: [],
    packageName: 'com.aurora.store'
  },
  {
    id: '2',
    name: 'Hermes',
    description: 'A lightweight file manager with modern UI and cloud storage integration.',
    icon: 'https://github.com/Hermes-Development/Hermes/releases/download/v2.0.0/icon.png',
    version: '2.0.0',
    latestVersion: '2.0.0',
    downloadUrl: 'https://github.com/Hermes-Development/Hermes/releases/download/v2.0.0/Hermes-v2.0.0-arm64-v8a.apk',
    githubRepo: 'Hermes-Development/Hermes',
    category: AppCategory.UTILITY,
    platform: Platform.ANDROID,
    size: '4.5 MB',
    author: 'Hermes Team',
    screenshots: [],
    packageName: 'com.hermes.filemanager'
  },
  {
    id: '3',
    name: 'Koler',
    description: 'A minimalist phone dialer with modern design.',
    icon: 'https://github.com/Chooloo/koler/releases/download/v1.2.3/koler-icon.png',
    version: '1.2.3',
    latestVersion: '1.2.3',
    downloadUrl: 'https://github.com/Chooloo/koler/releases/download/v1.2.3/Koler-v1.2.3.apk',
    githubRepo: 'Chooloo/koler',
    category: AppCategory.UTILITY,
    platform: Platform.ANDROID,
    size: '2.1 MB',
    author: 'Chooloo',
    screenshots: [],
    packageName: 'com.chooloo.dialer'
  },
  {
    id: '4',
    name: 'Clash for Android',
    description: 'A rule-based proxy utility for Android.',
    icon: 'https://github.com/Kr328/ClashForAndroid/releases/download/v2.5.12/icon.png',
    version: '2.5.12',
    latestVersion: '2.5.12',
    downloadUrl: 'https://github.com/Kr328/ClashForAndroid/releases/download/v2.5.12/cfa-2.5.12-arm64-v8a.apk',
    githubRepo: 'Kr328/ClashForAndroid',
    category: AppCategory.DEVELOPMENT,
    platform: Platform.ANDROID,
    size: '15.8 MB',
    author: 'Kr328',
    screenshots: [],
    packageName: 'com.github.kr328.clash'
  },
  {
    id: '5',
    name: 'BlackPlayer EX',
    description: 'A premium music player with customizable interface.',
    icon: 'https://kodiapps.com/images/app-icon/kodiapps.com_1552563011.jpg',
    version: '5.0.5',
    latestVersion: '5.0.5',
    downloadUrl: 'https://example.com/blackplayer.apk',
    category: AppCategory.MEDIA,
    platform: Platform.ANDROID,
    size: '12.3 MB',
    author: 'KodiApps',
    screenshots: [],
    packageName: 'com.kodiapps.blackplayerex'
  }
];

export default localAppsData;
