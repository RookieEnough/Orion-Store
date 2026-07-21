
// --- IMMEDIATE THEME DETECTION (Prevents white flash on boot) ---
(() => {
    try {
        const theme = localStorage.getItem('app-theme') || 'light';
        const isOled = localStorage.getItem('app-is-oled') === 'true';
        const root = document.documentElement;
        root.classList.remove('light', 'dusk', 'dark', 'oled');
        if (theme === 'dark' && isOled) {
            root.classList.add('oled', 'dark');
        } else {
            root.classList.add(theme);
        }
    } catch (e) { }
})();

import React, { useState, useEffect, useCallback, useMemo, useRef, Suspense, lazy, useDeferredValue, startTransition } from 'react';
import { flushSync } from 'react-dom';
import { App as CapacitorApp } from '@capacitor/app';
import { Capacitor } from '@capacitor/core';
import { LocalNotifications, ActionPerformed } from '@capacitor/local-notifications';
import { Haptics, ImpactStyle, NotificationType } from '@capacitor/haptics';
import { UnityAds } from 'capacitor-unity-ads';
import { shallow } from 'zustand/shallow';
import { DEV_SOCIALS, DEFAULT_FAQS, DEFAULT_DEV_PROFILE, DEFAULT_SUPPORT_EMAIL, DEFAULT_EASTER_EGG, DEFAULT_DONATION, CACHE_VERSION, NETWORK_TIMEOUT_MS, getAppFontDefinition } from './constants';
import { Platform, AppItem, AppFontKey, Tab, StoreConfig, SortOption, StoreCollection, BundleItem, UpdateStream } from './types';
import ClassicAppList from './components/ClassicAppList';
import ExpandedNewUpdatedGrid from './components/ExpandedNewUpdatedGrid';
const ModernAppList = lazy(() => import('./components/ModernAppList').then(m => ({ default: m.default })));
const ModernHomeSkeleton = lazy(() => import('./components/ModernAppList').then(m => ({ default: m.ModernHomeSkeleton })));
import Header from './components/Header';
import BottomNav from './components/BottomNav';
import StoreFilters from './components/StoreFilters';
import ModernUITutorial from './components/ModernUITutorial';
import MaintenanceMode from './components/MaintenanceMode';
import SpotlightOverlay from './components/SpotlightOverlay';
import AppTracker, { AppInfoResult } from './plugins/AppTracker';
import { useTutorialStore, TutorialStep } from './store/useTutorialStore';
import { useSettingsStore, useDataStore, CleanupEntry, Theme, TabViewState } from './store/useAppStore';
import { useDevConfigStore } from './store/useDevConfigStore';
import { getAvailableFilterCategories } from './utils/discovery';
import { compareVersions, getPreferredVersion, isComparableVersion, isSameVersion } from './utils/appVersioning';
import { sanitizeDownloadUrl } from './utils/downloadUrl';
import useAvailableUpdates from './hooks/useAvailableUpdates';
import ForcedStoreUpdateGate from './components/ForcedStoreUpdateGate';
import {
    ForcedStoreUpdateStatus,
    buildForcedStoreFileName,
    canResumeForcedStoreUpdate,
    createForcedStoreUpdateSnapshot,
    isStoreUpdateAvailable,
    shouldForceStoreUpdate
} from './utils/forcedStoreUpdate';

import { useScrollLock } from './hooks/useScrollLock';
import { usePullToRefresh } from './hooks/usePullToRefresh';
import { useVirusTotalScan } from './hooks/useVirusTotalScan';
import type { VirusTotalResult } from './hooks/useVirusTotalScan';
import CoreWorker from './workers/core.worker?worker';

const FAQModal = lazy(() => import('./components/FAQModal'));
const AdDonationModal = lazy(() => import('./components/AdDonationModal'));
const AboutTabContainer = lazy(() => import('./components/AboutTabContainer'));
const SelectedAppModalContainer = lazy(() => import('./components/SelectedAppModalContainer'));
const SubmissionModal = lazy(() => import('./components/SubmissionModal'));
const CustomBundleModal = lazy(() => import('./components/CustomBundleModal'));
const SettingsModal = lazy(() => import('./components/SettingsModal'));
// Preload-on-press cache: triggered on pointerdown of the settings icon so the
// chunk is already coming in by the time the click event fires, making the
// modal open with zero perceived delay.
let settingsModalPreloadPromise: Promise<unknown> | null = null;
const preloadSettingsModal = () => {
    if (!settingsModalPreloadPromise) {
        settingsModalPreloadPromise = import('./components/SettingsModal').catch(() => { });
    }
    return settingsModalPreloadPromise;
};

const NoticeModal = lazy(() => import('./components/NoticeModal'));
const SplashScreenPreview = lazy(() => import('./components/SplashScreenPreview'));
const ReleaseNotesModal = lazy(() => import('./components/ReleaseNotesModal'));
const BundlePreviewModal = lazy(() => import('./components/BundlePreviewModal'));
const ProfileStatsModal = lazy(() => import('./components/ProfileStatsModal'));
const VirusTotalScanModal = lazy(() => import('./components/VirusTotalScanModal'));

const CURRENT_STORE_VERSION = '1.3.5';
const UNITY_GAME_ID = '5996387';
const ADS_TEST_MODE = false;

const CONFIG_URL_PRIMARY = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/config.json';
const APPS_URL_PRIMARY = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/apps.json';

const CONFIG_URL_GITLAB = 'https://gitlab.com/RookieEnough/Orion-Data/-/raw/main/config.json';
const APPS_URL_GITLAB = 'https://gitlab.com/RookieEnough/Orion-Data/-/raw/main/apps.json';

const CONFIG_URL_CODEBERG = 'https://codeberg.org/RookieEnough/Orion-Data/raw/branch/main/config.json';
const APPS_URL_CODEBERG = 'https://codeberg.org/RookieEnough/Orion-Data/raw/branch/main/apps.json';

const APPS_URL_FALLBACK = 'https://cdn.jsdelivr.net/gh/RookieEnough/Orion-Data@main/apps.json';
const CONFIG_URL_FALLBACK = 'https://cdn.jsdelivr.net/gh/RookieEnough/Orion-Data@main/config.json';
const DEFAULT_MIRROR_JSON = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/data/mirror.json';

const ONE_WEEK_MS = 7 * 24 * 60 * 60 * 1000;
const PACKAGE_SUFFIXES = ['.preview', '.debug', '.test', '.beta', '.canary', '.dev', '.alpha', '.nightly', '.staging', '.release'];
const FALLBACK_TAB_STATE = Object.freeze({
    query: '',
    category: 'All',
    sort: SortOption.NEWEST,
    filterFavorites: false
});
const APP_FONT_STYLESHEET_URLS: Partial<Record<AppFontKey, string>> = {
    spaceGrotesk: 'https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;500;600;700&display=swap',
    inter: 'https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap',
    poppins: 'https://fonts.googleapis.com/css2?family=Poppins:wght@400;500;600;700&display=swap',
    manrope: 'https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600;700&display=swap',
    outfit: 'https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700&display=swap',
    dmSans: 'https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;700&display=swap',
    plusJakartaSans: 'https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap',
    rubik: 'https://fonts.googleapis.com/css2?family=Rubik:wght@400;500;600;700&display=swap',
    nunitoSans: 'https://fonts.googleapis.com/css2?family=Nunito+Sans:wght@400;600;700;800&display=swap',
    publicSans: 'https://fonts.googleapis.com/css2?family=Public+Sans:wght@400;500;600;700&display=swap'
};
const APP_FONT_LINK_ID = 'orion-app-font';

type ViewTransitionCapableDocument = Document & {
    startViewTransition?: (updateCallback: () => void | Promise<void>) => {
        finished: Promise<void>;
    };
};

const loadEmbeddedApps = async (): Promise<AppItem[]> => {
    const module = await import('./localData');
    return module.localAppsData as unknown as AppItem[];
};

const fetchWithTimeout = async (resource: string, options: RequestInit & { timeout?: number } = {}) => {
    const { timeout = NETWORK_TIMEOUT_MS } = options;
    const controller = new AbortController();
    const id = setTimeout(() => controller.abort(), timeout);
    try {
        const response = await fetch(resource, { ...options, signal: controller.signal });
        clearTimeout(id);
        return response;
    } catch (error) {
        clearTimeout(id);
        throw error;
    }
};

const fetchWithRetry = async (url: string, options: any, retries = 3, backoff = 1000) => {
    for (let i = 0; i < retries; i++) {
        try {
            const res = await fetchWithTimeout(url, options);
            if (res.ok) return res;
            throw new Error(`Request failed with status ${res.status}`);
        } catch (e) {
            if (i === retries - 1) throw e;
            await new Promise(r => setTimeout(r, backoff * (i + 1)));
        }
    }
    throw new Error('Retries exhausted');
};

const ensureAppFontStylesheet = (fontKey: AppFontKey) => {
    const href = APP_FONT_STYLESHEET_URLS[fontKey];
    if (typeof document === 'undefined') return;

    let link = document.getElementById(APP_FONT_LINK_ID) as HTMLLinkElement | null;

    // System default font: remove any loaded Google Font stylesheet
    if (!href) {
        if (link) {
            link.remove();
        }
        return;
    }

    if (!link) {
        link = document.createElement('link');
        link.id = APP_FONT_LINK_ID;
        link.rel = 'stylesheet';
        document.head.appendChild(link);
    }

    if (link.href !== href) {
        link.href = href;
    }
};

const getModernSortSectionTitle = (sort: SortOption) => {
    switch (sort) {
        case SortOption.HOME:
            return { title: 'Home Page', continuation: 'More Picks For You' };
        case SortOption.OLDEST:
            return { title: 'Oldest First', continuation: 'More Classics' };
        case SortOption.NAME_ASC:
            return { title: 'A to Z', continuation: 'More A to Z' };
        case SortOption.NAME_DESC:
            return { title: 'Z to A', continuation: 'More Z to A' };
        case SortOption.SIZE_ASC:
            return { title: 'Smallest Footprint', continuation: 'More Lightweight Picks' };
        case SortOption.SIZE_DESC:
            return { title: 'Largest Packages', continuation: 'More Heavyweights' };
        default:
            return { title: 'Recently Added', continuation: 'More Fresh Picks' };
    }
};

const buildSortedModernCollections = (apps: AppItem[], sort: SortOption, showAll = false): StoreCollection[] => {
    if (apps.length === 0) return [];

    const { title } = getModernSortSectionTitle(sort);
    const previewCount = showAll ? apps.length : 12;

    const collections: StoreCollection[] = [
        {
            id: `sorted-hero-${sort}${showAll ? '-all' : ''}`,
            title,
            type: 'hero',
            apps: apps.slice(0, 5)
        },
        {
            id: `sorted-grid-${sort}${showAll ? '-all' : ''}`,
            title,
            type: 'sorted_grid',
            apps: apps.slice(0, previewCount),
            totalAppCount: apps.length
        }
    ];

    return collections;
};

const isModernHomeState = (tabState: TabViewState) => (
    tabState.category === 'All'
    && !tabState.query
    && !tabState.filterFavorites
);

const getDisplaySort = (storeLayout: 'classic' | 'modern', tabState: TabViewState) => {
    if (storeLayout !== 'modern') {
        return tabState.sort === SortOption.HOME ? SortOption.NEWEST : tabState.sort;
    }

    if (isModernHomeState(tabState) && (tabState.sort === SortOption.NEWEST || tabState.sort === SortOption.HOME)) {
        return SortOption.HOME;
    }

    return tabState.sort === SortOption.HOME ? SortOption.NEWEST : tabState.sort;
};

const buildUpdatesAvailableCollection = (apps: AppItem[]): StoreCollection | null => {
    if (apps.length === 0) return null;

    return {
        id: 'updates-available',
        title: 'Updates Available',
        type: 'swimlane',
        apps: apps.slice(0, 12),
        totalAppCount: apps.length
    };
};

const injectCollectionAfterHero = (collections: StoreCollection[], injectedCollection: StoreCollection | null) => {
    if (!injectedCollection) return collections;

    const withoutDuplicate = collections.filter((collection) => collection.id !== injectedCollection.id);
    const heroIndex = withoutDuplicate.findIndex((collection) => collection.type === 'hero');

    if (heroIndex === -1) {
        return [injectedCollection, ...withoutDuplicate];
    }

    return [
        ...withoutDuplicate.slice(0, heroIndex + 1),
        injectedCollection,
        ...withoutDuplicate.slice(heroIndex + 1)
    ];
};

const scheduleBackgroundTask = (callback: () => void, timeout = 800) => {
    if (typeof window === 'undefined') return () => { };

    const callbackWindow = window as Window & {
        requestIdleCallback?: (fn: () => void, options?: { timeout?: number }) => number;
        cancelIdleCallback?: (id: number) => void;
    };

    if (callbackWindow.requestIdleCallback) {
        const idleId = callbackWindow.requestIdleCallback(() => callback(), { timeout });
        return () => callbackWindow.cancelIdleCallback?.(idleId);
    }

    const timer = window.setTimeout(callback, Math.min(timeout, 350));
    return () => window.clearTimeout(timer);
};

const normalizePackageName = (packageName?: string) => (packageName || '').trim().toLowerCase();

const sanitizeFileVersionToken = (version?: string) => (version || 'latest').replace(/[^a-zA-Z0-9._-]/g, '_');

const getDownloadVersionMetadata = (
    app: AppItem,
    targetUrl?: string,
    preferredStream: UpdateStream = 'Stable'
): { version?: string; stream?: UpdateStream } => {
    if (targetUrl && app.availableVersions) {
        for (const version of app.availableVersions) {
            if (version.variants.some((variant) => variant.url === targetUrl)) {
                return { version: version.version, stream: version.type };
            }
        }
    }

    const version = getPreferredVersion(app, preferredStream);
    return { version, stream: preferredStream };
};

const getVersionMetadataFromFileName = (
    app: AppItem,
    fileName?: string,
    preferredStream: UpdateStream = 'Stable'
): { version?: string; stream?: UpdateStream } => {
    if (!fileName) {
        return getDownloadVersionMetadata(app, undefined, preferredStream);
    }

    const normalizedFileName = fileName.toLowerCase();

    if (app.availableVersions) {
        for (const version of app.availableVersions) {
            const versionToken = sanitizeFileVersionToken(version.version).toLowerCase();
            if (versionToken && normalizedFileName.includes(versionToken)) {
                return { version: version.version, stream: version.type };
            }
        }
    }

    return getDownloadVersionMetadata(app, undefined, preferredStream);
};

