import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { storage, STORAGE_KEYS } from '@/utils/storage';
import { fetchWithTimeout, sanitizeApp, determineArch, cleanGithubRepo, getArchScore, hasUpdate, sanitizeUrl } from '@/utils';
import { CACHE_VERSION, REMOTE_CONFIG_URL, DEFAULT_APPS_JSON, DEFAULT_MIRROR_JSON, DEV_SOCIALS, DEFAULT_FAQS, DEFAULT_DEV_PROFILE, DEFAULT_SUPPORT_EMAIL, DEFAULT_EASTER_EGG } from '@/constants';
import { localAppsData } from '@/data/localData';
import type { AppItem, AppVariant, StoreConfig, Tab } from '@/types';

type Theme = 'light' | 'dusk' | 'dark';

interface Release {
  name?: string;
  tag_name?: string;
  published_at?: string;
  assets?: { name: string; browser_download_url: string; size: number }[];
}

interface AppState {
  // Theme
  theme: Theme;
  setTheme: (t: Theme) => void;
  cycleTheme: () => void;

  // Navigation
  activeTab: Tab;
  setActiveTab: (tab: Tab) => void;

  // Apps
  apps: AppItem[];
  isLoading: boolean;
  isRefreshing: boolean;
  config: StoreConfig | null;
  loadApps: (manual?: boolean) => Promise<void>;

  // Installed
  installedVersions: Record<string, string>;
  registerInstall: (id: string, ver: string) => void;
  checkHasUpdate: (app: AppItem) => boolean;

  // Selected
  selectedApp: AppItem | null;
  setSelectedApp: (app: AppItem | null) => void;

  // Search
  searchQuery: string;
  setSearchQuery: (q: string) => void;
  selectedCategory: string;
  setSelectedCategory: (c: string) => void;

  // Dev Mode
  isDevUnlocked: boolean;
  devTapCount: number;
  devToast: { msg: string; show: boolean };
  handleDevTap: () => void;

  // Settings
  useRemoteJson: boolean;
  toggleSourceMode: () => void;
  githubToken: string;
  setGithubToken: (t: string) => void;

  // UI
  showFAQ: boolean;
  setShowFAQ: (s: boolean) => void;

  // Download
  handleDownload: (app: AppItem, url?: string) => void;
}

