export function compareVersions(v1: string, v2: string): number {
  if (!v1 || !v2) return 0;
  const clean = (v: string) => v.toLowerCase().replace(/^v/, '').replace(/[^0-9.]/g, '');
  const [p1, p2] = [clean(v1).split('.').map(Number), clean(v2).split('.').map(Number)];
  for (let i = 0; i < Math.max(p1.length, p2.length); i++) {
    const diff = (p1[i] ?? 0) - (p2[i] ?? 0);
    if (diff) return diff > 0 ? 1 : -1;
  }
  return 0;
}

export function hasUpdate(local: string | undefined, remote: string): boolean {
  return !!local && local !== 'Installed' && compareVersions(remote, local) > 0;
}
