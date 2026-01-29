
import React, { useState, useEffect, useCallback, useMemo, useRef, Suspense, lazy } from 'react';
import { App as CapacitorApp } from '@capacitor/app';
import { Capacitor } from '@capacitor/core';
import { LocalNotifications, ActionPerformed } from '@capacitor/local-notifications';
import { Haptics, ImpactStyle, NotificationType } from '@capacitor/haptics';
import { UnityAds } from 'capacitor-unity-ads';
import { DEV_SOCIALS, DEFAULT_FAQS, DEFAULT_DEV_PROFILE, DEFAULT_SUPPORT_EMAIL, DEFAULT_EASTER_EGG, CACHE_VERSION, NETWORK_TIMEOUT_MS } from './constants';
import { Platform, AppItem, Tab, AppVariant, StoreConfig, AppCategory, SortOption } from './types';
import AppCard from './components/AppCard';
import Header from './components/Header';
import BottomNav from './components/BottomNav';
import StoreFilters from './components/StoreFilters';
import { localAppsData } from './localData';
import AppTracker from './plugins/AppTracker';

// --- LAZY LOAD HEAVY COMPONENTS ---
const AppDetail = lazy(() => import('./components/AppDetail'));
const FAQModal = lazy(() => import('./components/FAQModal'));
const AdDonationModal = lazy(() => import('./components/AdDonationModal'));
const AboutView = lazy(() => import('./components/AboutView'));
const SubmissionModal = lazy(() => import('./components/SubmissionModal'));
const SettingsModal = lazy(() => import('./components/SettingsModal'));
const StoreUpdateModal = lazy(() => import('./components/StoreUpdateModal'));

// APP CONSTANTS
const CURRENT_STORE_VERSION = '1.0.8'; 
const BUILT_IN_GH_TOKEN = '';

// --- ADS CONFIGURATION ---
const UNITY_GAME_ID = '5996387'; 
const ADS_TEST_MODE = false; 

// CONFIGURATION ENDPOINTS
const CONFIG_URL_PRIMARY = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/config.json';
const APPS_URL_PRIMARY = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/apps.json';
const APPS_URL_FALLBACK = 'https://cdn.jsdelivr.net/gh/RookieEnough/Orion-Data@main/apps.json';
const CONFIG_URL_FALLBACK = 'https://cdn.jsdelivr.net/gh/RookieEnough/Orion-Data@main/config.json';
const DEFAULT_MIRROR_JSON = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/mirror.json';

type Theme = 'light' | 'dusk' | 'dark' | 'oled';

// --- UTILS ---
const safeStorage = {
    getItem: (key: string) => {
        try {
            return localStorage.getItem(key);
        } catch (e) {
            return null;
        }
    },
    setItem: (key: string, value: string) => {
        try {
            localStorage.setItem(key, value);
        } catch (e: any) {
            if (e.name === 'QuotaExceededError') {
                localStorage.clear();
            }
        }
    }
};

const parseSizeToNumber = (sizeStr: string): number => {
    if (!sizeStr || sizeStr.toLowerCase().includes('varies')) return 0;
    const clean = sizeStr.toLowerCase().replace(/[^0-9.]/g, '');
    const num = parseFloat(clean);
    if (isNaN(num)) return 0;
    if (sizeStr.toLowerCase().includes('gb')) return num * 1024;
    return num; // Default MB
};

const determineArch = (filename: string): string => {
  const lower = filename.toLowerCase();
  if (lower.includes('arm64') || lower.includes('v8a')) return 'ARM64';
  if (lower.includes('armeabi') || lower.includes('v7a')) return 'ARMv7';
  if (lower.includes('x86_64') || lower.includes('x64')) return 'x64';
  if (lower.includes('x86')) return 'x86';
  return 'Universal';
};

const extractVersionString = (str: string): string | null => {
    if (!str) return null;
    let clean = str.toLowerCase();
    clean = clean.replace(/armeabi-v7a/g, '').replace(/arm64-v8a/g, '').replace(/x86_64/g, '').replace(/x86/g, '').replace(/v7a/g, '').replace(/v8a/g, '').replace(/-all/g, '').replace(/_all/g, '').replace(/-universal/g, '').replace(/_universal/g, '').replace(/universal/g, '').replace(/\.apk/g, '');
    
    // Strategy 1: Explicit vX.X.X
    const vMatch = clean.match(/v(\d+(?:[.-]\d+)+)/);
    if (vMatch && vMatch[1]) return vMatch[1].replace(/-/g, '.');
    
    // Strategy 2: Loose semantic version (Allows X.Y and X.Y.Z)
    const semMatch = clean.match(/(\d+(?:\.\d+)+)/);
    if (semMatch && semMatch[1]) return semMatch[1];
    
    // Strategy 3: Single number version prefixed with v
    const simpleMatch = clean.match(/v(\d+)(?![a-z])/);
    if (simpleMatch && simpleMatch[1]) return simpleMatch[1];
    
    return null;
};

const sanitizeUrl = (url?: string): string => {
    if (!url) return '#';
    if (url.trim().toLowerCase().startsWith('javascript:')) return '#';
    return url;
};

