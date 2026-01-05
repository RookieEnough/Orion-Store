import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { storage } from '@/utils/storage';
import { fetchWithTimeout } from '@/utils/fetch';
import { sanitizeApp, determineArch, cleanGithubRepo, getArchScore, sanitizeUrl, hasUpdate } from '@/utils/sanitize';
import { CACHE_VERSION, REMOTE_CONFIG_URL, DEFAULT_APPS_JSON, DEFAULT_MIRROR_JSON, DEV_SOCIALS, DEFAULT_FAQS, DEFAULT_DEV_PROFILE, DEFAULT_SUPPORT_EMAIL, DEFAULT_EASTER_EGG } from '@/constants';
import { localAppsData } from '@/data/localData';
import type { AppItem, AppVariant, StoreConfig, Tab } from '@/types';

type Theme = 'light' | 'dusk' | 'system';

interface Release {
  name?: string | undefined;
  tag_name?: string | undefined;
  published_at?: string | undefined;
  assets?: { name: string; browser_download_url: string; size: number }[] | undefined;
}

interface CacheItem {
  ts: number;
  data: Release[];
  etag?: string | null | undefined;
}

interface AppState {
  theme: Theme;
  setTheme: (t: Theme) => void;
  cycleTheme: () => void;
  activeTab: Tab;
  setActiveTab: (tab: Tab) => void;
  apps: AppItem[];
  isLoading: boolean;
  isRefreshing: boolean;
  config: StoreConfig | null;
  loadApps: (manual?: boolean) => Promise<void>;
  installedVersions: Record<string, string>;
  registerInstall: (id: string, ver: string) => void;
  checkHasUpdate: (app: AppItem) => boolean;
  selectedApp: AppItem | null;
  setSelectedApp: (app: AppItem | null) => void;
  searchQuery: string;
  setSearchQuery: (q: string) => void;
  selectedCategory: string;
  setSelectedCategory: (c: string) => void;
  isDevUnlocked: boolean;
  devTapCount: number;
  devToast: { msg: string; show: boolean };
  handleDevTap: () => void;
  useRemoteJson: boolean;
  toggleSourceMode: () => void;
  githubToken: string;
  setGithubToken: (t: string) => void;
  showFAQ: boolean;
  setShowFAQ: (s: boolean) => void;
  isLegend: boolean;
  setIsLegend: (v: boolean) => void;
  handleDownload: (app: AppItem, url?: string) => void;
}

const getInitialApps = (): AppItem[] => {
  if (storage.getCacheVersion() !== CACHE_VERSION) return localAppsData.map(sanitizeApp);
  try {
    const cached = storage.getCachedApps();
    const parsed: unknown = cached ? JSON.parse(cached) : [];
    return Array.isArray(parsed) ? (parsed as Partial<AppItem>[]).map(sanitizeApp) : localAppsData.map(sanitizeApp);
  } catch { return localAppsData.map(sanitizeApp); }
};

