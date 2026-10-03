import { MyAppsRow } from './appLibrary';
import { AppItem, Platform } from '../types';

/**
 * ============================================================================
 * DEV DUMMY APPS FOR "MY APPS" PREVIEW
 * ----------------------------------------------------------------------------
 * Set ENABLE_DUMMY_MY_APPS = false to disable immediately, or delete this file
 * before production release.
 * ============================================================================
 */
export const ENABLE_DUMMY_MY_APPS = false;

const createDummyApp = (overrides: Partial<AppItem> & { id: string; name: string }): AppItem => ({
  description: 'Dummy app description for UI previewing.',
  icon: '',
  version: '1.0.0',
  latestVersion: '1.0.0',
  downloadUrl: '#',
  category: 'Utility',
  platform: Platform.ANDROID,
  size: '24 MB',
  author: 'OpenSource Dev',
  screenshots: [],
  ...overrides,
});

export const DUMMY_MY_APPS_ROWS: MyAppsRow[] = [
  {
    app: createDummyApp({
      id: 'dummy-youtube-revanced',
      name: 'YouTube ReVanced',
      author: 'ReVanced Team',
      category: 'Media',
      platform: Platform.ANDROID,
      icon: 'https://upload.wikimedia.org/wikipedia/commons/0/09/YouTube_full-color_icon_%282017%29.svg',
      version: '19.16.39',
      latestVersion: '19.16.39',
      size: '86 MB',
    }),
    status: 'update',
    installedVersion: '19.09.37',
    lastRemoteVersion: '19.16.39',
    hasUpdate: true,
    isInstalledNow: true,
    isDownloading: false,
    hasSavedApk: false,
    progress: 0,
    downloadedAt: Date.now() - 3600000,
  },
  {
    app: createDummyApp({
      id: 'dummy-retroarch',
      name: 'RetroArch Plus',
      author: 'Libretro',
      category: 'Media',
      platform: Platform.TV,
      icon: 'https://raw.githubusercontent.com/libretro/RetroArch/master/pkg/android/phoenix/res/mipmap-xxxhdpi/ic_launcher.png',
      version: '1.18.0',
      latestVersion: '1.18.0',
      size: '142 MB',
    }),
    status: 'ready',
    installedVersion: '',
    lastRemoteVersion: '1.18.0',
    readyFileName: 'RetroArch_v1.18.0_TV.apk',
    hasUpdate: false,
    isInstalledNow: false,
    isDownloading: false,
    hasSavedApk: true,
    progress: 100,
    downloadedAt: Date.now() - 7200000,
  },
  {
    app: createDummyApp({
      id: 'dummy-obsidian',
      name: 'Obsidian Notes',
      author: 'Dynalist Inc.',
      category: 'Utility',
      platform: Platform.PC,
      icon: 'https://upload.wikimedia.org/wikipedia/commons/1/10/Obsidian_logo_2023.svg',
      version: '1.5.12',
      latestVersion: '1.5.12',
      size: '95 MB',
    }),
    status: 'downloading',
    installedVersion: '',
    lastRemoteVersion: '1.5.12',
    activeDownloadKey: 'dummy-obsidian-dl',
    hasUpdate: false,
    isInstalledNow: false,
    isDownloading: true,
    hasSavedApk: false,
    progress: 68,
    downloadedAt: Date.now(),
  },
  {
    app: createDummyApp({
      id: 'dummy-shizuku',
      name: 'Shizuku',
      author: 'RikkaApps',
      category: 'Utility',
      platform: Platform.ANDROID,
      icon: 'https://play-lh.googleusercontent.com/KVK7u_AKusImrcj3i0HanLxIbCzrpIEm7BgmB1TXwZHe2SXAUGHOe6bLISO-x5xR0E0=w480-h960-rw',
      version: '13.5.4',
      latestVersion: '13.5.4',
      size: '4.8 MB',
    }),
    status: 'installed',
    installedVersion: '13.5.4',
    lastRemoteVersion: '13.5.4',
    hasUpdate: false,
    isInstalledNow: true,
    isDownloading: false,
    hasSavedApk: false,
    progress: 0,
    downloadedAt: Date.now() - 86400000,
  },
  {
    app: createDummyApp({
      id: 'dummy-vlc-player',
      name: 'VLC Media Player',
      author: 'VideoLAN',
      category: 'Media',
      platform: Platform.PC,
      icon: 'https://upload.wikimedia.org/wikipedia/commons/e/e6/VLC_Icon.svg',
      version: '3.0.20',
      latestVersion: '3.0.20',
      size: '42 MB',
    }),
    status: 'downloaded',
    installedVersion: '',
    lastRemoteVersion: '3.0.20',
    cleanupFileName: 'vlc-3.0.20-win64.exe',
    hasUpdate: false,
    isInstalledNow: false,
    isDownloading: false,
    hasSavedApk: true,
    progress: 100,
    downloadedAt: Date.now() - 172800000,
  },
];
