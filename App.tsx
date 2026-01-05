
import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { DEV_SOCIALS, DEFAULT_FAQS, DEFAULT_DEV_PROFILE, DEFAULT_SUPPORT_EMAIL, DEFAULT_EASTER_EGG, CACHE_VERSION, NETWORK_TIMEOUT_MS } from './constants';
import { Platform, AppItem, Tab, AppVariant, StoreConfig } from './types';
import AppCard from './components/AppCard';
import AppDetail from './components/AppDetail';
import FAQModal from './components/FAQModal';
import { localAppsData } from './localData';

// APP CONSTANTS
const CURRENT_STORE_VERSION = '1.0.0'; 
const REMOTE_CONFIG_URL = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/config.json';
const DEFAULT_APPS_JSON = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/apps.json';
const DEFAULT_MIRROR_JSON = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/mirror.json';

// Hardcoded Fallback Token (Only use if you accept the security risk)
const BUILT_IN_GH_TOKEN = ''; 

type Theme = 'light' | 'dusk' | 'dark';

// --- UTILS ---

// 1. Safe Storage Wrapper (Prevents Quota Exceeded Crashes)
const safeStorage = {
    getItem: (key: string) => {
        try {
            return localStorage.getItem(key);
        } catch (e) {
            console.warn('LocalStorage read failed', e);
            return null;
        }
    },
    setItem: (key: string, value: string) => {
        try {
            localStorage.setItem(key, value);
        } catch (e: any) {
            // If quota exceeded, clear non-essential cache
            if (e.name === 'QuotaExceededError' || e.name === 'NS_ERROR_DOM_QUOTA_REACHED') {
                console.warn('Storage quota exceeded. Clearing cache...');
                // Keep settings, clear data
                const keysToKeep = ['theme_preference', 'isDevUnlocked', 'isLegend', 'gh_token', 'installed_apps', 'use_remote_json'];
                const savedData: Record<string, string> = {};
                keysToKeep.forEach(k => {
                    const val = localStorage.getItem(k);
                    if(val) savedData[k] = val;
                });
                localStorage.clear();
                // Restore settings
                Object.entries(savedData).forEach(([k, v]) => localStorage.setItem(k, v));
                // Try saving again
                try { localStorage.setItem(key, value); } catch(err) { console.error('Storage totally full'); }
            }
        }
    }
};

const determineArch = (filename: string): string => {
  const lower = filename.toLowerCase();
  if (lower.includes('arm64') || lower.includes('v8a')) return 'ARM64';
  if (lower.includes('armeabi') || lower.includes('v7a')) return 'ARMv7';
  if (lower.includes('x86_64') || lower.includes('x64')) return 'x64';
  if (lower.includes('x86')) return 'x86';
  if (lower.includes('universal') || lower.includes('all')) return 'Universal';
  return 'Universal';
};

const shuffleArray = <T,>(array: T[]): T[] => {
    const newArr = [...array];
    for (let i = newArr.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [newArr[i], newArr[j]] = [newArr[j], newArr[i]];
    }
    return newArr;
};

// Security: Prevent javascript: attacks
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
    category: app.category || 'Utility',
    platform: app.platform || 'Android',
    icon: sanitizeUrl(String(app.icon || '')),
    version: String(app.version || 'Latest'),
    latestVersion: String(app.latestVersion || 'Latest'),
    downloadUrl: sanitizeUrl(String(app.downloadUrl || '#')),
    screenshots: Array.isArray(app.screenshots) ? app.screenshots.map((s:string) => sanitizeUrl(s)) : []
});

const compareVersions = (v1: string, v2: string) => {
    if (!v1 || !v2) return 0;
    // Remove 'v', whitespace, and non-numeric chars except dots
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
        const response = await fetch(resource, {
            ...options,
            signal: controller.signal
        });
        clearTimeout(id);
        return response;
    } catch (error) {
        clearTimeout(id);
        throw error;
    }
};

