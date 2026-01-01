export function compareVersions(v1: string, v2: string): number {
  if (!v1 || !v2) return 0;

  const clean = (v: string) =>
    v
      .toLowerCase()
      .replace(/^v/, '')
      .replace(/[^0-9.]/g, '')
      .trim();

  const s1 = clean(v1);
  const s2 = clean(v2);

  if (s1 === s2) return 0;

  const parts1 = s1.split('.').map(Number);
  const parts2 = s2.split('.').map(Number);
  const len = Math.max(parts1.length, parts2.length);

  for (let i = 0; i < len; i++) {
    const n1 = parts1[i] || 0;
    const n2 = parts2[i] || 0;
    if (n1 > n2) return 1;
    if (n1 < n2) return -1;
  }

  return 0;
}

export function hasUpdate(localVersion: string | undefined, remoteVersion: string): boolean {
  if (!localVersion || localVersion === 'Installed') return false;
  return compareVersions(remoteVersion, localVersion) > 0;
}
