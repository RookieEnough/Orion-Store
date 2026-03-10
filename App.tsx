
import React, { useState, useEffect, useCallback, useMemo, useRef, Suspense, lazy } from 'react';
import { App as CapacitorApp } from '@capacitor/app';
import { Capacitor } from '@capacitor/core';
import { LocalNotifications, ActionPerformed } from '@capacitor/local-notifications';
import { Haptics, ImpactStyle, NotificationType } from '@capacitor/haptics';
import { UnityAds } from 'capacitor-unity-ads';
import { DEV_SOCIALS, DEFAULT_FAQS, DEFAULT_DEV_PROFILE, DEFAULT_SUPPORT_EMAIL, DEFAULT_EASTER_EGG, CACHE_VERSION, NETWORK_TIMEOUT_MS } from './constants';
import { Platform, AppItem, Tab, AppVariant, StoreConfig, AppCategory, SortOption, VersionOption } from './types';
import AppCard from './components/AppCard';
import Header from './components/Header';
import BottomNav from './components/BottomNav';
import StoreFilters from './components/StoreFilters';
import { localAppsData } from './localData';
import AppTracker from './plugins/AppTracker';
import { useSettingsStore, useDataStore, CleanupEntry } from './store/useAppStore';

import CoreWorker from './workers/core.worker?worker';

const AppDetail = lazy(() => import('./components/AppDetail'));
const FAQModal = lazy(() => import('./components/FAQModal'));
const AdDonationModal = lazy(() => import('./components/AdDonationModal'));
const AboutView = lazy(() => import('./components/AboutView'));
const SubmissionModal = lazy(() => import('./components/SubmissionModal'));
const SettingsModal = lazy(() => import('./components/SettingsModal'));
const StoreUpdateModal = lazy(() => import('./components/StoreUpdateModal'));
const NoticeModal = lazy(() => import('./components/NoticeModal'));
const SplashScreenPreview = lazy(() => import('./components/SplashScreenPreview'));
const ReleaseNotesModal = lazy(() => import('./components/ReleaseNotesModal'));

const CURRENT_STORE_VERSION = '1.2.3';
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

const sanitizeUrl = (url?: string): string => {
    if (!url) return '#';
    if (url.trim().toLowerCase().startsWith('javascript:')) return '#';
    return url;
};

