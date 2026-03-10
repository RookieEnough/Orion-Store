import { create, StateCreator } from 'zustand';
import { persist, createJSONStorage, StateStorage } from 'zustand/middleware';
import { get, set, del } from 'idb-keyval';
import { AppItem, SortOption, UpdateStream } from '../types';

// --- Types ---

export type Theme = 'light' | 'dusk' | 'dark' | 'oled';

export interface CleanupEntry {
  fileName: string;
  timestamp: number;
}

export interface TabViewState {
  query: string;
  category: string;
  sort: SortOption;
  filterFavorites: boolean;
}

interface SettingsState {
  theme: Theme;
  isOled: boolean;
  hiddenTabs: string[];
  autoUpdateEnabled: boolean;
  wifiOnly: boolean;
  deleteApk: boolean; // true = Silent Janitor, false = Manual Popup
  useShizuku: boolean; // true = Silent Install via Shizuku
  disableAnimations: boolean;
  compactMode: boolean;
  highRefreshRate: boolean;
  hapticEnabled: boolean;
  glassEffect: boolean;
  isDevUnlocked: boolean;
  isLegend: boolean;
  isContributor: boolean;
  adWatchCount: number;
  submissionCount: number;
  lastSubmissionTime: number;
  lastLeaderboardSubmissionTime: number;
  useRemoteJson: boolean;
  githubToken: string;
  installedVersions: Record<string, string>; // { appId: version } (From OS)
  lastRemoteVersions: Record<string, string>; // { appId: version } (From Orion Install)
  appStreams: Record<string, UpdateStream>; // { appId: 'Beta' } - Stream Locking
  resolvedPackageNames: Record<string, string>; // { appId: "com.app.preview" } - For handling forks/suffixes
  ignoredUpdates: Record<string, { type: 'week' | 'version' | 'never', timestamp?: number, version?: string }>;

  // Actions
  setTheme: (theme: Theme) => void;
  toggleOled: () => void;
  toggleHiddenTab: (tab: string) => void;
  toggleAutoUpdate: () => void;
  toggleWifiOnly: () => void;
  toggleDeleteApk: () => void;
  toggleUseShizuku: () => void;
  toggleDisableAnimations: () => void;
  toggleCompactMode: () => void;
  toggleHighRefreshRate: () => void;
  toggleHaptic: () => void;
  toggleGlass: () => void;
  setDevUnlocked: (isUnlocked: boolean) => void;
  setIsLegend: (isLegend: boolean) => void;
  incrementAdWatch: () => void;
  registerSubmission: () => void;
  registerLeaderboardSubmission: () => void;
  setSubmissionCount: (count: number) => void;
  setUseRemoteJson: (useRemote: boolean) => void;
  setGithubToken: (token: string) => void;
  setInstalledVersions: (versions: Record<string, string>) => void;
  setLastRemoteVersion: (appId: string, version: string) => void;
  removeLastRemoteVersion: (appId: string) => void;
  setAppStream: (appId: string, stream: UpdateStream) => void;
  setResolvedPackageName: (appId: string, packageName: string) => void;
  setAllResolvedPackageNames: (packages: Record<string, string>) => void;
  setIgnoredUpdate: (appId: string, type: 'week' | 'version' | 'never', version?: string) => void;
  clearIgnoredUpdate: (appId: string) => void;
}

interface DataState {
  apps: AppItem[];
  importedApps: AppItem[];

  // Per-Tab State Container
  tabs: Record<string, TabViewState>;

  // Download Tracking
  activeDownloads: Record<string, string>; // { appId: "downloadId|fileName" }
  downloadProgress: Record<string, number>;
  downloadStatus: Record<string, string>;
  readyToInstall: Record<string, string>; // { appId: fileName }

  // Pending Cleanup
  pendingCleanup: Record<string, CleanupEntry | string>;

  // Favorites
  favorites: string[]; // List of App IDs

  // Actions
  setApps: (apps: AppItem[]) => void;
  setImportedApps: (apps: AppItem[]) => void;

  // Scoped Actions
  setSearchQuery: (tab: string, query: string) => void;
  setSelectedCategory: (tab: string, category: string) => void;
  setSelectedSort: (tab: string, sort: SortOption) => void;
  toggleFilterFavorites: (tab: string) => void;

  updateDownloadState: (appId: string, progress: number, status: string) => void;
  startDownload: (appId: string, downloadId: string, fileName: string) => void;
  completeDownload: (appId: string, fileName: string) => void;
  failDownload: (appId: string) => void;
  cancelDownload: (appId: string) => void;
  setReadyToInstall: (map: Record<string, string>) => void;
  setPendingCleanup: (map: Record<string, CleanupEntry | string>) => void;
  toggleFavorite: (appId: string) => void;
}

// --- IDB Storage Adapter ---
const idbStorage: StateStorage = {
  getItem: async (name: string): Promise<string | null> => {
    const value = await get(name);
    return value || null;
  },
  setItem: async (name: string, value: string): Promise<void> => {
    await set(name, value);
  },
  removeItem: async (name: string): Promise<void> => {
    await del(name);
  },
};