const App: React.FC = () => {
    const settings = useSettingsStore((state) => ({
        theme: state.theme,
        appFont: state.appFont,
        isOled: state.isOled,
        hiddenTabs: state.hiddenTabs,
        autoUpdateEnabled: state.autoUpdateEnabled,
        wifiOnly: state.wifiOnly,
        deleteApk: state.deleteApk,
        disableAnimations: state.disableAnimations,
        compactMode: state.compactMode,
        highRefreshRate: state.highRefreshRate,
        pullToRefreshCharacter: state.pullToRefreshCharacter,
        hapticEnabled: state.hapticEnabled,
        glassEffect: state.glassEffect,
        useShizuku: state.useShizuku,
        installerPreference: state.installerPreference,
        installerPackage: state.installerPackage,
        isDevUnlocked: state.isDevUnlocked,
        adWatchCount: state.adWatchCount,
        submissionCount: state.submissionCount,
        lastSubmissionTime: state.lastSubmissionTime,
        useRemoteJson: state.useRemoteJson,
        loadLocalData: state.loadLocalData,
        storeLayout: state.storeLayout,
        hasSeenModernUITutorial: state.hasSeenModernUITutorial,
        customBundles: state.customBundles,
        localMaintenanceMode: state.localMaintenanceMode,
        virusTotalApiKey: state.virusTotalApiKey,
        forcedStoreUpdate: state.forcedStoreUpdate,
        setVirusTotalApiKey: state.setVirusTotalApiKey,
        setTheme: state.setTheme,
        setAppStream: state.setAppStream,
        setInstalledVersions: state.setInstalledVersions,
        setLastRemoteVersion: state.setLastRemoteVersion,
        setAllResolvedPackageNames: state.setAllResolvedPackageNames,
        setPackageOwner: state.setPackageOwner,
        setPackageOwners: state.setPackageOwners,
        removeLastRemoteVersion: state.removeLastRemoteVersion,
        setDevUnlocked: state.setDevUnlocked,
        incrementAdWatch: state.incrementAdWatch,
        setIsLegend: state.setIsLegend,
        registerSubmission: state.registerSubmission,
        setHasSeenModernUITutorial: state.setHasSeenModernUITutorial,
        userProfile: state.userProfile,
        setForcedStoreUpdate: state.setForcedStoreUpdate,
        clearForcedStoreUpdate: state.clearForcedStoreUpdate
    }), shallow);
    const data = useDataStore((state) => ({
        apps: state.apps,
        importedApps: state.importedApps,
        favorites: state.favorites,
        activeDownloads: state.activeDownloads,
        readyToInstall: state.readyToInstall,
        pendingCleanup: state.pendingCleanup,
        setApps: state.setApps,
        setImportedApps: state.setImportedApps,
        setSearchQuery: state.setSearchQuery,
        setSelectedCategory: state.setSelectedCategory,
        setSelectedSort: state.setSelectedSort,
        toggleFilterFavorites: state.toggleFilterFavorites,
        updateDownloadState: state.updateDownloadState,
        startDownload: state.startDownload,
        cancelDownload: state.cancelDownload,
        setReadyToInstall: state.setReadyToInstall,
        setPendingCleanup: state.setPendingCleanup
    }), shallow);
    const workerRef = useRef<Worker | null>(null);

    const [showSplashPreview, setShowSplashPreview] = useState(!Capacitor.isNativePlatform());
    const [activeTab, setActiveTab] = useState<Tab>('android');
    const [selectedApp, setSelectedApp] = useState<AppItem | null>(null);
    const [vtScanTarget, setVtScanTarget] = useState<AppItem | null>(null);
    const [selectedBundle, setSelectedBundle] = useState<BundleItem | null>(null);
    const [showScrollTop, setShowScrollTop] = useState(false);
    const [showSettingsModal, setShowSettingsModal] = useState(false);
    const [settingsInitialMenu, setSettingsInitialMenu] = useState<'none' | 'network' | 'storage' | 'visuals' | 'interface' | 'queue' | 'identity' | 'installer'>('none');
    const [installingId, setInstallingId] = useState<string | null>(null);
    const [scanningId, setScanningId] = useState<string | null>(null);
    const [showInstallToast, setShowInstallToast] = useState<{ app: AppItem, file: string } | null>(null);
    const [showErrorToast, setShowErrorToast] = useState(false);
    const [errorMsg, setErrorMsg] = useState('Failed to load apps');
    const [showFAQ, setShowFAQ] = useState(false);
    const [showAdDonation, setShowAdDonation] = useState(false);
    const [showSubmissionModal, setShowSubmissionModal] = useState(false);
    const [showCustomBundleModal, setShowCustomBundleModal] = useState(false);
    const [submissionCooldown, setSubmissionCooldown] = useState<string | null>(null);
    const [storeUpdateAvailable, setStoreUpdateAvailable] = useState(false);

    const [storeUpdateUrl, setStoreUpdateUrl] = useState('');
    const [forcedStoreUpdateStatus, setForcedStoreUpdateStatus] = useState<ForcedStoreUpdateStatus>('inactive');
    const forcedStoreUpdateStatusRef = useRef<ForcedStoreUpdateStatus>('inactive');
    // Keep the ref in sync so stale closures (e.g. evaluateStoreUpdateState
    // inside loadApps) can always read the latest value.
    useEffect(() => { forcedStoreUpdateStatusRef.current = forcedStoreUpdateStatus; }, [forcedStoreUpdateStatus]);
    const commitForcedStoreUpdateStatus = useCallback((status: ForcedStoreUpdateStatus) => {
        forcedStoreUpdateStatusRef.current = status;
        setForcedStoreUpdateStatus(status);
    }, []);
    const [forcedStoreProgress, setForcedStoreProgress] = useState(0);
    const [forcedStoreStatusText, setForcedStoreStatusText] = useState('');
    const [forcedStoreError, setForcedStoreError] = useState('');
    const [forcedStoreExportPath, setForcedStoreExportPath] = useState('');
    const [forcedStoreScanResult, setForcedStoreScanResult] = useState<VirusTotalResult | null>(null);
    const [forcedStorePollingActive, setForcedStorePollingActive] = useState(false);
    const [isTestingForcedUpdate, setIsTestingForcedUpdate] = useState(false);
    const [testForcedStatus, setTestForcedStatus] = useState<ForcedStoreUpdateStatus>('ready_to_scan_or_install');
    const [devClickCount, setDevClickCount] = useState(0);
    const [devToast, setDevToast] = useState<string | null>(null);
    const [easterEggCount, setEasterEggCount] = useState(0);
    const [isAnnouncementDismissed, setIsAnnouncementDismissed] = useState(false);
    const [isLoading, setIsLoading] = useState(false);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [remoteConfig, setRemoteConfig] = useState<StoreConfig | null>(null);
    const effectiveRemoteConfig = remoteConfig;
    const devConfigEnabled = useDevConfigStore((state) => state.enabled);
    const devConfigHasHydrated = useDevConfigStore((state) => state.hasHydrated);
    const [isForcedUpdateBypassedForSession, setIsForcedUpdateBypassedForSession] = useState(false);
    const [mirrorSource, setMirrorSource] = useState<string>('Checking...');
    const [pendingInstallRetry, setPendingInstallRetry] = useState<{ app: AppItem, file: string } | null>(null);
    const [showCleanupPill, setShowCleanupPill] = useState(false);
    const [showNotice, setShowNotice] = useState(false);
    const [showReleaseNotes, setShowReleaseNotes] = useState(false);
    const [showModernUITutorial, setShowModernUITutorial] = useState(false);
    const [showProfileStats, setShowProfileStats] = useState(false);
    const [profileStatsInitialView, setProfileStatsInitialView] = useState<'profile' | 'badges'>('profile');
    const [profileBadgeSelectionIndex, setProfileBadgeSelectionIndex] = useState<number | null>(null);
    const [showAllSorted, setShowAllSorted] = useState(false);
    const [viewAllApps, setViewAllApps] = useState<AppItem[] | null>(null);
    const [expandedModernView, setExpandedModernView] = useState<null | 'new_updated'>(null);
    const [expandedModernApps, setExpandedModernApps] = useState<AppItem[]>([]);
    const [returnToExpandedView, setReturnToExpandedView] = useState<null | 'new_updated'>(null);
    const [visibleApps, setVisibleApps] = useState<AppItem[]>([]);
    const [storeCollections, setStoreCollections] = useState<StoreCollection[]>([]);
    const [bypassMaintenance, setBypassMaintenance] = useState(false);
    const [isSortTransitioning, setIsSortTransitioning] = useState(false);
    const [isStartupUiReady, setIsStartupUiReady] = useState(!Capacitor.isNativePlatform());

    const devToastTimer = useRef<any>(null);
    const isMounted = useRef(true);
    const initializingDownloads = useRef<Set<string>>(new Set());
    const installingIdRef = useRef<string | null>(null);
    const waitingForResumeId = useRef<string | null>(null);
    const lastViewStateKey = useRef<string | null>(null);
    const lastRequestedSortRef = useRef<SortOption>(SortOption.NEWEST);
    const lastRequestedCategoryRef = useRef<string>('All');
    const lastRequestedTabRef = useRef<Tab>('android');
    const pendingSortTransitionRef = useRef<{ active: boolean; startedAt: number }>({ active: false, startedAt: 0 });
    const sortTransitionTimerRef = useRef<number | null>(null);
    const scrollBoostTimerRef = useRef<number | null>(null);
    const installedSyncTimerRef = useRef<number | null>(null);
    const loadGenerationRef = useRef(0);
    const backgroundTaskCleanupRef = useRef<(() => void) | null>(null);
    const pendingInstallExpectationsRef = useRef<Record<string, { appId: string; version?: string }>>({});
    const appListScrollRef = useRef<HTMLDivElement | null>(null);
    const forcedStoreDownloadIdRef = useRef<string | null>(null);
    const forcedStoreDownloadTriggered = useRef(false);
    const forcedStoreDownloadUrlRef = useRef('');
    const forcedStorePendingScanAfterKeyRef = useRef(false);
    const pendingDeepLinkRef = useRef<string | null>(null);
    // VirusTotal scan hook for the forced self-update flow. Driven inline by
    // the ForcedStoreUpdateGate (no modal hand-off). The package name mirrors
    // the synthetic app identity used in the gate render block below.
    const forcedStorePackageName = Capacitor.isNativePlatform()
        ? (window as any)?.orionStorePackageName || 'com.orion.store'
        : 'com.orion.store';
    const forcedStoreVt = useVirusTotalScan({
        apiKey: settings.virusTotalApiKey,
        appId: '__orion_store_self_update__',
        appName: 'Orion Store',
        packageName: forcedStorePackageName
    });
    // Destructure the stable fields used by handlers/effects. We read them here
    // (rather than spreading forcedStoreVt in deps) so the scan handler and the
    // result/error watcher don't churn identity on every hook-driven re-render.
    const { startScan: forcedStoreVtStartScan } = forcedStoreVt;
    // Pull-to-refresh is only enabled on the app data tabs (Android / PC / TV).
    // The About tab is excluded so that pulling on it is a no-op.
    const isAppDataTab = activeTab === 'android' || activeTab === 'pc' || activeTab === 'tv';
    const syncInstalledAppsRef = useRef<() => Promise<void>>(async () => { });

    const pendingCleanupCount = useMemo(() => Object.keys(data.pendingCleanup).length, [data.pendingCleanup]);
    const hasForcedStoreScanResult =
        !!forcedStoreScanResult ||
        forcedStoreVt.view === 'results' ||
        settings.forcedStoreUpdate?.status === 'scan_complete';
    const shouldBlockWithForcedStoreUpdate =
        (forcedStoreUpdateStatus !== 'inactive' || hasForcedStoreScanResult) &&
        !isForcedUpdateBypassedForSession;
    const isTutorialActive = useTutorialStore(state => state.isActive);
    const isAnyModalOpen = !!selectedApp
        || !!selectedBundle
        || !!vtScanTarget
        || showSettingsModal
        || showAdDonation
        || showSubmissionModal

        || showFAQ
        || showNotice
        || showReleaseNotes
        || showModernUITutorial
        || showCustomBundleModal
        || showProfileStats
        || isTutorialActive
        || shouldBlockWithForcedStoreUpdate;

    useScrollLock(isAnyModalOpen);

    useEffect(() => {
        if (showSplashPreview) {
            const timer = setTimeout(() => {
                if (isMounted.current) setShowSplashPreview(false);
            }, 1200);
            return () => clearTimeout(timer);
        }
    }, [showSplashPreview]);

    const startTutorial = useTutorialStore(state => state.startTutorial);

    // One-time Spotlight tutorial for users updating to 1.3.4
    useEffect(() => {
        const tutorialKey = `orion_tutorial_shown_1_3_4`;
        const needsModernUI = settings.storeLayout === 'classic' && !settings.hasSeenModernUITutorial;
        
        // Wait until startup is ready, other dialogs, splash screens, notice, and modern UI tutorial are all dismissed
        if (!isStartupUiReady || isAnyModalOpen || showSplashPreview || showNotice || showModernUITutorial || needsModernUI) return;

        if (!localStorage.getItem(tutorialKey)) {
            const timer = setTimeout(() => {
                if (isMounted.current) {
                    const steps: TutorialStep[] = [
                        {
                            targetId: 'nav-tab-about',
                            title: 'Developer Menu',
                            description: "Click the Dev tab to see what's new under the hood.",
                            onTargetClick: () => { 
                                document.getElementById('nav-tab-about')?.click();
                                useTutorialStore.getState().nextStep();
                            }
                        },
                        {
                            targetId: 'btn-donate-coffee',
                            title: 'Support Orion',
                            description: "We've added new ways to donate. Check them out!",
                            onTargetClick: () => { 
                                document.getElementById('btn-donate-coffee')?.click();
                                useTutorialStore.getState().nextStep();
                            }
                        },
                        {
                            targetId: undefined, // no target -> centered mode
                            title: 'Thank You!',
                            description: "You can now support Orion via Ko-fi, UPI, or PayPal.",
                            buttonLabel: "Got it!",
                            onTargetClick: () => { 
                                localStorage.setItem(tutorialKey, '1');
                                useTutorialStore.getState().endTutorial();
                            }
                        }
                    ];
                    startTutorial(steps);
                }
            }, 750);
            return () => clearTimeout(timer);
        }
    }, [startTutorial, isAnyModalOpen, showSplashPreview, showNotice, showModernUITutorial]);

    // One-time Modern UI tutorial for classic users updating to 1.3.1
    // Waits for modals and splash to clear so it doesn't overlap notice/spotlight
    useEffect(() => {
        const tutorialKey = `orion_tutorial_shown_${CURRENT_STORE_VERSION}`;
        if (
            isStartupUiReady &&
            settings.storeLayout === 'classic' &&
            !settings.hasSeenModernUITutorial &&
            !localStorage.getItem(tutorialKey) &&
            !isAnyModalOpen &&
            !showSplashPreview
        ) {
            const timer = setTimeout(() => {
                if (isMounted.current) {
                    localStorage.setItem(tutorialKey, '1');
                    setShowModernUITutorial(true);
                }
            }, 2500);
            return () => clearTimeout(timer);
        }
    }, [isAnyModalOpen, showSplashPreview]); // eslint-disable-line react-hooks/exhaustive-deps

    const currentTabState = useDataStore(
        useCallback((state) => state.tabs[activeTab] || FALLBACK_TAB_STATE, [activeTab]),
        shallow
    );
    const effectiveSort = getDisplaySort(settings.storeLayout, currentTabState);
    const sortControlValue = settings.storeLayout === 'modern' && effectiveSort === SortOption.HOME
        ? SortOption.NEWEST
        : effectiveSort;
    const canResetCurrentBrowseState = useMemo(() => {
        if (expandedModernView) return true;
        if (settings.storeLayout === 'modern') {
            return effectiveSort !== SortOption.HOME;
        }

        return currentTabState.category !== 'All'
            || !!currentTabState.query
            || currentTabState.filterFavorites
            || currentTabState.sort !== SortOption.NEWEST;
    }, [currentTabState, effectiveSort, settings.storeLayout]);
    const restorePrimaryBrowseState = useCallback(() => {
        const root = document.getElementById('root');
        if (root) root.scrollTop = 0;

        setExpandedModernView(null);
        setExpandedModernApps([]);
        setViewAllApps(null);
        setShowAllSorted(false);

        startTransition(() => {
            data.setSearchQuery(activeTab, '');
            data.setSelectedCategory(activeTab, 'All');
            if (currentTabState.filterFavorites) {
                data.toggleFilterFavorites(activeTab);
            }
            data.setSelectedSort(activeTab, SortOption.NEWEST);
        });
    }, [activeTab, currentTabState.filterFavorites, data]);
    const [deferredQuery, setDeferredQuery] = useState(currentTabState.query);
    useEffect(() => {
        const handler = setTimeout(() => {
            setDeferredQuery(currentTabState.query);
        }, 150); // 150ms debounce prevents rapid typing from forcing heavy layout trashing
        return () => clearTimeout(handler);
    }, [currentTabState.query]);
    const allKnownApps = useMemo(() => [...data.apps, ...data.importedApps], [data.apps, data.importedApps]);
    const appsByPlatform = useMemo(() => {
        const grouped: Record<Platform, AppItem[]> = {
            [Platform.ANDROID]: [],
            [Platform.PC]: [],
            [Platform.TV]: []
        };
        allKnownApps.forEach((app) => {
            if (grouped[app.platform]) {
                grouped[app.platform].push(app);
            }
        });
        return grouped;
    }, [allKnownApps]);
    const appsByPackageName = useMemo(() => {
        const grouped = new Map<string, AppItem[]>();
        allKnownApps.forEach((app) => {
            const packageKey = normalizePackageName(app.packageName);
            if (!packageKey) return;
            const existing = grouped.get(packageKey) || [];
            existing.push(app);
            grouped.set(packageKey, existing);
        });
        return grouped;
    }, [allKnownApps]);
    const appLookup = useMemo(() => new Map(allKnownApps.map((app) => [app.id, app])), [allKnownApps]);
    const favoriteIdSet = useMemo(() => new Set(data.favorites), [data.favorites]);
    const activeDownloadCount = useMemo(() => Object.keys(data.activeDownloads).length, [data.activeDownloads]);
    const appCounts = useMemo(() => ({
        android: appsByPlatform[Platform.ANDROID].length,
        pc: appsByPlatform[Platform.PC].length,
        tv: appsByPlatform[Platform.TV].length
    }), [appsByPlatform]);

    useEffect(() => {
        const storeState = useDataStore.getState();
        const nextTabs = { ...storeState.tabs };
        let changed = false;

        (Object.keys(nextTabs) as Tab[]).forEach((tab) => {
            const current = nextTabs[tab];
            if (!current) return;

            if (current.sort === SortOption.HOME) {
                nextTabs[tab] = { ...current, sort: SortOption.NEWEST };
                changed = true;
            }
        });

        if (changed) {
            useDataStore.setState({ tabs: nextTabs });
        }
    }, []);

    const getPackageCandidates = useCallback((targetApp: AppItem, resolvedPackageName?: string) => {
        if (!targetApp.packageName) return [];
        const candidates = new Set<string>();
        if (resolvedPackageName) candidates.add(resolvedPackageName);
        candidates.add(targetApp.packageName);
        PACKAGE_SUFFIXES.forEach((suffix) => candidates.add(`${targetApp.packageName}${suffix}`));
        return Array.from(candidates);
    }, []);

    const getPackageInfoMap = useCallback(async (packageNames: string[]) => {
        const uniquePackages = Array.from(new Set(packageNames.filter(Boolean)));
        if (uniquePackages.length === 0) return {} as Record<string, AppInfoResult>;

        try {
            if (uniquePackages.length > 1) {
                const batchResult = await AppTracker.getMultipleAppInfo({ packageNames: uniquePackages });
                if (batchResult && typeof batchResult === 'object') {
                    return batchResult;
                }
            }
        } catch (error) {
            // Fall back to per-package checks when batch lookup is unavailable.
        }

        const entries = await Promise.all(uniquePackages.map(async (packageName) => {
            try {
                const info = await AppTracker.getAppInfo({ packageName });
                return [packageName, info] as const;
            } catch (error) {
                return [packageName, { installed: false, version: '' }] as const;
            }
        }));

        return Object.fromEntries(entries) as Record<string, AppInfoResult>;
    }, []);

    const resolveOwnedAppId = useCallback((groupApps: AppItem[], packageKey: string, installedVersion: string) => {
        if (groupApps.length === 0) return null;
        if (groupApps.length === 1) return groupApps[0]?.id || null;

        const currentSettings = useSettingsStore.getState();
        const installExpectation = pendingInstallExpectationsRef.current[packageKey];

        if (installExpectation && groupApps.some((app) => app.id === installExpectation.appId)) {
            const versionMatchesExpectation = !installExpectation.version
                || !isComparableVersion(installExpectation.version)
                || compareVersions(installedVersion, installExpectation.version) >= 0;

            if (versionMatchesExpectation) {
                return installExpectation.appId;
            }
        }

        const explicitOwner = currentSettings.packageOwners[packageKey];
        if (explicitOwner && groupApps.some((app) => app.id === explicitOwner)) {
            const explicitVersion = currentSettings.lastRemoteVersions[explicitOwner];
            if (isSameVersion(explicitVersion, installedVersion)) {
                return explicitOwner;
            }
        }

        const remoteVersionMatches = groupApps.filter((app) =>
            isSameVersion(currentSettings.lastRemoteVersions[app.id], installedVersion)
        );
        if (remoteVersionMatches.length === 1) {
            return remoteVersionMatches[0]!.id;
        }

        const preferredVersionMatches = groupApps.filter((app) =>
            isSameVersion(
                getPreferredVersion(app, currentSettings.appStreams[app.id] || 'Stable'),
                installedVersion
            )
        );
        if (preferredVersionMatches.length === 1) {
            return preferredVersionMatches[0]!.id;
        }

        if (explicitOwner && groupApps.some((app) => app.id === explicitOwner)) {
            const hasComparableSignals = groupApps.some((app) =>
                isComparableVersion(currentSettings.lastRemoteVersions[app.id])
                || isComparableVersion(getPreferredVersion(app, currentSettings.appStreams[app.id] || 'Stable'))
            );

            if (!hasComparableSignals) {
                return explicitOwner;
            }
        }

        return null;
    }, []);

    const syncInstalledStateForTargets = useCallback(async (targetApps: AppItem[]) => {
        const emptyResult = { installed: false, version: '' };
        const scanResults: Record<string, { installed: boolean; version: string; packageName?: string }> = {};

        if (!Capacitor.isNativePlatform()) {
            return scanResults;
        }

        const currentSettings = useSettingsStore.getState();
        const currentVersions = { ...currentSettings.installedVersions };
        const currentResolvedPackages = { ...currentSettings.resolvedPackageNames };
        const nextPackageOwners = { ...currentSettings.packageOwners };
        const staleRemoteVersionIds = new Set<string>();
        const packageGroups = new Map<string, AppItem[]>();

        targetApps.forEach((app) => {
            const packageKey = normalizePackageName(app.packageName);
            if (!packageKey || packageGroups.has(packageKey)) return;
            packageGroups.set(packageKey, appsByPackageName.get(packageKey) || [app]);
        });

        const packageCandidates = Array.from(packageGroups.values()).flatMap((groupApps) =>
            groupApps.flatMap((app) => getPackageCandidates(app, currentSettings.resolvedPackageNames[app.id]))
        );
        const packageInfoMap = await getPackageInfoMap(packageCandidates);

        packageGroups.forEach((groupApps, packageKey) => {
            const groupCandidates = Array.from(new Set(
                groupApps.flatMap((app) => getPackageCandidates(app, currentSettings.resolvedPackageNames[app.id]))
            ));

            let detectedResult: { installed: boolean; version: string; packageName?: string } = emptyResult;
            for (const candidate of groupCandidates) {
                const info = packageInfoMap[candidate];
                if (info?.installed) {
                    detectedResult = {
                        installed: true,
                        version: info.version,
                        packageName: candidate
                    };
                    break;
                }
            }

            const ownerAppId = detectedResult.installed
                ? resolveOwnedAppId(groupApps, packageKey, detectedResult.version)
                : null;

            if (!detectedResult.installed || !ownerAppId) {
                delete nextPackageOwners[packageKey];

                groupApps.forEach((app) => {
                    delete currentVersions[app.id];
                    delete currentResolvedPackages[app.id];
                    scanResults[app.id] = emptyResult;

                    if (!detectedResult.installed && currentSettings.lastRemoteVersions[app.id]) {
                        staleRemoteVersionIds.add(app.id);
                    }
                });

                return;
            }

            nextPackageOwners[packageKey] = ownerAppId;

            groupApps.forEach((app) => {
                if (app.id === ownerAppId) {
                    currentVersions[app.id] = detectedResult.version;
                    if (detectedResult.packageName) {
                        currentResolvedPackages[app.id] = detectedResult.packageName;
                    }
                    scanResults[app.id] = detectedResult;
                    return;
                }

                delete currentVersions[app.id];
                delete currentResolvedPackages[app.id];
                scanResults[app.id] = emptyResult;
            });
        });

        settings.setInstalledVersions(currentVersions);
        settings.setAllResolvedPackageNames(currentResolvedPackages);
        settings.setPackageOwners(nextPackageOwners);
        staleRemoteVersionIds.forEach((appId) => settings.removeLastRemoteVersion(appId));

        return scanResults;
    }, [appsByPackageName, getPackageCandidates, getPackageInfoMap, resolveOwnedAppId, settings]);

    const performDeepScan = useCallback(async (
        targetApp: AppItem,
        expectedVersion?: string
    ): Promise<{ installed: boolean; version: string; packageName?: string; matchesExpected: boolean }> => {
        const scanResults = await syncInstalledStateForTargets([targetApp]);
        const result = scanResults[targetApp.id] || { installed: false, version: '' };
        const matchesExpected = result.installed && (
            !expectedVersion
            || !isComparableVersion(expectedVersion)
            || compareVersions(result.version, expectedVersion) >= 0
        );

        return {
            ...result,
            matchesExpected
        };
    }, [syncInstalledStateForTargets]);

    const startVerificationLoop = useCallback((app: AppItem) => {
        const packageKey = normalizePackageName(app.packageName);
        const expectedVersion = packageKey ? pendingInstallExpectationsRef.current[packageKey]?.version : undefined;
        const maxAttempts = expectedVersion && isComparableVersion(expectedVersion) ? 12 : 8;
        let attempts = 0;
        const startTime = Date.now();

        const verifyInterval = setInterval(async () => {
            attempts++;
            const result = await performDeepScan(app, expectedVersion);

            if (result.matchesExpected) {
                clearInterval(verifyInterval);
                const elapsed = Date.now() - startTime;
                const remaining = Math.max(0, 2500 - elapsed);

                window.setTimeout(() => {
                    const currentData = useDataStore.getState();
                    const file = currentData.readyToInstall[app.id];

                    if (result.version) {
                        settings.setLastRemoteVersion(app.id, result.version);
                    }
                    if (packageKey) {
                        settings.setPackageOwner(packageKey, app.id);
                        delete pendingInstallExpectationsRef.current[packageKey];
                    }

                    if (file || currentData.readyToInstall[app.id]) {
                        const newReady = { ...currentData.readyToInstall };
                        const targetFile = file || newReady[app.id];
                        delete newReady[app.id];

                        const newCleanup = { ...currentData.pendingCleanup };
                        if (!newCleanup[app.id] && targetFile) {
                            newCleanup[app.id] = { fileName: targetFile, timestamp: Date.now() };
                        }

                        useDataStore.setState({
                            readyToInstall: newReady,
                            pendingCleanup: newCleanup
                        });
                    }
                    setScanningId(null);
                }, remaining);
            } else if (attempts >= maxAttempts) {
                clearInterval(verifyInterval);
                if (packageKey) {
                    delete pendingInstallExpectationsRef.current[packageKey];
                }
                window.setTimeout(() => {
                    void syncInstalledAppsRef.current();
                }, 2200);
                setScanningId(null);
            }
        }, 1000);
    }, [performDeepScan, settings]);

    const beginPostInstallVerification = useCallback((app: AppItem) => {
        waitingForResumeId.current = null;
        setInstallingId(null);
        installingIdRef.current = null;
        setScanningId(app.id);
        startVerificationLoop(app);
    }, [startVerificationLoop]);

    const syncInstalledApps = useCallback(async () => {
        if (!Capacitor.isNativePlatform()) return;
        const allApps = [...data.apps, ...data.importedApps].filter((app) => !!app.packageName);
        if (allApps.length === 0) return;

        const currentSettings = useSettingsStore.getState();
        const newReadyToInstall = { ...data.readyToInstall };
        let readyToInstallChanged = false;
        let pendingCleanupChanged = false;
        const updatesForPendingCleanup: Record<string, CleanupEntry> = {};
        const scanResults = await syncInstalledStateForTargets(allApps);

        allApps.forEach((app) => {
            const result = scanResults[app.id];
            const readyFile = newReadyToInstall[app.id];

            if (!result?.installed || !readyFile || installingIdRef.current === app.id) {
                return;
            }

            const expectedMetadata = getVersionMetadataFromFileName(
                app,
                readyFile,
                currentSettings.appStreams[app.id] || 'Stable'
            );
            const expectedVersion = expectedMetadata.version;
            const matchesExpected = !expectedVersion
                || !isComparableVersion(expectedVersion)
                || compareVersions(result.version, expectedVersion) >= 0;

            if (!matchesExpected) {
                return;
            }

            delete newReadyToInstall[app.id];
            readyToInstallChanged = true;

            if (result.version) {
                settings.setLastRemoteVersion(app.id, result.version);
            }
            const packageKey = normalizePackageName(app.packageName);
            if (packageKey) {
                settings.setPackageOwner(packageKey, app.id);
                delete pendingInstallExpectationsRef.current[packageKey];
            }

            if (!data.pendingCleanup[app.id]) {
                updatesForPendingCleanup[app.id] = { fileName: readyFile, timestamp: Date.now() };
                pendingCleanupChanged = true;
            }
        });

        if (readyToInstallChanged) data.setReadyToInstall(newReadyToInstall);
        if (pendingCleanupChanged) data.setPendingCleanup({ ...data.pendingCleanup, ...updatesForPendingCleanup });

    }, [data.apps, data.importedApps, data.pendingCleanup, data.readyToInstall, data.setPendingCleanup, data.setReadyToInstall, settings, syncInstalledStateForTargets]);

    useEffect(() => { syncInstalledAppsRef.current = syncInstalledApps; }, [syncInstalledApps]);
    const scheduleInstalledSync = useCallback(() => {
        if (installedSyncTimerRef.current !== null) return;
        installedSyncTimerRef.current = window.setTimeout(() => {
            installedSyncTimerRef.current = null;
            backgroundTaskCleanupRef.current?.();
            backgroundTaskCleanupRef.current = scheduleBackgroundTask(() => {
                void syncInstalledAppsRef.current();
                backgroundTaskCleanupRef.current = null;
            }, 3200);
        }, 900);
    }, []);

    useEffect(() => {
        if (typeof document === 'undefined') return;

        const origins = ['https://wsrv.nl', 'https://raw.githubusercontent.com', 'https://github.com'];
        const createdLinks: HTMLLinkElement[] = [];

        origins.forEach((origin) => {
            if (document.head.querySelector(`link[data-orion-preconnect="${origin}"]`)) return;
            const link = document.createElement('link');
            link.rel = 'preconnect';
            link.href = origin;
            link.crossOrigin = 'anonymous';
            link.dataset.orionPreconnect = origin;
            document.head.appendChild(link);
            createdLinks.push(link);
        });

        return () => {
            createdLinks.forEach((link) => link.remove());
        };
    }, []);

    useEffect(() => {
        const worker = new CoreWorker();
        workerRef.current = worker;
        worker.onmessage = (e) => {
            const { type, payload } = e.data;
            if (type === 'DATA_PROCESSED') {
                data.setApps(payload.apps);
                data.setImportedApps(payload.imported);
                const tabData = useDataStore.getState().tabs[activeTab] || FALLBACK_TAB_STATE;
                const platformTarget = activeTab === 'pc' ? Platform.PC : activeTab === 'tv' ? Platform.TV : Platform.ANDROID;
                worker?.postMessage({
                    type: 'FILTER',
                    payload: {
                        query: tabData.query,
                        category: tabData.category,
                        sort: getDisplaySort(useSettingsStore.getState().storeLayout, tabData),
                        platform: platformTarget
                    }
                });
                setIsLoading(false);
                setIsRefreshing(false);
                scheduleInstalledSync();
            }
            else if (type === 'FILTER_RESULTS') {
                startTransition(() => {
                    setVisibleApps(payload.results || []);
                    setStoreCollections(payload.collections || []);
                });
                if (sortTransitionTimerRef.current) {
                    window.clearTimeout(sortTransitionTimerRef.current);
                    sortTransitionTimerRef.current = null;
                }
                if (pendingSortTransitionRef.current.active) {
                    const elapsed = performance.now() - pendingSortTransitionRef.current.startedAt;
                    const remaining = Math.max(0, 140 - elapsed);
                    sortTransitionTimerRef.current = window.setTimeout(() => {
                        pendingSortTransitionRef.current.active = false;
                        if (isMounted.current) setIsSortTransitioning(false);
                    }, remaining);
                } else if (isMounted.current) {
                    setIsSortTransitioning(false);
                }
            }
        };
        return () => {
            if (sortTransitionTimerRef.current) {
                window.clearTimeout(sortTransitionTimerRef.current);
                sortTransitionTimerRef.current = null;
            }
            if (installedSyncTimerRef.current !== null) {
                window.clearTimeout(installedSyncTimerRef.current);
                installedSyncTimerRef.current = null;
            }
            worker.terminate();
        };
    }, [scheduleInstalledSync]);

    useEffect(() => {
        if (workerRef.current && (data.apps.length > 0 || data.importedApps.length > 0)) {
            const sortChanged = lastRequestedSortRef.current !== effectiveSort;
            const categoryChanged = lastRequestedCategoryRef.current !== currentTabState.category;
            const tabChanged = lastRequestedTabRef.current !== activeTab;
            if ((sortChanged || categoryChanged || tabChanged) && !isLoading) {
                pendingSortTransitionRef.current = { active: true, startedAt: performance.now() };
                setIsSortTransitioning(true);
            }
            lastRequestedSortRef.current = effectiveSort;
            lastRequestedCategoryRef.current = currentTabState.category;
            lastRequestedTabRef.current = activeTab;
            const platformTarget = activeTab === 'pc' ? Platform.PC : activeTab === 'tv' ? Platform.TV : Platform.ANDROID;
            workerRef.current.postMessage({
                type: 'FILTER',
                payload: {
                    query: deferredQuery,
                    category: currentTabState.category,
                    sort: effectiveSort,
                    platform: platformTarget,
                    storefrontModules: effectiveRemoteConfig?.storefrontModules || []
                }
            });
        }
    }, [deferredQuery, currentTabState.category, effectiveSort, data.apps, data.importedApps, activeTab, remoteConfig, isLoading]);

    useEffect(() => {
        if (showInstallToast) {
            const timer = setTimeout(() => setShowInstallToast(null), 6000);
            return () => clearTimeout(timer);
        }
    }, [showInstallToast]);

    useEffect(() => {
        if (showErrorToast) {
            const timer = setTimeout(() => setShowErrorToast(false), 6000);
            return () => clearTimeout(timer);
        }
    }, [showErrorToast]);

    useEffect(() => {
        if (devToast) {
            if (devToastTimer.current) clearTimeout(devToastTimer.current);
            devToastTimer.current = setTimeout(() => setDevToast(null), 3000);
        }
    }, [devToast]);

    useEffect(() => {
        if (pendingCleanupCount > 0) {
            setShowCleanupPill(true);
            if (settings.deleteApk) {
                const timer = setTimeout(() => {
                    setShowCleanupPill(false);
                }, 10000);
                return () => clearTimeout(timer);
            }
        } else {
            setShowCleanupPill(false);
        }
    }, [pendingCleanupCount, settings.deleteApk]);

    const triggerHaptic = useCallback((type: 'impact' | 'notification' | 'selection' = 'impact', style?: ImpactStyle, notifType?: NotificationType) => {
        if (!settings.hapticEnabled) return;
        if (type === 'impact') Haptics.impact({ style: style || ImpactStyle.Light });
        if (type === 'notification') Haptics.notification({ type: notifType || NotificationType.Success });
        if (type === 'selection') Haptics.selection();
    }, [settings.hapticEnabled]);

    const applyThemeChange = useCallback((nextTheme: Theme) => {
        const updateTheme = () => settings.setTheme(nextTheme);
        if (typeof document === 'undefined' || settings.disableAnimations) {
            updateTheme();
            return;
        }

        const transitionDocument = document as ViewTransitionCapableDocument;
        if (!transitionDocument.startViewTransition) {
            updateTheme();
            return;
        }

        document.body.classList.add('theme-transitioning');
        try {
            const transition = transitionDocument.startViewTransition(() => {
                flushSync(updateTheme);
            });
            transition.finished.finally(() => {
                document.body.classList.remove('theme-transitioning');
            });
        } catch (error) {
            document.body.classList.remove('theme-transitioning');
            updateTheme();
        }
    }, [settings.disableAnimations, settings.setTheme]);

    useEffect(() => {
        isMounted.current = true;
        if (Capacitor.isNativePlatform()) {
            const listenerPromise = LocalNotifications.addListener('localNotificationActionPerformed', async (action: ActionPerformed) => {
                const { notification } = action;
                if (notification.extra && notification.extra.appId) {
                    const targetAppId = notification.extra.appId;
                    const targetFileName = notification.extra.fileName;
                    // Read fresh state to avoid stale closure over empty initial data.apps
                    const currentData = useDataStore.getState();
                    const app = [...currentData.apps, ...currentData.importedApps].find((a: AppItem) => a.id === targetAppId);
                    if (app) {
                        if (targetFileName && !currentData.pendingCleanup[targetAppId]) {
                            currentData.setReadyToInstall({ ...currentData.readyToInstall, [targetAppId]: targetFileName });
                        }
                        triggerHaptic('impact', ImpactStyle.Heavy);
                        setSelectedApp(app);
                    }
                }
            });

            const performStartupCleanup = async () => {
                const state = useSettingsStore.getState();
                const dataState = useDataStore.getState();
                const pendingFiles = dataState.pendingCleanup;
                const ids = Object.keys(pendingFiles);
                if (ids.length === 0) return;
                const now = Date.now();
                const toDelete: Record<string, string> = {};
                for (const appId of ids) {
                    const entry = pendingFiles[appId];
                    if (!entry) continue;
                    let fileName = '';
                    let shouldDelete = false;
                    if (typeof entry === 'string') {
                        fileName = entry;
                        if (state.deleteApk) shouldDelete = true;
                    } else {
                        fileName = entry.fileName;
                        const age = now - entry.timestamp;
                        if (state.deleteApk) {
                            shouldDelete = true;
                        } else if (age > ONE_WEEK_MS) {
                            shouldDelete = true;
                        }
                    }
                    if (shouldDelete && fileName) {
                        toDelete[appId] = fileName;
                    }
                }
                if (Object.keys(toDelete).length === 0) return;
                let cleanedCount = 0;
                for (const [appId, fileName] of Object.entries(toDelete)) {
                    try {
                        await AppTracker.deleteFile({ fileName });
                        cleanedCount++;
                    } catch (e) { }
                }
                if (cleanedCount > 0) {
                    const newCleanupState = { ...dataState.pendingCleanup };
                    for (const appId of Object.keys(toDelete)) {
                        delete newCleanupState[appId];
                    }
                    dataState.setPendingCleanup(newCleanupState);
                    setDevToast(`Janitor cleaned ${cleanedCount} files`);
                }
            };

            const cancelStartupWork = scheduleBackgroundTask(() => {
                void requestPermissions();
                try {
                    UnityAds.initialize({
                        gameId: UNITY_GAME_ID,
                        testMode: ADS_TEST_MODE,
                    }).catch(e => console.error("UnityAds Init Error:", e));
                } catch (e) { }
                window.setTimeout(() => { void performStartupCleanup(); }, 1400);
            }, 1800);

            return () => {
                isMounted.current = false;
                cancelStartupWork();
                backgroundTaskCleanupRef.current?.();
                listenerPromise.then(handler => handler.remove());
            };
        }
        const cancelWebStartupWork = scheduleBackgroundTask(() => {
            void requestPermissions();
        }, 1200);
        return () => {
            isMounted.current = false;
            cancelWebStartupWork();
            backgroundTaskCleanupRef.current?.();
        };
    }, []);

    useEffect(() => {
        const checkCooldown = () => {
            if (!settings.lastSubmissionTime) {
                setSubmissionCooldown(null);
                return;
            }
            const baseCooldownMinutes = 180;
            const reductionPerLevel = 15;
            const maxReduction = 150;
            const reduction = Math.min(settings.submissionCount * reductionPerLevel, maxReduction);
            const totalCooldownMinutes = Math.max(baseCooldownMinutes - reduction, 30);
            const cooldownMs = totalCooldownMinutes * 60 * 1000;
            const elapsed = Date.now() - settings.lastSubmissionTime;
            const remaining = cooldownMs - elapsed;

            if (remaining > 0) {
                const h = Math.floor(remaining / (1000 * 60 * 60));
                const m = Math.floor((remaining % (1000 * 60 * 60)) / (1000 * 60));
                setSubmissionCooldown(`${h}h ${m}m`);
            } else {
                setSubmissionCooldown(null);
            }
        };
        checkCooldown();
        const interval = setInterval(checkCooldown, 60000);
        return () => clearInterval(interval);
    }, [settings.submissionCount, settings.lastSubmissionTime]);

    useEffect(() => {
        if (!Capacitor.isNativePlatform()) return;
        const downloadKeys = Object.values(data.activeDownloads);
        if (downloadKeys.length === 0) return;
        const activeDownloadCount = downloadKeys.length;

        const poll = async () => {
            if (document.visibilityState === 'hidden') return;
            const snapshot = useDataStore.getState();
            const dlKeys = Object.keys(snapshot.activeDownloads);
            for (const appId of dlKeys) {
                const rawVal = snapshot.activeDownloads[appId];
                if (!rawVal) continue;
                const [dlId, _] = rawVal.split('|');
                if (!dlId) continue;

                try {
                    const res = await AppTracker.getDownloadProgress({ downloadId: dlId });
                    const prevProg = snapshot.downloadProgress[appId] || 0;
                    const diff = Math.abs(res.progress - prevProg);

                    if (diff >= 1 || res.progress === 100 || res.status !== snapshot.downloadStatus[appId]) {
                        data.updateDownloadState(appId, res.progress, res.status);
                    }

                    if (res.status === 'SUCCESSFUL') {
                        handleDownloadComplete(appId, true);
                    } else if (res.status === 'FAILED') {
                        if (res.progress > 90) {
                            const retry = await AppTracker.getDownloadProgress({ downloadId: dlId });
                            if (retry.status === 'SUCCESSFUL' || retry.progress === 100) {
                                handleDownloadComplete(appId, true);
                                continue;
                            }
                        }
                        handleDownloadComplete(appId, false);
                        setErrorMsg("Download Failed - Network Error");
                        setShowErrorToast(true);
                    }
                } catch (e) { }
            }
        };
        poll();
        const pollIntervalMs = activeDownloadCount > 2 ? 3000 : 2200;
        const interval = setInterval(poll, pollIntervalMs);

        const handleVisibilityChange = () => {
            if (document.visibilityState === 'visible') {
                poll();
            }
        };
        document.addEventListener('visibilitychange', handleVisibilityChange);

        return () => {
            clearInterval(interval);
            document.removeEventListener('visibilitychange', handleVisibilityChange);
        };
    }, [data.activeDownloads]);

    useEffect(() => {
        if (!Capacitor.isNativePlatform()) return;
        const syncDownloads = async () => {
            const activeKeys = Object.keys(data.activeDownloads);
            if (activeKeys.length === 0) return;
            try {
                const result = await AppTracker.checkActiveDownloads();
                for (const appId of activeKeys) {
                    const rawVal = data.activeDownloads[appId];
                    if (!rawVal) continue;
                    const [_, fileName] = rawVal.split('|');
                    if (!fileName) {
                        data.cancelDownload(appId);
                        continue;
                    }
                    if (!result[fileName]) {
                        try {
                            const check = await AppTracker.getDownloadProgress({ downloadId: fileName });
                            if (check.status === 'SUCCESSFUL' || check.progress === 100) {
                                handleDownloadComplete(appId, true);
                                continue;
                            }
                        } catch (e) { }
                        data.cancelDownload(appId);
                    }
                }
            } catch (e) { }
        };

        let pendingRetryTimer: number | null = null;
        const resumeListener = CapacitorApp.addListener('resume', () => {
            syncDownloads();
            if (waitingForResumeId.current) {
                const appId = waitingForResumeId.current;
                const app = appLookup.get(appId);
                if (app) {
                    // Begin verification FIRST — this sets scanningId before
                    // syncInstalledApps can race ahead and show "Open".
                    beginPostInstallVerification(app);
                } else {
                    // App not found in lookup, fall back to a full sync
                    waitingForResumeId.current = null;
                    syncInstalledAppsRef.current();
                }
            } else {
                syncInstalledAppsRef.current();
            }
            if (pendingInstallRetry) {
                // Capture by value & clear immediately to prevent double-install
                const retryTarget = pendingInstallRetry;
                setPendingInstallRetry(null);
                if (pendingRetryTimer !== null) window.clearTimeout(pendingRetryTimer);
                pendingRetryTimer = window.setTimeout(() => {
                    if (isMounted.current) {
                        handleInstallFile(retryTarget.app, retryTarget.file);
                    }
                    pendingRetryTimer = null;
                }, 500);
            }
        });
        syncDownloads();
        return () => {
            if (pendingRetryTimer !== null) window.clearTimeout(pendingRetryTimer);
            resumeListener.then(h => h.remove());
        };
    }, [appLookup, beginPostInstallVerification, data.activeDownloads, pendingInstallRetry]);

    useEffect(() => {
        const root = document.getElementById('root');
        if (!root) return;
        let rafId = 0;
        let isBoosted = false;
        const handleScroll = () => {
            // Only toggle classList when needed (avoid touching DOM on every scroll event)
            if (!isBoosted) {
                document.body.classList.add('scroll-boost');
                isBoosted = true;
            }
            if (scrollBoostTimerRef.current) {
                window.clearTimeout(scrollBoostTimerRef.current);
            }
            scrollBoostTimerRef.current = window.setTimeout(() => {
                document.body.classList.remove('scroll-boost');
                isBoosted = false;
                scrollBoostTimerRef.current = null;
            }, 160);
            if (rafId) return;
            rafId = requestAnimationFrame(() => {
                setShowScrollTop(root.scrollTop > 300);
                rafId = 0;
            });
        };
        root.addEventListener('scroll', handleScroll, { passive: true });
        return () => {
            root.removeEventListener('scroll', handleScroll);
            if (rafId) cancelAnimationFrame(rafId);
            if (scrollBoostTimerRef.current) {
                window.clearTimeout(scrollBoostTimerRef.current);
                scrollBoostTimerRef.current = null;
            }
            document.body.classList.remove('scroll-boost');
        };
    }, []);

    const scrollToTop = () => {
        const root = document.getElementById('root');
        if (root) {
            root.scrollTop = 0;
            triggerHaptic('selection');
        }
    };

    useEffect(() => {
        const viewKey = [
            activeTab,
            currentTabState.category,
            currentTabState.query,
            effectiveSort,
            currentTabState.filterFavorites
        ].join('::');

        if (lastViewStateKey.current === null) {
            lastViewStateKey.current = viewKey;
            return;
        }

        if (lastViewStateKey.current !== viewKey) {
            const root = document.getElementById('root');
            if (root) {
                requestAnimationFrame(() => {
                    root.scrollTop = 0;
                });
            }
        }

        lastViewStateKey.current = viewKey;
    }, [activeTab, currentTabState.category, currentTabState.query, effectiveSort, currentTabState.filterFavorites]);

    useEffect(() => {
        const root = document.documentElement;
        root.classList.remove('light', 'dusk', 'dark', 'oled');
        if (settings.theme === 'light') root.classList.add('light');
        else if (settings.theme === 'dusk') root.classList.add('dusk');
        else if (settings.theme === 'dark') {
            if (settings.isOled) root.classList.add('oled', 'dark');
            else root.classList.add('dark');
        } else root.classList.add(settings.theme);
    }, [settings.theme, settings.isOled]);

    useEffect(() => {
        document.documentElement.style.setProperty('--app-font-family', getAppFontDefinition(settings.appFont).family);
        const timer = window.setTimeout(() => ensureAppFontStylesheet(settings.appFont), 0);
        return () => window.clearTimeout(timer);
    }, [settings.appFont]);

    useEffect(() => {
        if (Capacitor.isNativePlatform()) {
            document.body.classList.add('native-ui');
            return () => document.body.classList.remove('native-ui');
        }
    }, []);

    useEffect(() => {
        const root = document.documentElement;
        root.dataset.orionActiveTab = activeTab;
        root.dataset.orionRefreshEligible = String(isAppDataTab && !isAnyModalOpen && !isRefreshing);
        root.dataset.orionRefreshCharacter = settings.pullToRefreshCharacter;

        return () => {
            delete root.dataset.orionActiveTab;
            delete root.dataset.orionRefreshEligible;
            delete root.dataset.orionRefreshCharacter;
        };
    }, [activeTab, isAppDataTab, isAnyModalOpen, isRefreshing, settings.pullToRefreshCharacter]);

    useEffect(() => {
        const nav = navigator as Navigator & { deviceMemory?: number };
        const isLowMemoryDevice = Capacitor.isNativePlatform() && typeof nav.deviceMemory === 'number' && nav.deviceMemory <= 4;
        if (isLowMemoryDevice) {
            document.body.classList.add('low-memory-device');
        }
        return () => document.body.classList.remove('low-memory-device');
    }, []);

    useEffect(() => {
        if (!settings.glassEffect) document.body.classList.add('no-glass');
        else document.body.classList.remove('no-glass');
    }, [settings.glassEffect]);

    useEffect(() => {
        if (settings.highRefreshRate) document.body.classList.add('perf-mode');
        else document.body.classList.remove('perf-mode');
        if (Capacitor.isNativePlatform()) AppTracker.setHighRefreshRate({ enable: settings.highRefreshRate }).catch(() => { });
    }, [settings.highRefreshRate]);

    useEffect(() => {
        if (settings.disableAnimations) document.body.classList.add('no-anim');
        else document.body.classList.remove('no-anim');
        if (settings.compactMode) document.body.classList.add('compact-mode');
        else document.body.classList.remove('compact-mode');

        if (settings.storeLayout === 'modern') document.body.classList.add('modern-layout-active');
        else document.body.classList.remove('modern-layout-active');
    }, [settings.disableAnimations, settings.compactMode, settings.storeLayout]);

    const requestPermissions = async () => {
        if (Capacitor.isNativePlatform()) {
            try {
                await AppTracker.requestPermissions();
                await LocalNotifications.createChannel({ id: 'orion_updates', name: 'Orion Updates', importance: 3 });
                await LocalNotifications.createChannel({ id: 'orion_cleanup', name: 'Cleanup', importance: 4 });
                await LocalNotifications.requestPermissions();
            } catch (e) { }
        }
    };

    const getStringHash = (str: string): number => {
        let hash = 0;
        for (let i = 0; i < str.length; i++) {
            const char = str.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash;
        }
        return Math.abs(hash);
    };

    useEffect(() => {
        if (isStartupUiReady) return;
        if (showSplashPreview || isLoading || visibleApps.length === 0) return;

        const cancelReady = scheduleBackgroundTask(() => {
            if (isMounted.current) setIsStartupUiReady(true);
        }, 1800);

        return () => cancelReady();
    }, [isLoading, isStartupUiReady, showSplashPreview, visibleApps.length]);

    useEffect(() => {
        if (effectiveRemoteConfig?.announcement) {
            const hash = getStringHash(effectiveRemoteConfig.announcement);
            const dismissedHash = localStorage.getItem('dismissed_announcement_hash');
            setIsAnnouncementDismissed(dismissedHash === String(hash));
        }
    }, [effectiveRemoteConfig]);

    useEffect(() => {
        if (!isStartupUiReady) return;
        if (effectiveRemoteConfig?.notice?.show) {
            const dismissedId = localStorage.getItem('dismissed_notice_id');
            if (dismissedId !== effectiveRemoteConfig.notice.id) {
                setShowNotice(true);
            }
        }
    }, [isStartupUiReady, effectiveRemoteConfig]);

    const handleDismissNotice = () => {
        setShowNotice(false);
        if (effectiveRemoteConfig?.notice?.id) {
            localStorage.setItem('dismissed_notice_id', effectiveRemoteConfig.notice.id);
        }
        // Show Modern UI tutorial after delay if user hasn't seen it and is on classic layout
        if (!settings.hasSeenModernUITutorial && settings.storeLayout === 'classic') {
            setTimeout(() => {
                if (isMounted.current) setShowModernUITutorial(true);
            }, 3000);
        }
    };

    // Also trigger tutorial on first load if no notice/spotlight is showing and user is on classic
    useEffect(() => {
        if (!settings.hasSeenModernUITutorial && settings.storeLayout === 'classic' && !showNotice && !isLoading && visibleApps.length > 0 && !useTutorialStore.getState().isActive) {
            const timer = setTimeout(() => {
                if (isMounted.current) setShowModernUITutorial(true);
            }, 3000);
            return () => clearTimeout(timer);
        }
    }, [settings.hasSeenModernUITutorial, settings.storeLayout, showNotice, isLoading, visibleApps.length, isTutorialActive]);

    useEffect(() => {
        if (!Capacitor.isNativePlatform()) return;
        const handleBack = async () => {
            if (shouldBlockWithForcedStoreUpdate) {
                CapacitorApp.exitApp();
                return;
            }
            if (document.body.classList.contains('lightbox-open')) {
                window.dispatchEvent(new Event('orion-close-lightbox'));
                return;
            }
            if (vtScanTarget) { setVtScanTarget(null); return; }
            if (selectedApp) { setSelectedApp(null); return; }
            if (expandedModernView) {
                setExpandedModernView(null);
                setExpandedModernApps([]);
                setReturnToExpandedView(null);
                return;
            }
            if (showAllSorted) {
                setShowAllSorted(false);
                setViewAllApps(null);
                if (returnToExpandedView === 'new_updated') {
                    const targetPlat = activeTab === 'android' ? Platform.ANDROID : activeTab === 'pc' ? Platform.PC : Platform.TV;
                    const { apps, importedApps } = useDataStore.getState();
                    const appsToShow = [...apps, ...importedApps].filter(a => a.platform === targetPlat).reverse().slice(0, 18);
                    setExpandedModernView('new_updated');
                    setExpandedModernApps(appsToShow);
                    setReturnToExpandedView(null);
                }
                return;
            }
            if (selectedBundle) setSelectedBundle(null);
            else if (showProfileStats) { setShowProfileStats(false); setProfileBadgeSelectionIndex(null); }
            else if (showCustomBundleModal) setShowCustomBundleModal(false);
            else if (showSettingsModal) setShowSettingsModal(false);
            else if (showReleaseNotes) setShowReleaseNotes(false);
            else if (showFAQ) setShowFAQ(false);
            else if (showSubmissionModal) setShowSubmissionModal(false);
            else if (showAdDonation) setShowAdDonation(false);

            else if (isTestingForcedUpdate) setIsTestingForcedUpdate(false);
            else if (showModernUITutorial) setShowModernUITutorial(false);
            else if (showNotice) handleDismissNotice();
            else if (canResetCurrentBrowseState) restorePrimaryBrowseState();
            else if (activeTab !== 'android') setActiveTab('android');
            else CapacitorApp.exitApp();
        };
        const backListener = CapacitorApp.addListener('backButton', handleBack);
        return () => { backListener.then(h => h.remove()); };
    }, [shouldBlockWithForcedStoreUpdate, selectedApp, selectedBundle, showSettingsModal, showFAQ, showSubmissionModal, showAdDonation, activeTab, showNotice, showReleaseNotes, canResetCurrentBrowseState, restorePrimaryBrowseState, showAllSorted, expandedModernView, vtScanTarget, showProfileStats, showCustomBundleModal, showModernUITutorial, isTestingForcedUpdate, returnToExpandedView]);

    const handleDownloadStart = useCallback((appId: string, downloadId: string, fileName: string) => {
        data.startDownload(appId, downloadId, fileName);
        triggerHaptic('impact', ImpactStyle.Medium);
    }, [data, triggerHaptic]);

    const handleCancelDownloadById = useCallback(async (appId: string, compositeId: string) => {
        const [dlId] = compositeId.split('|');
        triggerHaptic('impact', ImpactStyle.Medium);
        try {
            if (dlId) await AppTracker.cancelDownload({ downloadId: dlId });
        } catch (e) { } finally {
            data.cancelDownload(appId);
        }
    }, [data, triggerHaptic]);

    const handleCancelDownload = useCallback((app: AppItem, compositeId: string) => {
        return handleCancelDownloadById(app.id, compositeId);
    }, [handleCancelDownloadById]);

    const handleDeleteReadyFile = useCallback(async (app: AppItem, fileName: string) => {
        try {
            await AppTracker.deleteFile({ fileName });
            const packageKey = normalizePackageName(app.packageName);
            if (packageKey) {
                delete pendingInstallExpectationsRef.current[packageKey];
            }
            const newReady = { ...data.readyToInstall };
            delete newReady[app.id];
            data.setReadyToInstall(newReady);
            settings.removeLastRemoteVersion(app.id);
            triggerHaptic('notification', undefined, NotificationType.Success);
        } catch (e) { }
    }, [data, triggerHaptic, settings.removeLastRemoteVersion]);

    const handleInstallFile = async (app: AppItem, fileName: string) => {
        if (!Capacitor.isNativePlatform()) return;
        const packageKey = normalizePackageName(app.packageName);
        const expectedMetadata = getVersionMetadataFromFileName(
            app,
            fileName,
            useSettingsStore.getState().appStreams[app.id] || 'Stable'
        );

        try {
            const permission = await AppTracker.canRequestPackageInstalls();
            if (!permission.value) {
                setErrorMsg('Please allow permission and return here');
                setShowErrorToast(true);
                setPendingInstallRetry({ app, file: fileName });
                await AppTracker.openInstallPermissionSettings();
                return;
            }
        } catch (e) {
            setErrorMsg('Permission check failed. Cannot install.');
            setShowErrorToast(true);
            return;
        }
        try {
            triggerHaptic('impact', ImpactStyle.Heavy);
            setInstallingId(app.id);
            installingIdRef.current = app.id;

            if (packageKey) {
                pendingInstallExpectationsRef.current[packageKey] = {
                    appId: app.id,
                    version: expectedMetadata.version
                };
            }

            if (expectedMetadata.stream) {
                settings.setAppStream(app.id, expectedMetadata.stream);
            }
            if (settings.useShizuku) {
                await AppTracker.installPackageShizuku({ fileName });
                setShowInstallToast(null);
                setDevToast("Installed via Shizuku");
                beginPostInstallVerification(app);
            } else {
                waitingForResumeId.current = app.id;
                await AppTracker.installPackage({
                    fileName,
                    installerPreference: settings.installerPreference,
                    installerPackage: settings.installerPreference === 'package' ? settings.installerPackage : undefined
                });
                setShowInstallToast(null);
                if (waitingForResumeId.current === app.id) {
                    beginPostInstallVerification(app);
                }
            }
        } catch (e: any) {
            const msg = e?.message || JSON.stringify(e);
            const shouldVerifyAmbiguousInstallerResult =
                !settings.useShizuku &&
                waitingForResumeId.current === app.id &&
                (
                    msg.includes('INSTALL_CANCELED') ||
                    msg.includes('INSTALL_FAILED') ||
                    msg.includes('Activity')
                ) &&
                !msg.includes('INSTALL_FAILED_UPDATE_INCOMPATIBLE') &&
                !msg.includes('INSTALL_PARSE_FAILED');

            if (shouldVerifyAmbiguousInstallerResult) {
                setShowInstallToast(null);
                beginPostInstallVerification(app);
                return;
            }

            setInstallingId(null);
            installingIdRef.current = null;
            setScanningId(null);
            waitingForResumeId.current = null;
            if (packageKey) {
                delete pendingInstallExpectationsRef.current[packageKey];
            }
            if (msg.includes("CORRUPT") || msg.includes("PARSE_ERROR")) {
                setErrorMsg('File corrupted. Deleting...');
                setShowErrorToast(true);
                handleDeleteReadyFile(app, fileName);
            } else if (
                msg.includes('package appears to be invalid')
                || msg.includes('INSTALL_FAILED_UPDATE_INCOMPATIBLE')
                || msg.includes('INSTALL_PARSE_FAILED')
            ) {
                setErrorMsg('Update failed: installed app is not compatible with this package. Reinstall may be required.');
                setShowErrorToast(true);
            } else if (msg.includes("Shizuku")) {
                setErrorMsg("Shizuku Install Failed: " + msg);
                setShowErrorToast(true);
            } else if (!msg.includes('Activity')) {
                setErrorMsg('Installation failed.');
                setShowErrorToast(true);
            }
        }
    };

    const handleBatchInstall = useCallback(async () => {
        if (!settings.useShizuku) return;
        const readyIds = Object.keys(data.readyToInstall);
        if (readyIds.length === 0) return;
        triggerHaptic('impact', ImpactStyle.Heavy);
        setDevToast(`Updating ${readyIds.length} apps...`);
        for (const appId of readyIds) {
            const app = appLookup.get(appId);
            const fileName = data.readyToInstall[appId];
            if (app && fileName) {
                await handleInstallFile(app, fileName);
                await new Promise(r => setTimeout(r, 1000));
            }
        }
        setDevToast("All updates completed.");
        triggerHaptic('notification', undefined, NotificationType.Success);
    }, [data.readyToInstall, appLookup, settings.useShizuku, handleInstallFile, triggerHaptic]);

    const handleExportAPK = useCallback(async (app: AppItem, fileName: string) => {
        if (!Capacitor.isNativePlatform()) return;
        try {
            triggerHaptic('selection');
            await AppTracker.exportFile({ fileName });
            setDevToast(`Exported to Downloads`);
            triggerHaptic('notification', undefined, NotificationType.Success);
            const newCleanup = { ...data.pendingCleanup };
            delete newCleanup[app.id];
            data.setPendingCleanup(newCleanup);
        } catch (e: any) {
            setErrorMsg(e.message || "Export failed");
            setShowErrorToast(true);
        }
    }, [data, triggerHaptic]);

    const startForcedStoreDownload = useCallback(async () => {
        if (!Capacitor.isNativePlatform()) return;
        const snapshot = useSettingsStore.getState().forcedStoreUpdate;
        if (!snapshot) return;
        const downloadUrl = forcedStoreDownloadUrlRef.current;
        if (!downloadUrl) {
            setForcedStoreUpdateStatus('download_failed');
            setForcedStoreError('No download URL found for this update.');
            return;
        }
        try {
            const result = await AppTracker.downloadFile({ url: downloadUrl, fileName: snapshot.downloadFileName });
            forcedStoreDownloadIdRef.current = result.downloadId || snapshot.downloadFileName;
            forcedStoreDownloadTriggered.current = true;
            setForcedStoreUpdateStatus('downloading');
            setForcedStoreError('');
            setForcedStoreStatusText('Downloading the latest Orion build...');
            setForcedStorePollingActive(true);
            useSettingsStore.getState().setForcedStoreUpdate(createForcedStoreUpdateSnapshot({
                ...snapshot,
                status: 'downloading',
                downloadCompleted: false
            }));
        } catch (downloadError: any) {
            setForcedStoreUpdateStatus('download_failed');
            setForcedStoreError(downloadError?.message || 'Download failed. Check your connection and retry.');
        }
    }, []);

    useEffect(() => {
        if (!forcedStorePollingActive || !forcedStoreDownloadIdRef.current) return;
        const id = forcedStoreDownloadIdRef.current;
        let active = true;
        const poll = async () => {
            while (active) {
                try {
                    const progress = await AppTracker.getDownloadProgress({ downloadId: id });
                    if (!active) return;
                    setForcedStoreProgress(progress.progress || 0);
                    if (progress.status === 'SUCCESSFUL') {
                        const snapshot = useSettingsStore.getState().forcedStoreUpdate;
                        if (snapshot) {
                            useSettingsStore.getState().setForcedStoreUpdate(createForcedStoreUpdateSnapshot({
                                ...snapshot,
                                status: 'ready_to_scan_or_install',
                                downloadCompleted: true
                            }));
                        }
                        setForcedStoreUpdateStatus('ready_to_scan_or_install');
                        setForcedStoreStatusText('Download complete.');
                        setForcedStoreProgress(100);
                        forcedStoreDownloadIdRef.current = null;
                        forcedStoreDownloadTriggered.current = false;
                        setForcedStorePollingActive(false);
                        return;
                    }
                    if (progress.status === 'FAILED') {
                        const snapshot = useSettingsStore.getState().forcedStoreUpdate;
                        if (snapshot) {
                            useSettingsStore.getState().setForcedStoreUpdate(createForcedStoreUpdateSnapshot({
                                ...snapshot,
                                status: 'download_failed',
                                downloadCompleted: false
                            }));
                        }
                        setForcedStoreUpdateStatus('download_failed');
                        setForcedStoreError('Download failed. Check your connection and retry.');
                        forcedStoreDownloadIdRef.current = null;
                        forcedStoreDownloadTriggered.current = false;
                        setForcedStorePollingActive(false);
                        return;
                    }
                } catch {
                    const snapshot = useSettingsStore.getState().forcedStoreUpdate;
                    if (snapshot) {
                        useSettingsStore.getState().setForcedStoreUpdate(createForcedStoreUpdateSnapshot({
                            ...snapshot,
                            status: 'download_failed',
                            downloadCompleted: false
                        }));
                    }
                    setForcedStoreUpdateStatus('download_failed');
                    setForcedStoreError('Download failed. Check your connection and retry.');
                    forcedStoreDownloadIdRef.current = null;
                    forcedStoreDownloadTriggered.current = false;
                    setForcedStorePollingActive(false);
                    return;
                }
                await new Promise((resolve) => window.setTimeout(resolve, 800));
            }
        };
        poll();
        return () => { active = false; };
    }, [forcedStorePollingActive]);

    useEffect(() => {
        if (forcedStoreUpdateStatus === 'downloading' && !forcedStoreDownloadIdRef.current && !forcedStoreDownloadTriggered.current) {
            startForcedStoreDownload();
        }
    }, [forcedStoreUpdateStatus, startForcedStoreDownload]);

    const handleForcedStoreInstall = useCallback(async () => {
        if (!Capacitor.isNativePlatform()) return;
        let snapshot = useSettingsStore.getState().forcedStoreUpdate || settings.forcedStoreUpdate;
        if (!snapshot) {
            const targetVersion = effectiveRemoteConfig?.latestStoreVersion || effectiveRemoteConfig?.minStoreVersion;
            if (targetVersion) {
                snapshot = createForcedStoreUpdateSnapshot({
                    targetVersion,
                    downloadFileName: buildForcedStoreFileName(targetVersion),
                    status: 'scan_complete',
                    downloadCompleted: true,
                    exported: false
                });
                settings.setForcedStoreUpdate(snapshot);
            }
        }
        if (!snapshot || !snapshot.downloadFileName) {
            commitForcedStoreUpdateStatus('ready_to_scan_or_install');
            setForcedStoreError('Update package metadata is missing. Retry the download, then install again.');
            return;
        }
        commitForcedStoreUpdateStatus('installing');
        setForcedStoreStatusText('Opening installer...');
        settings.setForcedStoreUpdate(createForcedStoreUpdateSnapshot({
            ...snapshot,
            status: 'installing'
        }));
        try {
            const permission = await AppTracker.canRequestPackageInstalls();
            if (!permission.value) {
                await AppTracker.openInstallPermissionSettings();
                // Drop back to a ready state so the user can retry once the
                // system settings page hands control back to Orion.
                commitForcedStoreUpdateStatus('ready_to_scan_or_install');
                setForcedStoreStatusText('Grant install permission, then tap Install Now again.');
                settings.setForcedStoreUpdate(createForcedStoreUpdateSnapshot({
                    ...snapshot,
                    status: 'ready_to_scan_or_install'
                }));
                return;
            }
            await AppTracker.installPackage({ fileName: snapshot.downloadFileName });
            // Installer returned RESULT_OK. Auto-delete the downloaded APK
            // so it doesn't linger in the app's private downloads folder.
            AppTracker.deleteFile({ fileName: snapshot.downloadFileName }).catch(() => { });
            settings.setForcedStoreUpdate(createForcedStoreUpdateSnapshot({
                ...snapshot,
                status: 'installed',
                downloadCompleted: false
            }));
        } catch (installError: any) {
            const code = installError?.message;
            const message =
                code === 'INSTALL_CANCELED'
                    ? 'Install cancelled. Save the APK to install it manually.'
                    : code === 'INSTALL_FAILED'
                        ? 'Install failed. Save the APK to install it manually.'
                        : (code || 'Install failed. Save the APK to install it manually.');
            settings.setForcedStoreUpdate(createForcedStoreUpdateSnapshot({
                ...snapshot,
                status: 'install_failed'
            }));
            commitForcedStoreUpdateStatus('install_failed');
            setForcedStoreError(message);
        }
    }, [commitForcedStoreUpdateStatus, effectiveRemoteConfig?.latestStoreVersion, effectiveRemoteConfig?.minStoreVersion, settings]);

    const handleForcedStoreRetryInstall = useCallback(() => {
        handleForcedStoreInstall();
    }, [handleForcedStoreInstall]);

    const handleForcedStoreExport = useCallback(async () => {
        if (!Capacitor.isNativePlatform()) return;
        const snapshot = settings.forcedStoreUpdate;
        if (!snapshot || snapshot.exported) return;
        try {
            const result = await AppTracker.exportFile({ fileName: snapshot.downloadFileName });
            // Keep the status as 'install_failed' so the gate stays on the
            // "Install needs attention" view, but flip `exported` to true
            // so the action button morphs into a "Saved to Downloads" pill.
            settings.setForcedStoreUpdate(createForcedStoreUpdateSnapshot({
                ...snapshot,
                status: 'install_failed',
                exported: true
            }));
            setForcedStoreExportPath(result.path);
            setForcedStoreError('');
            setForcedStoreStatusText('Saved to your downloads folder.');
            triggerHaptic('notification', undefined, NotificationType.Success);
        } catch (exportError: any) {
            setForcedStoreError(exportError?.message || 'Export failed. Try again.');
        }
    }, [settings, triggerHaptic]);

    const getForcedStoreSnapshotForAction = useCallback((status: ForcedStoreUpdateStatus = forcedStoreUpdateStatusRef.current) => {
        const existingSnapshot = useSettingsStore.getState().forcedStoreUpdate || settings.forcedStoreUpdate;
        if (existingSnapshot) return existingSnapshot;

        const targetVersion = effectiveRemoteConfig?.latestStoreVersion || effectiveRemoteConfig?.minStoreVersion;
        if (!targetVersion && !storeUpdateUrl) return null;

        const rebuiltSnapshot = createForcedStoreUpdateSnapshot({
            targetVersion: targetVersion || '0.0.0',
            downloadFileName: targetVersion ? buildForcedStoreFileName(targetVersion) : '',
            status,
            downloadCompleted: status !== 'downloading',
            exported: false
        });
        settings.setForcedStoreUpdate(rebuiltSnapshot);
        return rebuiltSnapshot;
    }, [effectiveRemoteConfig?.latestStoreVersion, effectiveRemoteConfig?.minStoreVersion, settings, storeUpdateUrl]);

    const handleForcedStoreScan = useCallback((apiKeyOverride?: string) => {
        const snapshot = getForcedStoreSnapshotForAction('ready_to_scan_or_install');
        if (!snapshot) {
            setForcedStoreError('Update package metadata is missing. Retry the download, then scan again.');
            return;
        }
        const apiKeyForScan = (typeof apiKeyOverride === 'string' ? apiKeyOverride : settings.virusTotalApiKey).trim();
        if (!apiKeyForScan) {
            forcedStorePendingScanAfterKeyRef.current = true;
            commitForcedStoreUpdateStatus('byok_required');
            setForcedStoreStatusText('Add your VirusTotal API key below, then scan the update.');
            settings.setForcedStoreUpdate(createForcedStoreUpdateSnapshot({
                ...snapshot,
                status: 'byok_required'
            }));
            return;
        }
        forcedStorePendingScanAfterKeyRef.current = false;
        commitForcedStoreUpdateStatus('scanning');
        setForcedStoreStatusText('Scanning the APK with VirusTotal.');
        setForcedStoreError('');
        setForcedStoreScanResult(null);
        settings.setForcedStoreUpdate(createForcedStoreUpdateSnapshot({
            ...snapshot,
            status: 'scanning'
        }));
        // Run the scan inline via the hook — the gate shows live progress.
        const target = snapshot.downloadFileName && snapshot.downloadCompleted
            ? { type: 'downloaded-apk' as const, label: 'Orion Store APK', detail: snapshot.downloadFileName }
            : { type: 'remote-apk' as const, label: 'Orion Store APK', detail: storeUpdateUrl };
        forcedStoreVtStartScan(target, apiKeyForScan).then((result) => {
            if (!result) return;
            commitForcedStoreUpdateStatus('scan_complete');
            setForcedStoreScanResult(result);
            setForcedStoreStatusText('Scan complete. Install whenever you are ready.');
            setForcedStoreError('');
            const latestSnapshot = useSettingsStore.getState().forcedStoreUpdate;
            if (latestSnapshot) {
                useSettingsStore.getState().setForcedStoreUpdate(createForcedStoreUpdateSnapshot({
                    ...latestSnapshot,
                    status: 'scan_complete'
                }));
            }
        }).catch((err) => {
            const latestSnapshot = useSettingsStore.getState().forcedStoreUpdate;
            if (latestSnapshot) {
                useSettingsStore.getState().setForcedStoreUpdate(createForcedStoreUpdateSnapshot({
                    ...latestSnapshot,
                    status: 'ready_to_scan_or_install'
                }));
            }
            commitForcedStoreUpdateStatus('ready_to_scan_or_install');
            setForcedStoreError(err?.message || 'Scan failed. Try again.');
        });
    }, [commitForcedStoreUpdateStatus, getForcedStoreSnapshotForAction, settings, forcedStoreVtStartScan, storeUpdateUrl]);

    // Watch the inline VT scan: on results move to scan_complete, on error revert.
    // Depend on the primitive fields the effect reads (not the whole hook object,
    // which has a new identity each render) to avoid runaway re-runs mid-scan.
    const { view: forcedStoreVtView, result: forcedStoreVtResult, error: forcedStoreVtError, setError: forcedStoreVtSetError } = forcedStoreVt;
    useEffect(() => {
        if (forcedStoreUpdateStatus !== 'scanning') return;
        if (forcedStoreVtView === 'results' && forcedStoreVtResult) {
            commitForcedStoreUpdateStatus('scan_complete');
            setForcedStoreScanResult(forcedStoreVtResult);
            setForcedStoreStatusText('Scan complete. Install whenever you are ready.');
            setForcedStoreError('');
            const snapshot = settings.forcedStoreUpdate;
            if (snapshot) {
                settings.setForcedStoreUpdate(createForcedStoreUpdateSnapshot({
                    ...snapshot,
                    status: 'scan_complete'
                }));
            }
        } else if (forcedStoreVtView === 'error' && forcedStoreVtError) {
            if (forcedStoreVtResult) {
                commitForcedStoreUpdateStatus('scan_complete');
                setForcedStoreScanResult(forcedStoreVtResult);
                setForcedStoreStatusText('Scan complete. Install whenever you are ready.');
                setForcedStoreError('');
                forcedStoreVtSetError('');
                return;
            }
            const snapshot = settings.forcedStoreUpdate;
            if (snapshot) {
                settings.setForcedStoreUpdate(createForcedStoreUpdateSnapshot({
                    ...snapshot,
                    status: 'ready_to_scan_or_install'
                }));
            }
            commitForcedStoreUpdateStatus('ready_to_scan_or_install');
            setForcedStoreError(forcedStoreVtError);
            forcedStoreVtSetError('');
        }
    }, [commitForcedStoreUpdateStatus, forcedStoreUpdateStatus, forcedStoreVtView, forcedStoreVtResult, forcedStoreVtError, forcedStoreVtSetError, settings]);

    const handleForcedStoreOpenReport = useCallback(() => {
        const permalink = forcedStoreScanResult?.permalink || forcedStoreVt.result?.permalink;
        if (permalink) window.open(permalink, '_blank');
    }, [forcedStoreScanResult, forcedStoreVt.result]);

    const handleForcedStoreByokRedirect = useCallback(() => {
        setSettingsInitialMenu('network');
        setShowSettingsModal(true);
    }, []);

    const handleForcedStoreSaveApiKey = useCallback((key: string) => {
        const trimmed = key.trim();
        if (!trimmed) return;
        settings.setVirusTotalApiKey(trimmed);
        if (forcedStoreUpdateStatusRef.current === 'byok_required' || forcedStorePendingScanAfterKeyRef.current) {
            handleForcedStoreScan(trimmed);
        }
    }, [handleForcedStoreScan, settings]);

    useEffect(() => {
        if (
            forcedStoreUpdateStatus === 'byok_required' &&
            settings.virusTotalApiKey
        ) {
            if (forcedStorePendingScanAfterKeyRef.current && settings.forcedStoreUpdate) {
                handleForcedStoreScan();
                return;
            }
            setForcedStoreUpdateStatus('ready_to_scan_or_install');
            const snapshot = settings.forcedStoreUpdate;
            if (snapshot) {
                settings.setForcedStoreUpdate(createForcedStoreUpdateSnapshot({
                    ...snapshot,
                    status: 'ready_to_scan_or_install'
                }));
            }
        }
    }, [forcedStoreUpdateStatus, handleForcedStoreScan, settings]);

    const handleForcedStoreRetryDownload = useCallback(() => {
        setForcedStoreError('');
        setForcedStoreProgress(0);
        setForcedStoreScanResult(null);
        forcedStoreDownloadIdRef.current = null;
        forcedStoreDownloadTriggered.current = false;
        const snapshot = settings.forcedStoreUpdate;
        if (snapshot) {
            settings.setForcedStoreUpdate(createForcedStoreUpdateSnapshot({
                ...snapshot,
                status: 'downloading',
                downloadCompleted: false
            }));
        }
        setForcedStoreUpdateStatus('downloading');
        setForcedStoreStatusText('Retrying download...');
    }, [settings]);

    useEffect(() => {
        if (!Capacitor.isNativePlatform()) return;
        const resume = async () => {
            const snapshot = settings.forcedStoreUpdate;
            if (!snapshot || !snapshot.downloadCompleted) return;
            try {
                await AppTracker.resolveDownloadFile({ fileName: snapshot.downloadFileName });
            } catch {
                const updated = createForcedStoreUpdateSnapshot({
                    ...snapshot,
                    status: 'download_failed',
                    downloadCompleted: false
                });
                settings.setForcedStoreUpdate(updated);
                setForcedStoreUpdateStatus('download_failed');
                setForcedStoreError('Previously downloaded file not found. Please retry.');
            }
        };
        resume();
    }, [settings]);

    const handleDownloadAction = async (app: AppItem, url?: string, isAuto: boolean = false) => {
        if (data.readyToInstall[app.id]) {
            if (!isAuto) handleInstallFile(app, data.readyToInstall[app.id] || '');
            return;
        }
        if (data.activeDownloads[app.id]) {
            if (!isAuto) setSelectedApp(app);
            return;
        }
        if (initializingDownloads.current.has(app.id)) return;
        initializingDownloads.current.add(app.id);

        const preferredStream = useSettingsStore.getState().appStreams[app.id] || 'Stable';
        const downloadMetadata = getDownloadVersionMetadata(app, url, preferredStream);
        if (downloadMetadata.stream) {
            settings.setAppStream(app.id, downloadMetadata.stream);
        }

        const targetUrl = url || app.variants?.[0]?.url || app.downloadUrl;
        if (!targetUrl || targetUrl === '#') {
            initializingDownloads.current.delete(app.id);
            return;
        }

        if (settings.wifiOnly && !((navigator as any).connection?.type === 'wifi')) {
            if (!isAuto) {
                setErrorMsg('Download blocked: WiFi Only mode.');
                setShowErrorToast(true);
                triggerHaptic('notification', undefined, NotificationType.Error);
            }
            initializingDownloads.current.delete(app.id);
            return;
        }

        const safe = sanitizeDownloadUrl(targetUrl);
        if (safe === '#') {
            if (!isAuto) {
                setErrorMsg('Download link is invalid or unsupported.');
                setShowErrorToast(true);
                triggerHaptic('notification', undefined, NotificationType.Error);
            }
            initializingDownloads.current.delete(app.id);
            return;
        }
        const isAndroid = app.platform === Platform.ANDROID;
        const isStandardFile = safe.toLowerCase().endsWith('.apk') || safe.toLowerCase().endsWith('.exe') || safe.toLowerCase().endsWith('.zip');

        if (!isStandardFile && !isAndroid && !isAuto) {
            window.open(safe, '_blank');
            initializingDownloads.current.delete(app.id);
            return;
        }

        if (!Capacitor.isNativePlatform()) {
            const newRegistry = { ...useSettingsStore.getState().installedVersions, [app.id]: app.latestVersion };
            settings.setInstalledVersions(newRegistry);
            window.location.href = safe;
            initializingDownloads.current.delete(app.id);
            return;
        }
        if (app.platform === Platform.PC || app.platform === Platform.TV) {
            if (!isAuto) window.open(safe, '_blank');
            initializingDownloads.current.delete(app.id);
        } else {
            const sanitizedName = app.name.replace(/[^a-zA-Z0-9]/g, '_');
            const fileName = `${sanitizedName}_${sanitizeFileVersionToken(downloadMetadata.version || app.latestVersion)}.apk`;
            try {
                const result = await AppTracker.downloadFile({ url: safe, fileName });
                if (result?.downloadId) handleDownloadStart(app.id, result.downloadId, fileName);
            } catch (e: any) {
                if (e.message && e.message.includes("INSUFFICIENT_STORAGE")) {
                    if (!isAuto) {
                        setErrorMsg("Not enough space on device!");
                        setShowErrorToast(true);
                    }
                } else {
                    if (!isAuto) window.location.href = safe;
                }
            } finally {
                initializingDownloads.current.delete(app.id);
            }
        }
    };

    const handleBundleDownload = useCallback(async (bundle: BundleItem) => {
        const settingsSnapshot = useSettingsStore.getState();
        const removedIds = new Set(settingsSnapshot.removedBundleApps[bundle.id] || []);
        const extraIds = settingsSnapshot.extraBundleApps[bundle.id] || [];
        const extraApps = extraIds
            .map((id) => appLookup.get(id))
            .filter((a): a is AppItem => !!a);
        const baseApps = (bundle.apps || []).filter((app) => !removedIds.has(app.id));
        const merged = [...baseApps, ...extraApps];
        const bundleApps = merged.filter((app, index, source) => source.findIndex((entry) => entry.id === app.id) === index);
        if (bundleApps.length === 0) return;

        triggerHaptic('impact', ImpactStyle.Medium);

        // Filter out already installed apps (FIX: skip installed apps in bundle download)
        const installedVersionsSnapshot = settingsSnapshot.installedVersions;
        const eligibleApps = bundleApps.filter((app) => {
            if (installedVersionsSnapshot[app.id]) {
                return false;
            }
            return true;
        });

        if (eligibleApps.length === 0) {
            setDevToast(`All apps from ${bundle.title} are already installed.`);
            return;
        }

        setDevToast(`Queueing ${eligibleApps.length} apps from ${bundle.title}...`);

        let queuedCount = 0;
        let skippedCount = bundleApps.length - eligibleApps.length;
        for (const app of eligibleApps) {
            if (!isMounted.current) break;
            const latestData = useDataStore.getState();
            if (latestData.activeDownloads[app.id] || latestData.readyToInstall[app.id] || initializingDownloads.current.has(app.id)) {
                skippedCount++;
                continue;
            }

            try {
                await handleDownloadAction(app, undefined, true);
                queuedCount++;
            } catch (err) {
                console.warn('[bundle] Failed to queue', app.id, err);
            }
            await new Promise((resolve) => setTimeout(resolve, 180));
        }

        if (queuedCount > 0) {
            const skipNote = skippedCount > 0 ? ` (${skippedCount} skipped)` : '';
            setDevToast(`Queued ${queuedCount} apps from ${bundle.title}${skipNote}.`);
            triggerHaptic('notification', undefined, NotificationType.Success);
        } else {
            setDevToast(`Everything in ${bundle.title} is already queued or installed.`);
        }
    }, [handleDownloadAction, triggerHaptic, appLookup]);

    const handleDownloadComplete = useCallback((appId: string, success: boolean) => {
        // Read fresh state to avoid stale closure over activeDownloads / apps
        const currentData = useDataStore.getState();
        if (success && isMounted.current) {
            const rawVal = currentData.activeDownloads[appId];
            if (!rawVal) return;
            const [_, fileName] = rawVal.split('|');
            if (fileName) {
                currentData.completeDownload(appId, fileName);
                const app = [...currentData.apps, ...currentData.importedApps].find((a: AppItem) => a.id === appId);
                if (app) {
                    setShowInstallToast({ app, file: fileName });
                    LocalNotifications.schedule({
                        notifications: [{
                            title: "Download Complete",
                            body: `${app.name} is ready to install.`,
                            id: getStringHash(appId),
                            schedule: { at: new Date(Date.now() + 100) },
                            channelId: 'orion_updates',
                            extra: { appId: app.id, fileName }
                        }]
                    });
                }
            }
        } else {
            currentData.failDownload(appId);
        }
        triggerHaptic('notification', undefined, success ? NotificationType.Success : NotificationType.Error);
    }, [triggerHaptic]);

    const isCleaningUpRef = useRef(false);

    const handleBatchCleanup = useCallback(async () => {
        if (isCleaningUpRef.current) return;
        isCleaningUpRef.current = true;

        // Read fresh state to avoid stale closure issues
        const freshData = useDataStore.getState();
        const cleanupIds = Object.keys(freshData.pendingCleanup);
        if (cleanupIds.length === 0) {
            isCleaningUpRef.current = false;
            return;
        }

        triggerHaptic('impact', ImpactStyle.Heavy);
        let successCount = 0;
        const failedEntries: Record<string, typeof freshData.pendingCleanup[string]> = {};
        const nextReadyToInstall = { ...freshData.readyToInstall };

        for (const appId of cleanupIds) {
            const entry = freshData.pendingCleanup[appId];
            if (!entry) continue;
            const fileName = typeof entry === 'string' ? entry : entry.fileName;
            if (fileName) {
                try {
                    await AppTracker.deleteFile({ fileName });
                    successCount++;
                    delete nextReadyToInstall[appId];
                } catch (e) {
                    // Keep failed entries so we can try again later
                    failedEntries[appId] = entry;
                }
            }
        }

        // Only clear successfully deleted entries; keep failures
        useDataStore.setState({ pendingCleanup: failedEntries, readyToInstall: nextReadyToInstall });
        if (successCount > 0) {
            triggerHaptic('notification', undefined, NotificationType.Success);
            setDevToast(`Cleaned ${successCount} files`);
        }

        isCleaningUpRef.current = false;
    }, [triggerHaptic]);

    const loadApps = useCallback(async (isManualRefresh = false) => {
        if (isManualRefresh) { setIsRefreshing(true); triggerHaptic('impact', ImpactStyle.Light); }
        if (useDataStore.getState().apps.length === 0) setIsLoading(true);

        const loadGeneration = ++loadGenerationRef.current;
        let primaryPayloadDispatched = false;
        const currentSettings = useSettingsStore.getState();
        const devConfigStore = useDevConfigStore.getState();
        const devConfigUrl = devConfigStore.url.trim();
        const shouldUseDevConfig = devConfigStore.enabled;
        const shouldUseRemoteConfig = shouldUseDevConfig || currentSettings.useRemoteJson;
        const configTs = `?t=${Date.now()}`;
        const appsTs = isManualRefresh ? `?t=${Date.now()}` : '';
        const appendCacheBust = (url: string, cacheBust: string) => `${url}${url.includes('?') ? '&' : '?'}${cacheBust.slice(1)}`;
        const clearConfigDrivenState = () => {
            if (!isMounted.current) return;
            // Guard FIRST: never wipe state while the user is mid-scan/install.
            const activeCleanupGuard: ForcedStoreUpdateStatus[] = [
                'scanning', 'scan_complete', 'installing',
                'install_failed', 'export_ready', 'byok_required',
                'ready_to_scan_or_install'
            ];
            if (activeCleanupGuard.includes(forcedStoreUpdateStatusRef.current)) {
                return;
            }
            setRemoteConfig(null);
            setStoreUpdateAvailable(false);
            setStoreUpdateUrl('');
            forcedStoreDownloadUrlRef.current = '';
            setForcedStoreUpdateStatus('inactive');
            setForcedStoreStatusText('');
            setForcedStoreExportPath('');
            setForcedStoreScanResult(null);
            if (useSettingsStore.getState().forcedStoreUpdate) {
                useSettingsStore.getState().clearForcedStoreUpdate();
            }
        };
        const evaluateStoreUpdateState = (config: StoreConfig | null) => {
            if (!isMounted.current) return;

            // Guard: never clobber an active scan / install / export state.
            // evaluateStoreUpdateState re-runs on every config refresh.
            const GUARD_STATES: ForcedStoreUpdateStatus[] = [
                'scanning', 'scan_complete', 'installing',
                'install_failed', 'export_ready', 'byok_required',
                'ready_to_scan_or_install'
            ];
            if (GUARD_STATES.includes(forcedStoreUpdateStatusRef.current)) {
                if (config?.storeDownloadUrl) {
                    forcedStoreDownloadUrlRef.current = config.storeDownloadUrl;
                }
                return;
            }

            setStoreUpdateAvailable(false);
            setStoreUpdateUrl('');

            forcedStoreDownloadUrlRef.current = '';

            const latestVersion = config?.latestStoreVersion || config?.minStoreVersion;
            const minStoreVersion = config?.minStoreVersion;
            const hasStoreUpdate = isStoreUpdateAvailable(CURRENT_STORE_VERSION, latestVersion);

            if (!hasStoreUpdate || !latestVersion) {
                setIsForcedUpdateBypassedForSession(false);
                setForcedStoreUpdateStatus('inactive');
                setForcedStoreStatusText('');
                if (useSettingsStore.getState().forcedStoreUpdate) {
                    useSettingsStore.getState().clearForcedStoreUpdate();
                }
                return;
            }

            setStoreUpdateAvailable(true);
            setStoreUpdateUrl(config?.storeDownloadUrl || '');
            forcedStoreDownloadUrlRef.current = config?.storeDownloadUrl || '';

            // With the legacy update modal removed, the ForcedStoreUpdateGate is
            // the sole update mechanism.  Trigger it for ANY available update that
            // has a download URL — not just when below minStoreVersion.
            if (hasStoreUpdate && config?.storeDownloadUrl) {
                const fileName = buildForcedStoreFileName(latestVersion);

                if (
                    canResumeForcedStoreUpdate(useSettingsStore.getState().forcedStoreUpdate, {
                        targetVersion: latestVersion,
                        minStoreVersion,
                        currentVersion: CURRENT_STORE_VERSION
                    })
                ) {
                    const snapshot = useSettingsStore.getState().forcedStoreUpdate;
                    if (snapshot?.status === 'export_ready') {
                        setForcedStoreUpdateStatus('export_ready');
                        setForcedStoreExportPath('');
                    } else {
                        setForcedStoreUpdateStatus('ready_to_scan_or_install');
                    }
                    setForcedStoreStatusText('The Orion update is already downloaded.');
                    return;
                }

                // Guard: never overwrite an active scan / install / export
                // state with a fresh download.  evaluateStoreUpdateState can
                // re-run on config refresh or app resume — it must not clobber
                // states that the user is actively interacting with.
                const ACTIVE_STATES: ForcedStoreUpdateStatus[] = [
                    'scanning', 'scan_complete', 'installing',
                    'install_failed', 'export_ready', 'byok_required',
                    'ready_to_scan_or_install'
                ];
                if (ACTIVE_STATES.includes(forcedStoreUpdateStatusRef.current)) {
                    return;
                }

                useSettingsStore.getState().setForcedStoreUpdate(createForcedStoreUpdateSnapshot({
                    targetVersion: latestVersion,
                    downloadFileName: fileName,
                    status: 'downloading',
                    downloadCompleted: false,
                    exported: false
                }));
                setForcedStoreUpdateStatus('downloading');
                setForcedStoreStatusText('Downloading the latest Orion build...');
                return;
            }

            setForcedStoreUpdateStatus('inactive');
            if (useSettingsStore.getState().forcedStoreUpdate) {
                useSettingsStore.getState().clearForcedStoreUpdate();
            }
        };

        if (shouldUseRemoteConfig && currentSettings.loadLocalData && !isManualRefresh && useDataStore.getState().apps.length === 0 && workerRef.current) {
            const warmupWorker = workerRef.current;
            const currentImported = useDataStore.getState().importedApps;

            loadEmbeddedApps()
                .then((embeddedApps) => {
                    if (!isMounted.current || primaryPayloadDispatched || loadGenerationRef.current !== loadGeneration || workerRef.current !== warmupWorker) {
                        return;
                    }

                    warmupWorker.postMessage({
                        type: 'INIT_DATA',
                        payload: {
                            rawApps: embeddedApps,
                            mirrorData: null,
                            importedApps: currentImported
                        }
                    });
                })
                .catch(() => { });
        }

        try {
            let rawApps: AppItem[] = [];
            let mirrorData: Record<string, any> | null = null;

            if (shouldUseRemoteConfig) {
                const SOURCES = [
                    { name: 'GitHub', config: CONFIG_URL_PRIMARY, apps: APPS_URL_PRIMARY },
                    { name: 'GitLab', config: CONFIG_URL_GITLAB, apps: APPS_URL_GITLAB },
                    { name: 'Codeberg', config: CONFIG_URL_CODEBERG, apps: APPS_URL_CODEBERG },
                    { name: 'JSDelivr', config: CONFIG_URL_FALLBACK, apps: APPS_URL_FALLBACK }
                ];

                let remoteLoaded = false;

                if (shouldUseDevConfig) {
                    try {
                        if (!devConfigUrl) {
                            throw new Error('Enter a developer config URL before reloading.');
                        }

                        devConfigStore.setStatus('loading');
                        devConfigStore.setLastError(null);

                        const configReq = await fetchWithRetry(appendCacheBust(devConfigUrl, configTs), { cache: 'no-store' }, 1);
                        if (!configReq.ok) {
                            throw new Error(`Dev config request failed with ${configReq.status}`);
                        }
                        const config = await configReq.json();
                        if (!config?.appsJsonUrl) {
                            throw new Error('Dev config is missing appsJsonUrl.');
                        }

                        const activeAppsUrl = config.appsJsonUrl;
                        const activeMirrorUrl = config?.mirrorJsonUrl || DEFAULT_MIRROR_JSON;
                        const [appsResponse, mirrorReq] = await Promise.all([
                            fetchWithRetry(appendCacheBust(activeAppsUrl, appsTs || configTs), { cache: 'no-store' }, 1),
                            fetchWithRetry(appendCacheBust(activeMirrorUrl, appsTs || configTs), {}, 1).catch(() => null)
                        ]);

                        if (!appsResponse.ok) {
                            throw new Error(`Dev apps request failed with ${appsResponse.status}`);
                        }

                        rawApps = await appsResponse.json();
                        mirrorData = mirrorReq && mirrorReq.ok ? await mirrorReq.json() : null;
                        remoteLoaded = true;
                        devConfigStore.markLoaded(devConfigUrl);

                        if (isMounted.current) {
                            setRemoteConfig(config);
                            setMirrorSource('Developer Config');
                            evaluateStoreUpdateState(config);
                        }
                    } catch (error) {
                        const message = error instanceof Error ? error.message : 'Failed to load developer config.';
                        devConfigStore.setStatus('error');
                        devConfigStore.setLastError(message);
                        clearConfigDrivenState();
                    }
                } else {
                    devConfigStore.clearRuntimeState();
                    for (const source of SOURCES) {
                        try {
                            const configReq = await fetchWithRetry(`${source.config}${configTs}`, { cache: 'no-store' }, 1);
                            if (!configReq.ok) continue;

                            const config = await configReq.json();
                            const activeAppsUrl = config?.appsJsonUrl || source.apps;
                            const activeMirrorUrl = config?.mirrorJsonUrl || DEFAULT_MIRROR_JSON;

                            const [appsResponse, mirrorReq] = await Promise.all([
                                fetchWithRetry(appendCacheBust(activeAppsUrl, appsTs || configTs), { cache: 'no-store' }, 1),
                                fetchWithRetry(appendCacheBust(activeMirrorUrl, appsTs || configTs), {}, 1).catch(() => null)
                            ]);

                            if (!appsResponse.ok) continue;

                            rawApps = await appsResponse.json();
                            mirrorData = mirrorReq && mirrorReq.ok ? await mirrorReq.json() : null;
                            remoteLoaded = true;

                            if (isMounted.current) {
                                setRemoteConfig(config);
                                setMirrorSource(source.name);
                                evaluateStoreUpdateState(config);
                            }
                            break;
                        } catch (e) {
                            continue;
                        }
                    }
                }

                if (!remoteLoaded) {
                    clearConfigDrivenState();
                    if (currentSettings.loadLocalData) {
                        rawApps = await loadEmbeddedApps();
                        if (isMounted.current) {
                            setMirrorSource(shouldUseDevConfig ? 'Developer Config Failed (Local Fallback)' : 'Offline (Local)');
                        }
                    } else if (isMounted.current) {
                        setMirrorSource(shouldUseDevConfig ? 'Developer Config Failed' : 'Failed (Offline)');
                    }
                }
            } else {
                devConfigStore.clearRuntimeState();
                clearConfigDrivenState();
                if (currentSettings.loadLocalData) {
                    rawApps = await loadEmbeddedApps();
                    if (isMounted.current) setMirrorSource('Disabled');
                } else if (isMounted.current) {
                    setMirrorSource('Disabled & Blocked Local');
                }
            }
            const currentImported = useDataStore.getState().importedApps;
            if (workerRef.current) {
                primaryPayloadDispatched = true;
                workerRef.current.postMessage({
                    type: 'INIT_DATA',
                    payload: {
                        rawApps,
                        mirrorData,
                        importedApps: currentImported
                    }
                });
            }
        } catch (error) {
            if (isMounted.current && useDataStore.getState().apps.length === 0) {
                setErrorMsg('Failed to load apps');
                setShowErrorToast(true);
                clearConfigDrivenState();
                if (shouldUseDevConfig) {
                    const message = error instanceof Error ? error.message : 'Failed to load developer config.';
                    devConfigStore.setStatus('error');
                    devConfigStore.setLastError(message);
                }
                triggerHaptic('notification', undefined, NotificationType.Error);
                setIsLoading(false); setIsRefreshing(false);
            }
        }
    }, [triggerHaptic]);

    useEffect(() => {
        if (devConfigHasHydrated) {
            loadApps(false);
        }
    }, [loadApps, devConfigHasHydrated]);

    // Preload SettingsModal chunk after initial render so it opens instantly on Android
    useEffect(() => {
        // Fire as soon as the main thread is free — this is the warm-up path
        // for users who tap Settings before the per-press preload triggers.
        const cancelPreload = scheduleBackgroundTask(() => {
            preloadSettingsModal();
        }, 300);
        return () => cancelPreload();
    }, []);

    const targetPlatform = useMemo(() => {
        if (activeTab === 'pc') return Platform.PC;
        if (activeTab === 'tv') return Platform.TV;
        return Platform.ANDROID;
    }, [activeTab]);
    const platformAppsForTab = appsByPlatform[targetPlatform] || [];

    const dynamicCategories = useMemo(() => {
        return getAvailableFilterCategories(platformAppsForTab);
    }, [platformAppsForTab]);

    const visibleAppsForTab = useMemo(() => {
        let filtered = visibleApps.filter((app) => app.platform === targetPlatform);
        if (currentTabState.filterFavorites) {
            filtered = filtered.filter((app) => favoriteIdSet.has(app.id));
        }
        return filtered;
    }, [visibleApps, targetPlatform, currentTabState.filterFavorites, favoriteIdSet]);

    const classicAppsForCurrentView = showAllSorted && viewAllApps
        ? viewAllApps
        : visibleAppsForTab;

    // Defer the expensive app list to keep input responsive during transitions.
    // Modern "Show All" intentionally falls back to this classic renderer to
    // avoid loading every app into the heavier Modern card grid.
    const deferredClassicAppsForCurrentView = useDeferredValue(classicAppsForCurrentView);

    const shouldShowModernHome = settings.storeLayout === 'modern'
        && isModernHomeState(currentTabState);

    const availableUpdates = useAvailableUpdates(allKnownApps);
    const availableUpdatesForTab = useMemo(() => (
        availableUpdates.filter((app) => app.platform === targetPlatform)
    ), [availableUpdates, targetPlatform]);
    const updatesAvailableCollection = useMemo(() => (
        settings.storeLayout === 'modern' && activeTab === 'android'
            ? buildUpdatesAvailableCollection(availableUpdatesForTab)
            : null
    ), [activeTab, availableUpdatesForTab, settings.storeLayout]);
    // Hydrate the user's locally-saved custom bundles into BundleItems so they
    // can be merged INTO the curated remote 'recommendation_bundles' shelf.
    // Apps are hydrated against the current platform so cross-platform IDs are
    // filtered out automatically.
    //
    // Badge ownership convention:
    //   • "Rookie Approved"  → developer-curated bundles in config.json (remote)
    //   • "Community Shared" → community submissions the developer approved
    //                          and merged into config.json (remote)
    //   • "Local"            → bundles the user keeps only on this device
    //                          (NEVER overwrites a remote bundle's badge)
    //
    // Both "Rookie Approved" and "Community Shared" are therefore set in the
    // upstream Orion-Data config.json by the maintainer — never on-device. The
    // only badge we attach client-side is "Local" for the user's own creations
    // (whether or not they later submitted them as a GitHub issue).
    const customLocalBundles = useMemo<BundleItem[]>(() => {
        const list = settings.customBundles || [];
        if (list.length === 0) return [];
        const platformAppMap = new Map<string, AppItem>();
        for (const app of platformAppsForTab) {
            platformAppMap.set(app.id, app);
        }
        return list.map((cb) => {
            const apps = cb.appIds.map((id) => platformAppMap.get(id)).filter(Boolean) as AppItem[];
            return {
                id: cb.id,
                title: cb.title,
                description: cb.description,
                icon: cb.icon,
                appIds: cb.appIds,
                apps,
                badge: 'Local',
            };
        }).filter((b) => b.apps && b.apps.length > 0);
    }, [settings.customBundles, platformAppsForTab]);

    const activeModernCollections = useMemo(() => {
        if (showAllSorted) {
            return [];
        }

        if (!shouldShowModernHome) return [];

        if (effectiveSort === SortOption.HOME) {
            const withUpdates = injectCollectionAfterHero(storeCollections, updatesAvailableCollection);
            if (customLocalBundles.length === 0) return withUpdates;
            // Merge local bundles into the existing remote recommendation_bundles
            // shelf (after the curated remote bundles). If the remote shelf is
            // missing for this platform, synthesize a minimal one so users still
            // see their bundles surfaced.
            const remoteIdx = withUpdates.findIndex((c) => c.type === 'recommendation_bundles');
            if (remoteIdx === -1) {
                const synthetic: StoreCollection = {
                    id: 'recommendation_bundles_local',
                    title: 'Recommended app bundles',
                    subtitle: 'Curated picks for your platform',
                    type: 'recommendation_bundles',
                    bundles: customLocalBundles,
                };
                return injectCollectionAfterHero(withUpdates, synthetic);
            }
            const merged = [...withUpdates];
            const target = merged[remoteIdx]!;
            const remoteBundles = target.bundles ?? [];
            // Dedupe by id — remote always wins.
            const remoteIds = new Set(remoteBundles.map((b) => b.id));
            const localOnly = customLocalBundles.filter((b) => !remoteIds.has(b.id));
            merged[remoteIdx] = {
                ...target,
                bundles: [...remoteBundles, ...localOnly],
            };
            return merged;
        }

        return buildSortedModernCollections(visibleAppsForTab, effectiveSort);
    }, [shouldShowModernHome, showAllSorted, effectiveSort, storeCollections, updatesAvailableCollection, visibleAppsForTab, customLocalBundles]);

    const updateCount = availableUpdates.length;

    useEffect(() => {
        if (settings.autoUpdateEnabled && Capacitor.isNativePlatform()) {
            const candidates = availableUpdates.filter(app =>
                app.platform === Platform.ANDROID &&
                !data.activeDownloads[app.id] &&
                !data.readyToInstall[app.id] &&
                !initializingDownloads.current.has(app.id)
            );
            if (candidates.length > 0) candidates.forEach(app => handleDownloadAction(app, undefined, true));
        }
    }, [settings.autoUpdateEnabled, availableUpdates, data.activeDownloads, data.readyToInstall]);

    const toggleTheme = useCallback(() => {
        const newTheme: Theme = settings.theme === 'light' ? 'dusk' : settings.theme === 'dusk' ? 'dark' : 'light';
        applyThemeChange(newTheme);
        triggerHaptic('impact', ImpactStyle.Medium);
    }, [applyThemeChange, settings.theme, triggerHaptic]);

    const syncSpecificApp = useCallback(async (app: AppItem) => {
        if (!Capacitor.isNativePlatform() || !app.packageName) return;
        try {
            const result = (await syncInstalledStateForTargets([app]))[app.id];
            if (!result?.installed) {
                const currentSettings = useSettingsStore.getState();
                if (currentSettings.lastRemoteVersions[app.id]) {
                    settings.removeLastRemoteVersion(app.id);
                }
            }
        } catch (e) { }
    }, [settings, syncInstalledStateForTargets]);

    const handleAppSelect = useCallback((app: AppItem) => {
        setSelectedApp(app);
        if (app.packageName && Capacitor.isNativePlatform()) {
            syncSpecificApp(app);
        }
    }, [syncSpecificApp]);

    const handleCategoryNavigate = useCallback((category: string) => {
        const root = document.getElementById('root');
        if (root) root.scrollTop = 0;
        triggerHaptic('selection');
        setExpandedModernView(null);
        setExpandedModernApps([]);
        setReturnToExpandedView(null);
        setShowAllSorted(false);
        setViewAllApps(null);
        startTransition(() => {
            data.setSelectedCategory(activeTab, category);
            // When navigating to a specific category, switch sort from Home to Newest
            // so the sort button doesn't show "Home Page" in a filtered list
            const currentSort = useDataStore.getState().tabs[activeTab]?.sort;
            if (currentSort === SortOption.HOME) {
                data.setSelectedSort(activeTab, SortOption.NEWEST);
            }
        });
    }, [activeTab, data.setSelectedCategory, data.setSelectedSort, triggerHaptic]);

    const handleOpenSettings = useCallback(() => {
        setSettingsInitialMenu('none');
        setShowSettingsModal(true);
    }, []);

    // Fire-and-forget preload; pointerdown calls this so the chunk is already
    // resolved by the time the click event flips the modal visible.
    const handleOpenSettingsPreload = useCallback(() => {
        preloadSettingsModal();
    }, []);

    const handleOpenReleaseNotes = useCallback(() => {
        setShowReleaseNotes(true);
    }, []);



    const devProfile = effectiveRemoteConfig?.devProfile || DEFAULT_DEV_PROFILE;
    const supportEmail = effectiveRemoteConfig?.supportEmail || DEFAULT_SUPPORT_EMAIL;
    const socialLinks = effectiveRemoteConfig?.socials || DEV_SOCIALS;
    const faqs = effectiveRemoteConfig?.faqs || DEFAULT_FAQS;
    const easterEggUrl = effectiveRemoteConfig?.easterEggUrl || DEFAULT_EASTER_EGG;

    const handleBottomNavChange = useCallback((tab: Tab) => {
        if (tab !== 'about' && settings.hiddenTabs.includes(tab)) return;
        triggerHaptic('impact', ImpactStyle.Light);
        setActiveTab(tab);
        const root = document.getElementById('root');
        if (root) root.scrollTop = 0;
    }, [settings.hiddenTabs, triggerHaptic]);

    const handleTitleClick = useCallback(() => {
        if (settings.isDevUnlocked) {
            setDevToast('Already a developer.');
            return;
        }

        const stepsNeeded = 7;
        const newCount = devClickCount + 1;
        const remaining = stepsNeeded - newCount;
        setDevClickCount(newCount);

        if (remaining > 0 && remaining <= 4) {
            setDevToast(`You are ${remaining} steps away from being a developer.`);
            triggerHaptic('impact', ImpactStyle.Light);
        }

        if (newCount >= stepsNeeded) {
            settings.setDevUnlocked(true);
            setDevToast('You are now a developer!');
            triggerHaptic('notification', undefined, NotificationType.Success);
            setDevClickCount(0);
        }
    }, [devClickCount, settings.isDevUnlocked, settings.setDevUnlocked, triggerHaptic]);

    const handleAboutProfileClick = useCallback((view?: 'profile' | 'badges', badgeIndex?: number) => {
        if (view === 'badges') {
            setProfileStatsInitialView('badges');
            setProfileBadgeSelectionIndex(badgeIndex ?? null);
            setShowProfileStats(true);
            triggerHaptic('impact', ImpactStyle.Light);
            return;
        }

        setProfileBadgeSelectionIndex(null);
        const newCount = easterEggCount + 1;
        setEasterEggCount(newCount);
        if (newCount >= 7) {
            window.open(easterEggUrl, '_blank');
            setEasterEggCount(0);
            settings.incrementAdWatch();
            settings.setIsLegend(true);
            triggerHaptic('notification', undefined, NotificationType.Success);
        } else {
            triggerHaptic('impact', ImpactStyle.Light);
        }
    }, [easterEggCount, easterEggUrl, settings.incrementAdWatch, settings.setIsLegend, triggerHaptic]);

    const handleNavigateToApp = useCallback((appId: string) => {
        const target = appLookup.get(appId);
        if (target) {
            setSelectedApp(target);
        } else {
            setDevToast(`App \u2018${appId}\u2019 not found`);
        }
    }, [appLookup]);

    const handleNavigateToAppFromSettings = useCallback((appId: string) => {
        setShowSettingsModal(false);
        const target = appLookup.get(appId);
        if (target) {
            window.setTimeout(() => setSelectedApp(target), 100);
        } else {
            setDevToast(`App \u2018${appId}\u2019 not found`);
        }
    }, [appLookup]);

    // ── Deep Link Handler ──────────────────────────────────────────────
    // Handles orionstore://app/{appId} deep links from external sources.
    // When the app is launched via a deep link, data may not have loaded yet,
    // so we queue the appId and resolve it once appLookup is populated.
    useEffect(() => {
        const listener = CapacitorApp.addListener('appUrlOpen', ({ url }) => {
            try {
                // orionstore://app/youtube-revanced → hostname="app", pathname="/youtube-revanced"
                // orionstore://app//youtube-revanced → pathname="/youtube-revanced"
                const parsed = new URL(url);
                const scheme = parsed.protocol.replace(':', '');
                if (scheme !== 'orionstore') return;
                const segments = (parsed.hostname + parsed.pathname).split('/').filter(Boolean);
                // Expected: ["app", "{appId}"]
                if (segments[0] === 'app' && segments[1]) {
                    const appId = decodeURIComponent(segments[1]);
                    // If apps haven't loaded yet, queue the deep link for later resolution
                    if (appLookup.size === 0) {
                        pendingDeepLinkRef.current = appId;
                        return;
                    }
                    const target = appLookup.get(appId);
                    if (target) {
                        setSelectedApp(target);
                    } else {
                        setDevToast(`App \u2018${appId}\u2019 not found in Orion Store`);
                    }
                }
            } catch { /* ignore malformed URLs */ }
        });
        return () => { listener.then(l => l.remove()); };
    }, [appLookup]);

    // Resolve any deep link that arrived before apps finished loading
    useEffect(() => {
        if (appLookup.size > 0 && pendingDeepLinkRef.current) {
            const appId = pendingDeepLinkRef.current;
            pendingDeepLinkRef.current = null;
            const target = appLookup.get(appId);
            if (target) {
                setSelectedApp(target);
            } else {
                setDevToast(`App \u2018${appId}\u2019 not found in Orion Store`);
            }
        }
    }, [appLookup]);

    const handleReloadApps = useCallback(() => {
        if (isRefreshing) return;
        loadApps(true);
    }, [isRefreshing, loadApps]);

    useEffect(() => {
        const handler = () => handleReloadApps();
        window.addEventListener('orion:trigger-refresh' as any, handler);
        return () => window.removeEventListener('orion:trigger-refresh' as any, handler);
    }, [handleReloadApps]);

    // Pull-to-refresh is only enabled on the app data tabs (Android / PC / TV).
    // The About tab is excluded so that pulling on it is a no-op.
    usePullToRefresh({
        onRefresh: handleReloadApps,
        disabled: Capacitor.isNativePlatform() || isRefreshing || !isAppDataTab || (effectiveRemoteConfig?.maintenanceMode && !bypassMaintenance)
    });

    const handleSearchQueryChange = useCallback((query: string) => {
        setExpandedModernView(null);
        setExpandedModernApps([]);
        setReturnToExpandedView(null);
        setShowAllSorted(false);
        setViewAllApps(null);
        startTransition(() => data.setSearchQuery(activeTab, query));
    }, [activeTab, data.setSearchQuery]);

    const handleFilterCategoryChange = useCallback((category: string) => {
        setExpandedModernView(null);
        setExpandedModernApps([]);
        setShowAllSorted(false);
        setViewAllApps(null);
        startTransition(() => data.setSelectedCategory(activeTab, category));
    }, [activeTab, data.setSelectedCategory]);

    const handleSortChange = useCallback((sort: SortOption) => {
        setExpandedModernView(null);
        setExpandedModernApps([]);
        setViewAllApps(null);
        setShowAllSorted(false);
        if (settings.storeLayout === 'modern' && sort === SortOption.HOME) {
            restorePrimaryBrowseState();
            return;
        }

        startTransition(() => data.setSelectedSort(activeTab, sort));
    }, [activeTab, data, restorePrimaryBrowseState, settings.storeLayout]);

    const handleToggleFavorites = useCallback(() => {
        triggerHaptic('selection');
        startTransition(() => data.toggleFilterFavorites(activeTab));
    }, [activeTab, data.toggleFilterFavorites, triggerHaptic]);

    const handleOpenSubmissionModal = useCallback(() => {
        setShowSubmissionModal(true);
    }, []);

    useEffect(() => {
        const handler = () => setShowCustomBundleModal(true);
        window.addEventListener('orion:open-custom-bundle' as any, handler);
        return () => window.removeEventListener('orion:open-custom-bundle' as any, handler);
    }, []);

    const handleShowAllSorted = useCallback(() => {
        setExpandedModernView(null);
        setExpandedModernApps([]);
        setReturnToExpandedView(null);
        setViewAllApps(null);
        setShowAllSorted(true);
    }, []);

    const handleShowCollectionAll = useCallback((collection: StoreCollection) => {
        if (collection.id === 'new_updated') {
            const appsToShow = [...platformAppsForTab].reverse().slice(0, 18);
            if (appsToShow.length === 0) {
                setExpandedModernView(null);
                setExpandedModernApps([]);
                setViewAllApps(null);
                setShowAllSorted(true);
                startTransition(() => data.setSelectedSort(activeTab, SortOption.NEWEST));
                return;
            }

            setShowAllSorted(false);
            setViewAllApps(null);
            setExpandedModernView('new_updated');
            setExpandedModernApps(appsToShow);
            setTimeout(() => {
                document.getElementById('root')?.scrollTo({ top: 0, behavior: 'instant' });
            }, 10);
            return;
        }

        let appsIdToShow: AppItem[] = [];
        if (collection.id === 'updates-available') {
            appsIdToShow = availableUpdatesForTab;
        } else {
            appsIdToShow = collection.apps || [];
        }

        setExpandedModernView(null);
        setExpandedModernApps([]);
        setViewAllApps(appsIdToShow);
        setShowAllSorted(true);

        // Asynchronous scroll to prevent layout collision glitch during unmount
        setTimeout(() => {
            document.getElementById('root')?.scrollTo({ top: 0, behavior: 'instant' });
        }, 10);
    }, [activeTab, availableUpdatesForTab, data.setSelectedSort, platformAppsForTab]);

    const handleExpandedNewUpdatedMore = useCallback(() => {
        const appsToShow = [...platformAppsForTab].reverse();
        setReturnToExpandedView(expandedModernView);
        setExpandedModernView(null);
        // Retain apps state or clear it, doesn't matter since back restores it
        setViewAllApps(appsToShow);
        setShowAllSorted(true);
        setTimeout(() => {
            document.getElementById('root')?.scrollTo({ top: 0, behavior: 'instant' });
        }, 10);
        startTransition(() => data.setSelectedSort(activeTab, SortOption.NEWEST));
    }, [activeTab, data.setSelectedSort, platformAppsForTab, expandedModernView]);

    const renderModernSuspenseFallback = () => (
        <div className="relative flex w-full flex-col animate-fade-in pb-12 lg:pb-16">
            <div className="pointer-events-none relative z-20 mb-3 mt-1 w-full">
                <div className="flex gap-3 overflow-hidden -mx-3 px-4 py-6 -my-4">
                    {[...Array(2)].map((_, index) => (
                        <div
                            key={index}
                            className="orion-shadow-frame orion-shadow-frame-strong h-[172px] w-[84vw] max-w-[20rem] flex-shrink-0 rounded-[1.75rem]"
                        >
                            <div className="orion-shadow-surface h-full w-full rounded-[inherit] skeleton-shimmer" />
                        </div>
                    ))}
                </div>
            </div>
            <div className="px-4">
                <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 gap-2 sm:gap-2.5">
                    {[...Array(6)].map((_, index) => (
                        <div key={index} className="orion-shadow-frame rounded-[1.6rem]">
                            <div className="orion-shadow-surface flex flex-col items-center gap-2 rounded-[inherit] bg-card p-3">
                                <div className="h-12 w-12 rounded-2xl skeleton-shimmer" />
                                <div className="h-2.5 w-4/5 rounded-full skeleton-shimmer" />
                                <div className="h-2 w-3/5 rounded-full skeleton-shimmer" />
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );

    const renderAppGrid = (platform: Platform) => {
        const isFullBleedModernHome = settings.storeLayout === 'modern'
            && ((!showAllSorted && shouldShowModernHome) || expandedModernView !== null);
        const shellClassName = isFullBleedModernHome
            ? 'w-full'
            : 'mx-auto w-full max-w-[34rem] px-4 sm:max-w-[48rem] sm:px-6 lg:max-w-[82rem] lg:px-8 2xl:max-w-[94rem]';
        const insetListClassName = '';

        return (
            <div className={shellClassName}>
                <StoreFilters
                    searchQuery={currentTabState.query}
                    setSearchQuery={handleSearchQueryChange}
                    selectedCategory={currentTabState.category}
                    setSelectedCategory={handleFilterCategoryChange}
                    categories={dynamicCategories}
                    selectedSort={sortControlValue}
                    setSelectedSort={handleSortChange}
                    onRefresh={handleReloadApps}
                    isRefreshing={isRefreshing}
                    theme={settings.theme}
                    placeholder={`Search ${platform} apps...`}
                    onAddApp={handleOpenSubmissionModal}
                    submissionCooldown={submissionCooldown}
                    count={visibleAppsForTab.length}
                    showFavorites={currentTabState.filterFavorites}
                    onToggleFavorites={handleToggleFavorites}
                    variant={settings.storeLayout === 'modern' ? 'modern' : 'classic'}
                    onProfileClick={() => {
                        setProfileStatsInitialView('profile');
                        setProfileBadgeSelectionIndex(null);
                        setShowProfileStats(true);
                    }}
                    profileAvatarUrl={settings.userProfile?.avatarUrl}
                    profileInitial={settings.userProfile?.name?.[0] || 'U'}
                />
                {isLoading || isSortTransitioning ? (
                    shouldShowModernHome ? (
                        <Suspense fallback={renderModernSuspenseFallback()}>
                            <ModernHomeSkeleton />
                        </Suspense>
                    ) : (
                        <div className={insetListClassName}>
                            <div className="grid grid-cols-1 gap-4 animate-fade-in md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5">
                                {[...Array(6)].map((_, i) => (
                                    <div key={i} className="orion-shadow-frame relative rounded-3xl">
                                        <div className="orion-shadow-surface relative flex items-center gap-4 rounded-[inherit] bg-card p-4">
                                            <div className="shrink-0 w-16 h-16 rounded-2xl skeleton-shimmer" />
                                            <div className="flex-1 min-w-0 flex flex-col gap-2">
                                                <div className="h-4 w-3/4 rounded-full skeleton-shimmer" />
                                                <div className="h-3 w-1/2 rounded-full skeleton-shimmer" style={{ animationDelay: '0.15s' }} />
                                                <div className="h-5 w-16 rounded-lg skeleton-shimmer" style={{ animationDelay: '0.3s' }} />
                                            </div>
                                            <div className="shrink-0 w-10 h-10 rounded-full skeleton-shimmer" style={{ animationDelay: '0.1s' }} />
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )
                ) : classicAppsForCurrentView.length === 0 ? (
                    <div className="flex flex-col items-center justify-center py-20 text-theme-sub animate-fade-in">
                        <i className={`fas ${currentTabState.filterFavorites ? 'fa-heart-broken' : 'fa-search'} text-5xl mb-4 opacity-10`}></i>
                        <p className="text-lg font-bold">{currentTabState.filterFavorites ? 'No favorites found' : `No ${platform} apps found`}</p>
                        {currentTabState.filterFavorites && <p className="text-xs mt-2 opacity-50">Tap the heart on any app card to add it here.</p>}
                    </div>
                ) : settings.storeLayout === 'modern'
                    && activeModernCollections.length > 0
                    && shouldShowModernHome
                    && !showAllSorted
                    && expandedModernView === null ? (
                    <Suspense fallback={renderModernSuspenseFallback()}>
                        <ModernAppList
                            collections={activeModernCollections}
                            onAppClick={handleAppSelect}
                            onSeeAllCategory={handleCategoryNavigate}
                            onBundleClick={setSelectedBundle}
                            onShowAll={handleShowAllSorted}
                            onShowCollectionAll={handleShowCollectionAll}
                        />
                    </Suspense>
                ) : settings.storeLayout === 'modern'
                    && expandedModernView === 'new_updated'
                    && expandedModernApps.length > 0 ? (
                    <div className={insetListClassName}>
                        <ExpandedNewUpdatedGrid
                            title="New & Updated"
                            apps={expandedModernApps}
                            onAppClick={handleAppSelect}
                            onViewMore={handleExpandedNewUpdatedMore}
                        />
                    </div>
                ) : (
                    <div className={insetListClassName}>
                        <ClassicAppList
                            apps={deferredClassicAppsForCurrentView}
                            onAppClick={handleAppSelect}
                        />
                    </div>
                )}
            </div>
        );
    };

    const forcedStoreCompletedScanResult = forcedStoreScanResult || forcedStoreVtResult || null;
    const forcedStoreSnapshotIsScanComplete = settings.forcedStoreUpdate?.status === 'scan_complete';
    const forcedStoreInlineResultBlockedStatus: ForcedStoreUpdateStatus[] = [
        'checking',
        'downloading',
        'download_failed',
        'byok_required',
        'installing',
        'install_failed',
        'export_ready',
        'installed'
    ];
    const forcedStoreInlineResultEligibleStatus =
        forcedStoreSnapshotIsScanComplete ||
        !forcedStoreInlineResultBlockedStatus.includes(forcedStoreUpdateStatus);
    const shouldShowForcedStoreInlineScanResult =
        !isTestingForcedUpdate &&
        !isForcedUpdateBypassedForSession &&
        forcedStoreInlineResultEligibleStatus &&
        (
            forcedStoreUpdateStatus === 'scan_complete' ||
            forcedStoreVtView === 'results' ||
            forcedStoreSnapshotIsScanComplete ||
            Boolean(forcedStoreCompletedScanResult)
        );

    if (shouldShowForcedStoreInlineScanResult) {
        const activeTargetVersion =
            settings.forcedStoreUpdate?.targetVersion ||
            effectiveRemoteConfig?.latestStoreVersion ||
            effectiveRemoteConfig?.minStoreVersion ||
            '0.0.0';

        return (
            <ForcedStoreInlineScanResult
                currentVersion={CURRENT_STORE_VERSION}
                targetVersion={activeTargetVersion}
                result={forcedStoreCompletedScanResult}
                canInstall={true}
                onInstallNow={handleForcedStoreInstall}
                onOpenReport={handleForcedStoreOpenReport}
            />
        );
    }

    if (shouldBlockWithForcedStoreUpdate || isTestingForcedUpdate) {
        const isTest = isTestingForcedUpdate;
        const activeStatus: ForcedStoreUpdateStatus = isTest
            ? testForcedStatus
            : (forcedStoreUpdateStatus === 'inactive' && hasForcedStoreScanResult ? 'scan_complete' : forcedStoreUpdateStatus);
        const activeProgress = isTest
            ? (FORCED_UPDATE_TEST_PROGRESS[activeStatus] ?? 0)
            : forcedStoreProgress;
        const activeError = isTest ? '' : forcedStoreError;
        const activeStatusText = isTest ? (FORCED_UPDATE_TEST_STATUS_TEXT[activeStatus] || '') : forcedStoreStatusText;
        const activeExportPath = isTest
            ? '/storage/emulated/0/Download/OrionStore_9.9.9.apk'
            : forcedStoreExportPath;
        const activeCanInstall = activeStatus === 'ready_to_scan_or_install' || activeStatus === 'scan_complete';
        const activeCanScan = activeStatus === 'ready_to_scan_or_install' || activeStatus === 'scan_complete';
        const activeIsExported = isTest
            ? activeStatus === 'install_failed'
            : Boolean(settings.forcedStoreUpdate?.exported);
        const activeTargetVersion = isTest
            ? '9.9.9'
            : (settings.forcedStoreUpdate?.targetVersion || effectiveRemoteConfig?.latestStoreVersion || effectiveRemoteConfig?.minStoreVersion || '0.0.0');
        // In test mode, supply a sample scan result so the scan_complete UI can be previewed.
        const activeScanResult = isTest
            ? (activeStatus === 'scan_complete'
                ? { type: 'downloaded-apk' as const, stats: { harmless: 70, malicious: 0, suspicious: 0, undetected: 2, timeout: 0 }, results: {}, source: 'existing-report' as const, permalink: 'https://www.virustotal.com/gui/file/test' }
                : null)
            : (forcedStoreScanResult || forcedStoreVt.result);
        const activeScanProgress = isTest
            ? (activeStatus === 'scanning' ? 42 : 0)
            : forcedStoreVt.scanProgress;
        const activeScanNote = isTest
            ? (activeStatus === 'scanning' ? 'Hashing the APK and querying VirusTotal…' : '')
            : forcedStoreVt.scanNote;

        const noop = () => { };

        return (
            <>
                <ForcedStoreUpdateGate
                    currentVersion={CURRENT_STORE_VERSION}
                    targetVersion={activeTargetVersion}
                    status={activeStatus}
                    progress={activeProgress}
                    statusText={activeStatusText}
                    errorText={activeError}
                    exportPath={activeExportPath}
                    canInstall={activeCanInstall}
                    canScan={activeCanScan}
                    isExported={activeIsExported}
                    apiKey={isTest ? '' : settings.virusTotalApiKey}
                    scanProgress={activeScanProgress}
                    scanResult={activeScanResult}
                    scanNote={activeScanNote}
                    onRetryDownload={isTest ? () => setTestForcedStatus('downloading') : handleForcedStoreRetryDownload}
                    onScanBeforeInstall={isTest ? () => setTestForcedStatus('scanning') : handleForcedStoreScan}
                    onInstallNow={isTest ? () => setTestForcedStatus('installing') : handleForcedStoreInstall}
                    onRetryInstall={isTest ? () => setTestForcedStatus('installing') : handleForcedStoreRetryInstall}
                    onExportApk={isTest ? () => setTestForcedStatus('export_ready') : handleForcedStoreExport}
                    onOpenByokSettings={isTest ? noop : handleForcedStoreByokRedirect}
                    onSaveApiKey={isTest ? noop : handleForcedStoreSaveApiKey}
                    onRevokeApiKey={isTest ? noop : () => useSettingsStore.getState().setVirusTotalApiKey('')}
                    onOpenReport={isTest ? noop : handleForcedStoreOpenReport}
                    showDeveloperBypass={!isTest && useDevConfigStore.getState().enabled}
                    onDeveloperBypass={() => {
                        setIsForcedUpdateBypassedForSession(true);
                        setForcedStoreUpdateStatus('inactive');
                        setForcedStoreStatusText('');
                        setForcedStoreScanResult(null);
                    }}
                />
                {isTest && (
                    <ForcedUpdateDevPanel
                        status={activeStatus}
                        progress={activeProgress}
                        statusText={activeStatusText}
                        onStatusChange={setTestForcedStatus}
                        onClose={() => setIsTestingForcedUpdate(false)}
                    />
                )}
            </>
        );
    }

    if ((effectiveRemoteConfig?.maintenanceMode || settings.localMaintenanceMode) && !bypassMaintenance) {
        return (
            <MaintenanceMode
                maintenanceMessage={effectiveRemoteConfig?.maintenanceMessage}
                socialLinks={socialLinks}
                donation={effectiveRemoteConfig?.donation ?? DEFAULT_DONATION}
                onBypass={() => { setBypassMaintenance(true); triggerHaptic('notification', undefined, NotificationType.Success); }}
                triggerHaptic={triggerHaptic}
                version={CURRENT_STORE_VERSION}
            />
        );
    }

    return (
        <div className="app-shell relative min-h-[100dvh] overflow-x-hidden bg-surface font-sans text-theme-text selection:bg-primary/30">
            <Suspense fallback={null}>
                {showSplashPreview && <SplashScreenPreview />}
            </Suspense>

            {!showSplashPreview && !shouldBlockWithForcedStoreUpdate && (
                <>
                    {!isAnyModalOpen && (
                        <div className="fixed top-28 left-0 right-0 z-[200] pointer-events-none flex flex-col items-center gap-2">
                            {devToast && (<div className="bg-card/95 backdrop-blur-xl border border-theme-border px-6 py-3 rounded-full shadow-2xl animate-slide-up flex items-center gap-3 pointer-events-auto max-w-[90%] ring-1 ring-black/5 dark:ring-white/10"><i className={`fas ${settings.isDevUnlocked ? 'fa-check-circle text-green-500' : 'fa-info-circle text-primary'}`}></i><span className="text-sm font-bold text-theme-text truncate">{devToast}</span></div>)}
                            {showErrorToast && (<div className="bg-red-500 text-white px-6 py-3 rounded-full shadow-2xl animate-slide-up flex items-center gap-3 pointer-events-auto max-w-[90%] border border-red-400/50"><i className="fas fa-exclamation-circle text-lg animate-pulse"></i><div className="flex flex-col"><span className="text-xs font-black uppercase tracking-wider opacity-80">Error</span><span className="text-sm font-bold leading-tight">{errorMsg}</span></div><button onClick={() => setShowErrorToast(false)} className="ml-2 w-6 h-6 rounded-full bg-white/20 flex items-center justify-center hover:bg-white/30 transition-colors"><i className="fas fa-times text-xs"></i></button></div>)}
                            {showInstallToast && !showErrorToast && (
                                <div className="bg-card/95 backdrop-blur-xl border border-primary/30 px-2 py-2 pr-4 rounded-full shadow-2xl animate-slide-up flex items-center gap-3 pointer-events-auto max-w-[90%] ring-1 ring-black/5 dark:ring-white/10">
                                    <img src={showInstallToast.app.icon} className="w-10 h-10 rounded-full bg-surface border border-theme-border p-0.5 object-cover" alt="" />
                                    <div className="flex flex-col min-w-[120px]"><span className="text-[10px] font-bold text-primary uppercase tracking-wider">Ready to Install</span><span className="text-sm font-bold text-theme-text truncate max-w-[150px]">{showInstallToast.app.name}</span></div>
                                    <div className="h-8 w-px bg-theme-border mx-1"></div>
                                    <button onClick={() => handleInstallFile(showInstallToast.app, showInstallToast.file)} className="bg-primary text-white px-4 py-1.5 rounded-full text-xs font-bold hover:bg-primary/90 transition-colors shadow-lg shadow-primary/20">Install</button>
                                    <button onClick={() => setShowInstallToast(null)} className="w-6 h-6 rounded-full bg-theme-element flex items-center justify-center text-theme-sub hover:text-theme-text transition-colors"><i className="fas fa-times text-xs"></i></button>
                                </div>
                            )}
                        </div>
                    )}

                    {showCleanupPill && !selectedApp && !isAnyModalOpen && (
                        <div className="fixed bottom-28 left-0 right-0 z-[140] pointer-events-none flex justify-center">
                            <div className="bg-card/95 backdrop-blur-xl border border-theme-border p-2 pr-3 rounded-full flex items-center gap-3 animate-slide-up pointer-events-auto cursor-pointer hover:scale-105 transition-transform ring-1 ring-black/5 dark:ring-white/10 relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow-lg before:shadow-black/20" onClick={handleBatchCleanup}>
                                <div className="w-10 h-10 rounded-full bg-acid text-black flex items-center justify-center shrink-0 shadow-lg shadow-acid/30"><i className="fas fa-broom animate-pulse-slow"></i></div>
                                <div className="flex flex-col mr-2"><span className="text-sm font-black text-theme-text leading-none">{pendingCleanupCount} Files</span><span className="text-[9px] text-theme-sub font-bold uppercase tracking-wider leading-tight">Tap to Clean</span></div>
                                <div className="w-6 h-6 rounded-full bg-theme-element flex items-center justify-center"><i className="fas fa-arrow-right text-xs text-theme-sub"></i></div>
                            </div>
                        </div>
                    )}

                    <div className="w-full max-w-[1400px] mx-auto">
                    <Header
                        onTitleClick={handleTitleClick}
                        storeUpdateAvailable={storeUpdateAvailable}
                        onUpdateStore={() => { }}
                        theme={settings.theme}
                        toggleTheme={toggleTheme}
                        activeTab={activeTab}
                        onOpenSettings={handleOpenSettings}
                        onOpenSettingsPreload={handleOpenSettingsPreload}
                        onOpenReleaseNotes={handleOpenReleaseNotes}
                        updateCount={updateCount}
                        activeDownloadCount={activeDownloadCount}
                    />

                    {isStartupUiReady && effectiveRemoteConfig?.announcement && !isAnnouncementDismissed && activeTab !== 'about' && (
                        <div className="px-3 mb-2 animate-fade-in w-full">
                            <div className={`relative group border-2 border-blue-500/40 rounded-[2rem] p-4 flex items-center gap-4 group ${settings.theme === 'light' ? 'bg-blue-600/10' : 'bg-blue-600/15'} isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-blue-500/20`}>
                                <div className="absolute inset-0 bg-gradient-to-r from-blue-500/10 via-indigo-500/5 to-blue-500/10 opacity-70 animate-shine bg-[length:200%_100%] pointer-events-none"></div>
                                <div className="shrink-0 w-11 h-11 rounded-2xl bg-blue-500 text-white flex items-center justify-center text-xl shadow-lg shadow-blue-500/30 transform -rotate-3 group-hover:rotate-0 transition-transform"><i className="fas fa-bullhorn"></i></div>
                                <div className="flex-1 min-w-0 text-left"><p className={`text-xs font-black leading-relaxed ${settings.theme === 'light' ? 'text-blue-800' : 'text-blue-300'}`}>{effectiveRemoteConfig.announcement}</p></div>
                                <button onClick={() => { const hash = getStringHash(effectiveRemoteConfig.announcement || ''); localStorage.setItem('dismissed_announcement_hash', String(hash)); setIsAnnouncementDismissed(true); triggerHaptic('selection'); }} className={`shrink-0 w-8 h-8 rounded-full bg-blue-500/20 flex items-center justify-center hover:bg-red-500 hover:text-white transition-all shadow-sm ${settings.theme === 'light' ? 'text-blue-700' : 'text-blue-300'}`}><i className="fas fa-times text-xs"></i></button>
                            </div>
                        </div>
                    )}

                    <main ref={appListScrollRef} className="relative w-full min-h-[50vh] pb-[calc(env(safe-area-inset-bottom,0px)+6.5rem)]">
                        <div key={activeTab} className="animate-tab-enter">
                            {activeTab === 'android' && renderAppGrid(Platform.ANDROID)}
                            {activeTab === 'pc' && renderAppGrid(Platform.PC)}
                            {activeTab === 'tv' && renderAppGrid(Platform.TV)}
                            {activeTab === 'about' && (
                                <Suspense fallback={<div className="flex justify-center p-12"><div className="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin"></div></div>}>
                                    <AboutTabContainer
                                        devProfile={devProfile}
                                        socialLinks={socialLinks}
                                        donation={effectiveRemoteConfig?.donation ?? DEFAULT_DONATION}
                                        faqs={faqs}
                                        handleProfileClick={handleAboutProfileClick}
                                        setShowFAQ={setShowFAQ}
                                        onOpenAdDonation={() => setShowAdDonation(true)}
                                        currentStoreVersion={CURRENT_STORE_VERSION}
                                        onWipeCache={() => { localStorage.clear(); window.location.reload(); }}
                                        mirrorSource={mirrorSource}
                                        availableUpdates={availableUpdates}
                                        onTriggerUpdate={(app) => handleDownloadAction(app)}
                                        onTriggerDebugToast={(type) => {
                                            if (type === 'install') {
                                                const fallbackApp = allKnownApps[0];
                                                if (fallbackApp) setShowInstallToast({ app: fallbackApp, file: 'test.apk' });
                                            }
                                            if (type === 'error') {
                                                setShowErrorToast(true);
                                                setErrorMsg("This is a test error message for alignment checking.");
                                            }
                                            if (type === 'cleanup') {
                                                data.setPendingCleanup({
                                                    'test-1': { fileName: 'a', timestamp: Date.now() },
                                                    'test-2': { fileName: 'b', timestamp: Date.now() },
                                                    'test-3': { fileName: 'c', timestamp: Date.now() }
                                                });
                                            }
                                        }}
                                        onTriggerModernUITutorial={() => {
                                            settings.setHasSeenModernUITutorial(false);
                                            setShowModernUITutorial(true);
                                        }}
                                        onReloadApps={handleReloadApps}
                                        onTriggerForcedUpdateTest={() => setIsTestingForcedUpdate(true)}
                                    />
                                </Suspense>
                            )}
                        </div>
                        <div className="pointer-events-none flex justify-center px-4 pb-1 pt-4">
                            <div className="relative flex h-10 w-20 items-center justify-center">
                                <div className="absolute inset-x-4 inset-y-2 rounded-full bg-primary/10 blur-xl dark:bg-primary/15"></div>
                                <i className="fas fa-infinity text-[1.4rem] text-primary/50 animate-pulse-slow [filter:drop-shadow(0_0_8px_rgba(99,102,241,0.16))]"></i>
                            </div>
                        </div>
                    </main>

                    <button
                        onClick={scrollToTop}
                        style={{ bottom: 'calc(env(safe-area-inset-bottom) + 5.15rem)', right: 'calc(env(safe-area-inset-right) + 0.9rem)' }}
                        className={`fixed z-30 flex aspect-square h-[clamp(2.85rem,9vw,3.1rem)] items-center justify-center overflow-hidden rounded-[1.15rem] bg-primary text-white shadow-xl shadow-primary/30 transition-all duration-500 transform active:scale-90 hover:scale-110 ${showScrollTop ? 'translate-y-0 scale-100 opacity-100' : 'translate-y-10 scale-75 opacity-0 pointer-events-none'}`}
                    >
                        <svg
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="2.4"
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            className="h-[clamp(1rem,3.4vw,1.15rem)] w-[clamp(1rem,3.4vw,1.15rem)]"
                            aria-hidden="true"
                        >
                            <path d="M12 18V6"></path>
                            <path d="m7 11 5-5 5 5"></path>
                        </svg>
                    </button>

                    </div>
                    <BottomNav activeTab={activeTab} onTabChange={handleBottomNavChange} hiddenTabs={settings.hiddenTabs} glassEffect={settings.glassEffect} />

                    <Suspense fallback={null}>
                        {selectedBundle && (
                            <BundlePreviewModal
                                bundle={selectedBundle}
                                onClose={() => setSelectedBundle(null)}
                                onOpenApp={(app) => {
                                    setSelectedApp(app);
                                    if (app.packageName && Capacitor.isNativePlatform()) syncSpecificApp(app);
                                }}
                                onDownloadAll={handleBundleDownload}
                                allApps={allKnownApps}
                                platform={targetPlatform}
                            />
                        )}
                        {selectedApp && (
                            <SelectedAppModalContainer
                                app={selectedApp}
                                onClose={() => setSelectedApp(null)}
                                onDownload={handleDownloadAction}
                                isInstalling={installingId === selectedApp.id}
                                supportEmail={supportEmail}
                                onCancelDownload={handleCancelDownload}
                                onNavigateToApp={handleNavigateToApp}
                                onDeleteReadyFile={handleDeleteReadyFile}
                                onExportAPK={handleExportAPK}
                                isScanning={scanningId === selectedApp.id}
                                onVirusTotalScan={() => setVtScanTarget(selectedApp)}
                            />
                        )}
                        {vtScanTarget && (
                            <VirusTotalScanModal
                                app={vtScanTarget}
                                onClose={() => setVtScanTarget(null)}
                            />
                        )}
                        {showFAQ && <FAQModal onClose={() => setShowFAQ(false)} items={faqs} />}
                        {showNotice && effectiveRemoteConfig?.notice && <NoticeModal title={effectiveRemoteConfig.notice.title} message={effectiveRemoteConfig.notice.message} onClose={handleDismissNotice} />}
                        {showModernUITutorial && <ModernUITutorial onOpenSettings={() => { preloadSettingsModal(); setShowSettingsModal(true); }} onClose={() => setShowModernUITutorial(false)} />}
                        {showReleaseNotes && <ReleaseNotesModal onClose={() => setShowReleaseNotes(false)} />}
                        {showSettingsModal && (
                            <SettingsModal
                                onClose={() => setShowSettingsModal(false)}
                                allApps={[...data.apps, ...data.importedApps]}
                                availableUpdates={availableUpdates}
                                onTriggerUpdate={(app: AppItem) => handleDownloadAction(app)}
                                onInstallApp={handleInstallFile}
                                onCancelDownloadById={handleCancelDownloadById}
                                installingId={installingId}
                                onUpdateAll={handleBatchInstall}
                                onNavigateToApp={handleNavigateToAppFromSettings}
                                onReloadDevConfig={() => { void loadApps(true); }}
                                initialMenu={settingsInitialMenu}
                            />
                        )}
                        {showSubmissionModal && <SubmissionModal onClose={() => setShowSubmissionModal(false)} currentStoreVersion={CURRENT_STORE_VERSION} onSuccess={settings.registerSubmission} submissionCount={settings.submissionCount} activeTab={activeTab} />}
                        {showCustomBundleModal && <CustomBundleModal onClose={() => setShowCustomBundleModal(false)} allApps={allKnownApps} platform={targetPlatform} />}
                        {showAdDonation && <AdDonationModal onClose={() => setShowAdDonation(false)} onSuccess={settings.incrementAdWatch} currentStreak={settings.adWatchCount} />}
                        {showProfileStats && (
                            <ProfileStatsModal
                                onClose={() => {
                                    setShowProfileStats(false);
                                    setProfileBadgeSelectionIndex(null);
                                }}
                                initialView={profileStatsInitialView}
                                badgeActionMode={profileStatsInitialView === 'badges' ? 'select' : 'browse'}
                                selectionIndex={profileBadgeSelectionIndex}
                            />
                        )}

                    </Suspense>
                    <SpotlightOverlay />
                </>
            )}
        </div>
    );
};

interface ForcedStoreInlineScanResultProps {
    currentVersion: string;
    targetVersion: string;
    result: VirusTotalResult | null;
    canInstall: boolean;
    onInstallNow: () => void;
    onOpenReport: () => void;
}

const readForcedStoreScanStat = (value: unknown) => (
    typeof value === 'number' && Number.isFinite(value) ? value : 0
);

const ForcedStoreInlineScanResult: React.FC<ForcedStoreInlineScanResultProps> = ({
    currentVersion,
    targetVersion,
    result,
    canInstall,
    onInstallNow,
    onOpenReport
}) => {
    const stats = result?.stats ?? {};
    const malicious = readForcedStoreScanStat(stats.malicious);
    const suspicious = readForcedStoreScanStat(stats.suspicious);
    const harmless = readForcedStoreScanStat(stats.harmless);
    const undetected = readForcedStoreScanStat(stats.undetected);
    const timeout = readForcedStoreScanStat(stats.timeout);
    const flagged = malicious + suspicious;
    const total = harmless + malicious + suspicious + undetected + timeout;
    const hasReport = Boolean(result?.permalink);
    const isClean = flagged === 0;
    const summary = total > 0
        ? `${flagged} of ${total} VirusTotal engines flagged this update.`
        : 'VirusTotal finished scanning this update. You can continue installing.';

    return (
        <main
            className="fixed inset-0 z-[260] flex overflow-y-auto bg-surface px-6 text-theme-text"
            aria-live="polite"
            style={{
                paddingBottom: 'calc(1.5rem + env(safe-area-inset-bottom))',
                paddingTop: 'calc(1.5rem + env(safe-area-inset-top))'
            }}
        >
            <section className="m-auto flex w-full max-w-[440px] flex-col gap-6 py-4">
                <div className={`grid h-20 w-20 place-items-center rounded-[1.6rem] ${isClean ? 'bg-emerald-500/12 text-emerald-500' : 'bg-amber-500/12 text-amber-500'}`}>
                    <i className={`fas ${isClean ? 'fa-circle-check' : 'fa-triangle-exclamation'} text-3xl`} aria-hidden="true" />
                </div>

                <div className="flex flex-col gap-3">
                    <h1 className="m-0 text-[2rem] font-bold leading-[1.05] tracking-tight text-theme-text">
                        {isClean ? 'Scan complete' : 'Scan complete with caution'}
                    </h1>
                    <p className="m-0 max-w-[40ch] text-[0.95rem] leading-relaxed text-theme-sub">
                        {summary}
                    </p>
                    <p className="m-0 font-mono text-xs text-theme-sub">
                        v{currentVersion} <span className="text-theme-sub/60">-&gt;</span> v{targetVersion}
                    </p>
                </div>

                <div className={`flex items-center gap-2.5 rounded-2xl px-4 py-3 text-sm font-semibold ${isClean ? 'bg-emerald-500/10 text-emerald-500' : 'bg-amber-500/10 text-amber-500'}`}>
                    <i className={`fas ${isClean ? 'fa-circle-check' : 'fa-shield-halved'}`} aria-hidden="true" />
                    <span>{isClean ? 'No engines flagged the APK' : 'Review the report before installing'}</span>
                </div>

                <div className="grid grid-cols-3 gap-2">
                    <div className="rounded-2xl bg-theme-element px-3 py-3 text-center">
                        <p className="m-0 text-lg font-black text-red-500">{flagged}</p>
                        <p className="m-0 text-[10px] font-bold uppercase tracking-wider text-theme-sub">Flagged</p>
                    </div>
                    <div className="rounded-2xl bg-theme-element px-3 py-3 text-center">
                        <p className="m-0 text-lg font-black text-emerald-500">{harmless}</p>
                        <p className="m-0 text-[10px] font-bold uppercase tracking-wider text-theme-sub">Clean</p>
                    </div>
                    <div className="rounded-2xl bg-theme-element px-3 py-3 text-center">
                        <p className="m-0 text-lg font-black text-theme-text">{total}</p>
                        <p className="m-0 text-[10px] font-bold uppercase tracking-wider text-theme-sub">Engines</p>
                    </div>
                </div>

                <div className="mt-1 flex flex-col gap-2.5">
                    {hasReport && (
                        <button
                            type="button"
                            onClick={onOpenReport}
                            className="inline-flex min-h-[52px] w-full items-center justify-center gap-2.5 rounded-2xl bg-theme-element px-5 text-[0.95rem] font-bold tracking-tight text-theme-text transition-colors duration-200 hover:bg-theme-hover focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                        >
                            <i className="fas fa-arrow-up-right-from-square text-sm" aria-hidden="true" />
                            <span>Open VirusTotal report</span>
                        </button>
                    )}
                    {canInstall && (
                        <button
                            type="button"
                            onClick={onInstallNow}
                            className="inline-flex min-h-[52px] w-full items-center justify-center gap-2.5 rounded-2xl bg-primary px-5 text-[0.95rem] font-bold tracking-tight text-white shadow-lg shadow-primary/20 transition-colors duration-200 hover:brightness-105 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                        >
                            <i className="fas fa-arrow-up-right-from-square text-sm" aria-hidden="true" />
                            <span>Install now</span>
                        </button>
                    )}
                </div>
            </section>
        </main>
    );
};

const FORCED_UPDATE_TEST_STATUSES: { id: ForcedStoreUpdateStatus; label: string }[] = [
    { id: 'checking', label: 'Checking' },
    { id: 'downloading', label: 'Downloading' },
    { id: 'download_failed', label: 'Download failed' },
    { id: 'ready_to_scan_or_install', label: 'Ready' },
    { id: 'byok_required', label: 'BYOK required' },
    { id: 'scanning', label: 'Scanning' },
    { id: 'scan_complete', label: 'Scan complete' },
    { id: 'installing', label: 'Installing' },
    { id: 'install_failed', label: 'Install failed' },
    { id: 'export_ready', label: 'Export ready' },
    { id: 'installed', label: 'Installed' }
];

const FORCED_UPDATE_TEST_PROGRESS: Partial<Record<ForcedStoreUpdateStatus, number>> = {
    checking: 8,
    downloading: 64,
    download_failed: 32,
    scanning: 72,
    installing: 96
};

const FORCED_UPDATE_TEST_STATUS_TEXT: Partial<Record<ForcedStoreUpdateStatus, string>> = {
    checking: 'Checking Orion Store for the newest build.',
    downloading: 'Downloading OrionStore_9.9.9.apk from the update source.',
    download_failed: 'Download interrupted. Retry the transfer or save it later.',
    ready_to_scan_or_install: 'The update package is ready. Choose the next step.',
    byok_required: 'A VirusTotal API key is required before you can scan.',
    scanning: 'Scanning the APK before install.',
    scan_complete: 'The scan completed and the APK is ready to install.',
    installing: 'Android is taking over the installation flow.',
    install_failed: 'The installer paused. Export the APK and install it manually.',
    export_ready: 'The APK is saved to your Downloads folder.',
    installed: 'The update finished successfully.'
};

interface ForcedUpdateDevPanelProps {
    status: ForcedStoreUpdateStatus;
    progress: number;
    statusText: string;
    onStatusChange: (status: ForcedStoreUpdateStatus) => void;
    onClose: () => void;
}

const ForcedUpdateDevPanel: React.FC<ForcedUpdateDevPanelProps> = ({ status, progress, statusText, onStatusChange, onClose }) => {
    const [isExpanded, setIsExpanded] = React.useState(false);

    return (
        <div className="fsug-dev" role="region" aria-label="Forced update dev panel" data-expanded={isExpanded ? 'true' : 'false'}>
            <div className="fsug-dev-head">
                <button
                    type="button"
                    className="fsug-dev-toggle"
                    onClick={() => setIsExpanded((current) => !current)}
                    aria-expanded={isExpanded}
                    aria-controls="forced-update-dev-menu"
                >
                    <span className="fsug-dev-tag">DEV</span>
                    <span className="fsug-dev-title">Forced update preview</span>
                    <span className="fsug-dev-status">
                        <span className="fsug-dev-status-dot" />
                        <span className="fsug-dev-status-text">{status}</span>
                    </span>
                    <span className="fsug-dev-toggle-icon" aria-hidden="true">
                        <i className={`fas ${isExpanded ? 'fa-chevron-up' : 'fa-chevron-down'}`} />
                    </span>
                </button>
                <button type="button" className="fsug-dev-close" onClick={onClose} aria-label="Close preview">
                    <i className="fas fa-xmark" aria-hidden="true" />
                </button>
            </div>
            <div id="forced-update-dev-menu" className="fsug-dev-body" aria-hidden={!isExpanded}>
                <div className="fsug-dev-preview">
                    <div className="fsug-dev-preview-head">
                        <span className="fsug-dev-preview-label">Mock preview</span>
                        <span className="fsug-dev-preview-value">{Math.round(progress)}%</span>
                    </div>
                    <div className="fsug-dev-preview-track" aria-hidden="true">
                        <div className="fsug-dev-preview-fill" style={{ width: `${Math.max(0, Math.min(progress, 100))}%` }} />
                    </div>
                    <p className="fsug-dev-preview-copy">{statusText || 'Preview the current forced update state.'}</p>
                </div>
                <div className="fsug-dev-grid">
                    {FORCED_UPDATE_TEST_STATUSES.map((opt) => (
                        <button
                            key={opt.id}
                            type="button"
                            className={`fsug-dev-chip${opt.id === status ? ' fsug-dev-chip--active' : ''}`}
                            onClick={() => onStatusChange(opt.id)}
                        >
                            {opt.label}
                        </button>
                    ))}
                </div>
            </div>
        </div>
    );
};

export default App;
