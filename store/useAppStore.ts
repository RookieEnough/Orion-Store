import { create, StateCreator } from 'zustand';
import { persist, createJSONStorage, StateStorage } from 'zustand/middleware';
import { get, set, del } from 'idb-keyval';
import { AppFontKey, AppItem, SortOption, UpdateStream } from '../types';
import { DEFAULT_APP_FONT } from '../constants';

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
  appFont: AppFontKey;
  storeLayout: 'classic' | 'modern';
  isOled: boolean;
  hiddenTabs: string[];
  virusTotalApiKey: string;
  autoUpdateEnabled: boolean;
  wifiOnly: boolean;
  deleteApk: boolean; // true = Silent Janitor, false = Manual Popup
  useShizuku: boolean; // true = Silent Install via Shizuku
  installerPreference: 'system' | 'chooser' | 'package';
  installerPackage: string;
  installerLabel: string;
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
  loadLocalData: boolean;
  githubToken: string;
  installedVersions: Record<string, string>; // { appId: version } (From OS)
  lastRemoteVersions: Record<string, string>; // { appId: version } (From Orion Install)
  appStreams: Record<string, UpdateStream>; // { appId: 'Beta' } - Stream Locking
  resolvedPackageNames: Record<string, string>; // { appId: "com.app.preview" } - For handling forks/suffixes
  packageOwners: Record<string, string>; // { packageName: appId } - Ownership for duplicate package entries
  ignoredUpdates: Record<string, { type: 'week' | 'version' | 'never', timestamp?: number, version?: string }>;
  hasSeenModernUITutorial: boolean;
  userProfile: { name: string; avatarId: string; avatarUrl: string; createdAt: number } | null;
  customBundles: Array<{ id: string; title: string; description: string; icon: string; appIds: string[]; createdAt: number; submittedAt?: number }>;
  removedBundleApps: Record<string, string[]>; // { bundleId: [appId, ...] } - locally removed apps from official bundles
  extraBundleApps: Record<string, string[]>; // { bundleId: [appId, ...] } - locally added apps
  gameXP: number;
  dinoHighScore: number;
  unlockedBadges: string[];
  showcasedBadges: string[];
  localMaintenanceMode: boolean;
  hiddenInstallers: string[];
  coinFlipHintCount: number;

  // Actions
  setTheme: (theme: Theme) => void;
  setAppFont: (font: AppFontKey) => void;
  setStoreLayout: (layout: 'classic' | 'modern') => void;
  toggleOled: () => void;
  toggleHiddenTab: (tab: string) => void;
  setVirusTotalApiKey: (apiKey: string) => void;
  toggleAutoUpdate: () => void;
  toggleWifiOnly: () => void;
  toggleDeleteApk: () => void;
  toggleUseShizuku: () => void;
  setInstallerPreference: (installerPreference: 'system' | 'chooser' | 'package', installerPackage?: string, installerLabel?: string) => void;
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
  toggleLoadLocalData: () => void;
  setGithubToken: (token: string) => void;
  setInstalledVersions: (versions: Record<string, string>) => void;
  setLastRemoteVersion: (appId: string, version: string) => void;
  removeLastRemoteVersion: (appId: string) => void;
  setAppStream: (appId: string, stream: UpdateStream) => void;
  setResolvedPackageName: (appId: string, packageName: string) => void;
  setAllResolvedPackageNames: (packages: Record<string, string>) => void;
  setPackageOwner: (packageName: string, appId: string) => void;
  clearPackageOwner: (packageName: string) => void;
  setPackageOwners: (owners: Record<string, string>) => void;
  setIgnoredUpdate: (appId: string, type: 'week' | 'version' | 'never', version?: string) => void;
  clearIgnoredUpdate: (appId: string) => void;
  setHasSeenModernUITutorial: (seen: boolean) => void;
  setUserProfile: (profile: { name: string; avatarId: string; avatarUrl: string } | null) => void;
  addCustomBundle: (bundle: { id: string; title: string; description: string; icon: string; appIds: string[] }) => void;
  updateCustomBundle: (id: string, updates: Partial<{ title: string; description: string; icon: string; appIds: string[]; submittedAt: number }>) => void;
  removeCustomBundle: (id: string) => void;
  toggleBundleAppRemoval: (bundleId: string, appId: string) => void;
  addAppToBundle: (bundleId: string, appId: string) => void;
  removeAppFromBundleExtras: (bundleId: string, appId: string) => void;
  resetBundleCustomizations: (bundleId: string) => void;
  addGameXP: (amount: number) => void;
  updateDinoHighScore: (score: number) => void;
  unlockBadge: (badgeId: string) => void;
  toggleShowcasedBadge: (badgeId: string) => void;
  setShowcasedBadgeAtIndex: (index: number, badgeId: string) => void;
  clearShowcasedBadges: () => void;
  toggleLocalMaintenance: () => void;
  hideInstaller: (packageName: string) => void;
  resetHiddenInstallers: () => void;
  incrementCoinFlipHint: () => void;
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