// --- Store Implementation ---

const createSettingsSlice: StateCreator<SettingsState> = (set) => ({
  theme: 'light',
  isOled: false,
  hiddenTabs: [],
  autoUpdateEnabled: false,
  wifiOnly: false,
  deleteApk: false, // Defaults to OFF (Manual Popup Mode)
  useShizuku: false, // Defaults to OFF
  disableAnimations: false,
  compactMode: false,
  highRefreshRate: false,
  hapticEnabled: true,
  glassEffect: true,
  isDevUnlocked: false,
  isLegend: false,
  isContributor: false,
  adWatchCount: 0,
  submissionCount: 0,
  lastSubmissionTime: 0,
  lastLeaderboardSubmissionTime: 0,
  useRemoteJson: true,
  githubToken: '',
  installedVersions: {},
  lastRemoteVersions: {},
  appStreams: {},
  resolvedPackageNames: {},
  ignoredUpdates: {},

  setTheme: (theme) => set({ theme }),
  toggleOled: () => set((state) => ({ isOled: !state.isOled })),
  toggleHiddenTab: (tab) => set((state) => {
    const exists = state.hiddenTabs.includes(tab);
    const next = exists
      ? state.hiddenTabs.filter((t) => t !== tab)
      : [...state.hiddenTabs, tab];
    // Prevent hiding all tabs — at least one platform tab must remain visible
    const visibleTabs = ['android', 'pc', 'tv'].filter(t => !next.includes(t));
    if (visibleTabs.length === 0) return state;
    return { hiddenTabs: next };
  }),
  toggleAutoUpdate: () => set((state) => ({ autoUpdateEnabled: !state.autoUpdateEnabled })),
  toggleWifiOnly: () => set((state) => ({ wifiOnly: !state.wifiOnly })),
  toggleDeleteApk: () => set((state) => ({ deleteApk: !state.deleteApk })),
  toggleUseShizuku: () => set((state) => ({ useShizuku: !state.useShizuku })),
  toggleDisableAnimations: () => set((state) => ({ disableAnimations: !state.disableAnimations })),
  toggleCompactMode: () => set((state) => ({ compactMode: !state.compactMode })),
  toggleHighRefreshRate: () => set((state) => ({ highRefreshRate: !state.highRefreshRate })),
  toggleHaptic: () => set((state) => ({ hapticEnabled: !state.hapticEnabled })),
  toggleGlass: () => set((state) => ({ glassEffect: !state.glassEffect })),
  setDevUnlocked: (val) => set({ isDevUnlocked: val }),
  setIsLegend: (val) => set({ isLegend: val }),
  incrementAdWatch: () => set((state) => {
    const newCount = state.adWatchCount + 1;
    const isContributor = newCount >= 3 || state.isContributor;
    const isLegend = newCount >= 25 || state.isLegend;
    return { adWatchCount: newCount, isContributor, isLegend };
  }),
  registerSubmission: () => set((state) => ({
    submissionCount: state.submissionCount + 1,
    lastSubmissionTime: Date.now()
  })),
  registerLeaderboardSubmission: () => set({ lastLeaderboardSubmissionTime: Date.now() }),
  setSubmissionCount: (count) => set({ submissionCount: count }),
  setUseRemoteJson: (val) => set({ useRemoteJson: val }),
  setGithubToken: (token) => set({ githubToken: token }),
  setInstalledVersions: (versions) => set({ installedVersions: versions }),
  setLastRemoteVersion: (appId, version) => set((state) => ({
    lastRemoteVersions: { ...state.lastRemoteVersions, [appId]: version }
  })),
  removeLastRemoteVersion: (appId) => set((state) => {
    const next = { ...state.lastRemoteVersions };
    delete next[appId];
    return { lastRemoteVersions: next };
  }),
  setAppStream: (appId, stream) => set((state) => ({
    appStreams: { ...state.appStreams, [appId]: stream }
  })),
  setResolvedPackageName: (appId, packageName) => set((state) => ({
    resolvedPackageNames: { ...state.resolvedPackageNames, [appId]: packageName }
  })),
  setAllResolvedPackageNames: (packages) => set({ resolvedPackageNames: packages }),
  setIgnoredUpdate: (appId, type, version) => set((state) => ({
    ignoredUpdates: { ...state.ignoredUpdates, [appId]: { type, timestamp: Date.now(), version } }
  })),
  clearIgnoredUpdate: (appId) => set((state) => {
    const next = { ...state.ignoredUpdates };
    delete next[appId];
    return { ignoredUpdates: next };
  }),
});

