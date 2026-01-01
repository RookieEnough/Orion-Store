import { useState, useCallback, useEffect, useRef } from 'react';
import { useLocalStorage } from './useLocalStorage';
import { storage, STORAGE_KEYS } from '@/utils/storage';
import { fetchWithTimeout, shuffleArray, sanitizeApp, determineArch, cleanGithubRepo, getArchScore } from '@/utils';
import { CACHE_VERSION, REMOTE_CONFIG_URL, DEFAULT_APPS_JSON, DEFAULT_MIRROR_JSON } from '@/constants';
import { localAppsData } from '@/data/localData';
import type { AppItem, AppVariant, StoreConfig } from '@/types';

export function useApps(useRemote: boolean, token: string) {
  const [apps, setApps] = useState<AppItem[]>(() => {
    if (storage.get(STORAGE_KEYS.CACHE_VERSION) !== CACHE_VERSION) return localAppsData.map(sanitizeApp);
    try { return JSON.parse(storage.get(STORAGE_KEYS.CACHED_APPS) || '[]').map(sanitizeApp); }
    catch { return localAppsData.map(sanitizeApp); }
  });
  const [isLoading, setIsLoading] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [config, setConfig] = useState<StoreConfig | null>(null);
  const [installed, setInstalled] = useLocalStorage<Record<string, string>>(STORAGE_KEYS.INSTALLED_APPS, {});
  const appsRef = useRef(apps);
  useEffect(() => { appsRef.current = apps; }, [apps]);

  const load = useCallback(async (manual = false) => {
    if (manual) setIsRefreshing(true);
    if (!appsRef.current.length) setIsLoading(true);

    try {
      let raw: AppItem[] = localAppsData;
      let mirror: Record<string, unknown> | null = null;

      if (useRemote) {
        try {
          const cfg = await fetchWithTimeout(`${REMOTE_CONFIG_URL}?t=${Date.now()}`, { timeout: 3000 });
          if (cfg.ok) {
            const data = await cfg.json();
            setConfig(data);
            if (data.maintenanceMode) return;
          }
        } catch {}
        try {
          const res = await fetchWithTimeout(`${DEFAULT_APPS_JSON}?t=${Date.now()}`);
          if (res.ok) raw = await res.json();
        } catch {}
        try {
          const res = await fetchWithTimeout(`${DEFAULT_MIRROR_JSON}?t=${Date.now()}`, { timeout: 5000 });
          if (res.ok) mirror = await res.json();
        } catch {}
      }

      raw = raw.map(sanitizeApp);
      const cache = new Map<string, unknown[]>();
      if (mirror) Object.entries(mirror).forEach(([k, v]) => cache.set(k, Array.isArray(v) ? v : [v]));

      // Find repos needing fetch
      const toFetch = new Set<string>();
      if (manual || token) {
        raw.forEach(app => {
          if (app.downloadUrl && app.downloadUrl !== '#' && app.downloadUrl.startsWith('http')) return;
          if (app.githubRepo && mirror?.[app.githubRepo]) return;
          if (app.githubRepo) toFetch.add(cleanGithubRepo(app.githubRepo));
        });
      }

      // Fetch from GitHub
      const CACHE_TTL = token ? 600000 : 3600000;
      for (const repo of shuffleArray([...toFetch])) {
        if (!repo) continue;
        const key = `gh_v3_${repo}`;
        const cached = storage.get(key);
        const item = cached ? JSON.parse(cached) : null;

        if (item && Date.now() - item.ts < CACHE_TTL && !manual) { cache.set(repo, item.data); continue; }

        const headers: HeadersInit = token ? { Authorization: `Bearer ${token}` } : {};
        if (item?.etag) headers['If-None-Match'] = item.etag;

        try {
          const res = await fetch(`https://api.github.com/repos/${repo}/releases`, { headers });
          if (res.status === 304 && item) {
            cache.set(repo, item.data);
            storage.set(key, JSON.stringify({ ...item, ts: Date.now() }));
          } else if (res.ok) {
            const data = await res.json();
            cache.set(repo, data);
            storage.set(key, JSON.stringify({ ts: Date.now(), data, etag: res.headers.get('ETag') }));
          } else if (item) cache.set(repo, item.data);
        } catch { if (item) cache.set(repo, item.data); }
      }

      // Process apps
      const processed = raw.map(app => {
        if (app.downloadUrl !== '#' && app.downloadUrl.length > 5 && !manual) return app;

        const repo = cleanGithubRepo(app.githubRepo);
        const releases = repo ? cache.get(repo) as Release[] : null;

        if (repo && releases?.length) {
          const match = findRelease(releases, app.releaseKeyword);
          if (match) {
            const variants: AppVariant[] = match.assets
              .map(a => ({ arch: determineArch(a.name), url: a.browser_download_url }))
              .sort((a, b) => getArchScore(b.arch) - getArchScore(a.arch));
            return { ...app, version: match.ver, latestVersion: match.ver, downloadUrl: variants[0]?.url ?? '#', variants, size: `${(match.assets[0]?.size / 1048576).toFixed(1)} MB` };
          }
        }

        if (repo && (!releases?.length || app.downloadUrl === '#')) {
          return { ...app, version: 'View on GitHub', latestVersion: 'Unknown', downloadUrl: `https://github.com/${repo}/releases`, size: '?' };
        }
        return app;
      });

      setApps(processed);
      storage.set(STORAGE_KEYS.CACHED_APPS, JSON.stringify(processed));
      storage.set(STORAGE_KEYS.CACHE_VERSION, CACHE_VERSION);
    } catch (e) { console.error('Load error:', e); }
    finally { setIsLoading(false); setIsRefreshing(false); }
  }, [useRemote, token]);

  useEffect(() => { load(); }, [load]);

  return {
    apps, isLoading, isRefreshing, config, installedVersions: installed,
    refresh: () => load(true),
    registerInstall: useCallback((id: string, ver: string) => setInstalled(p => ({ ...p, [id]: ver })), [setInstalled]),
  };
}

interface Release { name?: string; tag_name?: string; published_at?: string; assets?: { name: string; browser_download_url: string; size: number }[] }

function findRelease(releases: Release[], keyword?: string) {
  for (const r of releases) {
    const apks = r.assets?.filter(a => a.name.toLowerCase().endsWith('.apk')) ?? [];
    if (!apks.length) continue;

    if (keyword) {
      const kw = keyword.toLowerCase();
      const match = r.name?.toLowerCase().includes(kw) || r.tag_name?.toLowerCase().includes(kw) || apks.some(a => a.name.toLowerCase().includes(kw));
      if (match) {
        const assets = apks.some(a => a.name.toLowerCase().includes(kw)) ? apks.filter(a => a.name.toLowerCase().includes(kw)) : apks;
        return { ver: r.tag_name || r.published_at?.split('T')[0] || 'Unknown', assets };
      }
    } else {
      return { ver: r.tag_name || r.published_at?.split('T')[0] || 'Unknown', assets: apks };
    }
  }
  return null;
}
