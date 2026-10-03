import { describe, expect, it } from 'vitest';
import { AppItem, Platform } from '../types';
import {
  buildMyAppsRows,
  filterMyAppsRows,
  getMyAppsCounts
} from './appLibrary';

const makeApp = (id: string, latestVersion = '2.0.0'): AppItem => ({
  id,
  name: id,
  description: '',
  icon: '',
  version: '1.0.0',
  latestVersion,
  downloadUrl: 'https://example.com/app.apk',
  category: 'Utility',
  platform: Platform.ANDROID,
  size: '1 MB',
  author: 'Tester',
  screenshots: []
});

describe('buildMyAppsRows', () => {
  it('builds installed, downloaded, ready, and update rows from the current state', () => {
    const installedApp = makeApp('installed', '2.0.0');
    const updateApp = makeApp('update', '3.0.0');
    const readyApp = makeApp('ready');
    const historyApp = makeApp('history');
    const now = Date.now();

    const rows = buildMyAppsRows({
      apps: [installedApp, updateApp, readyApp, historyApp],
      availableUpdates: [updateApp],
      installedVersions: { installed: '1.0.0', update: '2.0.0' },
      lastRemoteVersions: {},
      appLibrary: {
        history: { status: 'downloaded', fileName: 'history.apk', downloadedAt: now, lastSeenAt: now }
      },
      activeDownloads: {},
      downloadProgress: {},
      readyToInstall: { ready: 'ready.apk' },
      pendingCleanup: {}
    });

    expect(rows).toHaveLength(4);
    expect(rows.find((row) => row.app.id === 'installed')?.status).toBe('installed');
    expect(rows.find((row) => row.app.id === 'update')?.status).toBe('update');
    expect(rows.find((row) => row.app.id === 'ready')?.status).toBe('ready');
    expect(rows.find((row) => row.app.id === 'history')?.status).toBe('downloaded');
  });

  it('keeps installed apps with pending updates visible in the installed filter', () => {
    const updateApp = makeApp('update', '3.0.0');
    const rows = buildMyAppsRows({
      apps: [updateApp],
      availableUpdates: [updateApp],
      installedVersions: { update: '2.0.0' },
      lastRemoteVersions: {},
      appLibrary: {},
      activeDownloads: {},
      downloadProgress: {},
      readyToInstall: {},
      pendingCleanup: {}
    });

    expect(filterMyAppsRows(rows, 'installed')).toHaveLength(1);
    expect(getMyAppsCounts(rows).installed).toBe(1);
    expect(getMyAppsCounts(rows).updates).toBe(1);
  });

  it('treats installed history as part of the library even when no APK is saved', () => {
    const historyApp = makeApp('history');
    const rows = buildMyAppsRows({
      apps: [historyApp],
      availableUpdates: [],
      installedVersions: {},
      lastRemoteVersions: { history: '1.4.0' },
      appLibrary: {
        history: {
          status: 'installed',
          version: '1.4.0',
          downloadedAt: 10,
          installedAt: 20,
          lastSeenAt: 30
        }
      },
      activeDownloads: {},
      downloadProgress: {},
      readyToInstall: {},
      pendingCleanup: {}
    });

    expect(rows).toHaveLength(1);
    expect(rows[0]?.status).toBe('installed');
    expect(rows[0]?.isInstalledNow).toBe(false);
    expect(rows[0]?.hasSavedApk).toBe(false);
  });
});
