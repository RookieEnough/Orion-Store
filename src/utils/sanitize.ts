import type { AppItem } from '@/types';

export function sanitizeUrl(url?: string): string {
  if (!url) return '#';
  const trimmed = url.trim().toLowerCase();
  if (trimmed.startsWith('javascript:')) return '#';
  return url;
}

export function sanitizeApp(app: Partial<AppItem>): AppItem {
  return {
    id: String(app.id || crypto.randomUUID()),
    name: String(app.name || 'Unknown App'),
    description: String(app.description || ''),
    author: String(app.author || 'Unknown'),
    category: app.category || 'Utility',
    platform: app.platform || 'Android',
    icon: sanitizeUrl(String(app.icon || '')),
    version: String(app.version || 'Latest'),
    latestVersion: String(app.latestVersion || 'Latest'),
    downloadUrl: sanitizeUrl(String(app.downloadUrl || '#')),
    size: String(app.size || '?'),
    screenshots: Array.isArray(app.screenshots)
      ? app.screenshots.map(sanitizeUrl)
      : [],
    variants: app.variants,
    repoUrl: app.repoUrl,
    githubRepo: app.githubRepo,
    releaseKeyword: app.releaseKeyword,
    packageName: app.packageName,
    isInstalled: app.isInstalled,
  };
}

export function determineArch(filename: string): string {
  const lower = filename.toLowerCase();
  if (lower.includes('arm64') || lower.includes('v8a')) return 'ARM64';
  if (lower.includes('armeabi') || lower.includes('v7a')) return 'ARMv7';
  if (lower.includes('x86_64') || lower.includes('x64')) return 'x64';
  if (lower.includes('x86')) return 'x86';
  if (lower.includes('universal') || lower.includes('all')) return 'Universal';
  return 'Universal';
}