const getInitialTheme = (): Theme => {
  try {
    const cached = localStorage.getItem('app-theme') as Theme;
    if (cached && ['light', 'dusk', 'dark', 'oled'].includes(cached)) return cached;
  } catch {}
  return 'light';
};

const createSettingsSlice: StateCreator<SettingsState> = (set) => ({
  theme: getInitialTheme(),
  appFont: DEFAULT_APP_FONT,
  storeLayout: 'classic', // Default to classic for lighter new installs
  isOled: false,
  hiddenTabs: [],
  virusTotalApiKey: '',
  autoUpdateEnabled: false,
  wifiOnly: false,
  deleteApk: false, // Defaults to OFF (Manual Popup Mode)
  useShizuku: false, // Defaults to OFF
  installerPreference: 'system',
  installerPackage: '',
  installerLabel: '',
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
  loadLocalData: false,
  githubToken: '',
  installedVersions: {},
  lastRemoteVersions: {},
  appStreams: {},
  resolvedPackageNames: {},
  packageOwners: {},
  ignoredUpdates: {},
  hasSeenModernUITutorial: false,
  userProfile: null,
  customBundles: [],
  removedBundleApps: {},
  extraBundleApps: {},
  gameXP: 0,
  dinoHighScore: 0,
  unlockedBadges: [],
  showcasedBadges: [],
  localMaintenanceMode: false,
  hiddenInstallers: [],
  coinFlipHintCount: 0,

  setTheme: (theme) => { try { localStorage.setItem('app-theme', theme); } catch {} set({ theme }); },
  setAppFont: (appFont) => set({ appFont }),
  setStoreLayout: (layout) => set({ storeLayout: layout }),
  toggleOled: () => set((state) => {
    const next = !state.isOled;
    try { localStorage.setItem('app-is-oled', String(next)); } catch {}
    return { isOled: next };
  }),
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
  setVirusTotalApiKey: (virusTotalApiKey) => set({ virusTotalApiKey }),
  toggleAutoUpdate: () => set((state) => ({ autoUpdateEnabled: !state.autoUpdateEnabled })),
  toggleWifiOnly: () => set((state) => ({ wifiOnly: !state.wifiOnly })),
  toggleDeleteApk: () => set((state) => ({ deleteApk: !state.deleteApk })),
  toggleUseShizuku: () => set((state) => ({ useShizuku: !state.useShizuku })),
  setInstallerPreference: (installerPreference, installerPackage = '', installerLabel = '') => set({
    installerPreference,
    installerPackage: installerPreference === 'package' ? installerPackage : '',
    installerLabel: installerPreference === 'package' ? installerLabel : ''
  }),
  hideInstaller: (packageName) => set((state) => {
    if (state.hiddenInstallers.includes(packageName)) return state;
    return { hiddenInstallers: [...state.hiddenInstallers, packageName] };
  }),
  resetHiddenInstallers: () => set({ hiddenInstallers: [] }),
  incrementCoinFlipHint: () => set((state) => ({ coinFlipHintCount: Math.min(state.coinFlipHintCount + 1, 3) })),
  toggleDisableAnimations: () => set((state) => ({ disableAnimations: !state.disableAnimations })),
  toggleCompactMode: () => set((state) => ({ compactMode: !state.compactMode })),
  toggleHighRefreshRate: () => set((state) => ({ highRefreshRate: !state.highRefreshRate })),
  toggleHaptic: () => set((state) => ({ hapticEnabled: !state.hapticEnabled })),
  toggleGlass: () => set((state) => ({ glassEffect: !state.glassEffect })),
  setDevUnlocked: (val) => set((state) => ({ 
    isDevUnlocked: val,
    localMaintenanceMode: val ? state.localMaintenanceMode : false
  })),
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
  toggleLoadLocalData: () => set((state) => ({ loadLocalData: !state.loadLocalData })),
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
  setPackageOwner: (packageName, appId) => set((state) => ({
    packageOwners: { ...state.packageOwners, [packageName]: appId }
  })),
  clearPackageOwner: (packageName) => set((state) => {
    const next = { ...state.packageOwners };
    delete next[packageName];
    return { packageOwners: next };
  }),
  setPackageOwners: (owners) => set({ packageOwners: owners }),
  setIgnoredUpdate: (appId, type, version) => set((state) => ({
    ignoredUpdates: { ...state.ignoredUpdates, [appId]: { type, timestamp: Date.now(), version } }
  })),
  clearIgnoredUpdate: (appId) => set((state) => {
    const next = { ...state.ignoredUpdates };
    delete next[appId];
    return { ignoredUpdates: next };
  }),
  setHasSeenModernUITutorial: (seen) => set({ hasSeenModernUITutorial: seen }),
  setUserProfile: (profile) => set((state) => ({
    userProfile: profile ? {
      name: profile.name,
      avatarId: profile.avatarId,
      avatarUrl: profile.avatarUrl,
      createdAt: state.userProfile?.createdAt ?? Date.now()
    } : null
  })),
  addCustomBundle: (bundle) => set((state) => ({
    customBundles: [...state.customBundles, { ...bundle, createdAt: Date.now() }]
  })),
  updateCustomBundle: (id, updates) => set((state) => ({
    customBundles: state.customBundles.map((b) => (b.id === id ? { ...b, ...updates } : b))
  })),
  removeCustomBundle: (id) => set((state) => ({
    customBundles: state.customBundles.filter((b) => b.id !== id)
  })),
  toggleBundleAppRemoval: (bundleId, appId) => set((state) => {
    const current = state.removedBundleApps[bundleId] || [];
    const exists = current.includes(appId);
    const next = exists ? current.filter((id) => id !== appId) : [...current, appId];
    return { removedBundleApps: { ...state.removedBundleApps, [bundleId]: next } };
  }),
  addAppToBundle: (bundleId, appId) => set((state) => {
    const current = state.extraBundleApps[bundleId] || [];
    if (current.includes(appId)) return state;
    return { extraBundleApps: { ...state.extraBundleApps, [bundleId]: [...current, appId] } };
  }),
  removeAppFromBundleExtras: (bundleId, appId) => set((state) => {
    const current = state.extraBundleApps[bundleId] || [];
    return { extraBundleApps: { ...state.extraBundleApps, [bundleId]: current.filter((id) => id !== appId) } };
  }),
  resetBundleCustomizations: (bundleId) => set((state) => {
    const removed = { ...state.removedBundleApps };
    const extras = { ...state.extraBundleApps };
    delete removed[bundleId];
    delete extras[bundleId];
    return { removedBundleApps: removed, extraBundleApps: extras };
  }),
  addGameXP: (amount) => set((state) => {
    // Simple anti-cheat: capping XP gain per call and total XP
    const cappedAmount = Math.min(amount, 50); 
    return { gameXP: state.gameXP + cappedAmount };
  }),
  updateDinoHighScore: (score) => set((state) => ({
    dinoHighScore: Math.max(state.dinoHighScore, score)
  })),
  unlockBadge: (badgeId) => set((state) => {
    if (state.unlockedBadges.includes(badgeId)) return state;
    return { unlockedBadges: [...state.unlockedBadges, badgeId] };
  }),
  toggleShowcasedBadge: (badgeId) => set((state) => {
    const exists = state.showcasedBadges.includes(badgeId);
    if (exists) {
      return { showcasedBadges: state.showcasedBadges.filter((id) => id !== badgeId) };
    }
    if (state.showcasedBadges.length >= 3) {
      return state;
    }
    return { showcasedBadges: [...state.showcasedBadges, badgeId] };
  }),
  setShowcasedBadgeAtIndex: (index, badgeId) => set((state) => {
    if (index < 0 || index > 2) return state;
    if (state.showcasedBadges[index] === badgeId) return state;
    if (state.showcasedBadges.includes(badgeId)) return state;

    const next = [...state.showcasedBadges];
    if (index >= next.length) {
      if (next.length >= 3) return state;
      next.push(badgeId);
    } else {
      next[index] = badgeId;
    }

    return { showcasedBadges: next.slice(0, 3) };
  }),
  clearShowcasedBadges: () => set({ showcasedBadges: [] }),
  toggleLocalMaintenance: () => set((state) => ({ localMaintenanceMode: !state.localMaintenanceMode })),
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
        appFont: state.appFont,
        storeLayout: state.storeLayout,
        isOled: state.isOled,
        hiddenTabs: state.hiddenTabs,
        virusTotalApiKey: state.virusTotalApiKey,
        autoUpdateEnabled: state.autoUpdateEnabled,
        wifiOnly: state.wifiOnly,
        deleteApk: state.deleteApk,
        useShizuku: state.useShizuku,
        installerPreference: state.installerPreference,
        installerPackage: state.installerPackage,
        installerLabel: state.installerLabel,
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
        loadLocalData: state.loadLocalData,
        githubToken: state.githubToken,
        installedVersions: state.installedVersions,
        lastRemoteVersions: state.lastRemoteVersions,
        appStreams: state.appStreams,
        resolvedPackageNames: state.resolvedPackageNames,
        packageOwners: state.packageOwners,
        ignoredUpdates: state.ignoredUpdates,
        hasSeenModernUITutorial: state.hasSeenModernUITutorial,
        userProfile: state.userProfile,
        customBundles: state.customBundles,
        removedBundleApps: state.removedBundleApps,
        extraBundleApps: state.extraBundleApps,
        gameXP: state.gameXP,
        dinoHighScore: state.dinoHighScore,
        unlockedBadges: state.unlockedBadges,
        showcasedBadges: state.showcasedBadges,
        localMaintenanceMode: state.localMaintenanceMode,
        hiddenInstallers: state.hiddenInstallers,
      }),
      onRehydrateStorage: () => (state) => {
        if (state && state.theme) {
          try {
            localStorage.setItem('app-theme', state.theme);
            document.documentElement.classList.remove('light', 'dusk', 'dark', 'oled');
            document.documentElement.classList.add(state.theme);
          } catch {}
        }
      }
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