const compareVersions = (v1: string, v2: string) => {
    if (!v1 || !v2) return 0;
    const clean = (v: string) => v.toLowerCase().replace(/^v/, '').replace(/[^0-9.]/g, '').trim();
    const s1 = clean(v1);
    const s2 = clean(v2);
    if (s1 === s2) return 0;
    const parts1 = s1.split('.').map(Number);
    const parts2 = s2.split('.').map(Number);
    for (let i = 0; i < Math.max(parts1.length, parts2.length); i++) {
        const num1 = parts1[i] || 0;
        const num2 = parts2[i] || 0;
        if (num1 > num2) return 1;
        if (num1 < num2) return -1;
    }
    return 0;
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

const App: React.FC = () => {
    const settings = useSettingsStore();
    const data = useDataStore();
    const workerRef = useRef<Worker | null>(null);

    const [showSplashPreview, setShowSplashPreview] = useState(!Capacitor.isNativePlatform());
    const [activeTab, setActiveTab] = useState<Tab>('android');
    const [selectedApp, setSelectedApp] = useState<AppItem | null>(null);
    const [showScrollTop, setShowScrollTop] = useState(false);
    const [showSettingsModal, setShowSettingsModal] = useState(false);
    const [installingId, setInstallingId] = useState<string | null>(null);
    const [scanningId, setScanningId] = useState<string | null>(null);
    const [showInstallToast, setShowInstallToast] = useState<{ app: AppItem, file: string } | null>(null);
    const [showErrorToast, setShowErrorToast] = useState(false);
    const [errorMsg, setErrorMsg] = useState('Failed to load apps');
    const [profileImgError, setProfileImgError] = useState(false);
    const [showFAQ, setShowFAQ] = useState(false);
    const [showAdDonation, setShowAdDonation] = useState(false);
    const [showSubmissionModal, setShowSubmissionModal] = useState(false);
    const [submissionCooldown, setSubmissionCooldown] = useState<string | null>(null);
    const [storeUpdateAvailable, setStoreUpdateAvailable] = useState(false);
    const [showStoreUpdateModal, setShowStoreUpdateModal] = useState(false);
    const [isTestingUpdate, setIsTestingUpdate] = useState(false);
    const [storeUpdateUrl, setStoreUpdateUrl] = useState('');
    const [devClickCount, setDevClickCount] = useState(0);
    const [devToast, setDevToast] = useState<string | null>(null);
    const [easterEggCount, setEasterEggCount] = useState(0);
    const [isAnnouncementDismissed, setIsAnnouncementDismissed] = useState(false);
    const [isLoading, setIsLoading] = useState(false);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [remoteConfig, setRemoteConfig] = useState<StoreConfig | null>(null);
    const [isEditingToken, setIsEditingToken] = useState(false);
    const [mirrorSource, setMirrorSource] = useState<string>('Checking...');
    const [pendingInstallRetry, setPendingInstallRetry] = useState<{ app: AppItem, file: string } | null>(null);
    const [showCleanupPill, setShowCleanupPill] = useState(false);
    const [showNotice, setShowNotice] = useState(false);
    const [showReleaseNotes, setShowReleaseNotes] = useState(false);
    const [visibleApps, setVisibleApps] = useState<AppItem[]>([]);
    const [bypassMaintenance, setBypassMaintenance] = useState(false);

    const devToastTimer = useRef<any>(null);
    const isMounted = useRef(true);
    const initializingDownloads = useRef<Set<string>>(new Set());
    const installingIdRef = useRef<string | null>(null);
    const waitingForResumeId = useRef<string | null>(null);

    const pendingCleanupCount = Object.keys(data.pendingCleanup).length;
    const isAnyModalOpen = showSettingsModal || showAdDonation || showSubmissionModal || showStoreUpdateModal || showFAQ || showNotice || showReleaseNotes;

    useEffect(() => {
        if (showSplashPreview) {
            const timer = setTimeout(() => {
                if (isMounted.current) setShowSplashPreview(false);
            }, 2500);
            return () => clearTimeout(timer);
        }
    }, [showSplashPreview]);

    const currentTabState = useMemo(() => {
        return data.tabs[activeTab] || { query: '', category: 'All', sort: SortOption.NEWEST, filterFavorites: false };
    }, [data.tabs, activeTab]);

    const performDeepScan = useCallback(async (targetApp: AppItem): Promise<boolean> => {
        if (!Capacitor.isNativePlatform() || !targetApp.packageName) return false;

        let info = { installed: false, version: '' };
        let finalPackageName = '';

        const knownPkg = settings.resolvedPackageNames[targetApp.id];
        if (knownPkg) {
            try {
                const res = await AppTracker.getAppInfo({ packageName: knownPkg });
                if (res.installed) { info = res; finalPackageName = knownPkg; }
            } catch (e) { }
        }

        if (!info.installed) {
            try {
                const res = await AppTracker.getAppInfo({ packageName: targetApp.packageName });
                if (res.installed) { info = res; finalPackageName = targetApp.packageName; }
            } catch (e) { }
        }

        if (!info.installed) {
            const suffixes = ['.preview', '.debug', '.test', '.beta', '.canary', '.dev', '.alpha', '.nightly', '.staging', '.release'];
            for (const suffix of suffixes) {
                const altPkg = targetApp.packageName + suffix;
                if (altPkg === knownPkg) continue;
                try {
                    const res = await AppTracker.getAppInfo({ packageName: altPkg });
                    if (res.installed) {
                        info = res;
                        finalPackageName = altPkg;
                        break;
                    }
                } catch (e) { }
            }
        }

        if (info.installed) {
            const newMap = { ...useSettingsStore.getState().installedVersions, [targetApp.id]: info.version };
            settings.setInstalledVersions(newMap);

            if (finalPackageName && settings.resolvedPackageNames[targetApp.id] !== finalPackageName) {
                settings.setResolvedPackageName(targetApp.id, finalPackageName);
            }
            return true;
        }
        return false;
    }, [settings.resolvedPackageNames, settings.setInstalledVersions, settings.setResolvedPackageName]);

    const startVerificationLoop = useCallback((app: AppItem) => {
        let attempts = 0;
        const verifyInterval = setInterval(async () => {
            attempts++;
            const found = await performDeepScan(app);

            if (found) {
                clearInterval(verifyInterval);
                const currentData = useDataStore.getState();
                const file = currentData.readyToInstall[app.id];

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
            } else if (attempts >= 5) {
                clearInterval(verifyInterval);
                setScanningId(null);
            }
        }, 1000);
    }, [performDeepScan]);

    const syncInstalledApps = useCallback(async () => {
        if (!Capacitor.isNativePlatform()) return;
        const allApps = [...data.apps, ...data.importedApps];
        if (allApps.length === 0) return;

        const currentVersions = { ...useSettingsStore.getState().installedVersions };
        const newReadyToInstall = { ...data.readyToInstall };

        let readyToInstallChanged = false;
        let pendingCleanupChanged = false;
        const updatesForPendingCleanup: Record<string, CleanupEntry> = {};

        await Promise.all(allApps.map(async (app) => {
            if (app.packageName) {
                try {
                    let info = { installed: false, version: '' };
                    const knownPkg = settings.resolvedPackageNames[app.id];
                    if (knownPkg) {
                        try {
                            const knownInfo = await AppTracker.getAppInfo({ packageName: knownPkg });
                            if (knownInfo.installed) info = knownInfo;
                        } catch (e) { }
                    }
                    if (!info.installed) {
                        try {
                            const baseInfo = await AppTracker.getAppInfo({ packageName: app.packageName });
                            if (baseInfo.installed) info = baseInfo;
                        } catch (e) { }
                    }

                    if (info.installed) {
                        currentVersions[app.id] = info.version;

                        if (newReadyToInstall[app.id] && installingIdRef.current !== app.id) {
                            if (compareVersions(info.version, app.latestVersion) >= 0) {
                                const fileName = newReadyToInstall[app.id];
                                delete newReadyToInstall[app.id];
                                readyToInstallChanged = true;
                                if (!data.pendingCleanup[app.id] && fileName) {
                                    updatesForPendingCleanup[app.id] = { fileName, timestamp: Date.now() };
                                    pendingCleanupChanged = true;
                                }
                            }
                        }
                    } else {
                        // Only remove from map if we successfully confirmed uninstalled
                        delete currentVersions[app.id];
                        if (settings.lastRemoteVersions[app.id]) {
                            settings.removeLastRemoteVersion(app.id);
                        }
                    }
                } catch (e) {
                    // On scan failure, keep existing status — don't remove from map
                }
            }
        }));

        settings.setInstalledVersions(currentVersions);
        if (readyToInstallChanged) data.setReadyToInstall(newReadyToInstall);
        if (pendingCleanupChanged) data.setPendingCleanup({ ...data.pendingCleanup, ...updatesForPendingCleanup });

    }, [data.apps, data.importedApps, settings.setInstalledVersions, data.readyToInstall, data.setReadyToInstall, data.pendingCleanup, data.setPendingCleanup, settings.lastRemoteVersions, settings.resolvedPackageNames]);

    const syncInstalledAppsRef = useRef(syncInstalledApps);
    useEffect(() => { syncInstalledAppsRef.current = syncInstalledApps; }, [syncInstalledApps]);

    useEffect(() => {
        const worker = new CoreWorker();
        workerRef.current = worker;
        worker.onmessage = (e) => {
            const { type, payload } = e.data;
            if (type === 'DATA_PROCESSED') {
                data.setApps(payload.apps);
                data.setImportedApps(payload.imported);
                const tabData = data.tabs[activeTab] || { query: '', category: 'All', sort: SortOption.NEWEST };
                worker?.postMessage({
                    type: 'FILTER',
                    payload: {
                        query: tabData.query,
                        category: tabData.category,
                        sort: tabData.sort
                    }
                });
                setIsLoading(false);
                setIsRefreshing(false);
                syncInstalledAppsRef.current();
            }
            else if (type === 'FILTER_RESULTS') {
                setVisibleApps(payload);
            }
        };
        return () => { worker.terminate(); };
    }, []);

    useEffect(() => {
        if (workerRef.current && (data.apps.length > 0 || data.importedApps.length > 0)) {
            workerRef.current.postMessage({
                type: 'FILTER',
                payload: {
                    query: currentTabState.query,
                    category: currentTabState.category,
                    sort: currentTabState.sort
                }
            });
        }
    }, [currentTabState.query, currentTabState.category, currentTabState.sort, data.apps, data.importedApps, activeTab]);

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

    useEffect(() => {
        isMounted.current = true;
        requestPermissions();
        if (Capacitor.isNativePlatform()) {
            try {
                UnityAds.initialize({
                    gameId: UNITY_GAME_ID,
                    testMode: ADS_TEST_MODE,
                }).catch(e => console.error("UnityAds Init Error:", e));
            } catch (e) { }

            const listenerPromise = LocalNotifications.addListener('localNotificationActionPerformed', async (action: ActionPerformed) => {
                const { notification } = action;
                if (notification.extra && notification.extra.appId) {
                    const targetAppId = notification.extra.appId;
                    const targetFileName = notification.extra.fileName;
                    // Read fresh state to avoid stale closure over empty initial data.apps
                    const currentData = useDataStore.getState();
                    const app = currentData.apps.find((a: AppItem) => a.id === targetAppId);
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

            setTimeout(performStartupCleanup, 2000);
            return () => {
                isMounted.current = false;
                listenerPromise.then(handler => handler.remove());
            };
        }
        return () => { isMounted.current = false; };
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

        const poll = async () => {
            const dlKeys = Object.keys(data.activeDownloads);
            for (const appId of dlKeys) {
                const rawVal = data.activeDownloads[appId];
                if (!rawVal) continue;
                const [dlId, _] = rawVal.split('|');
                if (!dlId) continue;

                try {
                    const res = await AppTracker.getDownloadProgress({ downloadId: dlId });
                    const prevProg = data.downloadProgress[appId] || 0;
                    const diff = Math.abs(res.progress - prevProg);

                    if (diff >= 1 || res.progress === 100 || res.status !== data.downloadStatus[appId]) {
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
        const interval = setInterval(poll, 1500);
        return () => clearInterval(interval);
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

        const resumeListener = CapacitorApp.addListener('resume', () => {
            syncDownloads();
            syncInstalledAppsRef.current();
            if (waitingForResumeId.current) {
                const appId = waitingForResumeId.current;
                waitingForResumeId.current = null;
                setInstallingId(null);
                installingIdRef.current = null;
                setScanningId(appId);
                const app = [...data.apps, ...data.importedApps].find(a => a.id === appId);
                if (app) startVerificationLoop(app);
            }
            if (pendingInstallRetry) {
                setTimeout(() => {
                    handleInstallFile(pendingInstallRetry.app, pendingInstallRetry.file);
                    setPendingInstallRetry(null);
                }, 500);
            }
        });
        syncDownloads();
        return () => { resumeListener.then(h => h.remove()); };
    }, [data.activeDownloads, pendingInstallRetry, startVerificationLoop]);

    useEffect(() => {
        const root = document.getElementById('root');
        if (!root) return;
        const handleScroll = () => setShowScrollTop(root.scrollTop > 300);
        root.addEventListener('scroll', handleScroll, { passive: true });
        return () => root.removeEventListener('scroll', handleScroll);
    }, []);

    const scrollToTop = () => {
        const root = document.getElementById('root');
        if (root) {
            root.scrollTo({ top: 0, behavior: 'smooth' });
            triggerHaptic('selection');
        }
    };

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
    }, [settings.disableAnimations, settings.compactMode]);

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
        if (remoteConfig?.announcement) {
            const hash = getStringHash(remoteConfig.announcement);
            const dismissedHash = localStorage.getItem('dismissed_announcement_hash');
            setIsAnnouncementDismissed(dismissedHash === String(hash));
        }
    }, [remoteConfig]);

    useEffect(() => {
        if (remoteConfig?.notice?.show) {
            const dismissedId = localStorage.getItem('dismissed_notice_id');
            if (dismissedId !== remoteConfig.notice.id) {
                setShowNotice(true);
            }
        }
    }, [remoteConfig]);

    const handleDismissNotice = () => {
        setShowNotice(false);
        if (remoteConfig?.notice?.id) {
            localStorage.setItem('dismissed_notice_id', remoteConfig.notice.id);
        }
    };

    useEffect(() => {
        if (!Capacitor.isNativePlatform()) return;
        const handleBack = async () => {
            if (document.body.classList.contains('lightbox-open')) {
                window.dispatchEvent(new Event('orion-close-lightbox'));
                return;
            }
            if (selectedApp) setSelectedApp(null);
            else if (showSettingsModal) setShowSettingsModal(false);
            else if (showReleaseNotes) setShowReleaseNotes(false);
            else if (showFAQ) setShowFAQ(false);
            else if (showSubmissionModal) setShowSubmissionModal(false);
            else if (showAdDonation) setShowAdDonation(false);
            else if (showStoreUpdateModal) setShowStoreUpdateModal(false);
            else if (showNotice) handleDismissNotice();
            else if (activeTab !== 'android') setActiveTab('android');
            else CapacitorApp.exitApp();
        };
        const backListener = CapacitorApp.addListener('backButton', handleBack);
        return () => { backListener.then(h => h.remove()); };
    }, [selectedApp, showSettingsModal, showFAQ, showSubmissionModal, showAdDonation, activeTab, showStoreUpdateModal, showNotice, showReleaseNotes]);

    const handleDownloadStart = useCallback((appId: string, downloadId: string, fileName: string) => {
        data.startDownload(appId, downloadId, fileName);
        triggerHaptic('impact', ImpactStyle.Medium);
    }, [data, triggerHaptic]);

    const handleCancelDownload = useCallback(async (app: AppItem, compositeId: string) => {
        const [dlId] = compositeId.split('|');
        triggerHaptic('impact', ImpactStyle.Medium);
        try {
            if (dlId) await AppTracker.cancelDownload({ downloadId: dlId });
        } catch (e) { } finally {
            data.cancelDownload(app.id);
        }
    }, [data, triggerHaptic]);

    const handleDeleteReadyFile = useCallback(async (app: AppItem, fileName: string) => {
        try {
            await AppTracker.deleteFile({ fileName });
            const newReady = { ...data.readyToInstall };
            delete newReady[app.id];
            data.setReadyToInstall(newReady);
            settings.removeLastRemoteVersion(app.id);
            triggerHaptic('notification', undefined, NotificationType.Success);
        } catch (e) { }
    }, [data, triggerHaptic, settings.removeLastRemoteVersion]);

    const handleInstallFile = async (app: AppItem, fileName: string) => {
        if (!Capacitor.isNativePlatform()) return;
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

            if (app.availableVersions) {
                for (const ver of app.availableVersions) {
                    for (const variant of ver.variants) {
                        if (fileName.includes(ver.version)) {
                            settings.setAppStream(app.id, ver.type);
                            break;
                        }
                    }
                }
            }

            settings.setLastRemoteVersion(app.id, app.latestVersion);

            if (settings.useShizuku) {
                await AppTracker.installPackageShizuku({ fileName });
                setShowInstallToast(null);
                setDevToast("Installed via Shizuku");
                setInstallingId(null);
                installingIdRef.current = null;
                setScanningId(app.id);
                startVerificationLoop(app);
            } else {
                waitingForResumeId.current = app.id;
                await AppTracker.installPackage({ fileName });
                setShowInstallToast(null);
                // Do NOT optimistically mark as installed here — the resume listener + 
                // verification loop will detect actual installation status when the user 
                // returns from the native installer.
            }
        } catch (e: any) {
            setInstallingId(null);
            installingIdRef.current = null;
            setScanningId(null);
            waitingForResumeId.current = null;
            const msg = e?.message || JSON.stringify(e);
            if (msg.includes("CORRUPT") || msg.includes("PARSE_ERROR")) {
                setErrorMsg('File corrupted. Deleting...');
                setShowErrorToast(true);
                handleDeleteReadyFile(app, fileName);
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
            const app = [...data.apps, ...data.importedApps].find(a => a.id === appId);
            const fileName = data.readyToInstall[appId];
            if (app && fileName) {
                await handleInstallFile(app, fileName);
                await new Promise(r => setTimeout(r, 1000));
            }
        }
        setDevToast("All updates completed.");
        triggerHaptic('notification', undefined, NotificationType.Success);
    }, [data.readyToInstall, data.apps, data.importedApps, settings.useShizuku, handleInstallFile, triggerHaptic]);

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

        if (url && app.availableVersions) {
            for (const ver of app.availableVersions) {
                const variant = ver.variants.find(v => v.url === url);
                if (variant) {
                    settings.setAppStream(app.id, ver.type);
                    break;
                }
            }
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

        const safe = sanitizeUrl(targetUrl);
        const isAndroid = app.platform === Platform.ANDROID;
        const isStandardFile = safe.toLowerCase().endsWith('.apk') || safe.toLowerCase().endsWith('.exe') || safe.toLowerCase().endsWith('.zip');

        if (!isStandardFile && !isAndroid && !isAuto) {
            window.open(safe, '_blank');
            initializingDownloads.current.delete(app.id);
            return;
        }

        if (!Capacitor.isNativePlatform()) {
            const newRegistry = { ...settings.installedVersions, [app.id]: app.latestVersion };
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
            const fileName = `${sanitizedName}_${app.latestVersion}.apk`;
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

    const handleDownloadComplete = useCallback((appId: string, success: boolean) => {
        // Read fresh state to avoid stale closure over activeDownloads / apps
        const currentData = useDataStore.getState();
        if (success && isMounted.current) {
            const rawVal = currentData.activeDownloads[appId];
            if (!rawVal) return;
            const [_, fileName] = rawVal.split('|');
            if (fileName) {
                currentData.completeDownload(appId, fileName);
                const app = currentData.apps.find((a: AppItem) => a.id === appId);
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

    const handleBatchCleanup = useCallback(async () => {
        const cleanupIds = Object.keys(data.pendingCleanup);
        if (cleanupIds.length === 0) return;
        triggerHaptic('impact', ImpactStyle.Heavy);
        let successCount = 0;
        const failedEntries: Record<string, typeof data.pendingCleanup[string]> = {};
        for (const appId of cleanupIds) {
            const entry = data.pendingCleanup[appId];
            if (!entry) continue;
            const fileName = typeof entry === 'string' ? entry : entry.fileName;
            if (fileName) {
                try {
                    await AppTracker.deleteFile({ fileName });
                    successCount++;
                } catch (e) {
                    // Keep failed entries so we can try again later
                    failedEntries[appId] = entry;
                }
            }
        }
        // Only clear successfully deleted entries; keep failures
        data.setPendingCleanup(failedEntries);
        if (successCount > 0) {
            triggerHaptic('notification', undefined, NotificationType.Success);
            setDevToast(`Cleaned ${successCount} files`);
        }
    }, [data, triggerHaptic]);

    const loadApps = useCallback(async (isManualRefresh = false) => {
        if (isManualRefresh) { setIsRefreshing(true); triggerHaptic('impact', ImpactStyle.Light); }
        if (data.apps.length === 0) setIsLoading(true);

        try {
            let rawApps: AppItem[] = [];
            let mirrorData: Record<string, any> | null = null;
            let configData: StoreConfig | null = null;

            if (settings.useRemoteJson) {
                const configTs = `?t=${Date.now()}`;
                const appsTs = isManualRefresh ? `?t=${Date.now()}` : '';

                const SOURCES = [
                    { name: 'GitHub', config: CONFIG_URL_PRIMARY, apps: APPS_URL_PRIMARY },
                    { name: 'GitLab', config: CONFIG_URL_GITLAB, apps: APPS_URL_GITLAB },
                    { name: 'Codeberg', config: CONFIG_URL_CODEBERG, apps: APPS_URL_CODEBERG },
                    { name: 'JSDelivr', config: CONFIG_URL_FALLBACK, apps: APPS_URL_FALLBACK }
                ];

                let success = false;

                for (const source of SOURCES) {
                    try {
                        // 1. Fetch Config
                        const configReq = await fetchWithRetry(`${source.config}${configTs}`, { cache: 'no-store' }, 1);
                        if (!configReq.ok) continue;

                        configData = await configReq.json();
                        if (isMounted.current && configData) setRemoteConfig(configData);

                        // Check for Store Update
                        if (configData?.latestStoreVersion && compareVersions(configData.latestStoreVersion, CURRENT_STORE_VERSION) > 0) {
                            if (isMounted.current) {
                                setStoreUpdateAvailable(true);
                                setStoreUpdateUrl(configData.storeDownloadUrl!);
                                if (!sessionStorage.getItem('store_update_notified')) {
                                    setShowStoreUpdateModal(true);
                                    sessionStorage.setItem('store_update_notified', 'true');
                                }
                            }
                        }

                        // 2. Fetch Apps & Mirror Data
                        const activeAppsUrl = configData?.appsJsonUrl || source.apps;
                        const activeMirrorUrl = configData?.mirrorJsonUrl || DEFAULT_MIRROR_JSON;

                        const [appsResponse, mirrorReq] = await Promise.all([
                            fetchWithRetry(`${activeAppsUrl}${appsTs}`, { cache: 'no-store' }, 1),
                            fetchWithRetry(`${activeMirrorUrl}${appsTs}`, {}, 1).catch(() => null)
                        ]);

                        if (appsResponse.ok) {
                            rawApps = await appsResponse.json();
                            if (mirrorReq && mirrorReq.ok) {
                                mirrorData = await mirrorReq.json();
                            }
                            if (isMounted.current) setMirrorSource(source.name);
                            success = true;
                            break; // Stop after first successful source
                        }
                    } catch (e) {
                        console.warn(`Failed to load from ${source.name}`, e);
                    }
                }

                if (!success) {
                    // Final Fallback to Local Data
                    rawApps = localAppsData as unknown as AppItem[];
                    if (isMounted.current) setMirrorSource('Offline (Local)');
                }

            } else {
                rawApps = localAppsData as unknown as AppItem[];
                if (isMounted.current) setMirrorSource('Disabled');
            }
            const currentImported = useDataStore.getState().importedApps;
            if (workerRef.current) {
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
            if (isMounted.current && data.apps.length === 0) {
                setErrorMsg('Failed to load apps');
                setShowErrorToast(true);
                triggerHaptic('notification', undefined, NotificationType.Error);
                setIsLoading(false); setIsRefreshing(false);
            }
        }
    }, [settings.useRemoteJson, settings.githubToken, triggerHaptic]);

    useEffect(() => {
        const timer = setTimeout(() => loadApps(false), 500);
        return () => clearTimeout(timer);
    }, [loadApps]);

    const targetPlatform = useMemo(() => {
        if (activeTab === 'pc') return Platform.PC;
        if (activeTab === 'tv') return Platform.TV;
        return Platform.ANDROID;
    }, [activeTab]);

    const dynamicCategories = useMemo(() => {
        const allApps = [...data.apps, ...data.importedApps];
        const platformApps = allApps.filter(app => app.platform === targetPlatform);
        const cats = new Set<string>(Object.values(AppCategory));
        platformApps.forEach(app => {
            if (app.category) cats.add(app.category);
        });
        return ['All', ...Array.from(cats).sort()];
    }, [data.apps, data.importedApps, targetPlatform]);

    const visibleAppsForTab = useMemo(() => {
        const platformTarget = activeTab === 'pc' ? Platform.PC : activeTab === 'tv' ? Platform.TV : Platform.ANDROID;
        let filtered = visibleApps.filter(a => a.platform === platformTarget);
        if (currentTabState.filterFavorites) {
            filtered = filtered.filter(a => data.favorites.includes(a.id));
        }
        return filtered;
    }, [visibleApps, activeTab, currentTabState.filterFavorites, data.favorites]);

    const availableUpdates = useMemo(() => {
        return [...data.apps, ...data.importedApps].filter(a => {
            const localVer = settings.lastRemoteVersions[a.id] || settings.installedVersions[a.id];
            if (!localVer || localVer === "Installed") return false;
            const preferredStream = settings.appStreams[a.id] || 'Stable';

            let targetVersion = a.latestVersion;
            if (a.availableVersions) {
                const streamVersion = a.availableVersions.find(v => v.type === preferredStream);
                if (streamVersion) {
                    targetVersion = streamVersion.version;
                } else if (preferredStream !== 'Stable') {
                    const stable = a.availableVersions.find(v => v.type === 'Stable');
                    if (stable) targetVersion = stable.version;
                }
            }

            const isUpdate = compareVersions(targetVersion, localVer) > 0;
            if (!isUpdate) return false;

            const ignored = settings.ignoredUpdates[a.id];
            if (ignored) {
                if (ignored.type === 'never') return false;
                if (ignored.type === 'week' && ignored.timestamp && Date.now() - ignored.timestamp < ONE_WEEK_MS) return false;
                if (ignored.type === 'version' && ignored.version === targetVersion) return false;
            }

            return true;
        });
    }, [data.apps, data.importedApps, settings.installedVersions, settings.lastRemoteVersions, settings.appStreams, settings.ignoredUpdates]);

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

    const appCounts = useMemo(() => {
        const all = [...data.apps, ...data.importedApps];
        return {
            android: all.filter(a => a.platform === Platform.ANDROID).length,
            pc: all.filter(a => a.platform === Platform.PC).length,
            tv: all.filter(a => a.platform === Platform.TV).length
        };
    }, [data.apps, data.importedApps]);

    const toggleTheme = () => {
        const newTheme = settings.theme === 'light' ? 'dusk' : settings.theme === 'dusk' ? 'dark' : 'light';
        settings.setTheme(newTheme);
        triggerHaptic('impact', ImpactStyle.Medium);
    };

    const syncSpecificApp = async (appId: string, packageName: string) => {
        if (!Capacitor.isNativePlatform()) return;
        try {
            const info = await AppTracker.getAppInfo({ packageName });
            const currentMap = { ...settings.installedVersions };
            if (info.installed) currentMap[appId] = info.version;
            else {
                delete currentMap[appId];
                if (settings.lastRemoteVersions[appId]) settings.removeLastRemoteVersion(appId);
            }
            settings.setInstalledVersions(currentMap);
        } catch (e) { }
    };

    const renderAppGrid = (platform: Platform) => {
        return (
            <div className="px-6">
                <StoreFilters
                    searchQuery={currentTabState.query}
                    setSearchQuery={(q) => data.setSearchQuery(activeTab, q)}
                    selectedCategory={currentTabState.category}
                    setSelectedCategory={(c) => data.setSelectedCategory(activeTab, c)}
                    categories={dynamicCategories}
                    selectedSort={currentTabState.sort}
                    setSelectedSort={(s) => data.setSelectedSort(activeTab, s)}
                    onRefresh={() => loadApps(true)}
                    isRefreshing={isRefreshing}
                    theme={settings.theme}
                    placeholder={`Search ${platform} apps...`}
                    onAddApp={() => setShowSubmissionModal(true)}
                    submissionCooldown={submissionCooldown}
                    count={appCounts[platform.toLowerCase() as keyof typeof appCounts]}
                    showFavorites={currentTabState.filterFavorites}
                    onToggleFavorites={() => { triggerHaptic('selection'); data.toggleFilterFavorites(activeTab); }}
                />
                {isLoading ? (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 animate-fade-in">
                        {[...Array(6)].map((_, i) => (<div key={i} className="h-24 bg-theme-element animate-pulse rounded-3xl" />))}
                    </div>
                ) : visibleAppsForTab.length === 0 ? (
                    <div className="flex flex-col items-center justify-center py-20 text-theme-sub animate-fade-in">
                        <i className={`fas ${currentTabState.filterFavorites ? 'fa-heart-broken' : 'fa-search'} text-5xl mb-4 opacity-10`}></i>
                        <p className="text-lg font-bold">{currentTabState.filterFavorites ? 'No favorites found' : `No ${platform} apps found`}</p>
                        {currentTabState.filterFavorites && <p className="text-xs mt-2 opacity-50">Tap the heart on any app card to add it here.</p>}
                    </div>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 animate-fade-in">
                        {visibleAppsForTab.map(app => {
                            const localVer = settings.lastRemoteVersions[app.id] || settings.installedVersions[app.id];
                            let hasUpdate = false;
                            if (localVer && localVer !== "Installed") {
                                const preferredStream = settings.appStreams[app.id] || 'Stable';
                                if (app.availableVersions) {
                                    const streamVer = app.availableVersions.find(v => v.type === preferredStream);
                                    if (streamVer) {
                                        hasUpdate = compareVersions(streamVer.version, localVer) > 0;
                                    } else if (preferredStream !== 'Stable') {
                                        const stable = app.availableVersions.find(v => v.type === 'Stable');
                                        if (stable) hasUpdate = compareVersions(stable.version, localVer) > 0;
                                    }
                                } else {
                                    hasUpdate = compareVersions(app.latestVersion, localVer) > 0;
                                }
                            }

                            return (
                                <AppCard
                                    key={app.id}
                                    app={app}
                                    onClick={(a) => {
                                        setSelectedApp(a);
                                        if (a.packageName && Capacitor.isNativePlatform()) syncSpecificApp(a.id, settings.resolvedPackageNames[a.id] || a.packageName);
                                    }}
                                    localVersion={localVer}
                                    hasUpdateNotification={hasUpdate}
                                    isDownloading={!!data.activeDownloads[app.id]} // Pass BOOLEAN only
                                    isReadyToInstall={!!data.readyToInstall[app.id]}
                                />
                            );
                        })}
                    </div>
                )}
            </div>
        );
    };

    if (remoteConfig?.maintenanceMode && !bypassMaintenance) {
        return (
            <div className="min-h-screen w-full flex items-center justify-center p-6 bg-gradient-to-br from-slate-100 to-slate-200 dark:from-gray-900 dark:to-black relative overflow-hidden transition-colors duration-500">
                <div className="absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none">
                    <div className="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] bg-primary/20 rounded-full blur-[120px] animate-pulse-slow"></div>
                    <div className="absolute bottom-[-10%] right-[-10%] w-[50%] h-[50%] bg-acid/20 rounded-full blur-[120px] animate-pulse-slow" style={{ animationDelay: '1s' }}></div>
                </div>
                <div className="relative z-10 bg-surface/60 dark:bg-surface/40 backdrop-blur-2xl border border-white/40 dark:border-white/10 p-10 rounded-[2.5rem] shadow-2xl flex flex-col items-center text-center max-w-sm w-full animate-slide-up ring-1 ring-black/5 dark:ring-white/5">
                    <div className="mb-8 relative"><div className="w-24 h-24 bg-gradient-to-tr from-primary to-primary-light rounded-3xl flex items-center justify-center shadow-xl shadow-primary/30 transform -rotate-6 relative z-10 animate-bounce"><i className="fas fa-wrench text-4xl text-white"></i></div></div>
                    <h1 className="text-3xl font-black text-theme-text mb-3 tracking-tight">Under Maintenance</h1>
                    <p className="text-theme-sub text-base font-medium leading-relaxed mb-8">{remoteConfig.maintenanceMessage || "We're currently tuning the engine to bring you a better experience. Orion Store will be back online shortly."}</p>
                    {settings.isDevUnlocked && (<button onClick={() => { setBypassMaintenance(true); triggerHaptic('notification', undefined, NotificationType.Success); }} className="px-6 py-3 rounded-xl bg-theme-element/80 border border-theme-border text-theme-sub hover:text-primary hover:bg-theme-element transition-all font-bold text-xs uppercase tracking-widest flex items-center gap-2 hover:scale-105 active:scale-95 shadow-sm"><i className="fas fa-user-shield"></i><span>Developer Bypass</span></button>)}
                </div>
            </div>
        );
    }

    const devProfile = remoteConfig?.devProfile || DEFAULT_DEV_PROFILE;
    const supportEmail = remoteConfig?.supportEmail || DEFAULT_SUPPORT_EMAIL;
    const socialLinks = remoteConfig?.socials || DEV_SOCIALS;
    const faqs = remoteConfig?.faqs || DEFAULT_FAQS;
    const easterEggUrl = remoteConfig?.easterEggUrl || DEFAULT_EASTER_EGG;

    const getCleanupFileName = (appId: string) => {
        const entry = data.pendingCleanup[appId];
        if (!entry) return undefined;
        return typeof entry === 'string' ? entry : entry.fileName;
    };

    return (
        <div className="min-h-screen bg-surface text-theme-text transition-colors duration-300 font-sans selection:bg-primary/30 relative overflow-x-hidden">
            <Suspense fallback={null}>
                {showSplashPreview && <SplashScreenPreview />}
            </Suspense>

            {!showSplashPreview && (
                <>
                    {!isAnyModalOpen && (
                        <div className="fixed top-28 left-0 right-0 z-[200] pointer-events-none flex flex-col items-center gap-2">
                            {devToast && (<div className="bg-card/95 backdrop-blur-xl border border-theme-border px-6 py-3 rounded-full shadow-2xl animate-slide-up flex items-center gap-3 pointer-events-auto max-w-[90%] ring-1 ring-black/5 dark:ring-white/10"><i className={`fas ${settings.isDevUnlocked ? 'fa-check-circle text-green-500' : 'fa-info-circle text-primary'}`}></i><span className="text-sm font-bold text-theme-text truncate">{devToast}</span></div>)}
                            {showErrorToast && (<div className="bg-red-500 text-white px-6 py-3 rounded-full shadow-2xl animate-slide-up flex items-center gap-3 pointer-events-auto max-w-[90%] border border-red-400/50"><i className="fas fa-exclamation-circle text-lg animate-pulse"></i><div className="flex flex-col"><span className="text-xs font-black uppercase tracking-wider opacity-80">Error</span><span className="text-sm font-bold leading-tight">{errorMsg}</span></div><button onClick={() => setShowErrorToast(false)} className="ml-2 w-6 h-6 rounded-full bg-white/20 flex items-center justify-center hover:bg-white/30 transition-colors"><i className="fas fa-times text-xs"></i></button></div>)}
                            {showInstallToast && !showErrorToast && (!selectedApp || selectedApp.id !== showInstallToast.app.id) && (
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
                            <div className="bg-card/95 backdrop-blur-xl border border-theme-border p-2 pr-3 rounded-full shadow-2xl shadow-black/20 flex items-center gap-3 animate-slide-up pointer-events-auto cursor-pointer hover:scale-105 transition-transform ring-1 ring-black/5 dark:ring-white/10" onClick={handleBatchCleanup}>
                                <div className="w-10 h-10 rounded-full bg-acid text-black flex items-center justify-center shrink-0 shadow-lg shadow-acid/30"><i className="fas fa-broom animate-pulse-slow"></i></div>
                                <div className="flex flex-col mr-2"><span className="text-sm font-black text-theme-text leading-none">{pendingCleanupCount} Files</span><span className="text-[9px] text-theme-sub font-bold uppercase tracking-wider leading-tight">Tap to Clean</span></div>
                                <div className="w-6 h-6 rounded-full bg-theme-element flex items-center justify-center"><i className="fas fa-arrow-right text-xs text-theme-sub"></i></div>
                            </div>
                        </div>
                    )}

                    <Header
                        onTitleClick={() => { if (settings.isDevUnlocked) { setDevToast("Already a developer."); return; } const newCount = devClickCount + 1; setDevClickCount(newCount); const stepsNeeded = 7; const remaining = stepsNeeded - newCount; if (remaining > 0 && remaining <= 4) { setDevToast(`You are ${remaining} steps away from being a developer.`); triggerHaptic('impact', ImpactStyle.Light); } if (newCount >= stepsNeeded) { settings.setDevUnlocked(true); setDevToast("You are now a developer!"); triggerHaptic('notification', undefined, NotificationType.Success); setDevClickCount(0); } }}
                        storeUpdateAvailable={storeUpdateAvailable}
                        onUpdateStore={() => setShowStoreUpdateModal(true)}
                        theme={settings.theme}
                        toggleTheme={toggleTheme}
                        activeTab={activeTab}
                        onOpenSettings={() => setShowSettingsModal(true)}
                        onOpenReleaseNotes={() => setShowReleaseNotes(true)}
                        updateCount={updateCount}
                        activeDownloadCount={Object.keys(data.activeDownloads).length}
                    />

                    {remoteConfig?.announcement && !isAnnouncementDismissed && activeTab !== 'about' && (
                        <div className="px-6 mb-2 animate-fade-in max-w-7xl mx-auto w-full">
                            <div className={`relative group overflow-hidden border-2 border-blue-500/40 rounded-[2rem] p-4 flex items-center gap-4 shadow-lg shadow-blue-500/5 group ${settings.theme === 'light' ? 'bg-blue-600/10' : 'bg-blue-600/15'}`}>
                                <div className="absolute inset-0 bg-gradient-to-r from-blue-500/10 via-indigo-500/5 to-blue-500/10 opacity-70 animate-shine bg-[length:200%_100%] pointer-events-none"></div>
                                <div className="shrink-0 w-11 h-11 rounded-2xl bg-blue-500 text-white flex items-center justify-center text-xl shadow-lg shadow-blue-500/30 transform -rotate-3 group-hover:rotate-0 transition-transform"><i className="fas fa-bullhorn animate-pulse"></i></div>
                                <div className="flex-1 min-w-0 text-left"><p className={`text-xs font-black leading-relaxed ${settings.theme === 'light' ? 'text-blue-800' : 'text-blue-300'}`}>{remoteConfig.announcement}</p></div>
                                <button onClick={() => { const hash = getStringHash(remoteConfig.announcement || ''); localStorage.setItem('dismissed_announcement_hash', String(hash)); setIsAnnouncementDismissed(true); triggerHaptic('selection'); }} className={`shrink-0 w-8 h-8 rounded-full bg-blue-500/20 flex items-center justify-center hover:bg-red-500 hover:text-white transition-all shadow-sm ${settings.theme === 'light' ? 'text-blue-700' : 'text-blue-300'}`}><i className="fas fa-times text-xs"></i></button>
                            </div>
                        </div>
                    )}

                    <main className="max-w-7xl mx-auto w-full pb-28 min-h-[50vh]">
                        <div key={activeTab} className="animate-tab-enter">
                            {activeTab === 'android' && renderAppGrid(Platform.ANDROID)}
                            {activeTab === 'pc' && renderAppGrid(Platform.PC)}
                            {activeTab === 'tv' && renderAppGrid(Platform.TV)}
                            {activeTab === 'about' && (
                                <Suspense fallback={<div className="flex justify-center p-12"><div className="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin"></div></div>}>
                                    <AboutView devProfile={devProfile} socialLinks={socialLinks} faqs={faqs} isLegend={settings.isLegend} isContributor={settings.isContributor} adWatchCount={settings.adWatchCount} profileImgError={profileImgError} setProfileImgError={setProfileImgError} handleProfileClick={() => { const newCount = easterEggCount + 1; setEasterEggCount(newCount); if (newCount >= 7) { window.open(easterEggUrl, '_blank'); setEasterEggCount(0); settings.incrementAdWatch(); settings.setIsLegend(true); triggerHaptic('notification', undefined, NotificationType.Success); } else { triggerHaptic('impact', ImpactStyle.Light); } }} setShowFAQ={setShowFAQ} onOpenAdDonation={() => setShowAdDonation(true)} isDevUnlocked={settings.isDevUnlocked} setDevUnlocked={settings.setDevUnlocked} useRemoteJson={settings.useRemoteJson} toggleSourceMode={() => { settings.setUseRemoteJson(!settings.useRemoteJson); triggerHaptic('selection'); }} githubToken={settings.githubToken} isEditingToken={isEditingToken} setIsEditingToken={setIsEditingToken} saveGithubToken={(t) => { settings.setGithubToken(t); setIsEditingToken(false); triggerHaptic('notification', undefined, NotificationType.Success); setTimeout(() => loadApps(true), 500); }} currentStoreVersion={CURRENT_STORE_VERSION} onWipeCache={() => { localStorage.clear(); window.location.reload(); }} onTestStoreUpdate={() => { setIsTestingUpdate(true); setShowStoreUpdateModal(true); triggerHaptic('impact', ImpactStyle.Medium); }} mirrorSource={mirrorSource} hiddenTabs={settings.hiddenTabs} toggleHiddenTab={settings.toggleHiddenTab} autoUpdateEnabled={settings.autoUpdateEnabled} toggleAutoUpdate={settings.toggleAutoUpdate} availableUpdates={availableUpdates} onTriggerUpdate={(app) => handleDownloadAction(app)} onTriggerDebugToast={(type) => { if (type === 'install') setShowInstallToast({ app: data.apps[0] || (localAppsData[0] as unknown as AppItem), file: 'test.apk' }); if (type === 'error') { setShowErrorToast(true); setErrorMsg("This is a test error message for alignment checking."); } if (type === 'cleanup') data.setPendingCleanup({ 'test-1': { fileName: 'a', timestamp: Date.now() }, 'test-2': { fileName: 'b', timestamp: Date.now() }, 'test-3': { fileName: 'c', timestamp: Date.now() } }); }} />
                                </Suspense>
                            )}
                        </div>
                    </main>

                    <button onClick={scrollToTop} className={`fixed bottom-24 right-6 z-30 w-12 h-12 rounded-2xl bg-primary text-white shadow-xl shadow-primary/30 flex items-center justify-center transition-all duration-500 transform hover:scale-110 active:scale-90 ${showScrollTop ? 'translate-y-0 opacity-100 scale-100' : 'translate-y-10 opacity-0 scale-75 pointer-events-none'}`}><i className="fas fa-arrow-up"></i></button>

                    <BottomNav activeTab={activeTab} onTabChange={(t) => { if (t !== 'about' && settings.hiddenTabs.includes(t)) return; triggerHaptic('impact', ImpactStyle.Light); setActiveTab(t); scrollToTop(); }} hiddenTabs={settings.hiddenTabs} />

                    <Suspense fallback={null}>
                        {selectedApp && (
                            <AppDetail
                                app={{
                                    ...selectedApp,
                                    packageName: settings.resolvedPackageNames[selectedApp.id] || selectedApp.packageName
                                }}
                                onClose={() => setSelectedApp(null)}
                                onDownload={handleDownloadAction}
                                isInstalling={installingId === selectedApp.id}
                                localVersion={settings.lastRemoteVersions[selectedApp.id] || settings.installedVersions[selectedApp.id]}
                                supportEmail={supportEmail}
                                isUpdateAvailable={!!(settings.lastRemoteVersions[selectedApp.id] || settings.installedVersions[selectedApp.id]) && (settings.lastRemoteVersions[selectedApp.id] || settings.installedVersions[selectedApp.id]) !== "Installed" && compareVersions(selectedApp.latestVersion, settings.lastRemoteVersions[selectedApp.id] || settings.installedVersions[selectedApp.id] || '') > 0}
                                activeDownloadId={data.activeDownloads[selectedApp.id]}
                                cleanupFileName={getCleanupFileName(selectedApp.id)}
                                onCleanupDone={() => { const newCleanup = { ...data.pendingCleanup }; delete newCleanup[selectedApp.id]; data.setPendingCleanup(newCleanup); }}
                                currentProgress={data.downloadProgress[selectedApp.id]}
                                currentStatus={data.downloadStatus[selectedApp.id]}
                                readyFileName={data.readyToInstall[selectedApp.id]}
                                onCancelDownload={handleCancelDownload}
                                onNavigateToApp={(appId) => { const target = data.apps.find((a: AppItem) => a.id === appId); if (target) setSelectedApp(target); }}
                                onDeleteReadyFile={handleDeleteReadyFile}
                                onExportAPK={handleExportAPK}
                                isScanning={scanningId === selectedApp.id}
                            />
                        )}
                        {showFAQ && <FAQModal onClose={() => setShowFAQ(false)} items={faqs} />}
                        {showNotice && remoteConfig?.notice && <NoticeModal title={remoteConfig.notice.title} message={remoteConfig.notice.message} onClose={handleDismissNotice} />}
                        {showReleaseNotes && <ReleaseNotesModal onClose={() => setShowReleaseNotes(false)} />}
                        {showSettingsModal && (
                            <SettingsModal
                                onClose={() => setShowSettingsModal(false)}
                                allApps={[...data.apps, ...data.importedApps]}
                                availableUpdates={availableUpdates}
                                onTriggerUpdate={(app: AppItem) => handleDownloadAction(app)}
                                onInstallApp={handleInstallFile}
                                onCancelDownload={handleCancelDownload}
                                installingId={installingId}
                                onUpdateAll={handleBatchInstall}
                                onNavigateToApp={(appId: string) => { setShowSettingsModal(false); const target = [...data.apps, ...data.importedApps].find(a => a.id === appId); if (target) { setTimeout(() => setSelectedApp(target), 100); } else { setDevToast(`App "${appId}" not found`); } }}
                            />
                        )}
                        {showSubmissionModal && <SubmissionModal onClose={() => setShowSubmissionModal(false)} currentStoreVersion={CURRENT_STORE_VERSION} onSuccess={settings.registerSubmission} submissionCount={settings.submissionCount} activeTab={activeTab} />}
                        {showAdDonation && <AdDonationModal onClose={() => setShowAdDonation(false)} onSuccess={settings.incrementAdWatch} currentStreak={settings.adWatchCount} />}
                        {showStoreUpdateModal && (isTestingUpdate || (remoteConfig?.latestStoreVersion)) && <StoreUpdateModal currentVersion={CURRENT_STORE_VERSION} newVersion={isTestingUpdate ? "9.9.9" : (remoteConfig?.latestStoreVersion || "Unknown")} downloadUrl={isTestingUpdate ? "#" : storeUpdateUrl} onClose={() => { setShowStoreUpdateModal(false); setIsTestingUpdate(false); }} />}
                    </Suspense>
                </>
            )}
        </div>
    );
};

export default App;
