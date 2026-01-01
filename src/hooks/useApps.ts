import { useState, useCallback, useEffect, useRef } from 'react';
import { useLocalStorage } from './useLocalStorage';
import { storage, STORAGE_KEYS } from '@/utils/storage';
import { fetchWithTimeout, shuffleArray, sanitizeApp, determineArch } from '@/utils';
import { CACHE_VERSION, REMOTE_CONFIG_URL, DEFAULT_APPS_JSON, DEFAULT_MIRROR_JSON } from '@/constants';
import { localAppsData } from '@/data/localData';
import type { AppItem, AppVariant, StoreConfig } from '@/types';

interface UseAppsReturn {
  apps: AppItem[];
  isLoading: boolean;
  isRefreshing: boolean;
  config: StoreConfig | null;
  installedVersions: Record<string, string>;
  refresh: () => void;
  registerInstall: (appId: string, version: string) => void;
}

export function useApps(useRemote: boolean, githubToken: string): UseAppsReturn {
  const [apps, setApps] = useState<AppItem[]>(() => {
    const cached = storage.get(STORAGE_KEYS.CACHED_APPS);
    const cacheVer = storage.get(STORAGE_KEYS.CACHE_VERSION);
    if (cacheVer !== CACHE_VERSION || !cached) {
      return localAppsData.map(sanitizeApp);
    }
    try {
      return JSON.parse(cached).map(sanitizeApp);
    } catch {
      return localAppsData.map(sanitizeApp);
    }
  });

  const [isLoading, setIsLoading] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [config, setConfig] = useState<StoreConfig | null>(null);
  const [installedVersions, setInstalledVersions] = useLocalStorage<Record<string, string>>(
    STORAGE_KEYS.INSTALLED_APPS,
    {}
  );

  const appsRef = useRef(apps);
  useEffect(() => {
    appsRef.current = apps;
  }, [apps]);

  const loadApps = useCallback(
    async (isManualRefresh = false) => {
      if (isManualRefresh) setIsRefreshing(true);
      if (appsRef.current.length === 0) setIsLoading(true);

      try {
        let rawApps: AppItem[] = [];
        let mirrorData: Record<string, unknown> | null = null;

        if (useRemote) {
          // Fetch config
          try {
            const configRes = await fetchWithTimeout(`${REMOTE_CONFIG_URL}?t=${Date.now()}`, {
              timeout: 3000,
            });
            if (configRes.ok) {
              const configData = await configRes.json();
              setConfig(configData);
              if (configData.maintenanceMode) {
                setIsLoading(false);
                setIsRefreshing(false);
                return;
              }
            }
          } catch {
            // Config fetch failed, continue with defaults
          }

          // Fetch apps
          try {
            const appsRes = await fetchWithTimeout(`${DEFAULT_APPS_JSON}?t=${Date.now()}`);
            if (appsRes.ok) {
              rawApps = await appsRes.json();
            }
          } catch {
            rawApps = localAppsData;
          }

          // Fetch mirror
          try {
            const mirrorRes = await fetchWithTimeout(`${DEFAULT_MIRROR_JSON}?t=${Date.now()}`, {
              timeout: 5000,
            });
            if (mirrorRes.ok) {
              mirrorData = await mirrorRes.json();
            }
          } catch {
            // Mirror fetch failed
          }
        } else {
          rawApps = localAppsData;
        }

        rawApps = rawApps.map(sanitizeApp);

        // Build repo cache from mirror
        const repoCache = new Map<string, unknown[]>();
        if (mirrorData) {
          Object.entries(mirrorData).forEach(([key, data]) => {
            repoCache.set(key, Array.isArray(data) ? data : [data]);
          });
        }

        // Find repos needing API fetch
        const canUseApi = isManualRefresh || !!githubToken;
        const reposToFetch = new Set<string>();

        rawApps.forEach((app) => {
          const hasStaticLink = app.downloadUrl && app.downloadUrl !== '#' && app.downloadUrl.startsWith('http');
          const hasMirror = app.githubRepo && mirrorData && mirrorData[app.githubRepo];
          if (!hasStaticLink && !hasMirror && app.githubRepo && canUseApi) {
            const cleanRepo = app.githubRepo.replace(/^https?:\/\/(www\.)?github\.com\//, '').replace(/\/$/, '');
            reposToFetch.add(cleanRepo);
          }
        });

        // Fetch from GitHub API
        if (reposToFetch.size > 0) {
          const CACHE_KEY_PREFIX = 'gh_smart_v3_';
          const CACHE_DURATION = githubToken ? 10 * 60 * 1000 : 60 * 60 * 1000;
          const repoArray = shuffleArray(Array.from(reposToFetch));

          for (const repo of repoArray) {
            const storageKey = `${CACHE_KEY_PREFIX}${repo}`;
            const cached = storage.get(storageKey);
            let cachedItem = cached ? JSON.parse(cached) : null;

            if (cachedItem && Date.now() - cachedItem.timestamp < CACHE_DURATION && !isManualRefresh) {
              repoCache.set(repo, cachedItem.data);
              continue;
            }

            const headers: HeadersInit = {};
            if (githubToken) headers['Authorization'] = `Bearer ${githubToken}`;
            if (cachedItem?.etag) headers['If-None-Match'] = cachedItem.etag;

            try {
              const res = await fetch(`https://api.github.com/repos/${repo}/releases`, { headers });
              if (res.status === 304 && cachedItem) {
                repoCache.set(repo, cachedItem.data);
                storage.set(storageKey, JSON.stringify({ ...cachedItem, timestamp: Date.now() }));
              } else if (res.ok) {
                const data = await res.json();
                repoCache.set(repo, data);
                storage.set(
                  storageKey,
                  JSON.stringify({ timestamp: Date.now(), data, etag: res.headers.get('ETag') })
                );
              } else if (cachedItem) {
                repoCache.set(repo, cachedItem.data);
              }
            } catch {
              if (cachedItem) repoCache.set(repo, cachedItem.data);
            }
          }
        }

        // Process apps with release data
        const processedApps = rawApps.map((app) => {
          if (app.downloadUrl !== '#' && app.downloadUrl.length > 5 && !isManualRefresh) {
            return app;
          }

          const cleanRepo = app.githubRepo?.replace(/^https?:\/\/(www\.)?github\.com\//, '').replace(/\/$/, '');
          let releasesData = cleanRepo ? (repoCache.get(cleanRepo) as unknown[]) : null;

          if (cleanRepo && releasesData && releasesData.length > 0) {
            let foundRelease: { tag_name?: string; published_at?: string; assets?: unknown[] } | null = null;
            let matchingAssets: { name: string; browser_download_url: string; size: number }[] = [];

            for (const release of releasesData as { name?: string; tag_name?: string; assets?: { name: string; browser_download_url: string; size: number }[] }[]) {
              const assets = release.assets || [];
              const apks = assets.filter((a) => a.name.toLowerCase().endsWith('.apk'));
              if (apks.length === 0) continue;

              if (app.releaseKeyword) {
                const keyword = app.releaseKeyword.toLowerCase();
                const nameMatch = release.name?.toLowerCase().includes(keyword);
                const tagMatch = release.tag_name?.toLowerCase().includes(keyword);
                const fileMatch = apks.some((a) => a.name.toLowerCase().includes(keyword));

                if (nameMatch || tagMatch || fileMatch) {
                  foundRelease = release;
                  matchingAssets = fileMatch ? apks.filter((a) => a.name.toLowerCase().includes(keyword)) : apks;
                  break;
                }
              } else {
                foundRelease = release;
                matchingAssets = apks;
                break;
              }
            }

            if (foundRelease && matchingAssets.length > 0) {
              const variants: AppVariant[] = matchingAssets.map((asset) => ({
                arch: determineArch(asset.name),
                url: asset.browser_download_url,
              }));

              variants.sort((a, b) => {
                const score = (arch: string) => {
                  if (arch === 'Universal') return 5;
                  if (arch === 'ARM64') return 4;
                  if (arch === 'ARMv7') return 3;
                  if (arch === 'x64') return 2;
                  return 1;
                };
                return score(b.arch) - score(a.arch);
              });

              const sizeMB = (matchingAssets[0].size / 1024 / 1024).toFixed(1);
              const versionId = foundRelease.tag_name || (foundRelease.published_at?.split('T')[0] ?? 'Unknown');

              return {
                ...app,
                version: versionId,
                latestVersion: versionId,
                downloadUrl: variants[0].url,
                variants,
                size: `${sizeMB} MB`,
              };
            }
          }

          if (cleanRepo && (!releasesData || releasesData.length === 0 || app.downloadUrl === '#')) {
            return {
              ...app,
              version: 'View on GitHub',
              latestVersion: 'Unknown',
              downloadUrl: `https://github.com/${cleanRepo}/releases`,
              size: '?',
            };
          }

          return app;
        });

        setApps(processedApps);
        storage.set(STORAGE_KEYS.CACHED_APPS, JSON.stringify(processedApps));
        storage.set(STORAGE_KEYS.CACHE_VERSION, CACHE_VERSION);
      } catch (error) {
        console.error('Error loading apps:', error);
      } finally {
        setIsLoading(false);
        setIsRefreshing(false);
      }
    },
    [useRemote, githubToken]
  );

  useEffect(() => {
    loadApps(false);
  }, [loadApps]);

  const registerInstall = useCallback(
    (appId: string, version: string) => {
      setInstalledVersions((prev) => ({ ...prev, [appId]: version }));
    },
    [setInstalledVersions]
  );

  return {
    apps,
    isLoading,
    isRefreshing,
    config,
    installedVersions,
    refresh: () => loadApps(true),
    registerInstall,
  };
}
