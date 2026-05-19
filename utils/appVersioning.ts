import { AppItem, UpdateStream } from '../types';

export const normalizeVersion = (value: string): string =>
  (value || '')
    .toLowerCase()
    .replace(/^v/, '')
    .split('-')[0]!
    .split('+')[0]!
    .replace(/[^0-9.]/g, '')
    .trim();

export const isComparableVersion = (value?: string): boolean => !!normalizeVersion(value || '');

export const isSameVersion = (left?: string, right?: string): boolean => {
  if (!left || !right) return false;
  const normalizedLeft = normalizeVersion(left);
  const normalizedRight = normalizeVersion(right);
  return !!normalizedLeft && normalizedLeft === normalizedRight;
};

export const compareVersions = (v1: string, v2: string): number => {
  if (!v1 || !v2) return 0;

  const left = normalizeVersion(v1);
  const right = normalizeVersion(v2);

  if (!left || !right) return 0;
  if (left === right) return 0;

  const leftParts = left.split('.').map(Number);
  const rightParts = right.split('.').map(Number);

  for (let index = 0; index < Math.max(leftParts.length, rightParts.length); index++) {
    const leftNum = leftParts[index] || 0;
    const rightNum = rightParts[index] || 0;
    if (leftNum > rightNum) return 1;
    if (leftNum < rightNum) return -1;
  }

  return 0;
};

export const getPreferredVersion = (
  app: AppItem,
  preferredStream: UpdateStream | string = 'Stable'
): string => {
  if (!app.availableVersions || app.availableVersions.length === 0) {
    return app.latestVersion;
  }

  const streamVersion = app.availableVersions.find((version) => version.type === preferredStream);
  if (streamVersion) return streamVersion.version;

  if (preferredStream !== 'Stable') {
    const stableVersion = app.availableVersions.find((version) => version.type === 'Stable');
    if (stableVersion) return stableVersion.version;
  }

  return app.availableVersions[0]?.version || app.latestVersion;
};

export const hasAvailableUpdate = (
  app: AppItem,
  installedVersion?: string,
  preferredStream: UpdateStream | string = 'Stable'
): boolean => {
  if (!installedVersion) return false;
  return compareVersions(getPreferredVersion(app, preferredStream), installedVersion) > 0;
};
