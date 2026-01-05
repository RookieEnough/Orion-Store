import type { AppItem } from '@/types';

export const sanitizeUrl = (url?: string): string =>
  !url || url.trim().toLowerCase().startsWith('javascript:') ? '#' : url;

export const cleanGithubRepo = (repo?: string): string =>
  repo?.replace(/^https?:\/\/(www\.)?github\.com\//, '').replace(/\/$/, '') ?? '';

export const getArchScore = (arch: string): number =>
  ({ Universal: 5, ARM64: 4, ARMv7: 3, x64: 2, x86: 1 })[arch] ?? 0;

export const determineArch = (filename: string): string => {
  const f = filename.toLowerCase();
  if (f.includes('arm64') || f.includes('v8a')) return 'ARM64';
  if (f.includes('armeabi') || f.includes('v7a')) return 'ARMv7';
  if (f.includes('x86_64') || f.includes('x64')) return 'x64';
  if (f.includes('x86')) return 'x86';
  return 'Universal';
};

export const sanitizeApp = (app: Partial<AppItem>): AppItem => ({
  id: app.id ?? crypto.randomUUID(),
  name: app.name ?? 'Unknown App',
  description: app.description ?? '',
  author: app.author ?? 'Unknown',
  category: app.category ?? 'Utility',
  platform: app.platform ?? 'Android',
  icon: sanitizeUrl(app.icon ?? ''),
  version: app.version ?? 'Latest',
  latestVersion: app.latestVersion ?? 'Latest',
  downloadUrl: sanitizeUrl(app.downloadUrl ?? '#'),
  size: app.size ?? '?',
  screenshots: app.screenshots?.map(sanitizeUrl) ?? [],
  variants: app.variants,
  repoUrl: app.repoUrl,
  githubRepo: app.githubRepo,
  releaseKeyword: app.releaseKeyword,
  packageName: app.packageName,
  isInstalled: app.isInstalled,
});

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