const sanitizeApp = (app: any): AppItem => ({
    ...app,
    name: String(app.name || 'Unknown App'),
    description: String(app.description || ''),
    author: String(app.author || 'Unknown'),
    category: app.category || AppCategory.UTILITY,
    platform: app.platform || Platform.ANDROID,
    icon: sanitizeUrl(String(app.icon || '')),
    version: String(app.version || 'Latest'),
    latestVersion: String(app.latestVersion || 'Latest'),
    downloadUrl: sanitizeUrl(String(app.downloadUrl || '#')),
    screenshots: Array.isArray(app.screenshots) ? app.screenshots.map((s:string) => sanitizeUrl(s)) : []
});

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
  const [activeTab, setActiveTab] = useState<Tab>('android');
  const [selectedApp, setSelectedApp] = useState<AppItem | null>(null);
  const [showScrollTop, setShowScrollTop] = useState(false);
  
  const [hiddenTabs, setHiddenTabs] = useState<string[]>(() => {
      try { return JSON.parse(safeStorage.getItem('hidden_tabs') || '[]'); } catch { return []; }
  });
  const [autoUpdateEnabled, setAutoUpdateEnabled] = useState(() => safeStorage.getItem('auto_update_enabled') === 'true');
  const [wifiOnly, setWifiOnly] = useState(() => safeStorage.getItem('wifi_only') === 'true');
  const [deleteApk, setDeleteApk] = useState(() => safeStorage.getItem('delete_apk') === 'true');
  const [disableAnimations, setDisableAnimations] = useState(() => safeStorage.getItem('disable_anim') === 'true');
  const [compactMode, setCompactMode] = useState(() => safeStorage.getItem('compact_mode') === 'true');
  const [highRefreshRate, setHighRefreshRate] = useState(() => safeStorage.getItem('high_refresh_rate') === 'true');
  const [showSettingsModal, setShowSettingsModal] = useState(false);
  const [isOled, setIsOled] = useState(() => safeStorage.getItem('oled_enabled') === 'true');

  const [activeDownloads, setActiveDownloads] = useState<Record<string, string>>(() => {
      try {
          const saved = safeStorage.getItem('active_native_downloads');
          return saved ? JSON.parse(saved) : {};
      } catch { return {}; }
  });
  
  const [readyToInstall, setReadyToInstall] = useState<Record<string, string>>(() => {
      try {
          const saved = safeStorage.getItem('ready_to_install');
          return saved ? JSON.parse(saved) : {};
      } catch { return {}; }
  });

  const [pendingCleanup, setPendingCleanup] = useState<Record<string, string>>(() => {
      try {
          const saved = safeStorage.getItem('pending_cleanup_files');
          return saved ? JSON.parse(saved) : {};
      } catch { return {}; }
  });

  const [pendingInstallRetry, setPendingInstallRetry] = useState<{app: AppItem, file: string} | null>(null);

  useEffect(() => { safeStorage.setItem('ready_to_install', JSON.stringify(readyToInstall)); }, [readyToInstall]);
  useEffect(() => { safeStorage.setItem('pending_cleanup_files', JSON.stringify(pendingCleanup)); }, [pendingCleanup]);
  
  const [downloadProgressMap, setDownloadProgressMap] = useState<Record<string, number>>({});
  const [downloadStatusMap, setDownloadStatusMap] = useState<Record<string, string>>({});
  const [autoUpdateBanner, setAutoUpdateBanner] = useState<{ count: number, visible: boolean }>({ count: 0, visible: false });

  const [installingId, setInstallingId] = useState<string | null>(null);
  const [showInstallToast, setShowInstallToast] = useState<{app: AppItem, file: string} | null>(null);
  const [showErrorToast, setShowErrorToast] = useState(false);
  const [errorMsg, setErrorMsg] = useState('Failed to load apps');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('All');
  const [selectedSort, setSelectedSort] = useState<SortOption>(SortOption.NEWEST);
  const [profileImgError, setProfileImgError] = useState(false);
  const [showFAQ, setShowFAQ] = useState(false);
  const [showAdDonation, setShowAdDonation] = useState(false); 
  const [showSubmissionModal, setShowSubmissionModal] = useState(false);
  const [submissionCooldown, setSubmissionCooldown] = useState<string | null>(null);
  const [submissionCount, setSubmissionCount] = useState(() => parseInt(safeStorage.getItem('submission_count') || '0'));
  const [storeUpdateAvailable, setStoreUpdateAvailable] = useState(false);
  const [showStoreUpdateModal, setShowStoreUpdateModal] = useState(false);
  const [isTestingUpdate, setIsTestingUpdate] = useState(false);
  const [storeUpdateUrl, setStoreUpdateUrl] = useState('');
  const [isDevUnlocked, setIsDevUnlocked] = useState(() => safeStorage.getItem('isDevUnlocked') === 'true');
  const [devClickCount, setDevClickCount] = useState(0);
  const [devToast, setDevToast] = useState<string | null>(null);
  const devToastTimer = useRef<any>(null);
  const [easterEggCount, setEasterEggCount] = useState(0);
  const [isLegend, setIsLegend] = useState(() => safeStorage.getItem('isLegend') === 'true');
  const [adWatchCount, setAdWatchCount] = useState(() => parseInt(safeStorage.getItem('total_ads_watched') || '0'));
  const [isContributor, setIsContributor] = useState(() => safeStorage.getItem('isContributor') === 'true');
  const [isAnnouncementDismissed, setIsAnnouncementDismissed] = useState(false);

  const [importedApps, setImportedApps] = useState<AppItem[]>(() => {
      try {
          const saved = safeStorage.getItem('imported_apps');
          return saved ? JSON.parse(saved) : [];
      } catch { return []; }
  });

  const [apps, setApps] = useState<AppItem[]>(() => {
      const cached = safeStorage.getItem('orion_cached_apps_v2');
      const cacheVer = safeStorage.getItem('orion_cache_ver');
      if (cacheVer !== CACHE_VERSION) return localAppsData.map(sanitizeApp) as AppItem[];
      if (cached) {
          try {
              const parsed = JSON.parse(cached);
              if (Array.isArray(parsed) && parsed.length > 0) return parsed.map(sanitizeApp);
          } catch (e) {}
      }
      return localAppsData.map(sanitizeApp) as AppItem[];
  });

  const appsRef = useRef(apps);
  useEffect(() => { 
      appsRef.current = [...apps, ...importedApps]; 
  }, [apps, importedApps]);

  const [isLoading, setIsLoading] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [remoteConfig, setRemoteConfig] = useState<StoreConfig | null>(null);
  const [githubToken, setGithubToken] = useState(() => safeStorage.getItem('gh_token') || BUILT_IN_GH_TOKEN);
  const [isEditingToken, setIsEditingToken] = useState(false);
  const [useRemoteJson, setUseRemoteJson] = useState(() => safeStorage.getItem('use_remote_json') !== 'false');
  const [mirrorSource, setMirrorSource] = useState<string>('Checking...');
  const [installedVersions, setInstalledVersions] = useState<Record<string, string>>(() => {
      try { return JSON.parse(safeStorage.getItem('installed_apps') || '{}'); } catch { return {}; }
  });
  
  const [theme, setTheme] = useState<Theme>(() => (safeStorage.getItem('theme_preference') as Theme) || 'light');

  // --- INITIALIZE NATIVE SERVICES ---
  useEffect(() => {
      requestPermissions();
      if (Capacitor.isNativePlatform()) {
          try {
              UnityAds.initialize({
                  gameId: UNITY_GAME_ID,
                  testMode: ADS_TEST_MODE, 
              }).catch(e => console.error("UnityAds Init Error:", e));
          } catch (e) {}

          const listenerPromise = LocalNotifications.addListener('localNotificationActionPerformed', async (action: ActionPerformed) => {
              const { notification } = action;
              if (notification.extra && notification.extra.appId) {
                  const targetAppId = notification.extra.appId;
                  const targetFileName = notification.extra.fileName;
                  const app = appsRef.current.find(a => a.id === targetAppId);
                  
                  if (app) {
                      const currentCleanup = JSON.parse(safeStorage.getItem('pending_cleanup_files') || '{}');
                      if (targetFileName && !currentCleanup[targetAppId]) {
                          setReadyToInstall(prev => {
                              const next = { ...prev, [targetAppId]: targetFileName };
                              safeStorage.setItem('ready_to_install', JSON.stringify(next));
                              return next;
                          });
                      }
                      Haptics.impact({ style: ImpactStyle.Heavy });
                      setSelectedApp(app);
                  }
              }
          });
          
          const cleanupStaleDownloads = async () => {
              const currentDownloads = { ...activeDownloads };
              let changed = false;
              
              for (const appId in currentDownloads) {
                  const val = currentDownloads[appId];
                  const [dlId] = val.split('|');
                  try {
                      const status = await AppTracker.getDownloadProgress({ downloadId: dlId });
                      
                      if (status.status === 'FAILED') {
                          delete currentDownloads[appId];
                          changed = true;
                      } else if (status.status === 'SUCCESSFUL') {
                          delete currentDownloads[appId];
                          setReadyToInstall(prev => {
                              const next = { ...prev, [appId]: dlId }; 
                              safeStorage.setItem('ready_to_install', JSON.stringify(next));
                              return next;
                          });
                          changed = true;
                      }
                  } catch (e) {
                      delete currentDownloads[appId];
                      changed = true;
                  }
              }
              
              if (changed) {
                  setActiveDownloads(currentDownloads);
                  safeStorage.setItem('active_native_downloads', JSON.stringify(currentDownloads));
              }
          };
          cleanupStaleDownloads();

          return () => { listenerPromise.then(handler => handler.remove()); };
      }
  }, []);

  // --- SAFE SCROLL HANDLER ---
  useEffect(() => {
      const root = document.getElementById('root');
      if (!root) return;

      const handleScroll = () => {
          if (root.scrollTop > 300) {
              setShowScrollTop(true);
          } else {
              setShowScrollTop(false);
          }
      };
      
      root.addEventListener('scroll', handleScroll, { passive: true });
      return () => {
          root.removeEventListener('scroll', handleScroll);
      };
  }, []);

  const scrollToTop = () => {
      const root = document.getElementById('root');
      if (root) {
          root.scrollTo({ top: 0, behavior: 'smooth' });
          Haptics.selection();
      }
  };

  useEffect(() => {
      const root = document.documentElement;
      root.classList.remove('light', 'dusk', 'dark', 'oled');
      if (theme === 'light') root.classList.add('light');
      else if (theme === 'dusk') root.classList.add('dusk');
      else if (theme === 'dark') {
          if (isOled) root.classList.add('oled', 'dark');
          else root.classList.add('dark');
      } else root.classList.add(theme);
      
      safeStorage.setItem('theme_preference', theme);
      safeStorage.setItem('oled_enabled', String(isOled));
  }, [theme, isOled]);

  useEffect(() => {
      if (highRefreshRate) {
          document.body.classList.add('perf-mode');
      } else {
          document.body.classList.remove('perf-mode');
      }

      if (Capacitor.isNativePlatform()) {
          AppTracker.setHighRefreshRate({ enable: highRefreshRate }).catch(() => {});
      }
  }, [highRefreshRate]);

  const devProfile = remoteConfig?.devProfile || DEFAULT_DEV_PROFILE;
  const supportEmail = remoteConfig?.supportEmail || DEFAULT_SUPPORT_EMAIL;
  const socialLinks = remoteConfig?.socials || DEV_SOCIALS;
  const faqs = remoteConfig?.faqs || DEFAULT_FAQS;
  const easterEggUrl = remoteConfig?.easterEggUrl || DEFAULT_EASTER_EGG;

  const isMounted = useRef(true);
  useEffect(() => {
      isMounted.current = true;
      return () => { isMounted.current = false; };
  }, []);

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
          const dismissedHash = safeStorage.getItem('dismissed_announcement_hash');
          setIsAnnouncementDismissed(dismissedHash === String(hash));
      }
  }, [remoteConfig]);

  useEffect(() => {
      if (!Capacitor.isNativePlatform()) return;
      const handleBack = async () => {
          if (selectedApp) setSelectedApp(null);
          else if (showSettingsModal) setShowSettingsModal(false);
          else if (showFAQ) setShowFAQ(false);
          else if (showSubmissionModal) setShowSubmissionModal(false);
          else if (showAdDonation) setShowAdDonation(false);
          else if (showStoreUpdateModal) setShowStoreUpdateModal(false);
          else if (activeTab !== 'android') setActiveTab('android');
          else CapacitorApp.exitApp();
      };
      const backListener = CapacitorApp.addListener('backButton', handleBack);
      return () => { backListener.then(h => h.remove()); };
  }, [selectedApp, showSettingsModal, showFAQ, showSubmissionModal, showAdDonation, activeTab, showStoreUpdateModal]);

  useEffect(() => {
      if (!Capacitor.isNativePlatform()) return;
      const downloadKeys = Object.values(activeDownloads);
      if (downloadKeys.length === 0) return;

      const poll = async () => {
          const newProgress: Record<string, number> = { ...downloadProgressMap };
          const newStatus: Record<string, string> = { ...downloadStatusMap };
          let changed = false;

          for (const appId in activeDownloads) {
              const rawVal = activeDownloads[appId];
              const [dlId, _] = rawVal.split('|');

              try {
                  const res = await AppTracker.getDownloadProgress({ downloadId: dlId });
                  const prevProg = newProgress[appId] || 0;
                  const diff = Math.abs(res.progress - prevProg);
                  
                  if (diff >= 1 || res.progress === 100 || res.status !== newStatus[appId]) {
                      if (res.progress !== newProgress[appId]) { newProgress[appId] = res.progress; changed = true; }
                      if (res.status !== newStatus[appId]) { newStatus[appId] = res.status; changed = true; }
                  }
                  
                  if (res.status === 'SUCCESSFUL') {
                      handleDownloadComplete(appId, true);
                  } else if (res.status === 'FAILED') {
                      handleDownloadComplete(appId, false);
                      setErrorMsg("Download Failed - Network Error");
                      setShowErrorToast(true);
                      setTimeout(() => setShowErrorToast(false), 3000);
                  }
              } catch (e) {
                  // Keep checking
              }
          }

          if (changed && isMounted.current) {
              setDownloadProgressMap(newProgress);
              setDownloadStatusMap(newStatus);
          }
      };

      const interval = setInterval(poll, 800);
      return () => clearInterval(interval);
  }, [activeDownloads]);

  const requestPermissions = async () => {
      if (Capacitor.isNativePlatform()) {
          try {
              await AppTracker.requestPermissions();
              await LocalNotifications.createChannel({ id: 'orion_updates', name: 'Orion Updates', importance: 3 });
              await LocalNotifications.createChannel({ id: 'orion_cleanup', name: 'Cleanup', importance: 4 });
              await LocalNotifications.requestPermissions();
          } catch (e) {}
      }
  };

  const toggleHiddenTab = (tabName: string) => {
      setHiddenTabs(prev => {
          let next;
          if (prev.includes(tabName)) next = prev.filter(t => t !== tabName);
          else next = [...prev, tabName];
          if (['android', 'pc', 'tv'].filter(t => !next.includes(t)).length === 0) return prev;
          safeStorage.setItem('hidden_tabs', JSON.stringify(next));
          Haptics.selection();
          return next;
      });
  };

  const toggleAutoUpdate = async () => {
      const newState = !autoUpdateEnabled;
      setAutoUpdateEnabled(newState);
      safeStorage.setItem('auto_update_enabled', String(newState));
      Haptics.selection();
      if (newState) { await requestPermissions(); checkForUpdates(); }
  };

  const toggleWifiOnly = () => {
      setWifiOnly(!wifiOnly);
      safeStorage.setItem('wifi_only', String(!wifiOnly));
      Haptics.selection();
  };

  const toggleDeleteApk = async () => {
      const newState = !deleteApk;
      if (newState) await requestPermissions();
      setDeleteApk(newState);
      safeStorage.setItem('delete_apk', String(newState));
      Haptics.selection();
  };

  const toggleDisableAnimations = () => {
      setDisableAnimations(!disableAnimations);
      safeStorage.setItem('disable_anim', String(!disableAnimations));
      Haptics.selection();
  };

  const toggleCompactMode = () => {
      setCompactMode(!compactMode);
      safeStorage.setItem('compact_mode', String(!compactMode));
      Haptics.selection();
  };

  const toggleHighRefreshRate = () => {
      const newState = !highRefreshRate;
      setHighRefreshRate(newState);
      safeStorage.setItem('high_refresh_rate', String(newState));
      Haptics.selection();
  };

  const toggleTab = (tab: Tab) => {
      if (tab !== 'about' && hiddenTabs.includes(tab)) return;
      Haptics.impact({ style: ImpactStyle.Light });
      setActiveTab(tab); 
      setSearchQuery(''); 
      setSelectedCategory('All'); 
      const root = document.getElementById('root');
      if (root) root.scrollTo({ top: 0, behavior: 'instant' as ScrollBehavior }); 
  };

  useEffect(() => {
      if (disableAnimations) document.body.classList.add('no-anim');
      else document.body.classList.remove('no-anim');
      if (compactMode) document.body.classList.add('compact-mode');
      else document.body.classList.remove('compact-mode');
  }, [disableAnimations, compactMode]);

  const isWifiConnected = () => {
      const conn = (navigator as any).connection;
      return conn && conn.type ? conn.type === 'wifi' : true; 
  };

  useEffect(() => {
      safeStorage.setItem('active_native_downloads', JSON.stringify(activeDownloads));
  }, [activeDownloads]);

  const handleDownloadStart = useCallback((appId: string, downloadId: string, fileName: string) => {
      const compositeValue = `${downloadId}|${fileName}`;
      setActiveDownloads(prev => ({ ...prev, [appId]: compositeValue }));
      setReadyToInstall(prev => { 
          if (!prev[appId]) return prev;
          const n = {...prev}; delete n[appId]; 
          safeStorage.setItem('ready_to_install', JSON.stringify(n));
          return n; 
      });
      Haptics.impact({ style: ImpactStyle.Medium });
  }, []);

  const handleCancelDownload = useCallback(async (app: AppItem, compositeId: string) => {
      const [dlId] = compositeId.split('|');
      Haptics.impact({ style: ImpactStyle.Medium });
      
      try {
          await AppTracker.cancelDownload({ downloadId: dlId });
      } catch (e) {
          console.error("Cancel failed in plugin (UI will still clear):", e);
      } finally {
          setActiveDownloads(prev => { 
              const next = { ...prev }; 
              delete next[app.id]; 
              return next; 
          });
          setDownloadProgressMap(prev => { 
              const next = { ...prev }; 
              delete next[app.id]; 
              return next; 
          });
          setDownloadStatusMap(prev => { 
              const next = { ...prev }; 
              delete next[app.id]; 
              return next; 
          });
      }
  }, []);

  const handleDeleteReadyFile = useCallback(async (app: AppItem, fileName: string) => {
      try {
          await AppTracker.deleteFile({ fileName });
          setReadyToInstall(prev => {
              const next = { ...prev };
              delete next[app.id]; 
              safeStorage.setItem('ready_to_install', JSON.stringify(next));
              return next;
          });
          Haptics.notification({ type: NotificationType.Success });
      } catch (e) {
          console.error("Failed to delete file", e);
      }
  }, []);

  const handleInstallFile = async (app: AppItem, fileName: string) => {
      try {
          Haptics.impact({ style: ImpactStyle.Heavy });
          setInstallingId(app.id);
          // NEW: Only store retry context if we specifically hit the permission error
          // setPendingInstallRetry({app, file: fileName}); // MOVED to error block
          await AppTracker.installPackage({ fileName });
          setShowInstallToast(null);
      } catch (e: any) {
          const msg = e?.message || JSON.stringify(e);
          if (msg.includes("CORRUPT") || msg.includes("PARSE_ERROR")) {
              setErrorMsg('File corrupted. Deleting...');
              setShowErrorToast(true);
              handleDeleteReadyFile(app, fileName);
          } else if (msg.includes("INSTALL_PERMISSION_REQUIRED")) {
              // Capture context for Resume
              setPendingInstallRetry({app, file: fileName});
              setErrorMsg('Please allow permission and return here');
              setShowErrorToast(true);
          } else if (!msg.includes('Activity')) {
             setErrorMsg('Installation failed.');
             setShowErrorToast(true);
             setTimeout(() => setShowErrorToast(false), 3000);
          }
      } finally {
          setInstallingId(null);
      }
  };

  const handleDownloadAction = async (app: AppItem, url?: string) => {
      if (readyToInstall[app.id]) {
          handleInstallFile(app, readyToInstall[app.id]);
          return;
      }
      if (activeDownloads[app.id]) {
          // If downloading, clicking just opens details
          setSelectedApp(app);
          return;
      }
      const targetUrl = url || app.variants?.[0]?.url || app.downloadUrl;
      if (!targetUrl || targetUrl === '#') return;
      if (wifiOnly && !isWifiConnected()) {
          setErrorMsg('Download blocked: WiFi Only mode.');
          setShowErrorToast(true);
          Haptics.notification({ type: NotificationType.Error });
          setTimeout(() => setShowErrorToast(false), 3000);
          return;
      }
      const safe = sanitizeUrl(targetUrl);
      const isAndroid = app.platform === Platform.ANDROID;
      
      const isStandardFile = safe.toLowerCase().endsWith('.apk') || safe.toLowerCase().endsWith('.exe') || safe.toLowerCase().endsWith('.zip');
      
      if (!isStandardFile && !isAndroid) { 
          window.open(safe, '_blank'); 
          return; 
      }

      if (!Capacitor.isNativePlatform()) {
          registerInstall(app.id, app.latestVersion);
          window.location.href = safe;
          return;
      }
      if (app.platform === Platform.PC || app.platform === Platform.TV) {
          window.open(safe, '_blank');
      } else {
          const sanitizedName = app.name.replace(/[^a-zA-Z0-9]/g, '_');
          const fileName = `${sanitizedName}_${app.latestVersion}.apk`;
          try {
              const result = await AppTracker.downloadFile({ url: safe, fileName });
              if (result?.downloadId) handleDownloadStart(app.id, result.downloadId, fileName);
          } catch (e: any) { 
              if (e.message && e.message.includes("INSUFFICIENT_STORAGE")) {
                  setErrorMsg("Not enough space on device!");
                  setShowErrorToast(true);
              } else {
                  window.location.href = safe;
              }
          }
      }
  };

  const handleDownloadComplete = useCallback((appId: string, success: boolean) => {
      setActiveDownloads(prev => {
          const compositeValue = prev[appId];
          const [_, fileName] = compositeValue ? compositeValue.split('|') : [null, null];

          if (success && fileName && isMounted.current) {
              setReadyToInstall(curr => {
                  const updated = { ...curr, [appId]: fileName };
                  safeStorage.setItem('ready_to_install', JSON.stringify(updated));
                  return updated;
              });
              const app = appsRef.current.find(a => a.id === appId);
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
          const next = { ...prev }; 
          delete next[appId]; 
          return next; 
      });
      setDownloadProgressMap(prev => { const next = { ...prev }; delete next[appId]; return next; });
      setDownloadStatusMap(prev => { const next = { ...prev }; delete next[appId]; return next; });
      Haptics.notification({ type: success ? NotificationType.Success : NotificationType.Error });
  }, []);


const handleRedownload = (app: AppItem, specificUrl?: string) => {
    const urlToUse = specificUrl || app.downloadUrl;
    if (!urlToUse || urlToUse === '#' || urlToUse === '') {
        setErrorMsg('Download link not found');
        setShowErrorToast(true);
        setTimeout(() => setShowErrorToast(false), 3000);
        return;
    }

    try {
        // Remove installed version 
        const newReg = { ...installedVersions };
        delete newReg[app.id];
        setInstalledVersions(newReg);
        safeStorage.setItem('installed_apps', JSON.stringify(newReg));

        // Show redownload status
        setErrorMsg('Removing old file, Redownloading now...');
        setShowErrorToast(true);

        // Trigger redownload 
        setTimeout(() => {
            setShowErrorToast(false);
            handleDownloadAction(app, urlToUse);
        }, 1000);
    } catch (error) {
        console.error('Redownload error:', error);
        setErrorMsg('Failed to redownload. Try again.');
        setShowErrorToast(true);
        setTimeout(() => setShowErrorToast(false), 3000);
    }
  };



  const checkForUpdates = async () => {
      if (!autoUpdateEnabled || !Capacitor.isNativePlatform() || (wifiOnly && !isWifiConnected())) return;
      const updates = appsRef.current.filter(app => {
          const localVer = installedVersions[app.id];
          return localVer && localVer !== "Installed" && !activeDownloads[app.id] && !readyToInstall[app.id] && compareVersions(app.latestVersion, localVer) > 0;
      });
      if (updates.length > 0) {
          setAutoUpdateBanner({ count: updates.length, visible: true });
          setTimeout(() => setAutoUpdateBanner(prev => ({ ...prev, visible: false })), 6000);
          Haptics.notification({ type: NotificationType.Warning });
          
          const MAX_CONCURRENT_UPDATES = 2;
          const batch = updates.slice(0, MAX_CONCURRENT_UPDATES);
          
          for (const app of batch) {
              const url = app.variants?.[0]?.url || app.downloadUrl;
              await handleDownloadAction(app, url);
              await new Promise(r => setTimeout(r, 1500)); 
          }
      }
  };

  useEffect(() => {
      if (autoUpdateEnabled && apps.length > 0) {
          const timer = setTimeout(checkForUpdates, 5000); 
          return () => clearTimeout(timer);
      }
  }, [apps, autoUpdateEnabled, installedVersions]);

  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;
    const syncInstalledApps = async () => {
        if (!appsRef.current || appsRef.current.length === 0) return;
        const checks = appsRef.current.filter(app => app.packageName).map(async (app) => {
            try {
                const result = await AppTracker.getAppInfo({ packageName: app.packageName! });
                return { id: app.id, installed: result.installed, version: result.version };
            } catch (e) { return null; }
        });
        const results = await Promise.all(checks);
        setInstalledVersions(prev => {
            const next = { ...prev };
            let changed = false;
            results.forEach(res => {
                if (res) {
                    if (res.installed) {
                        if (next[res.id] !== res.version) { next[res.id] = res.version; changed = true; }
                        const app = appsRef.current.find(a => a.id === res.id);
                        if (app && compareVersions(res.version, app.latestVersion) >= 0) {
                            setReadyToInstall(curr => { 
                                if (curr[res.id]) { 
                                    const fileName = curr[res.id];
                                    const n = {...curr}; delete n[res.id]; 
                                    if (deleteApk) {
                                        setPendingCleanup(p => {
                                            const updated = {...p, [res.id]: fileName};
                                            safeStorage.setItem('pending_cleanup_files', JSON.stringify(updated));
                                            return updated;
                                        });
                                        Haptics.notification({ type: NotificationType.Warning });
                                        setSelectedApp(app);
                                    }
                                    safeStorage.setItem('ready_to_install', JSON.stringify(n));
                                    return n; 
                                } 
                                return curr; 
                            });
                        }
                    } else if (next[res.id]) { delete next[res.id]; changed = true; }
                }
            });
            if (changed) { safeStorage.setItem('installed_apps', JSON.stringify(next)); return next; }
            return prev;
        });
    };
    
    // UPDATED RESUME LISTENER: Check for permission retry
    const resumeListener = CapacitorApp.addListener('resume', () => {
        syncInstalledApps();
        
        if (pendingInstallRetry) {
            // Give Android a moment to register permission change
            setTimeout(() => {
                handleInstallFile(pendingInstallRetry.app, pendingInstallRetry.file);
                setPendingInstallRetry(null);
            }, 500);
        }
    });
    
    syncInstalledApps();
    
    return () => { resumeListener.then(h => h.remove()); };
  }, [apps, importedApps, deleteApk, pendingInstallRetry]); 

  const handleAdWatched = () => {
      const newCount = adWatchCount + 1;
      setAdWatchCount(newCount);
      safeStorage.setItem('total_ads_watched', String(newCount));
      Haptics.notification({ type: NotificationType.Success });
      if (newCount >= 3 && !isContributor) { setIsContributor(true); safeStorage.setItem('isContributor', 'true'); }
  };

  const registerInstall = (appId: string, version: string) => {
      const newRegistry = { ...installedVersions, [appId]: version };
      setInstalledVersions(newRegistry);
      safeStorage.setItem('installed_apps', JSON.stringify(newRegistry));
  };

  const saveGithubToken = (token: string) => {
      setGithubToken(token);
      safeStorage.setItem('gh_token', token);
      setIsEditingToken(false);
      Haptics.notification({ type: NotificationType.Success });
      setTimeout(() => loadApps(true), 500);
  };

  const handleDismissAnnouncement = () => {
      if (remoteConfig?.announcement) {
          const hash = getStringHash(remoteConfig.announcement);
          safeStorage.setItem('dismissed_announcement_hash', String(hash));
          setIsAnnouncementDismissed(true);
          Haptics.selection();
      }
  };

  const handleTestUpdateModal = () => {
      setIsTestingUpdate(true);
      setShowStoreUpdateModal(true);
      Haptics.impact({ style: ImpactStyle.Medium });
  };

  const showDevToast = (msg: string) => {
      if (devToastTimer.current) clearTimeout(devToastTimer.current);
      setDevToast(msg);
      devToastTimer.current = setTimeout(() => setDevToast(null), 2000);
  };

  const handleHeaderClick = () => {
      if (isDevUnlocked) {
          showDevToast("No need, you are already a developer.");
          return;
      }
      const newCount = devClickCount + 1;
      setDevClickCount(newCount);
      const stepsNeeded = 7;
      const remaining = stepsNeeded - newCount;
      Haptics.impact({ style: ImpactStyle.Light });
      if (remaining > 0 && remaining <= 4) {
          showDevToast(`You are ${remaining} steps away from being a developer.`);
      } else if (remaining <= 0) {
          setIsDevUnlocked(true);
          safeStorage.setItem('isDevUnlocked', 'true');
          showDevToast("You are now a developer!");
          Haptics.notification({ type: NotificationType.Success });
          setDevClickCount(0);
      }
  };

  const handleSubmissionSuccess = useCallback(() => {
      const newCount = submissionCount + 1;
      setSubmissionCount(newCount);
      safeStorage.setItem('submission_count', String(newCount));
      safeStorage.setItem('last_submission_ts', String(Date.now()));
      Haptics.notification({ type: NotificationType.Success });
  }, [submissionCount]);

  useEffect(() => {
      const checkCooldown = () => {
          const lastTs = safeStorage.getItem('last_submission_ts');
          if (lastTs) {
              const baseCooldown = 180;
              const reductionPerSub = 15;
              const maxReduction = 150;
              const currentReduction = Math.min(submissionCount * reductionPerSub, maxReduction);
              const totalCooldownMinutes = baseCooldown - currentReduction;
              const elapsed = Date.now() - parseInt(lastTs);
              const remaining = (totalCooldownMinutes * 60 * 1000) - elapsed;
              
              if (remaining > 0) {
                  const hrs = Math.floor(remaining / (1000 * 60 * 60));
                  const mins = Math.floor((remaining % (1000 * 60 * 60)) / (1000 * 60));
                  setSubmissionCooldown(`${hrs}h ${mins}m`);
              } else {
                  setSubmissionCooldown(null);
              }
          }
      };
      checkCooldown();
      const interval = setInterval(checkCooldown, 60000);
      return () => clearInterval(interval);
  }, [submissionCount]);

  const loadApps = useCallback(async (isManualRefresh = false) => {
      if (isManualRefresh) { setIsRefreshing(true); Haptics.impact({ style: ImpactStyle.Light }); }
      if (appsRef.current.length === 0) setIsLoading(true);
      try {
        let rawApps: AppItem[] = [];
        let mirrorData: Record<string, any> | null = null;
        let configData: StoreConfig | null = null;
        
        if (useRemoteJson) {
            let activeAppsUrl = APPS_URL_PRIMARY; 
            let activeMirrorUrl = DEFAULT_MIRROR_JSON;
            const configTs = `?t=${Date.now()}`;
            const appsTs = isManualRefresh ? `?t=${Date.now()}` : '';
            try {
                const configReq = await fetchWithRetry(`${CONFIG_URL_PRIMARY}${configTs}`, { cache: 'no-store' }, 2);
                if (configReq.ok) configData = await configReq.json();
                else throw new Error();
            } catch (e) {
                try {
                    const configReq = await fetchWithRetry(`${CONFIG_URL_FALLBACK}${configTs}`, { cache: 'no-store' }, 1);
                    if (configReq.ok) configData = await configReq.json();
                } catch (err) {}
            }
            if (configData) {
                if(isMounted.current) setRemoteConfig(configData);
                if (configData.latestStoreVersion && compareVersions(configData.latestStoreVersion, CURRENT_STORE_VERSION) > 0) {
                    if(isMounted.current) { 
                        setStoreUpdateAvailable(true); 
                        setStoreUpdateUrl(configData.storeDownloadUrl!); 
                        if (!sessionStorage.getItem('store_update_notified')) {
                            setShowStoreUpdateModal(true);
                            sessionStorage.setItem('store_update_notified', 'true');
                        }
                    }
                }
                if (configData.appsJsonUrl) activeAppsUrl = configData.appsJsonUrl;
                if (configData.mirrorJsonUrl) activeMirrorUrl = configData.mirrorJsonUrl;
            }
            try {
                const appsResponse = await fetchWithRetry(`${activeAppsUrl}${appsTs}`, { cache: 'no-store' }, 2);
                if (!appsResponse.ok) throw new Error();
                rawApps = await appsResponse.json();
            } catch (e) {
                try {
                    const fallbackRes = await fetchWithRetry(`${APPS_URL_FALLBACK}${appsTs}`, { cache: 'no-store' }, 2);
                    rawApps = fallbackRes.ok ? await fallbackRes.json() : localAppsData as unknown as AppItem[];
                } catch (err) { rawApps = localAppsData as unknown as AppItem[]; }
            }
            try {
                 const mirrorReq = await fetchWithRetry(`${activeMirrorUrl}${appsTs}`, {}, 1);
                 if (mirrorReq.ok) { 
                    mirrorData = await mirrorReq.json(); 
                    if(isMounted.current) setMirrorSource('Remote (GitHub)'); 
                 }
            } catch (e) { 
                try {
                    const localMirror = await fetch('./mirror.json');
                    if (localMirror.ok) { 
                        mirrorData = await localMirror.json(); 
                        if(isMounted.current) setMirrorSource('Local File'); 
                    }
                } catch(err) {}
            }
        } else { 
            rawApps = localAppsData as unknown as AppItem[]; 
            if(isMounted.current) setMirrorSource('Disabled'); 
        }

        const repoCache = new Map<string, any[]>();
        if (mirrorData) {
            Object.keys(mirrorData).forEach(key => {
                const data = mirrorData![key];
                repoCache.set(key.toLowerCase(), Array.isArray(data) ? data : [data]);
            });
        }
        
        const processItem = (app: AppItem): AppItem => {
          const isGitHub = !!(app.githubRepo || (app.repoUrl && app.repoUrl.includes('github.com')));
          if (!isGitHub) return app;
          let cleanRepoPath = (app.githubRepo || app.repoUrl || '').replace(/^https?:\/\/(www\.)?github\.com\//i, '').replace(/\.git$/i, '').replace(/\/$/, '');                                
          let releases = cleanRepoPath ? repoCache.get(cleanRepoPath.toLowerCase()) : null;
          if (cleanRepoPath && releases?.length) {
            let foundRelease = null;
            let targetAssets = [];
            for (const rel of releases) {
              const candidateAssets = (rel.assets || []).filter((a: any) => a.name.toLowerCase().endsWith('.apk'));
              if (candidateAssets.length === 0) continue;
              if (app.releaseKeyword) {
                 const kw = app.releaseKeyword.toLowerCase();
                 const matchesKeyword = (rel.name?.toLowerCase().includes(kw)) || (rel.tag_name?.toLowerCase().includes(kw)) || (candidateAssets.some((a: any) => a.name.toLowerCase().includes(kw)));
                 if (matchesKeyword) {
                    targetAssets = candidateAssets.filter((a: any) => a.name.toLowerCase().includes(kw));
                    if (targetAssets.length === 0) targetAssets = candidateAssets;
                    foundRelease = rel;
                    break;
                 }
              } else {
                 foundRelease = rel;
                 targetAssets = candidateAssets;
                 break;
              }
            }
            if (foundRelease && targetAssets.length) {
              const variants: AppVariant[] = targetAssets.map((a: any) => ({ arch: determineArch(a.name), url: a.browser_download_url }));
              variants.sort((a,b) => {
                const priority = (name: string) => name === 'Universal' ? 1 : name === 'ARM64' ? 2 : name === 'ARMv7' ? 3 : 4;
                return priority(a.arch) - priority(b.arch);
              });
              const tagName = foundRelease.tag_name || '';
              const fileName = targetAssets[0].name;
              const releaseName = foundRelease.name || '';
              let finalVersion = "Unknown";
              const tagVer = extractVersionString(tagName);
              const fileVer = extractVersionString(fileName);
              const relNameVer = extractVersionString(releaseName);
              
              if (fileVer) { finalVersion = fileVer; }
              else if (tagVer && !['latest', 'all', 'nightly', 'pre-release'].includes(tagName.toLowerCase())) { finalVersion = tagVer; }
              else if (relNameVer) { finalVersion = relNameVer; }
              else {
                  const d = new Date(foundRelease.published_at || foundRelease.created_at || Date.now());
                  const mm = String(d.getMonth() + 1).padStart(2, '0');
                  const dd = String(d.getDate()).padStart(2, '0');
                  finalVersion = `${d.getFullYear()}.${mm}.${dd}`;
              }
              return { ...app, version: finalVersion, latestVersion: finalVersion, downloadUrl: variants[0].url, variants, size: `${(targetAssets[0].size/1048576).toFixed(1)} MB` };
            }
          } 
          return app;
        };

        if (isMounted.current) {
            const processedApps = rawApps.map(sanitizeApp).map(processItem);
            const processedImported = importedApps.map(sanitizeApp).map(processItem);
            setApps(processedApps);
            setImportedApps(processedImported);
            safeStorage.setItem('orion_cached_apps_v2', JSON.stringify(processedApps));
            safeStorage.setItem('orion_cache_ver', CACHE_VERSION);
        }
      } catch (error) { 
        if(isMounted.current && appsRef.current.length === 0) {
               setErrorMsg('Failed to load apps'); 
               setShowErrorToast(true); 
               Haptics.notification({ type: NotificationType.Error }); 
        }
      } finally { 
        if(isMounted.current) { setIsLoading(false); setIsRefreshing(false); } 
      }
  }, [useRemoteJson, githubToken, importedApps]);

  useEffect(() => { 
      const timer = setTimeout(() => loadApps(false), 500);
      return () => clearTimeout(timer);
  }, [loadApps]);

  const visibleApps = useMemo(() => {
    const q = searchQuery.toLowerCase().trim();
    const allApps = [...apps, ...importedApps];
    let filtered = allApps.filter(app => {
      const matchesSearch = app.name.toLowerCase().includes(q) || app.author.toLowerCase().includes(q);
      const matchesCategory = selectedCategory === 'All' || app.category === selectedCategory;
      return matchesSearch && matchesCategory;
    });
    filtered = [...filtered].sort((a, b) => {
        switch (selectedSort) {
            case SortOption.NAME_ASC: return a.name.localeCompare(b.name);
            case SortOption.NAME_DESC: return b.name.localeCompare(a.name);
            case SortOption.SIZE_ASC: return parseSizeToNumber(a.size) - parseSizeToNumber(b.size);
            case SortOption.SIZE_DESC: return parseSizeToNumber(b.size) - parseSizeToNumber(a.size);
            default: return 0;
        }
    });
    if (selectedSort === SortOption.NEWEST) filtered.reverse();
    return filtered;
  }, [searchQuery, selectedCategory, selectedSort, apps, importedApps]);

  const updateCount = useMemo(() => {
      return [...apps, ...importedApps].filter(app => {
          const localVer = installedVersions[app.id];
          return localVer && localVer !== "Installed" && compareVersions(app.latestVersion, localVer) > 0;
      }).length;
  }, [installedVersions, apps, importedApps]);

  const availableUpdates = useMemo(() => {
      return visibleApps.filter(a => installedVersions[a.id] && installedVersions[a.id] !== "Installed" && compareVersions(a.latestVersion, installedVersions[a.id]) > 0);
  }, [visibleApps, installedVersions]);

  const appCounts = useMemo(() => {
      const all = [...apps, ...importedApps];
      return {
          android: all.filter(a => a.platform === Platform.ANDROID).length,
          pc: all.filter(a => a.platform === Platform.PC).length,
          tv: all.filter(a => a.platform === Platform.TV).length
      };
  }, [apps, importedApps]);

  const handleAppClick = useCallback((app: AppItem) => setSelectedApp(app), []);

  const handleNavigateToApp = useCallback((appId: string) => {
      const target = appsRef.current.find(a => a.id === appId);
      if (target) {
          setSelectedApp(target);
      }
  }, []);

  const renderAppGrid = (platform: Platform) => {
    const platformApps = visibleApps.filter(a => a.platform === platform);
    return (
      <div className="px-6">
        <StoreFilters 
          searchQuery={searchQuery} setSearchQuery={setSearchQuery}
          selectedCategory={selectedCategory} setSelectedCategory={setSelectedCategory}
          categories={['All', ...Object.values(AppCategory)]}
          selectedSort={selectedSort} setSelectedSort={setSelectedSort}
          onRefresh={() => loadApps(true)} isRefreshing={isRefreshing} theme={theme}
          placeholder={`Search ${platform} apps...`}
          onAddApp={() => setShowSubmissionModal(true)} submissionCooldown={submissionCooldown}
          count={appCounts[platform.toLowerCase() as keyof typeof appCounts]}
        />
        {isLoading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 animate-fade-in">
            {[...Array(6)].map((_, i) => ( <div key={i} className="h-24 bg-theme-element animate-pulse rounded-3xl" /> ))}
          </div>
        ) : platformApps.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-theme-sub animate-fade-in">
             <i className="fas fa-search text-5xl mb-4 opacity-10"></i>
             <p className="font-bold text-lg">No {platform} apps found</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 animate-fade-in">
            {platformApps.map(app => (
              <AppCard 
                key={app.id} 
                app={app} 
                onClick={handleAppClick} 
                localVersion={installedVersions[app.id]}
                hasUpdateNotification={!!installedVersions[app.id] && installedVersions[app.id] !== "Installed" && compareVersions(app.latestVersion, installedVersions[app.id]) > 0}
                downloadProgress={downloadProgressMap[app.id]} 
                downloadStatus={downloadStatusMap[app.id]} 
                isReadyToInstall={!!readyToInstall[app.id]}
              />
            ))}
          </div>
        )}
      </div>
    );
  };

  const toggleTheme = () => {
    const newTheme = theme === 'light' ? 'dusk' : theme === 'dusk' ? 'dark' : 'light';
    setTheme(newTheme);
    Haptics.impact({ style: ImpactStyle.Medium });
  };

  const clearPendingCleanup = (appId: string) => {
      setPendingCleanup(prev => {
          const updated = {...prev}; delete updated[appId];
          safeStorage.setItem('pending_cleanup_files', JSON.stringify(updated));
          return updated;
      });
  };

  if (remoteConfig?.maintenanceMode && !isDevUnlocked) {
      return (
        <div className="min-h-screen bg-surface text-theme-text transition-colors duration-300 font-sans flex flex-col items-center justify-center p-6 text-center animate-fade-in relative overflow-hidden">
            <h1 className="text-4xl font-black text-theme-text mb-4 tracking-tighter relative z-10">System Offline</h1>
            <p className="text-theme-sub text-lg font-medium max-w-sm leading-relaxed mb-10 relative z-10">
                {remoteConfig.maintenanceMessage || "Orion Store is currently undergoing scheduled maintenance."}
            </p>
        </div>
      );
  }

  return (
    <div className="min-h-screen bg-surface text-theme-text transition-colors duration-300 font-sans selection:bg-primary/30 relative overflow-x-hidden">
      {devToast && (
        <div className="fixed top-24 inset-x-0 z-[120] flex justify-center w-full pointer-events-none">
            <div className="bg-surface/90 backdrop-blur-xl border border-theme-border px-6 py-3 rounded-full shadow-2xl animate-fade-in flex items-center gap-3 pointer-events-auto">
                <i className={`fas ${isDevUnlocked ? 'fa-check-circle text-green-500' : 'fa-info-circle text-primary'}`}></i>
                <span className="text-sm font-bold text-theme-text">{devToast}</span>
            </div>
        </div>
      )}
      {showInstallToast && (
        <div className="fixed top-[calc(env(safe-area-inset-top)+5rem)] left-1/2 -translate-x-1/2 z-[110] w-[90%] max-w-sm animate-slide-up">
            <div className="bg-card border border-theme-border p-4 rounded-2xl shadow-2xl flex items-center gap-4">
                <img src={showInstallToast.app.icon} className="w-10 h-10 rounded-xl object-contain" alt="" />
                <div className="flex-1 min-w-0">
                    <p className="text-sm font-black truncate">{showInstallToast.app.name}</p>
                    <p className="text-[10px] text-theme-sub">Download complete</p>
                </div>
                <div className="flex gap-2">
                    <button onClick={() => setShowInstallToast(null)} className="w-8 h-8 rounded-full bg-theme-element flex items-center justify-center text-theme-sub"><i className="fas fa-times text-xs"></i></button>
                    <button onClick={() => handleInstallFile(showInstallToast.app, showInstallToast.file)} className="px-3 py-1.5 rounded-xl bg-primary text-white text-xs font-bold">Install</button>
                </div>
            </div>
        </div>
      )}
      {showErrorToast && (
        <div className="fixed top-[calc(env(safe-area-inset-top)+5rem)] left-1/2 -translate-x-1/2 z-[110] w-[90%] max-w-sm animate-slide-up">
            <div className="bg-red-500/10 border border-red-500/30 p-4 rounded-2xl shadow-2xl flex items-center gap-4 backdrop-blur-md">
                <div className="w-10 h-10 rounded-full bg-red-500/20 flex items-center justify-center shrink-0">
                    <i className="fas fa-exclamation-triangle text-red-500"></i>
                </div>
                <div className="flex-1 min-w-0">
                    <p className="text-sm font-black text-red-500 truncate">Error</p>
                    <p className="text-[10px] text-red-400 font-bold break-words">{errorMsg}</p>
                </div>
                <button onClick={() => setShowErrorToast(false)} className="text-red-500/60 hover:text-red-500"><i className="fas fa-times"></i></button>
            </div>
        </div>
      )}
      {autoUpdateBanner.visible && (
        <div className="fixed top-[calc(env(safe-area-inset-top)+5rem)] left-1/2 -translate-x-1/2 z-[100] w-[90%] max-w-sm animate-slide-up">
            <div className="bg-primary/95 backdrop-blur-xl text-white p-4 rounded-2xl shadow-2xl flex items-center gap-4 border border-white/20">
                <div className="w-10 h-10 rounded-full bg-white/20 flex items-center justify-center shrink-0"> <i className="fas fa-sync-alt animate-spin text-lg"></i> </div>
                <div className="flex-1 min-w-0">
                    <p className="text-sm font-black leading-tight">Orion Auto-Update</p>
                    <p className="text-[10px] opacity-80 font-medium">Updating {autoUpdateBanner.count} apps in background...</p>
                </div>
                <button onClick={() => setAutoUpdateBanner({ ...autoUpdateBanner, visible: false })} className="text-white/60 hover:text-white"><i className="fas fa-times"></i></button>
            </div>
        </div>
      )}
      <Header onTitleClick={handleHeaderClick} storeUpdateAvailable={storeUpdateAvailable} onUpdateStore={() => setShowStoreUpdateModal(true)} theme={theme} toggleTheme={toggleTheme} activeTab={activeTab} onOpenSettings={() => setShowSettingsModal(true)} updateCount={updateCount} activeDownloadCount={Object.keys(activeDownloads).length} />
      
      {remoteConfig?.announcement && !isAnnouncementDismissed && activeTab !== 'about' && (
        <div className="px-6 mb-2 animate-fade-in max-w-7xl mx-auto w-full">
            <div className={`relative group overflow-hidden border-2 border-blue-500/40 rounded-[2rem] p-4 flex items-center gap-4 shadow-lg shadow-blue-500/5 group ${theme === 'light' ? 'bg-blue-600/10' : 'bg-blue-600/15'}`}>
                <div className="absolute inset-0 bg-gradient-to-r from-blue-500/10 via-indigo-500/5 to-blue-500/10 opacity-70 animate-shine bg-[length:200%_100%] pointer-events-none"></div>
                <div className="shrink-0 w-11 h-11 rounded-2xl bg-blue-500 text-white flex items-center justify-center text-xl shadow-lg shadow-blue-500/30 transform -rotate-3 group-hover:rotate-0 transition-transform">
                    <i className="fas fa-bullhorn animate-pulse"></i>
                </div>
                <div className="flex-1 min-w-0 text-left">
                    <p className={`text-xs font-black leading-relaxed ${theme === 'light' ? 'text-blue-800' : 'text-blue-300'}`}>
                        {remoteConfig.announcement}
                    </p>
                </div>
                <button 
                    onClick={handleDismissAnnouncement}
                    className={`shrink-0 w-8 h-8 rounded-full bg-blue-500/20 flex items-center justify-center hover:bg-red-500 hover:text-white transition-all shadow-sm ${theme === 'light' ? 'text-blue-700' : 'text-blue-300'}`}
                >
                    <i className="fas fa-times text-xs"></i>
                </button>
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
                    <AboutView devProfile={devProfile} socialLinks={socialLinks} faqs={faqs} isLegend={isLegend} isContributor={isContributor} adWatchCount={adWatchCount} profileImgError={profileImgError} setProfileImgError={setProfileImgError} handleProfileClick={() => { setEasterEggCount(e => e + 1); if(easterEggCount >= 7) { window.open(easterEggUrl); setEasterEggCount(0); setIsLegend(true); safeStorage.setItem('isLegend', 'true'); Haptics.notification({ type: NotificationType.Success }); } }} setShowFAQ={setShowFAQ} onOpenAdDonation={() => setShowAdDonation(true)} isDevUnlocked={isDevUnlocked} useRemoteJson={useRemoteJson} toggleSourceMode={() => { setUseRemoteJson(!useRemoteJson); Haptics.selection(); }} githubToken={githubToken} isEditingToken={isEditingToken} setIsEditingToken={setIsEditingToken} saveGithubToken={saveGithubToken} currentStoreVersion={CURRENT_STORE_VERSION} onWipeCache={() => { localStorage.clear(); window.location.reload(); }} onTestStoreUpdate={handleTestUpdateModal} mirrorSource={mirrorSource} hiddenTabs={hiddenTabs} toggleHiddenTab={toggleHiddenTab} autoUpdateEnabled={autoUpdateEnabled} toggleAutoUpdate={toggleAutoUpdate} availableUpdates={availableUpdates} onTriggerUpdate={() => {}} />
                </Suspense>
            )}
        </div>
      </main>
      
      <button
        onClick={scrollToTop}
        className={`fixed bottom-24 right-6 z-30 w-12 h-12 rounded-2xl bg-surface/90 backdrop-blur-xl border border-theme-border shadow-2xl flex items-center justify-center text-theme-text transition-all duration-500 transform ${showScrollTop ? 'translate-y-0 opacity-100 scale-100' : 'translate-y-10 opacity-0 scale-75 pointer-events-none'}`}
      >
        <i className="fas fa-arrow-up text-primary"></i>
      </button>

      <BottomNav activeTab={activeTab} onTabChange={toggleTab} hiddenTabs={hiddenTabs} />
      
      <Suspense fallback={null}>
          {selectedApp && (
              <AppDetail 
                  app={selectedApp} 
                  onClose={() => setSelectedApp(null)} 
                  onDownload={handleDownloadAction}
                  isInstalling={installingId === selectedApp.id}
                  localVersion={installedVersions[selectedApp.id]}
                  supportEmail={supportEmail}
                  isUpdateAvailable={!!installedVersions[selectedApp.id] && installedVersions[selectedApp.id] !== "Installed" && compareVersions(selectedApp.latestVersion, installedVersions[selectedApp.id] || '') > 0}
                  activeDownloadId={activeDownloads[selectedApp.id]}
                  cleanupFileName={pendingCleanup[selectedApp.id]}
                  onCleanupDone={() => clearPendingCleanup(selectedApp.id)}
                  currentProgress={downloadProgressMap[selectedApp.id]}
                  currentStatus={downloadStatusMap[selectedApp.id]}
                  readyFileName={readyToInstall[selectedApp.id]}
                  onCancelDownload={(app, dlId) => handleCancelDownload(app, dlId)}
                  onNavigateToApp={handleNavigateToApp}
                  onDeleteReadyFile={handleDeleteReadyFile}
              />
          )}
          {showFAQ && <FAQModal onClose={() => setShowFAQ(false)} items={faqs} />}
          {showSettingsModal && (
              <SettingsModal 
                  onClose={() => setShowSettingsModal(false)} 
                  theme={theme} setTheme={setTheme} 
                  isOled={isOled} setIsOled={setIsOled} 
                  hiddenTabs={hiddenTabs} toggleHiddenTab={toggleHiddenTab} 
                  autoUpdateEnabled={autoUpdateEnabled} toggleAutoUpdate={toggleAutoUpdate} 
                  wifiOnly={wifiOnly} toggleWifiOnly={toggleWifiOnly} 
                  deleteApk={deleteApk} toggleDeleteApk={toggleDeleteApk} 
                  disableAnimations={disableAnimations} toggleDisableAnimations={toggleDisableAnimations} 
                  compactMode={compactMode} toggleCompactMode={toggleCompactMode} 
                  highRefreshRate={highRefreshRate} toggleHighRefreshRate={toggleHighRefreshRate}
                  availableUpdates={availableUpdates} onTriggerUpdate={app => handleDownloadAction(app)} 
                  activeDownloads={activeDownloads} downloadProgress={downloadProgressMap} 
                  readyToInstall={readyToInstall} onInstallApp={(app, file) => handleInstallFile(app, file)} 
                  onCancelDownload={handleCancelDownload} 
              />
          )}
          {showSubmissionModal && <SubmissionModal onClose={() => setShowSubmissionModal(false)} currentStoreVersion={CURRENT_STORE_VERSION} onSuccess={handleSubmissionSuccess} submissionCount={submissionCount} activeTab={activeTab} />}
          {showAdDonation && <AdDonationModal onClose={() => setShowAdDonation(false)} onSuccess={handleAdWatched} currentStreak={adWatchCount} />}
          
          {showStoreUpdateModal && (isTestingUpdate || (remoteConfig?.latestStoreVersion)) && (
              <StoreUpdateModal 
                currentVersion={CURRENT_STORE_VERSION} 
                newVersion={isTestingUpdate ? "9.9.9" : (remoteConfig?.latestStoreVersion || "Unknown")} 
                downloadUrl={isTestingUpdate ? "#" : storeUpdateUrl} 
                onClose={() => { setShowStoreUpdateModal(false); setIsTestingUpdate(false); }} 
              />
          )}
      </Suspense>
    </div>
  );
};

export default App;