export const useStore = create<AppState>()(
  persist(
    (set, get) => ({
      theme: 'system',
      setTheme: theme => {
        document.documentElement.classList.remove('light', 'dusk');
        if (theme === 'system') {
          const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
          document.documentElement.classList.add(prefersDark ? 'dusk' : 'light');
        } else {
          document.documentElement.classList.add(theme);
        }
        set({ theme });
      },
      cycleTheme: () => {
        const t = get().theme;
        get().setTheme(t === 'light' ? 'dusk' : t === 'dusk' ? 'system' : 'light');
      },

      activeTab: 'android',
      setActiveTab: activeTab => {
        set({ activeTab, searchQuery: '', selectedCategory: 'All' });
        window.scrollTo({ top: 0, behavior: 'smooth' });
      },

      apps: getInitialApps(),
      isLoading: false,
      isRefreshing: false,
      config: null,

      loadApps: async (manual = false) => {
        const { useRemoteJson, githubToken, apps } = get();
        if (manual) set({ isRefreshing: true });
        if (apps.length === 0) set({ isLoading: true });

        try {
          let raw: AppItem[] = localAppsData;
          let mirror: Record<string, Release[]> | null = null;

          if (useRemoteJson) {
            const [cfgRes, appsRes, mirrorRes] = await Promise.all([
              fetchWithTimeout(`${REMOTE_CONFIG_URL}?t=${Date.now()}`, { timeout: 3000 }).catch(() => null),
              fetchWithTimeout(`${DEFAULT_APPS_JSON}?t=${Date.now()}`).catch(() => null),
              fetchWithTimeout(`${DEFAULT_MIRROR_JSON}?t=${Date.now()}`, { timeout: 5000 }).catch(() => null),
            ]);

            if (cfgRes?.ok) {
              const cfg = (await cfgRes.json()) as StoreConfig;
              set({ config: cfg });
              if (cfg.maintenanceMode) { set({ isLoading: false, isRefreshing: false }); return; }
            }
            if (appsRes?.ok) raw = (await appsRes.json()) as AppItem[];
            if (mirrorRes?.ok) mirror = (await mirrorRes.json()) as Record<string, Release[]>;
          }

          raw = raw.map(sanitizeApp);
          const cache = new Map<string, Release[]>();
          if (mirror) {
            for (const [k, v] of Object.entries(mirror)) {
              cache.set(k, Array.isArray(v) ? v : [v]);
            }
          }

          const toFetch: string[] = [];
          if (manual || githubToken) {
            for (const app of raw) {
              if (app.downloadUrl && app.downloadUrl !== '#' && app.downloadUrl.startsWith('http')) continue;
              if (app.githubRepo && mirror?.[app.githubRepo]) continue;
              if (app.githubRepo) toFetch.push(cleanGithubRepo(app.githubRepo));
            }
          }

          const CACHE_TTL = githubToken ? 10 * 60 * 1000 : 60 * 60 * 1000;
          const batches: string[][] = [];
          for (let i = 0; i < toFetch.length; i += 5) batches.push(toFetch.slice(i, i + 5));

          for (const batch of batches) {
            await Promise.all(batch.map(async repo => {
              if (!repo) return;
              const key = `gh_v3_${repo}`;
              const cached = storage.get(key);
              const item: CacheItem | null = cached ? (JSON.parse(cached) as CacheItem) : null;

              if (item && Date.now() - item.ts < CACHE_TTL && !manual) { cache.set(repo, item.data); return; }

              const headers: HeadersInit = githubToken ? { Authorization: `Bearer ${githubToken}` } : {};
              if (item?.etag) headers['If-None-Match'] = item.etag;

              try {
                const res = await fetch(`https://api.github.com/repos/${repo}/releases`, { headers });
                if (res.status === 304 && item) {
                  cache.set(repo, item.data);
                  storage.set(key, JSON.stringify({ ...item, ts: Date.now() }));
                } else if (res.ok) {
                  const data = (await res.json()) as Release[];
                  cache.set(repo, data);
                  storage.set(key, JSON.stringify({ ts: Date.now(), data, etag: res.headers.get('ETag') }));
                } else if (item) {
                  cache.set(repo, item.data);
                }
              } catch { if (item) cache.set(repo, item.data); }
            }));
          }

          const processed = raw.map(app => {
            if (app.downloadUrl !== '#' && app.downloadUrl.length > 5 && !manual) return app;

            const repo = cleanGithubRepo(app.githubRepo);
            const releases = repo ? cache.get(repo) : undefined;

            if (repo && releases && releases.length > 0) {
              const match = findRelease(releases, app.releaseKeyword);
              if (match) {
                const variants: AppVariant[] = match.assets
                  .map(a => ({ arch: determineArch(a.name), url: a.browser_download_url }))
                  .sort((a, b) => getArchScore(b.arch) - getArchScore(a.arch));
                const firstAsset = match.assets[0];
                const MB = 1024 * 1024;
                const size = firstAsset ? `${(firstAsset.size / MB).toFixed(1)} MB` : '?';
                return { ...app, version: match.ver, latestVersion: match.ver, downloadUrl: variants[0]?.url ?? '#', variants, size };
              }
            }

            if (repo && (releases === undefined || releases.length === 0 || app.downloadUrl === '#')) {
              return { ...app, version: 'View on GitHub', latestVersion: 'Unknown', downloadUrl: `https://github.com/${repo}/releases`, size: '?' };
            }
            return app;
          });

          set({ apps: processed });
          storage.setCachedApps(JSON.stringify(processed));
          storage.setCacheVersion(CACHE_VERSION);
        } catch (e) { console.error('Load error:', e); }
        finally { set({ isLoading: false, isRefreshing: false }); }
      },

      installedVersions: {},
      registerInstall: (id, ver) => set(s => ({ installedVersions: { ...s.installedVersions, [id]: ver } })),
      checkHasUpdate: app => hasUpdate(get().installedVersions[app.id], app.latestVersion),

      selectedApp: null,
      setSelectedApp: selectedApp => set({ selectedApp }),

      searchQuery: '',
      setSearchQuery: searchQuery => set({ searchQuery }),
      selectedCategory: 'All',
      setSelectedCategory: selectedCategory => set({ selectedCategory }),

      isDevUnlocked: false,
      devTapCount: 0,
      devToast: { msg: '', show: false },
      handleDevTap: () => {
        const { isDevUnlocked, devTapCount } = get();
        const showToast = (msg: string) => {
          set({ devToast: { msg, show: true } });
          setTimeout(() => set({ devToast: { msg: '', show: false } }), 2000);
        };

        if (isDevUnlocked) { showToast('You are already a developer.'); return; }

        const count = devTapCount + 1;
        set({ devTapCount: count });
        const left = 9 - count;

        if (left <= 0) {
          set({ isDevUnlocked: true });
          showToast('You are now a developer!');
        } else if (left <= 5) {
          showToast(`${left} steps away from being a developer.`);
        }
      },

      useRemoteJson: true,
      toggleSourceMode: () => set(s => ({ useRemoteJson: !s.useRemoteJson })),
      githubToken: '',
      setGithubToken: githubToken => {
        set({ githubToken });
        setTimeout(() => { void get().loadApps(true); }, 500);
      },

      showFAQ: false,
      setShowFAQ: showFAQ => set({ showFAQ }),
      isLegend: false,
      setIsLegend: isLegend => set({ isLegend }),

      handleDownload: (app, specificUrl) => {
        const url = sanitizeUrl(specificUrl ?? app.downloadUrl);
        if (url === '#') return;

        const isWeb = !url.toLowerCase().endsWith('.apk') && !url.toLowerCase().endsWith('.exe');
        if (isWeb) { window.open(url, '_blank'); return; }

        get().registerInstall(app.id, app.latestVersion);
        if (app.platform === 'PC') { window.open(url, '_blank'); } else { window.location.href = url; }
      },
    }),
    {
      name: 'orion-store',
      partialize: state => ({
        theme: state.theme,
        installedVersions: state.installedVersions,
        isDevUnlocked: state.isDevUnlocked,
        useRemoteJson: state.useRemoteJson,
        githubToken: state.githubToken,
        isLegend: state.isLegend,
      }),
    },
  ),
);

