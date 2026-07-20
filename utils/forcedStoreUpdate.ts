const VERSION_PARTS = /(\d+)\.(\d+)\.(\d+)/;

export const CURRENT_FORCED_STORE_UPDATE_VERSION = 1;

export type ForcedStoreUpdateStatus =
  | 'inactive'
  | 'checking'
  | 'downloading'
  | 'download_failed'
  | 'ready_to_scan_or_install'
  | 'byok_required'
  | 'scanning'
  | 'scan_complete'
  | 'installing'
  | 'install_failed'
  | 'export_ready'
  | 'installed';

export interface ForcedStoreUpdateSnapshot {
  schemaVersion: number;
  targetVersion: string;
  downloadFileName: string;
  status: ForcedStoreUpdateStatus;
  downloadCompleted: boolean;
  exported: boolean;
}

export interface ForcedStoreUpdateResumeOptions {
  targetVersion: string;
  minStoreVersion?: string | null;
  currentVersion: string;
}

const compareSemver = (left: string, right: string) => {
  const leftMatch = left.match(VERSION_PARTS);
  const rightMatch = right.match(VERSION_PARTS);

  if (!leftMatch || !rightMatch) {
    return left.localeCompare(right, undefined, { numeric: true });
  }

  for (let index = 1; index <= 3; index += 1) {
    const delta = Number(leftMatch[index]) - Number(rightMatch[index]);
    if (delta !== 0) return delta;
  }

  return 0;
};

export const isStoreUpdateAvailable = (currentVersion: string, latestVersion?: string | null) =>
  !!latestVersion && compareSemver(currentVersion, latestVersion) < 0;

export const shouldForceStoreUpdate = (currentVersion: string, minStoreVersion?: string | null) =>
  !!minStoreVersion && compareSemver(currentVersion, minStoreVersion) < 0;

export const buildForcedStoreFileName = (targetVersion: string) => `OrionStore_${targetVersion}.apk`;

export const createForcedStoreUpdateSnapshot = (
  snapshot: Omit<ForcedStoreUpdateSnapshot, 'schemaVersion'>
): ForcedStoreUpdateSnapshot => ({
  schemaVersion: CURRENT_FORCED_STORE_UPDATE_VERSION,
  ...snapshot
});

export const canResumeForcedStoreUpdate = (
  snapshot: ForcedStoreUpdateSnapshot | null | undefined,
  options: ForcedStoreUpdateResumeOptions
) => {
  if (!snapshot) return false;
  if (snapshot.schemaVersion !== CURRENT_FORCED_STORE_UPDATE_VERSION) return false;
  if (!options.targetVersion) return false;

  // Accept either a mandatory update (minStoreVersion) OR a voluntary one
  // (latestStoreVersion).  The old check only used shouldForceStoreUpdate
  // which requires minStoreVersion — that always failed for voluntary updates,
  // causing evaluateStoreUpdateState to overwrite the active scan/install state.
  const isForced = shouldForceStoreUpdate(options.currentVersion, options.minStoreVersion);
  const isAvailable = isStoreUpdateAvailable(options.currentVersion, options.targetVersion);
  if (!isForced && !isAvailable) return false;

  return snapshot.downloadCompleted && snapshot.targetVersion === options.targetVersion;
};