export const useSettingsStore = create<SettingsState>()(
  persist(
    createSettingsSlice,
    {
      name: 'orion-settings-storage',
      storage: createJSONStorage(() => idbStorage),
      // IMPORTANT: Explicitly persist package detection state so it survives app restarts
      partialize: (state) => ({
        theme: state.theme,
        isOled: state.isOled,
        hiddenTabs: state.hiddenTabs,
        autoUpdateEnabled: state.autoUpdateEnabled,
        wifiOnly: state.wifiOnly,
        deleteApk: state.deleteApk,
        useShizuku: state.useShizuku,
        disableAnimations: state.disableAnimations,
        compactMode: state.compactMode,
        highRefreshRate: state.highRefreshRate,
        hapticEnabled: state.hapticEnabled,
        glassEffect: state.glassEffect,
        isDevUnlocked: state.isDevUnlocked,
        isLegend: state.isLegend,
        isContributor: state.isContributor,
        adWatchCount: state.adWatchCount,
        submissionCount: state.submissionCount,
        lastSubmissionTime: state.lastSubmissionTime,
        lastLeaderboardSubmissionTime: state.lastLeaderboardSubmissionTime,
        useRemoteJson: state.useRemoteJson,
        githubToken: state.githubToken,
        installedVersions: state.installedVersions,
        lastRemoteVersions: state.lastRemoteVersions,
        appStreams: state.appStreams,
        resolvedPackageNames: state.resolvedPackageNames,
        ignoredUpdates: state.ignoredUpdates,
      })
    }
  )
);

const defaultTabState: TabViewState = {
  query: '',
  category: 'All',
  sort: SortOption.NEWEST,
  filterFavorites: false
};

const createDataSlice: StateCreator<DataState> = (set) => ({
  apps: [],
  importedApps: [],

  // Independent state for each tab
  tabs: {
    android: { ...defaultTabState },
    pc: { ...defaultTabState },
    tv: { ...defaultTabState }
  },

  activeDownloads: {},
  downloadProgress: {},
  downloadStatus: {},
  readyToInstall: {},
  pendingCleanup: {},
  favorites: [],

  setApps: (apps) => set({ apps }),
  setImportedApps: (importedApps) => set({ importedApps }),

  setSearchQuery: (tab, query) => set((state) => ({
    tabs: { ...state.tabs, [tab]: { ...state.tabs[tab] || defaultTabState, query } }
  })),

  setSelectedCategory: (tab, category) => set((state) => ({
    tabs: { ...state.tabs, [tab]: { ...state.tabs[tab] || defaultTabState, category } }
  })),

  setSelectedSort: (tab, sort) => set((state) => ({
    tabs: { ...state.tabs, [tab]: { ...state.tabs[tab] || defaultTabState, sort } }
  })),

  toggleFilterFavorites: (tab) => set((state) => {
    const current = state.tabs[tab] || defaultTabState;
    return {
      tabs: { ...state.tabs, [tab]: { ...current, filterFavorites: !current.filterFavorites } }
    };
  }),

  updateDownloadState: (appId, progress, status) => set((state) => ({
    downloadProgress: { ...state.downloadProgress, [appId]: progress },
    downloadStatus: { ...state.downloadStatus, [appId]: status }
  })),

  startDownload: (appId, downloadId, fileName) => set((state) => {
    const newReady = { ...state.readyToInstall };
    delete newReady[appId];
    return {
      activeDownloads: { ...state.activeDownloads, [appId]: `${downloadId}|${fileName}` },
      downloadStatus: { ...state.downloadStatus, [appId]: 'PENDING' },
      readyToInstall: newReady
    };
  }),

  completeDownload: (appId, fileName) => set((state) => {
    const newActive = { ...state.activeDownloads };
    delete newActive[appId];
    const newProgress = { ...state.downloadProgress };
    delete newProgress[appId];
    const newStatus = { ...state.downloadStatus };
    delete newStatus[appId];

    return {
      activeDownloads: newActive,
      downloadProgress: newProgress,
      downloadStatus: newStatus,
      readyToInstall: { ...state.readyToInstall, [appId]: fileName }
    };
  }),

  failDownload: (appId) => set((state) => {
    const newActive = { ...state.activeDownloads };
    delete newActive[appId];
    return { activeDownloads: newActive };
  }),

  cancelDownload: (appId) => set((state) => {
    const newActive = { ...state.activeDownloads };
    delete newActive[appId];
    const newProgress = { ...state.downloadProgress };
    delete newProgress[appId];
    const newStatus = { ...state.downloadStatus };
    delete newStatus[appId];
    return { activeDownloads: newActive, downloadProgress: newProgress, downloadStatus: newStatus };
  }),

  setReadyToInstall: (map) => set({ readyToInstall: map }),
  setPendingCleanup: (map) => set({ pendingCleanup: map }),

  toggleFavorite: (appId) => set((state) => {
    const exists = state.favorites.includes(appId);
    const next = exists
      ? state.favorites.filter(id => id !== appId)
      : [...state.favorites, appId];
    return { favorites: next };
  }),
});

export const useDataStore = create<DataState>()(
  persist(
    createDataSlice,
    {
      name: 'orion-data-storage',
      storage: createJSONStorage(() => idbStorage),
      partialize: (state) => ({
        importedApps: state.importedApps,
        readyToInstall: state.readyToInstall,
        pendingCleanup: state.pendingCleanup,
        favorites: state.favorites,
        tabs: state.tabs // Persist per-tab settings
      }),
    }
  )
);