function findRelease(releases: Release[], keyword?: string) {
  for (const r of releases) {
    const apks = r.assets?.filter(a => a.name.toLowerCase().endsWith('.apk')) ?? [];
    if (apks.length === 0) continue;

    if (keyword) {
      const kw = keyword.toLowerCase();
      const nameMatch = r.name?.toLowerCase().includes(kw) ?? false;
      const tagMatch = r.tag_name?.toLowerCase().includes(kw) ?? false;
      const assetMatch = apks.some(a => a.name.toLowerCase().includes(kw));
      if (nameMatch || tagMatch || assetMatch) {
        const assets = apks.some(a => a.name.toLowerCase().includes(kw)) ? apks.filter(a => a.name.toLowerCase().includes(kw)) : apks;
        return { ver: r.tag_name ?? r.published_at?.split('T')[0] ?? 'Unknown', assets };
      }
    } else {
      return { ver: r.tag_name ?? r.published_at?.split('T')[0] ?? 'Unknown', assets: apks };
    }
  }
  return null;
}

export const useTheme = () => useStore(s => s.theme);
export const useApps = () => useStore(s => s.apps);
export const useConfig = () => useStore(s => s.config);
export const useSocialLinks = () => useStore(s => s.config?.socials ?? DEV_SOCIALS);
export const useFaqs = () => useStore(s => s.config?.faqs ?? DEFAULT_FAQS);
export const useDevProfile = () => useStore(s => s.config?.devProfile ?? DEFAULT_DEV_PROFILE);
export const useSupportEmail = () => useStore(s => s.config?.supportEmail ?? DEFAULT_SUPPORT_EMAIL);
export const useEasterEggUrl = () => useStore(s => s.config?.easterEggUrl ?? DEFAULT_EASTER_EGG);

if (typeof window !== 'undefined') {
  const { theme, setTheme } = useStore.getState();
  setTheme(theme); // Apply theme on load (handles system preference)
  
  // Listen for system theme changes
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (useStore.getState().theme === 'system') {
      useStore.getState().setTheme('system');
    }
  });
  
  void useStore.getState().loadApps();
}
