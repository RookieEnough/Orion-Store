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
