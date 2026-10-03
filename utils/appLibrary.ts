import { AppItem } from '../types';
import { AppLibraryEntry, CleanupEntry } from '../store/useAppStore';

export type MyAppsFilter = 'all' | 'installed' | 'downloaded' | 'updates';

export type MyAppsRowStatus = 'downloading' | 'ready' | 'update' | 'installed' | 'downloaded';

export interface MyAppsRow {
  app: AppItem;
  status: MyAppsRowStatus;
  installedVersion: string;
  lastRemoteVersion: string;
  libraryEntry?: AppLibraryEntry;
  readyFileName?: string;
  cleanupFileName?: string;
  activeDownloadKey?: string;
  progress: number;
  isDownloading: boolean;
  isInstalledNow: boolean;
  hasUpdate: boolean;
  hasSavedApk: boolean;
  downloadedAt: number;
  installedAt?: number;
}

interface MyAppsSnapshot {
  apps: AppItem[];
  availableUpdates: AppItem[];
  installedVersions: Record<string, string>;
  lastRemoteVersions: Record<string, string>;
  appLibrary: Record<string, AppLibraryEntry>;
  activeDownloads: Record<string, string>;
  downloadProgress: Record<string, number>;
  readyToInstall: Record<string, string>;
  pendingCleanup: Record<string, CleanupEntry | string>;
}

const getCleanupFileName = (entry: CleanupEntry | string | undefined) => {
  if (!entry) return undefined;
  return typeof entry === 'string' ? entry : entry.fileName;
};

export const buildMyAppsRows = (snapshot: MyAppsSnapshot): MyAppsRow[] => {
  const updateIds = new Set(snapshot.availableUpdates.map((app) => app.id));

  return snapshot.apps
    .map((app): MyAppsRow | null => {
      const libraryEntry = snapshot.appLibrary[app.id];
      const installedVersion = snapshot.installedVersions[app.id] || '';
      const lastRemoteVersion = snapshot.lastRemoteVersions[app.id] || '';
      const readyFileName = snapshot.readyToInstall[app.id];
      const cleanupFileName = getCleanupFileName(snapshot.pendingCleanup[app.id]);
      const activeDownloadKey = snapshot.activeDownloads[app.id];
      const hasSavedApk = !!readyFileName || !!cleanupFileName || !!libraryEntry?.fileName;
      const hasUpdate = updateIds.has(app.id);
      const isDownloading = !!activeDownloadKey;
      const isInstalledNow = !!installedVersion;
      const isKnownLibraryItem = !!libraryEntry || isInstalledNow || hasSavedApk || isDownloading || hasUpdate;

      if (!isKnownLibraryItem) return null;

      let status: MyAppsRowStatus;
      if (isDownloading) status = 'downloading';
      else if (readyFileName) status = 'ready';
      else if (hasUpdate) status = 'update';
      else if (isInstalledNow || libraryEntry?.status === 'installed') status = 'installed';
      else status = 'downloaded';

      const cleanupEntry = snapshot.pendingCleanup[app.id];
      const cleanupTimestamp = typeof cleanupEntry === 'object'
        ? cleanupEntry?.timestamp
        : undefined;

      return {
        app,
        status,
        installedVersion,
        lastRemoteVersion,
        libraryEntry,
        readyFileName,
        cleanupFileName,
        activeDownloadKey,
        progress: snapshot.downloadProgress[app.id] || 0,
        isDownloading,
        isInstalledNow,
        hasUpdate,
        hasSavedApk,
        downloadedAt: libraryEntry?.downloadedAt ?? cleanupTimestamp ?? 0,
        installedAt: libraryEntry?.installedAt,
      };
    })
    .filter((row): row is MyAppsRow => row !== null)
    .sort((left, right) => right.downloadedAt - left.downloadedAt);
};

export const filterMyAppsRows = (rows: MyAppsRow[], filter: MyAppsFilter): MyAppsRow[] => {
  if (filter === 'all') return rows;
  if (filter === 'installed') return rows.filter((row) => row.status === 'installed' || row.hasUpdate);
  if (filter === 'downloaded') return rows.filter((row) => (
    row.status === 'downloading' || row.status === 'ready' || row.status === 'downloaded'
  ));
  return rows.filter((row) => row.hasUpdate);
};

export const getMyAppsCounts = (rows: MyAppsRow[]) => {
  const installed = rows.filter((row) => row.status === 'installed' || row.hasUpdate).length;
  const downloaded = rows.filter((row) => row.status === 'downloading' || row.status === 'ready' || row.status === 'downloaded').length;
  const updates = rows.filter((row) => row.hasUpdate).length;
  return { all: rows.length, installed, downloaded, updates };
};