export const useStore = create<AppState>()(
  persist(
    (set, get) => ({
      // Theme
      theme: 'light',
      setTheme: (theme) => {
        document.documentElement.classList.remove('light', 'dusk', 'dark');
        document.documentElement.classList.add(theme);
        set({ theme });
      },
      cycleTheme: () => {
        const t = get().theme;
        get().setTheme(t === 'light' ? 'dusk' : t === 'dusk' ? 'dark' : 'light');
      },

      // Navigation
      activeTab: 'android',
      setActiveTab: (activeTab) => {
        set({ activeTab, searchQuery: '', selectedCategory: 'All' });
        window.scrollTo({ top: 0, behavior: 'smooth' });
      },

      // Apps
      apps: (() => {
        if (storage.get(STORAGE_KEYS.CACHE_VERSION) !== CACHE_VERSION) return localAppsData.map(sanitizeApp);
        try { return JSON.parse(storage.get(STORAGE_KEYS.CACHED_APPS) || '[]').map(sanitizeApp); }
        catch { return localAppsData.map(sanitizeApp); }
      })(),
      isLoading: false,
      isRefreshing: false,
      config: null,

      loadApps: async (manual = false) => {
        const { useRemoteJson, githubToken, apps } = get();
        if (manual) set({ isRefreshing: true });
        if (!apps.length) set({ isLoading: true });

        try {
          let raw: AppItem[] = localAppsData;
          let mirror: Record<string, unknown> | null = null;

          if (useRemoteJson) {
            const [cfgRes, appsRes, mirrorRes] = await Promise.all([
              fetchWithTimeout(`${REMOTE_CONFIG_URL}?t=${Date.now()}`, { timeout: 3000 }).catch(() => null),
              fetchWithTimeout(`${DEFAULT_APPS_JSON}?t=${Date.now()}`).catch(() => null),
              fetchWithTimeout(`${DEFAULT_MIRROR_JSON}?t=${Date.now()}`, { timeout: 5000 }).catch(() => null),
            ]);

            if (cfgRes?.ok) {
              const cfg = await cfgRes.json();
              set({ config: cfg });
              if (cfg.maintenanceMode) { set({ isLoading: false, isRefreshing: false }); return; }
            }
            if (appsRes?.ok) raw = await appsRes.json();
            if (mirrorRes?.ok) mirror = await mirrorRes.json();
          }

          raw = raw.map(sanitizeApp);
          const cache = new Map<string, unknown[]>();
          if (mirror) Object.entries(mirror).forEach(([k, v]) => cache.set(k, Array.isArray(v) ? v : [v]));

          // Find repos needing fetch
          const toFetch: string[] = [];
          if (manual || githubToken) {
            raw.forEach(app => {
              if (app.downloadUrl && app.downloadUrl !== '#' && app.downloadUrl.startsWith('http')) return;
              if (app.githubRepo && mirror?.[app.githubRepo]) return;
              if (app.githubRepo) toFetch.push(cleanGithubRepo(app.githubRepo));
            });
          }

          // Parallel GitHub fetches (batch of 5)
          const CACHE_TTL = githubToken ? 600000 : 3600000;
          const batches = [];
          for (let i = 0; i < toFetch.length; i += 5) batches.push(toFetch.slice(i, i + 5));

          for (const batch of batches) {
            await Promise.all(batch.map(async (repo) => {
              if (!repo) return;
              const key = `gh_v3_${repo}`;
              const cached = storage.get(key);
              const item = cached ? JSON.parse(cached) : null;

              if (item && Date.now() - item.ts < CACHE_TTL && !manual) { cache.set(repo, item.data); return; }

              const headers: HeadersInit = githubToken ? { Authorization: `Bearer ${githubToken}` } : {};
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
            }));
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
                const firstAsset = match.assets[0];
                const size = firstAsset ? `${(firstAsset.size / 1048576).toFixed(1)} MB` : '?';
                return { ...app, version: match.ver, latestVersion: match.ver, downloadUrl: variants[0]?.url ?? '#', variants, size };
              }
            }

            if (repo && (!releases?.length || app.downloadUrl === '#')) {
              return { ...app, version: 'View on GitHub', latestVersion: 'Unknown', downloadUrl: `https://github.com/${repo}/releases`, size: '?' };
            }
            return app;
          });

          set({ apps: processed });
          storage.set(STORAGE_KEYS.CACHED_APPS, JSON.stringify(processed));
          storage.set(STORAGE_KEYS.CACHE_VERSION, CACHE_VERSION);
        } catch (e) { console.error('Load error:', e); }
        finally { set({ isLoading: false, isRefreshing: false }); }
      },

      // Installed
      installedVersions: {},
      registerInstall: (id, ver) => set(s => ({ installedVersions: { ...s.installedVersions, [id]: ver } })),
      checkHasUpdate: (app) => hasUpdate(get().installedVersions[app.id], app.latestVersion),

      // Selected
      selectedApp: null,
      setSelectedApp: (selectedApp) => set({ selectedApp }),

      // Search
      searchQuery: '',
      setSearchQuery: (searchQuery) => set({ searchQuery }),
      selectedCategory: 'All',
      setSelectedCategory: (selectedCategory) => set({ selectedCategory }),

      // Dev Mode
      isDevUnlocked: false,
      devTapCount: 0,
      devToast: { msg: '', show: false },
      handleDevTap: () => {
        const { isDevUnlocked, devTapCount } = get();
        const showToast = (msg: string) => {
          set({ devToast: { msg, show: true } });
          setTimeout(() => set({ devToast: { msg: '', show: false } }), 2000);
        };

        if (isDevUnlocked) return showToast('You are already a developer.');

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

      // Settings
      useRemoteJson: true,
      toggleSourceMode: () => set(s => ({ useRemoteJson: !s.useRemoteJson })),
      githubToken: '',
      setGithubToken: (githubToken) => {
        set({ githubToken });
        setTimeout(() => get().loadApps(true), 500);
      },

      // UI
      showFAQ: false,
      setShowFAQ: (showFAQ) => set({ showFAQ }),

      // Download
      handleDownload: (app, specificUrl) => {
        const url = sanitizeUrl(specificUrl || app.downloadUrl);
        if (url === '#') return;

        const isWeb = !url.toLowerCase().endsWith('.apk') && !url.toLowerCase().endsWith('.exe');
        if (isWeb) { window.open(url, '_blank'); return; }

        get().registerInstall(app.id, app.latestVersion);
        app.platform === 'PC' ? window.open(url, '_blank') : (window.location.href = url);
      },
    }),
    {
      name: 'orion-store',
      partialize: (state) => ({
        theme: state.theme,
        installedVersions: state.installedVersions,
        isDevUnlocked: state.isDevUnlocked,
        useRemoteJson: state.useRemoteJson,
        githubToken: state.githubToken,
      }),
    }
  )
);

// Helpers
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

// Selectors (for performance - components only re-render when selected state changes)
export const useTheme = () => useStore(s => s.theme);
export const useApps = () => useStore(s => s.apps);
export const useConfig = () => useStore(s => s.config);
export const useSocialLinks = () => useStore(s => s.config?.socials ?? DEV_SOCIALS);
export const useFaqs = () => useStore(s => s.config?.faqs ?? DEFAULT_FAQS);
export const useDevProfile = () => useStore(s => s.config?.devProfile ?? DEFAULT_DEV_PROFILE);
export const useSupportEmail = () => useStore(s => s.config?.supportEmail ?? DEFAULT_SUPPORT_EMAIL);
export const useEasterEggUrl = () => useStore(s => s.config?.easterEggUrl ?? DEFAULT_EASTER_EGG);

// Init theme on load
if (typeof window !== 'undefined') {
  const theme = useStore.getState().theme;
  document.documentElement.classList.add(theme);
  useStore.getState().loadApps();
}