const App: React.FC = () => {
  const [isCategoryDropdownOpen, setIsCategoryDropdownOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<Tab>('android');
  const [selectedApp, setSelectedApp] = useState<AppItem | null>(null);
  const [installingId, setInstallingId] = useState<string | null>(null);
  const [showInstallToast, setShowInstallToast] = useState(false);
  const [showUpdateToast, setShowUpdateToast] = useState(false); 
  const [showErrorToast, setShowErrorToast] = useState(false);
  const [errorMsg, setErrorMsg] = useState('Failed to load apps');
  const [showThemeToast, setShowThemeToast] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('All');
  const [showScrollTop, setShowScrollTop] = useState(false);
  const [profileImgError, setProfileImgError] = useState(false);
  const [scrolledDown, setScrolledDown] = useState(false);
  const [showFAQ, setShowFAQ] = useState(false);
  
  // Store Update State
  const [storeUpdateAvailable, setStoreUpdateAvailable] = useState(false);
  const [storeUpdateUrl, setStoreUpdateUrl] = useState('');
  const [showStoreUpdateModal, setShowStoreUpdateModal] = useState(false);
  
  // Developer Mode State
  const [isDevUnlocked, setIsDevUnlocked] = useState(() => safeStorage.getItem('isDevUnlocked') === 'true');
  const [devTapCount, setDevTapCount] = useState(0);
  const [showDevToast, setShowDevToast] = useState(false);
  const [devToastMsg, setDevToastMsg] = useState('');
  const devToastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  
  const [easterEggCount, setEasterEggCount] = useState(0);
  const [isLegend, setIsLegend] = useState(() => safeStorage.getItem('isLegend') === 'true');
  
  // Data Sources & Loading States
  const [apps, setApps] = useState<AppItem[]>(() => {
      const cached = safeStorage.getItem('orion_cached_apps_v2');
      const cacheVer = safeStorage.getItem('orion_cache_ver');
      
      // Force clear if version mismatched
      if (cacheVer !== CACHE_VERSION) {
          return localAppsData.map(sanitizeApp) as AppItem[];
      }

      if (cached) {
          try {
              const parsed = JSON.parse(cached);
              if (Array.isArray(parsed) && parsed.length > 0) {
                 return parsed.map(sanitizeApp);
              }
          } catch (e) {
              console.warn("Cache load failed");
          }
      }
      return localAppsData.map(sanitizeApp) as AppItem[];
  });

  const appsRef = useRef(apps);
  useEffect(() => { appsRef.current = apps; }, [apps]);

  const [isLoading, setIsLoading] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [remoteConfig, setRemoteConfig] = useState<StoreConfig | null>(null);
  
  const [githubToken, setGithubToken] = useState(() => safeStorage.getItem('gh_token') || BUILT_IN_GH_TOKEN);
  const [isEditingToken, setIsEditingToken] = useState(false);

  const [useRemoteJson, setUseRemoteJson] = useState(() => {
      const pref = safeStorage.getItem('use_remote_json');
      return pref !== 'false'; 
  });

  const [installedVersions, setInstalledVersions] = useState<Record<string, string>>(() => {
      const saved = safeStorage.getItem('installed_apps');
      try {
          return saved ? JSON.parse(saved) : {};
      } catch (e) {
          return {};
      }
  });

  const [theme, setTheme] = useState<Theme>(() => {
    const saved = safeStorage.getItem('theme_preference') as Theme;
    return saved || 'light';
  });

  // Derived Data
  const socialLinks = remoteConfig?.socials || DEV_SOCIALS;
  const faqs = remoteConfig?.faqs || DEFAULT_FAQS;
  const devProfile = remoteConfig?.devProfile || DEFAULT_DEV_PROFILE;
  const supportEmail = remoteConfig?.supportEmail || DEFAULT_SUPPORT_EMAIL;
  const easterEggUrl = remoteConfig?.easterEggUrl || DEFAULT_EASTER_EGG;

  const registerInstall = (appId: string, version: string) => {
      const newRegistry = { ...installedVersions, [appId]: version };
      setInstalledVersions(newRegistry);
      safeStorage.setItem('installed_apps', JSON.stringify(newRegistry));
  };

  const saveGithubToken = (token: string) => {
      setGithubToken(token);
      safeStorage.setItem('gh_token', token);
      setIsEditingToken(false);
      setTimeout(() => loadApps(true), 500);
  };

  // --- CORE DATA ENGINE ---
  const loadApps = useCallback(async (isManualRefresh = false) => {
      if (isManualRefresh) setIsRefreshing(true);
      if (appsRef.current.length === 0) setIsLoading(true);

      try {
        let rawApps: AppItem[] = [];
        let mirrorData: Record<string, any> | null = null;
        let configData: StoreConfig | null = null;
        
        if (useRemoteJson) {
            // 1. FETCH CONFIG & MIRROR
            let activeAppsUrl = DEFAULT_APPS_JSON;
            let activeMirrorUrl = DEFAULT_MIRROR_JSON;

            try {
                const configReq = await fetchWithTimeout(`${REMOTE_CONFIG_URL}?t=${Date.now()}`, { timeout: 3000 });
                if (configReq.ok) {
                    configData = await configReq.json();
                    setRemoteConfig(configData);
                    
                    if (configData?.latestStoreVersion && configData?.storeDownloadUrl) {
                        if (compareVersions(configData.latestStoreVersion, CURRENT_STORE_VERSION) > 0) {
                            setStoreUpdateAvailable(true);
                            setStoreUpdateUrl(configData.storeDownloadUrl);
                            setShowStoreUpdateModal(true);
                        }
                    }

                    if (configData?.maintenanceMode) {
                        setErrorMsg(configData.maintenanceMessage || "Store is under maintenance");
                        setShowErrorToast(true);
                        setIsLoading(false);
                        setIsRefreshing(false);
                        return; 
                    }
                    if (configData?.appsJsonUrl) activeAppsUrl = configData.appsJsonUrl;
                    if (configData?.mirrorJsonUrl) activeMirrorUrl = configData.mirrorJsonUrl;
                }
            } catch (e) { console.warn("Config fetch warning", e); }

            // 2. FETCH APPS LIST
            try {
                const appsResponse = await fetchWithTimeout(`${activeAppsUrl}?t=${Date.now()}`);
                if (!appsResponse.ok) throw new Error("Failed to load apps config");
                rawApps = await appsResponse.json();
                if (!Array.isArray(rawApps)) throw new Error("Invalid remote data");
            } catch (e) {
                 console.warn("Remote apps load failed, using local");
                 rawApps = localAppsData as unknown as AppItem[];
            }

            // 3. FETCH MIRROR (OPTIMIZATION)
            try {
                 const mirrorReq = await fetchWithTimeout(`${activeMirrorUrl}?t=${Date.now()}`, { timeout: 5000 });
                 if (mirrorReq.ok) {
                    mirrorData = await mirrorReq.json();
                    if(mirrorData) console.log("Loaded Mirror Data");
                 }
            } catch (e) { console.warn("Mirror fetch failed"); }

        } else {
            rawApps = localAppsData as unknown as AppItem[];
        }

        rawApps = rawApps.map(sanitizeApp);

        // 🛡️ PRODUCTION SAFEGUARD: 
        // If we have no mirror and no manual refresh, DO NOT fallback to GitHub API for all apps.
        // It prevents hitting rate limits on boot.
        const canUseApi = isManualRefresh || !!githubToken;

        // 4. IDENTIFY MISSING DATA
        const repositoriesToFetch = new Set<string>();
        
        // Prepare Cache
        const repoCache = new Map<string, any[]>();
        if (mirrorData) {
            Object.keys(mirrorData).forEach(key => {
                const data = mirrorData![key];
                // Support both legacy single-object mirrors and new list-based mirrors
                repoCache.set(key, Array.isArray(data) ? data : [data]); 
            });
        }

        // Logic: If app has a valid downloadUrl defined in JSON, USE IT. Do not fetch API.
        rawApps.forEach(app => {
            const hasStaticLink = app.downloadUrl && app.downloadUrl !== '#' && app.downloadUrl.startsWith('http');
            const hasMirrorEntry = app.githubRepo && mirrorData && mirrorData[app.githubRepo];

            // Only fetch from API if: 
            // 1. No static link provided 
            // 2. No mirror data available
            // 3. We have a valid GitHub Repo to check
            if (!hasStaticLink && !hasMirrorEntry && app.githubRepo) {
                const cleanRepo = app.githubRepo.replace(/^https?:\/\/(www\.)?github\.com\//, '').replace(/\/$/, '');
                if (canUseApi) repositoriesToFetch.add(cleanRepo);
            }
        });

        // 5. FETCH MISSING RELEASES (Smart Queue)
        if (repositoriesToFetch.size > 0) {
             console.log(`Fetching ${repositoriesToFetch.size} repos from API...`);
             const CACHE_KEY_PREFIX = 'gh_smart_v3_';
             const CACHE_DURATION = githubToken ? 10 * 60 * 1000 : 60 * 60 * 1000; 
             const repoArray = shuffleArray(Array.from(repositoriesToFetch));
             const BATCH_SIZE = 2; // Reduced for safety

             const fetchRepoSmart = async (repo: string) => {
                const storageKey = `${CACHE_KEY_PREFIX}${repo}`;
                let cachedItem = null;
                const item = safeStorage.getItem(storageKey);
                if (item) cachedItem = JSON.parse(item);

                // Cache Hit
                if (cachedItem && (Date.now() - cachedItem.timestamp < CACHE_DURATION) && !isManualRefresh) {
                    repoCache.set(repo, cachedItem.data);
                    return;
                }

                const headers: HeadersInit = {};
                if (githubToken) headers['Authorization'] = `Bearer ${githubToken}`;
                if (cachedItem && cachedItem.etag) headers['If-None-Match'] = cachedItem.etag;

                try {
                    const res = await fetch(`https://api.github.com/repos/${repo}/releases`, { headers });
                    
                    if (res.status === 304 && cachedItem) {
                        repoCache.set(repo, cachedItem.data);
                        safeStorage.setItem(storageKey, JSON.stringify({ ...cachedItem, timestamp: Date.now() }));
                    } else if (res.ok) {
                        const data = await res.json();
                        repoCache.set(repo, data);
                        safeStorage.setItem(storageKey, JSON.stringify({ timestamp: Date.now(), data: data, etag: res.headers.get('ETag') }));
                    } else {
                        // Rate limited or Error -> Use Cache if available
                         if (cachedItem) repoCache.set(repo, cachedItem.data);
                    }
                } catch (e) {
                    if (cachedItem) repoCache.set(repo, cachedItem.data);
                }
            };

            for (let i = 0; i < repoArray.length; i += BATCH_SIZE) {
                const batch = repoArray.slice(i, i + BATCH_SIZE);
                await Promise.all(batch.map(repo => fetchRepoSmart(repo)));
                if (i + BATCH_SIZE < repoArray.length) await new Promise(resolve => setTimeout(resolve, 1000));
            }
        }

        // 6. MERGE & BUILD FINAL LIST
        const processedApps = rawApps.map(app => {
          // If we have a hardcoded URL in apps.json, respect it first (Fastest Path)
          if (app.downloadUrl !== '#' && app.downloadUrl.length > 5 && !isManualRefresh) {
              return app;
          }

          const cleanRepo = app.githubRepo?.replace(/^https?:\/\/(www\.)?github\.com\//, '').replace(/\/$/, '');
          let releasesData = cleanRepo ? repoCache.get(cleanRepo) : null;
          
          if (releasesData && !Array.isArray(releasesData)) releasesData = [releasesData];

          if (cleanRepo && releasesData && releasesData.length > 0) {
            let foundRelease = null;
            let matchingAssets = [];

            for (const release of releasesData) {
              const assets = release.assets || [];
              const apks = assets.filter((a: any) => a.name.toLowerCase().endsWith('.apk'));
              if (apks.length === 0) continue;

              if (app.releaseKeyword) {
                 const keyword = app.releaseKeyword.toLowerCase();
                 // Improved matching: Checks Asset Name OR Release Name/Tag
                 // This helps if you name the Release "CapCut v10" but the file is just "app.apk"
                 const nameMatch = release.name?.toLowerCase().includes(keyword);
                 const tagMatch = release.tag_name?.toLowerCase().includes(keyword);
                 const fileMatch = apks.some((a: any) => a.name.toLowerCase().includes(keyword));

                 if (nameMatch || tagMatch || fileMatch) {
                    // If matched by tag/title, we take all APKs. If matched by file, we filter specific ones.
                    const relevantAssets = fileMatch 
                        ? apks.filter((a: any) => a.name.toLowerCase().includes(keyword))
                        : apks;

                    if (relevantAssets.length > 0) {
                        foundRelease = release;
                        matchingAssets = relevantAssets;
                        break;
                    }
                 }
              } else {
                 foundRelease = release;
                 matchingAssets = apks;
                 break;
              }
            }

            if (foundRelease && matchingAssets.length > 0) {
              const variants: AppVariant[] = matchingAssets.map((asset: any) => ({
                arch: determineArch(asset.name),
                url: asset.browser_download_url
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
              const versionId = foundRelease.tag_name || foundRelease.published_at.split('T')[0];

              return {
                ...app,
                version: versionId,
                latestVersion: versionId,
                downloadUrl: variants[0].url,
                variants: variants,
                size: `${sizeMB} MB`,
              };
            }
          } 
          
          // FAILSAFE: If no release found (or rate limited), do NOT leave downloadUrl as '#'.
          // Point to the Releases page so user can at least download manually.
          if (cleanRepo && (!releasesData || releasesData.length === 0 || app.downloadUrl === '#')) {
              return {
                  ...app,
                  version: "View on GitHub",
                  latestVersion: "Unknown",
                  downloadUrl: `https://github.com/${cleanRepo}/releases`,
                  size: "?",
              };
          }
          
          return app;
        });

        setApps(processedApps);
        safeStorage.setItem('orion_cached_apps_v2', JSON.stringify(processedApps));
        safeStorage.setItem('orion_cache_ver', CACHE_VERSION);

      } catch (error) {
        console.error("Error initializing store:", error);
        setErrorMsg('Failed to load apps');
        setShowErrorToast(true);
      } finally {
        setIsLoading(false);
        setIsRefreshing(false);
      }
  }, [useRemoteJson, githubToken]);

  useEffect(() => {
      loadApps(false);
  }, [loadApps]);

  const handleManualRefresh = (e?: React.MouseEvent) => {
      e?.stopPropagation();
      loadApps(true); 
  };

  const toggleSourceMode = () => {
      const newValue = !useRemoteJson;
      setUseRemoteJson(newValue);
      safeStorage.setItem('use_remote_json', String(newValue));
  };

  useEffect(() => {
    const root = document.documentElement;
    root.classList.remove('light', 'dusk', 'dark');
    root.classList.add(theme);
    safeStorage.setItem('theme_preference', theme);
  }, [theme]);

  // SCROLL & UI HANDLERS
  useEffect(() => {
    const handleScroll = () => {
      const scrollY = window.scrollY;
      setShowScrollTop(scrollY > 300);
      setScrolledDown(scrollY > 20);
    };
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      if (!target.closest('#category-dropdown')) {
        setIsCategoryDropdownOpen(false);
      }
    };
    if (isCategoryDropdownOpen) document.addEventListener('click', handleClickOutside);
    return () => document.removeEventListener('click', handleClickOutside);
  }, [isCategoryDropdownOpen]);

  const categories = useMemo(() => {
    const currentPlatform = activeTab === 'android' ? Platform.ANDROID : Platform.PC;
    const currentApps = apps.filter(app => app.platform === currentPlatform);
    const cats = Array.from(new Set(currentApps.map(app => app.category)));
    return ['All', ...cats];
  }, [apps, activeTab]);

  const handleTabChange = (tab: Tab) => {
    setActiveTab(tab);
    setSearchQuery(''); 
    setSelectedCategory('All'); 
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleDownloadAction = (app: AppItem, url: string) => {
      const safe = sanitizeUrl(url);
      const isWebLink = !safe.toLowerCase().endsWith('.apk') && !safe.toLowerCase().endsWith('.exe');

      if (isWebLink) {
         window.open(safe, '_blank');
         return;
      }

      registerInstall(app.id, app.latestVersion);
      if (app.platform === Platform.PC) {
            window.open(safe, '_blank');
        } else {
            window.location.href = safe;
        }
        
        const isUpdate = installedVersions[app.id] && installedVersions[app.id] !== app.latestVersion;
        if (isUpdate) {
            setShowUpdateToast(true);
            setTimeout(() => setShowUpdateToast(false), 3000);
        } else {
            setShowInstallToast(true);
            setTimeout(() => setShowInstallToast(false), 3000);
        }
  };

  const handleDownload = (app: AppItem, specificUrl?: string) => {
    const urlToUse = specificUrl || app.downloadUrl;
    if (!urlToUse || urlToUse === '#' || urlToUse === '') {
        setErrorMsg('Download link not found');
        setShowErrorToast(true);
        setTimeout(() => setShowErrorToast(false), 3000);
        return;
    }
    handleDownloadAction(app, urlToUse);
  };

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


  const handleUpdateStore = () => {
      if(storeUpdateUrl) window.location.href = sanitizeUrl(storeUpdateUrl);
  };

  const toggleTheme = () => {
    let newTheme: Theme;
    if (theme === 'light') newTheme = 'dusk';
    else if (theme === 'dusk') newTheme = 'dark';
    else newTheme = 'light';
    setTheme(newTheme);
    setShowThemeToast(true);
    setTimeout(() => setShowThemeToast(false), 4000);
  };

  const scrollToTop = () => window.scrollTo({ top: 0, behavior: 'smooth' });

  const handleProfileClick = () => {
    const newCount = easterEggCount + 1;
    setEasterEggCount(newCount);
    if (newCount >= 8) {
      window.open(easterEggUrl, '_blank');
      setEasterEggCount(0);
      setIsLegend(true);
      safeStorage.setItem('isLegend', 'true');
    }
  };

  const resetLegendStatus = () => {
    setIsLegend(false);
    setEasterEggCount(0);
    localStorage.removeItem('isLegend');
  };

  const handleTitleClick = () => {
      if (isDevUnlocked) {
          setDevToastMsg("You are already a developer.");
          setShowDevToast(true);
          if (devToastTimerRef.current) clearTimeout(devToastTimerRef.current);
          devToastTimerRef.current = setTimeout(() => setShowDevToast(false), 2000);
          return;
      }

      const newCount = devTapCount + 1;
      setDevTapCount(newCount);
      
      const stepsNeeded = 9;
      const remaining = stepsNeeded - newCount;

      if (remaining <= 0) {
          setIsDevUnlocked(true);
          safeStorage.setItem('isDevUnlocked', 'true');
          setDevToastMsg("You are now a developer!");
          setShowDevToast(true);
          if (devToastTimerRef.current) clearTimeout(devToastTimerRef.current);
          devToastTimerRef.current = setTimeout(() => setShowDevToast(false), 3000);
      } else if (remaining <= 5) {
          setDevToastMsg(`You are ${remaining} steps away from being a developer.`);
          setShowDevToast(true);
          if (devToastTimerRef.current) clearTimeout(devToastTimerRef.current);
          devToastTimerRef.current = setTimeout(() => setShowDevToast(false), 2000);
      }
  };

  const renderHeader = () => (
    <header className="absolute top-0 left-0 w-full z-20 px-6 py-6 flex justify-between items-center">
        <div className="flex items-center gap-3 select-none">
            <div className="w-10 h-10 bg-primary text-white rounded-xl flex items-center justify-center shadow-lg shadow-primary/30 transform rotate-3">
                <i className="fas fa-shapes text-lg"></i>
            </div>
            <h1 
                onClick={handleTitleClick}
                className="text-2xl font-black tracking-tighter text-theme-text cursor-pointer active:scale-95 transition-transform"
            >
                Orion<span className="text-primary">Store</span>
            </h1>
        </div>
        
        <div className="flex items-center gap-3">
            {storeUpdateAvailable && (
                <button
                    onClick={handleUpdateStore}
                    className="px-3 py-2 rounded-xl bg-acid/20 text-acid-dark dark:text-acid border border-acid/30 font-bold text-xs flex items-center gap-2 animate-pulse"
                    title="Update OrionStore"
                >
                    <i className="fas fa-arrow-circle-up"></i>
                    <span className="hidden sm:inline">Update</span>
                </button>
            )}

            <button 
                onClick={toggleTheme}
                className="w-10 h-10 rounded-full bg-theme-element hover:bg-theme-hover flex items-center justify-center text-theme-sub hover:text-acid transition-all hover:scale-110 active:scale-95"
                title={`Theme: ${theme}`}
            >
                <i className={`fas ${theme === 'light' ? 'fa-sun' : theme === 'dusk' ? 'fa-cloud-sun' : 'fa-moon'}`}></i>
            </button>
        </div>
    </header>
  );

  const renderSearchBar = (placeholder: string) => (
    <div className="relative mb-6 group z-10 animate-fade-in">
        <div className="absolute -inset-0.5 bg-gradient-to-r from-acid via-primary to-neon rounded-2xl opacity-20 group-focus-within:opacity-100 transition duration-500 blur group-focus-within:blur-md"></div>
        <div className="relative flex items-center bg-theme-input rounded-2xl border border-theme-border p-1 shadow-lg transition-transform group-focus-within:scale-[1.01]">
            <div className="pl-4 pr-3 text-theme-sub group-focus-within:text-acid transition-colors">
                <i className="fas fa-search text-lg"></i>
            </div>
            <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder={placeholder}
                className="w-full bg-transparent border-none outline-none focus:outline-none focus:ring-0 text-theme-text placeholder-gray-500 h-12 font-medium text-lg"
            />
            {searchQuery && (
                <button 
                    onClick={() => setSearchQuery('')} 
                    className="w-10 h-10 flex items-center justify-center text-theme-sub hover:text-red-500 transition-colors outline-none focus:outline-none"
                >
                     <i className="fas fa-times"></i>
                </button>
            )}
        </div>
    </div>
  );

  const renderCategoryFilter = () => (
  <div className="relative z-10 mb-6 animate-fade-in flex items-center gap-3">
    <span className="text-sm font-bold text-theme-sub uppercase tracking-wider hidden md:block">Category:</span>

    <div id="category-dropdown" className="relative flex-1 md:flex-none md:w-64">
      <button
        onClick={() => setIsCategoryDropdownOpen(!isCategoryDropdownOpen)}
        className="w-full flex justify-between items-center py-3 px-4 bg-card border border-theme-border rounded-2xl font-bold text-theme-text shadow-sm hover:shadow-lg transition-all"
      >
        {selectedCategory}
        <i className={`fas fa-chevron-${isCategoryDropdownOpen ? 'up' : 'down'} text-xs transition-transform`}></i>
      </button>

      {isCategoryDropdownOpen && (
        <ul className={`absolute mt-2 w-full backdrop-blur-3xl rounded-2xl shadow-2xl border border-theme-border max-h-60 overflow-y-auto z-50 animate-fade-in no-scrollbar ${
          theme === 'light' ? 'bg-white/90' : 
          theme === 'dusk' ? 'bg-[#2a2d3e]/95' : 
          'bg-gray-900/90'
        }`}>
          {categories.map((category) => (
            <li
              key={category}
              onClick={() => {
                setSelectedCategory(category);
                setIsCategoryDropdownOpen(false);
              }}
              className={`px-4 py-3 cursor-pointer hover:bg-primary/20 transition-colors ${
                selectedCategory === category ? 'font-bold text-primary' : 'text-theme-text'
              }`}
            >
              {category}
            </li>
          ))}
        </ul>
      )}
    </div>

    <button
        onClick={handleManualRefresh}
        className={`shrink-0 w-12 h-12 rounded-2xl border border-theme-border bg-card flex items-center justify-center text-theme-sub hover:text-primary hover:border-primary transition-all shadow-sm active:scale-95 ${isRefreshing ? 'animate-spin text-primary' : ''}`}
        title="Refresh Data"
    >
        <i className="fas fa-sync-alt"></i>
    </button>
  </div>
);

  const renderLoadingState = () => (
    <div className="flex flex-col items-center justify-center py-24 animate-fade-in">
      <div className="w-12 h-12 border-4 border-theme-element border-t-primary rounded-full animate-spin mb-4"></div>
      <p className="text-theme-sub font-bold animate-pulse">Loading Store...</p>
    </div>
  );

  const renderEmptyState = () => (
    <div className="flex flex-col items-center justify-center py-12 text-center animate-fade-in opacity-60">
        <div className="w-20 h-20 bg-theme-element rounded-full flex items-center justify-center mb-4">
            <i className="fas fa-search text-3xl text-theme-sub"></i>
        </div>
        <h3 className="text-lg font-bold text-theme-text mb-1">No apps found</h3>
        <p className="text-theme-sub text-sm">Try searching for something else</p>
    </div>
  );

  const renderAppGrid = (platform: Platform) => {
    if (isLoading) return renderLoadingState();

    const filteredApps = apps.filter(app => {
        const appName = app.name || "";
        const appDesc = app.description || "";
        const matchesPlatform = app.platform === platform;
        const matchesSearch = appName.toLowerCase().includes(searchQuery.toLowerCase()) || 
                              appDesc.toLowerCase().includes(searchQuery.toLowerCase());
        const matchesCategory = selectedCategory === 'All' || app.category === selectedCategory;
        return matchesPlatform && matchesSearch && matchesCategory;
    });

    const title = platform === Platform.ANDROID ? 'Featured Apps' : 'PC Software';
    const placeholder = platform === Platform.ANDROID ? "Search Android Apps..." : "Search PC Software...";

    return (
        <div className="px-6 pt-2 pb-28 space-y-2 animate-fade-in">
           {remoteConfig?.announcement && (
                <div className="bg-indigo-500/10 border border-indigo-500/30 text-indigo-600 dark:text-indigo-300 p-4 rounded-2xl mb-6 flex items-start gap-3">
                    <i className="fas fa-bullhorn mt-1"></i>
                    <div>
                        <p className="font-bold text-sm">Announcement</p>
                        <p className="text-xs opacity-90">{remoteConfig.announcement}</p>
                    </div>
                </div>
           )}
        
           {platform === Platform.PC && (
               <div className="bg-gradient-to-r from-primary to-purple-600 p-6 rounded-3xl mb-8 text-white relative overflow-hidden shadow-2xl shadow-primary/20">
                    <div className="absolute -right-10 -bottom-10 opacity-20 text-9xl">
                        <i className="fas fa-laptop-code"></i>
                    </div>
                    <div className="relative z-10">
                        <h3 className="text-2xl font-bold mb-2">Desktop Station</h3>
                        <p className="text-indigo-100 font-medium mb-4 max-w-xs">
                            Professional grade open-source tools for your workstation. 
                        </p>
                    </div>
               </div>
           )}

           {renderSearchBar(placeholder)}
           {renderCategoryFilter()}

           <div className="flex items-center justify-between mb-4 mt-4">
                    <h2 className="text-xl font-bold text-theme-text">
                        {selectedCategory === 'All' ? title : `${selectedCategory} Apps`}
                    </h2>
                    <span className="text-xs font-bold bg-acid text-black px-3 py-1 rounded-full">
                        {filteredApps.length}
                    </span>
            </div>
            
            {filteredApps.length > 0 ? (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                    {filteredApps.map(app => {
                      const localVersion = installedVersions[app.id];
                      // Use compareVersions to check if remote is newer than local
                      // returns 1 if remote (app.latestVersion) > local
                      const isNewer = compareVersions(app.latestVersion, localVersion || '') > 0;
                      // Update available if: Installed AND Newer Version AND Version is not generic "Installed"
                      const hasUpdate = !!localVersion && isNewer && localVersion !== "Installed";
                      
                      return (
                        <AppCard 
                          key={app.id} 
                          app={app} 
                          onClick={setSelectedApp} 
                          hasUpdateNotification={hasUpdate}
                          localVersion={localVersion}
                        />
                      );
                    })}
                </div>
            ) : renderEmptyState()}
        </div>
    );
  };

  const renderAboutView = () => (
    <div className="p-6 pb-28 flex flex-col items-center text-center">
        <div 
            onClick={handleProfileClick}
            className="w-32 h-32 rounded-full p-1 mb-6 bg-gradient-to-br from-acid to-primary animate-pulse-slow cursor-pointer transition-transform active:scale-90 select-none relative z-30"
        >
            {isLegend && (
                <div className={`absolute -top-4 -right-10 z-50 transition-all duration-500 transform origin-bottom-left ${scrolledDown ? 'opacity-0 scale-0 translate-y-10' : 'opacity-100 scale-100 translate-y-0'}`}>
                    <div className="bg-gradient-to-br from-yellow-300 via-yellow-500 to-yellow-600 text-yellow-900 px-3 py-1.5 rounded-2xl shadow-[0_0_15px_rgba(250,204,21,0.6)] animate-shine border border-yellow-200 transform rotate-6 flex items-center gap-1 min-w-[70px] justify-center">
                        <i className="fas fa-crown text--[8px] animate-bounce"></i>
                        <span className="text-[9px] font-black tracking-wider uppercase">Legend</span>
                    </div>
                    <div className="absolute -bottom-0.5 left-2 w-2 h-2 bg-yellow-400 rounded-full shadow-lg z-40 border border-yellow-200"></div>
                    <div className="absolute -bottom-3 left-0 w-1 h-1 bg-yellow-500 rounded-full shadow-lg z-40 border border-yellow-200"></div>
                </div>
            )}

            {profileImgError ? (
                <div className="w-full h-full rounded-full bg-card border-4 border-card flex items-center justify-center relative overflow-hidden">
                    <div className="absolute inset-0 bg-gradient-to-br from-gray-900 to-black opacity-90"></div>
                    <div className="absolute w-full h-full bg-gradient-to-tr from-acid/20 to-neon/20 animate-pulse"></div>
                    <span className="relative text-7xl font-black text-transparent bg-clip-text bg-gradient-to-r from-acid via-primary to-neon tracking-tighter filter drop-shadow-lg">
                        R
                    </span>
                </div>
            ) : (
                <img 
                    src={devProfile.image} 
                    alt={devProfile.name} 
                    onError={() => setProfileImgError(true)}
                    className="w-full h-full rounded-full object-cover border-4 border-card bg-theme-element"
                />
            )}
        </div>
        
        <div className="relative z-0 flex flex-col items-center animate-fade-in w-full">
            <h2 className="text-3xl font-black text-theme-text mb-2">{devProfile.name}</h2>
            <p className="text-theme-sub max-w-md mb-8 text-lg">
                {devProfile.bio}
            </p>

            <div className="w-full max-w-md space-y-6">
                
                <div className="space-y-3">
                    <div className="flex items-center gap-2 px-2">
                        <div className="h-px bg-theme-border flex-1"></div>
                        <span className="text-xs font-bold text-theme-sub uppercase tracking-widest">Connect</span>
                        <div className="h-px bg-theme-border flex-1"></div>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                        <a href={socialLinks.github} target="_blank" rel="noreferrer" className="flex items-center justify-center gap-3 p-4 bg-card border border-theme-border rounded-2xl hover:scale-[1.02] transition-all cursor-pointer group shadow-sm">
                            <i className="fab fa-github text-2xl text-theme-text"></i>
                            <span className="font-bold text-theme-text">GitHub</span>
                        </a>
                        <a href={socialLinks.x} target="_blank" rel="noreferrer" className="flex items-center justify-center gap-3 p-4 bg-black dark:bg-white rounded-2xl hover:scale-[1.02] transition-all cursor-pointer shadow-lg shadow-black/10 group">
                            <div className="w-6 h-6 text-white dark:text-black flex items-center justify-center">
                                <svg viewBox="0 0 24 24" aria-hidden="true" className="w-full h-full fill-current">
                                    <path d="M18.901 1.153h3.68l-8.04 9.19L24 22.846h-7.406l-5.8-7.584-6.638 7.584H.474l8.6-9.83L0 1.154h7.594l5.243 6.932ZM17.61 20.644h2.039L6.486 3.24H4.298Z"></path>
                                </svg>
                            </div>
                        </a>
                        <a href={socialLinks.discord} target="_blank" rel="noreferrer" className="col-span-2 flex items-center justify-between p-4 bg-[#5865F2]/10 rounded-2xl hover:scale-[1.01] transition-all cursor-pointer border border-[#5865F2]/20">
                            <div className="flex items-center gap-3">
                                <div className="w-8 h-8 rounded-full bg-[#5865F2] text-white flex items-center justify-center text-sm">
                                    <i className="fab fa-discord"></i>
                                </div>
                                <span className="font-bold text-[#5865F2]">Join Discord Community</span>
                            </div>
                            <i className="fas fa-arrow-right text-[#5865F2] text-sm opacity-50"></i>
                        </a>
                    </div>
                </div>

                <div className="space-y-3">
                    <div className="flex items-center gap-2 px-2">
                        <div className="h-px bg-theme-border flex-1"></div>
                        <span className="text-xs font-bold text-theme-sub uppercase tracking-widest">Resources</span>
                        <div className="h-px bg-theme-border flex-1"></div>
                    </div>
                    <div className="flex flex-col gap-3">
                        <a href={socialLinks.coffee} target="_blank" rel="noreferrer" className="flex items-center justify-between p-4 bg-yellow-50 dark:bg-yellow-900/20 border-2 border-yellow-400 rounded-2xl hover:scale-[1.01] transition-all cursor-pointer shadow-lg shadow-yellow-400/20 group">
                            <div className="flex items-center gap-4">
                                <div className="w-10 h-10 rounded-full bg-yellow-400 text-yellow-900 flex items-center justify-center text-xl group-hover:rotate-12 transition-transform">
                                    <i className="fas fa-coffee"></i>
                                </div>
                                <div className="text-left">
                                    <span className="font-bold text-gray-900 dark:text-yellow-100 text-lg block">Buy me a coffee</span>
                                    <span className="text-xs text-yellow-600 dark:text-yellow-200 font-semibold">Support development</span>
                                </div>
                            </div>
                            <i className="fas fa-heart text-red-500 animate-bounce"></i>
                        </a>

                        <button 
                            onClick={() => setShowFAQ(true)}
                            className="flex items-center justify-between p-4 bg-purple-50 dark:bg-purple-900/20 border-2 border-dashed border-purple-300 dark:border-purple-700 rounded-2xl hover:scale-[1.01] transition-all cursor-pointer w-full group text-left"
                        >
                            <div className="flex items-center gap-4">
                                <div className="w-10 h-10 rounded-full bg-purple-400 text-white flex items-center justify-center text-xl group-hover:bg-purple-500 transition-colors">
                                    <i className="fas fa-question"></i>
                                </div>
                                <div>
                                    <span className="font-bold text-gray-900 dark:text-purple-100 text-lg block">FAQs</span>
                                    <span className="text-xs text-purple-600 dark:text-purple-300 font-semibold">Secrets & Safety</span>
                                </div>
                            </div>
                            <i className="fas fa-chevron-right text-purple-400 group-hover:translate-x-1 transition-transform"></i>
                        </button>
                    </div>
                </div>
            </div>
            
            {/* Developer Options (Hidden unless unlocked) */}
            {isDevUnlocked && (
                <div className="flex flex-col items-center gap-3 mt-8 w-full max-w-md animate-fade-in">
                     <div className="flex items-center gap-2 px-2 w-full">
                        <div className="h-px bg-theme-border flex-1"></div>
                        <span className="text-xs font-bold text-theme-sub uppercase tracking-widest">Developer Options</span>
                        <div className="h-px bg-theme-border flex-1"></div>
                    </div>

                    <div className="flex flex-col gap-3 w-full p-4 bg-card border border-theme-border rounded-2xl shadow-lg shadow-primary/5">
                         <div className="flex items-center justify-between">
                            <div className="text-left">
                                <span className="font-bold text-theme-text block">Data Source</span>
                                <span className="text-xs text-theme-sub">
                                    {useRemoteJson ? "Remote (Config)" : "Local Bundle"}
                                </span>
                            </div>
                            <button 
                                onClick={toggleSourceMode}
                                className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${useRemoteJson ? 'bg-primary text-white' : 'bg-theme-element text-theme-sub'}`}
                            >
                                {useRemoteJson ? "Remote" : "Local"}
                            </button>
                         </div>

                         <div className="h-px bg-theme-border w-full"></div>

                         {/* GitHub Token Input */}
                         <div className="flex flex-col gap-2 text-left">
                            <div className="flex justify-between items-center">
                                 <span className="font-bold text-theme-text text-sm">GitHub Token (PAT)</span>
                                 <button onClick={() => setIsEditingToken(!isEditingToken)} className="text-xs text-primary font-bold">
                                     {isEditingToken ? 'Cancel' : 'Edit'}
                                 </button>
                            </div>
                            <p className="text-[10px] text-theme-sub leading-tight">
                                 Use a personal access token to bypass rate limits (5000 req/hr vs 60 req/hr).
                            </p>
                            
                            {isEditingToken ? (
                                <div className="flex gap-2 mt-1">
                                    <input 
                                        type="password" 
                                        placeholder="ghp_xxxxxxxxxxxx"
                                        className="flex-1 bg-theme-input border border-theme-border rounded-lg px-3 py-2 text-sm outline-none focus:border-primary"
                                        onKeyDown={(e) => {
                                            if(e.key === 'Enter') saveGithubToken((e.target as HTMLInputElement).value)
                                        }}
                                    />
                                    <button 
                                        className="bg-primary text-white px-3 rounded-lg text-xs font-bold"
                                        onClick={(e) => {
                                             const input = (e.currentTarget.previousElementSibling as HTMLInputElement);
                                             saveGithubToken(input.value);
                                        }}
                                    >
                                        Save
                                    </button>
                                </div>
                            ) : (
                                <div className="flex items-center gap-2">
                                    <div className="bg-theme-element px-3 py-2 rounded-lg flex-1 flex items-center justify-between">
                                        <span className="text-xs font-mono text-theme-sub">
                                            {githubToken ? '••••••••••••••••••••' : 'No token set'}
                                        </span>
                                        {githubToken && <i className="fas fa-check-circle text-green-500 text-xs"></i>}
                                    </div>
                                    {githubToken && (
                                        <button onClick={() => saveGithubToken('')} className="w-8 h-8 flex items-center justify-center bg-red-500/10 text-red-500 rounded-lg">
                                            <i className="fas fa-trash text-xs"></i>
                                        </button>
                                    )}
                                </div>
                            )}
                         </div>
                    </div>

                    <button
                       onClick={() => {
                           localStorage.clear();
                           window.location.reload();
                       }}
                       className="px-4 py-2 rounded-xl bg-red-500/10 text-red-500 text-xs font-bold hover:bg-red-500/20 transition-colors flex items-center gap-2"
                    >
                       <i className="fas fa-trash-alt"></i>
                       Wipe Cache & Reset
                    </button>
                </div>
            )}

            {/* Footer Version & Source */}
            <div className="mt-12 mb-2 flex flex-col items-center gap-4 animate-fade-in">
                <div className="flex items-center gap-3 text-sm font-medium text-theme-sub">
                    <span className="opacity-60 font-mono">v{CURRENT_STORE_VERSION}</span>
                    <span className="w-1 h-1 rounded-full bg-theme-border"></span>
                    <div className={`px-3 py-1 rounded-full border text-xs font-bold flex items-center gap-2 shadow-sm ${
                        useRemoteJson 
                        ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-600 dark:text-emerald-400' 
                        : 'bg-amber-500/10 border-amber-500/20 text-amber-600 dark:text-amber-400'
                    }`}>
                        <span className="uppercase tracking-wider opacity-80">Source:</span>
                        <span>{useRemoteJson ? "Remote" : "Local"}</span>
                    </div>
                </div>
                <span className="text-xs font-mono text-theme-sub opacity-40">Made with 💜 for Geeks</span>
            </div>
        </div>
    </div>
  );

  return (
    <div className="min-h-screen bg-surface text-theme-text transition-colors duration-300 font-sans selection:bg-primary/30">
      {renderHeader()}

      <main className="max-w-7xl mx-auto w-full pt-24">
        {activeTab === 'android' && renderAppGrid(Platform.ANDROID)}
        {activeTab === 'pc' && renderAppGrid(Platform.PC)}
        {activeTab === 'about' && renderAboutView()}
      </main>

      <div className="fixed bottom-6 left-0 right-0 z-40 flex justify-center pointer-events-none">
         <nav className="bg-surface/90 backdrop-blur-xl border border-theme-border p-2 rounded-[2rem] shadow-2xl flex items-center gap-1 animate-slide-up pointer-events-auto">
           <button 
              onClick={() => handleTabChange('android')}
              className={`px-6 py-3 rounded-[1.5rem] font-bold transition-all duration-300 flex items-center gap-2 ${activeTab === 'android' ? 'bg-primary text-white shadow-lg shadow-primary/30 scale-105' : 'text-theme-sub hover:bg-theme-element'}`}
           >
              <i className="fab fa-android text-lg"></i>
              {activeTab === 'android' && <span className="animate-fade-in text-sm">Apps</span>}
           </button>

           <button 
              onClick={() => handleTabChange('pc')}
              className={`px-6 py-3 rounded-[1.5rem] font-bold transition-all duration-300 flex items-center gap-2 ${activeTab === 'pc' ? 'bg-primary text-white shadow-lg shadow-primary/30 scale-105' : 'text-theme-sub hover:bg-theme-element'}`}
           >
              <i className="fas fa-desktop text-lg"></i>
              {activeTab === 'pc' && <span className="animate-fade-in text-sm">PC</span>}
           </button>

           <button 
              onClick={() => handleTabChange('about')}
              className={`px-6 py-3 rounded-[1.5rem] font-bold transition-all duration-300 flex items-center gap-2 ${activeTab === 'about' ? 'bg-primary text-white shadow-lg shadow-primary/30 scale-105' : 'text-theme-sub hover:bg-theme-element'}`}
           >
              <i className="fas fa-code text-lg"></i>
              {activeTab === 'about' && <span className="animate-fade-in text-sm">Dev</span>}
           </button>
        </nav>
      </div>

        {selectedApp && (
            <AppDetail 
                app={selectedApp} 
                onClose={() => setSelectedApp(null)} 
                onDownload={handleDownload}
                onRedownload={handleRedownload}
                isInstalling={installingId === selectedApp.id}
                localVersion={installedVersions[selectedApp.id]}
                supportEmail={supportEmail}
                // Pass precise update status to ensure button consistency
                isUpdateAvailable={
                    !!installedVersions[selectedApp.id] && 
                    compareVersions(selectedApp.latestVersion, installedVersions[selectedApp.id] || '') > 0 && 
                    installedVersions[selectedApp.id] !== "Installed"
                }
            />
        )}

        {showFAQ && <FAQModal onClose={() => setShowFAQ(false)} items={faqs} />}
        
        {/* Store Update Modal */}
        {showStoreUpdateModal && (
            <div className="fixed inset-0 z-[100] flex items-center justify-center p-6 animate-fade-in">
                <div className="absolute inset-0 bg-black/70 backdrop-blur-md" onClick={() => setShowStoreUpdateModal(false)}></div>
                <div className="bg-surface border border-theme-border rounded-3xl p-6 max-w-sm w-full shadow-2xl relative z-10 animate-slide-up">
                    <div className="w-16 h-16 bg-acid text-black rounded-full flex items-center justify-center text-3xl mb-4 shadow-lg shadow-acid/30">
                        <i className="fas fa-rocket"></i>
                    </div>
                    <h3 className="text-2xl font-black text-theme-text mb-2">Update Available!</h3>
                    <p className="text-theme-sub mb-6">
                        A new version of OrionStore ({remoteConfig?.latestStoreVersion}) is available. Update now for the latest features and fixes.
                    </p>
                    <div className="flex gap-3">
                        <button 
                            onClick={() => setShowStoreUpdateModal(false)}
                            className="flex-1 py-3 rounded-xl font-bold text-theme-sub hover:bg-theme-element transition-colors"
                        >
                            Later
                        </button>
                        <button 
                            onClick={handleUpdateStore}
                            className="flex-1 py-3 rounded-xl font-bold bg-primary text-white shadow-lg shadow-primary/30 hover:bg-primary/90 transition-all active:scale-95"
                        >
                            Update Now
                        </button>
                    </div>
                </div>
            </div>
        )}

        <div className={`fixed top-24 left-1/2 -translate-x-1/2 z-[60] transition-all duration-500 ${showThemeToast ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-4 pointer-events-none'}`}>
           <div className="bg-surface/80 backdrop-blur-xl border border-theme-border text-theme-text px-5 py-2.5 rounded-full shadow-2xl flex items-center gap-3 font-bold">
               <i className={`fas ${theme === 'light' ? 'fa-sun text-yellow-500' : theme === 'dusk' ? 'fa-cloud-sun text-indigo-400' : 'fa-moon text-blue-400'}`}></i>
               <span>Switched to {theme.charAt(0).toUpperCase() + theme.slice(1)} Mode</span>
           </div>
        </div>

        <div className={`fixed top-36 left-1/2 -translate-x-1/2 z-50 transition-all duration-500 ${showInstallToast ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-10 pointer-events-none'}`}>
           <div className="bg-gray-900 dark:bg-white text-white dark:text-black px-6 py-3 rounded-full shadow-2xl flex items-center gap-3 font-bold">
               <i className="fas fa-check-circle text-green-500"></i>
               <span>Download Started</span>
           </div>
        </div>

        {/* Update Toast */}
        <div className={`fixed top-36 left-1/2 -translate-x-1/2 z-50 transition-all duration-500 ${showUpdateToast ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-10 pointer-events-none'}`}>
           <div className="bg-acid text-black px-6 py-3 rounded-full shadow-2xl flex items-center gap-3 font-bold border border-black/10">
               <i className="fas fa-sync-alt fa-spin"></i>
               <span>Update Started!</span>
           </div>
        </div>
        
        {/* Error Toast */}
        <div className={`fixed top-36 left-1/2 -translate-x-1/2 z-50 transition-all duration-500 ${showErrorToast ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-10 pointer-events-none'}`}>
           <div className="bg-red-100 dark:bg-red-900/50 text-red-800 dark:text-red-200 border border-red-200 dark:border-red-800 px-6 py-3 rounded-full shadow-2xl flex items-center gap-3 font-bold">
               <i className="fas fa-exclamation-circle"></i>
               <span>{errorMsg}</span>
           </div>
        </div>

        {/* Dev Step Toast */}
        <div className={`fixed top-36 left-1/2 -translate-x-1/2 z-[70] transition-all duration-200 ${showDevToast ? 'opacity-100 translate-y-0 scale-100' : 'opacity-0 translate-y-4 scale-95 pointer-events-none'}`}>
           <div className="bg-card/90 backdrop-blur-md text-theme-text border border-theme-border px-6 py-2 rounded-full shadow-2xl flex items-center gap-3 font-medium text-sm">
               <span>{devToastMsg}</span>
           </div>
        </div>

        <button
            onClick={scrollToTop}
            className={`fixed bottom-24 right-6 z-40 w-12 h-12 rounded-full bg-card border border-theme-border shadow-lg flex items-center justify-center text-primary transition-all duration-300 ${showScrollTop ? 'translate-y-0 opacity-100' : 'translate-y-10 opacity-0'}`}
        >
            <i className="fas fa-arrow-up"></i>
        </button>
    </div>
  );
};

export default App;
