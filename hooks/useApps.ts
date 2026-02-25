import { useState, useCallback, useRef, useEffect } from 'react';
import { AppItem, AppCategory, Platform, AppVariant } from '../types';
import { localAppsData } from '../localData';
import { CACHE_VERSION, NETWORK_TIMEOUT_MS } from '../constants';

// Constants
const CONFIG_URL_PRIMARY = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/config.json';
const APPS_URL_PRIMARY = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/apps.json';
const APPS_URL_FALLBACK = 'https://cdn.jsdelivr.net/gh/RookieEnough/Orion-Data@main/apps.json';
const CONFIG_URL_FALLBACK = 'https://cdn.jsdelivr.net/gh/RookieEnough/Orion-Data@main/config.json';
const DEFAULT_MIRROR_JSON = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/mirror.json';

const CURRENT_STORE_VERSION = '1.0.8';

// Utility functions
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
  screenshots: Array.isArray(app.screenshots) ? app.screenshots.map((s: string) => sanitizeUrl(s)) : []
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
  
  const vMatch = clean.match(/v(\d+(?:[.-]\d+)+)/);
  if (vMatch && vMatch[1]) return vMatch[1].replace(/-/g, '.');
  
  const semMatch = clean.match(/(\d+(?:\.\d+)+)/);
  if (semMatch && semMatch[1]) return semMatch[1];
  
  const simpleMatch = clean.match(/v(\d+)(?![a-z])/);
  if (simpleMatch && simpleMatch[1]) return simpleMatch[1];
  
  return null;
};

export interface UseAppsReturn {
  apps: AppItem[];
  isLoading: boolean;
  isRefreshing: boolean;
  errorMsg: string;
  showErrorToast: boolean;
  loadApps: (isManualRefresh?: boolean) => Promise<void>;
  clearError: () => void;
}

/**
 * Custom hook for managing apps data
 * Handles fetching from remote API, caching, and filtering
 */
export const useApps = (importedApps: AppItem[] = []): UseAppsReturn => {
  const [apps, setApps] = useState<AppItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [errorMsg, setErrorMsg] = useState('Failed to load apps');
  const [showErrorToast, setShowErrorToast] = useState(false);
  
  const isMounted = useRef(true);
  const useRemoteJson = localStorage.getItem('use_remote_json') !== 'false';
  const githubToken = localStorage.getItem('gh_token') || '';

  useEffect(() => {
    isMounted.current = true;
    return () => { isMounted.current = false; };
  }, []);

  const loadApps = useCallback(async (isManualRefresh = false) => {
    if (isManualRefresh) { setIsRefreshing(true); }
    if (apps.length === 0) setIsLoading(true);
    
    try {
      let rawApps: AppItem[] = [];
      let mirrorData: Record<string, any> | null = null;
      
      if (useRemoteJson) {
        let activeAppsUrl = APPS_URL_PRIMARY;
        let activeMirrorUrl = DEFAULT_MIRROR_JSON;
        const appsTs = isManualRefresh ? `?t=${Date.now()}` : '';
        
        // Fetch apps
        try {
          const appsResponse = await fetchWithRetry(`${activeAppsUrl}${appsTs}`, { cache: 'no-store' }, 2);
          if (!appsResponse.ok) throw new Error();
          rawApps = await appsResponse.json();
        } catch (e) {
          try {
            const fallbackRes = await fetchWithRetry(`${APPS_URL_FALLBACK}${appsTs}`, { cache: 'no-store' }, 2);
            rawApps = fallbackRes.ok ? await fallbackRes.json() : localAppsData as unknown as AppItem[];
          } catch (err) { 
            rawApps = localAppsData as unknown as AppItem[]; 
          }
        }
        
        // Fetch mirror data
        try {
          const mirrorReq = await fetchWithRetry(`${activeMirrorUrl}${appsTs}`, {}, 1);
          if (mirrorReq.ok) { 
            mirrorData = await mirrorReq.json();
          }
        } catch (e) {}
      } else {
        rawApps = localAppsData as unknown as AppItem[];
      }

      // Process apps with mirror data
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
        
        let cleanRepoPath = (app.githubRepo || app.repoUrl || '')
          .replace(/^https?:\/\/(www\.)?github\.com\//i, '')
          .replace(/\.git$/i, '')
          .replace(/\/$/, '');
        
        let releases = cleanRepoPath ? repoCache.get(cleanRepoPath.toLowerCase()) : null;
        
        if (cleanRepoPath && releases?.length) {
          let foundRelease = null;
          let targetAssets = [];
          
          for (const rel of releases) {
            const candidateAssets = (rel.assets || []).filter((a: any) => a.name.toLowerCase().endsWith('.apk'));
            if (candidateAssets.length === 0) continue;
            
            if (app.releaseKeyword) {
              const kw = app.releaseKeyword.toLowerCase();
              const matchesKeyword = (rel.name?.toLowerCase().includes(kw)) || 
                (rel.tag_name?.toLowerCase().includes(kw)) || 
                (candidateAssets.some((a: any) => a.name.toLowerCase().includes(kw)));
              
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
            const variants: AppVariant[] = targetAssets.map((a: any) => ({ 
              arch: determineArch(a.name), 
              url: a.browser_download_url 
            }));
            
            variants.sort((a, b) => {
              const priority = (name: string) => 
                name === 'Universal' ? 1 : 
                name === 'ARM64' ? 2 : 
                name === 'ARMv7' ? 3 : 4;
              return priority(a.arch) - priority(b.arch);
            });
            
            const tagName = foundRelease.tag_name || '';
            const fileName = targetAssets[0].name;
            let finalVersion = "Unknown";
            
            const fileVer = extractVersionString(fileName);
            const tagVer = extractVersionString(tagName);
            
            if (fileVer) { finalVersion = fileVer; }
            else if (tagVer && !['latest', 'all', 'nightly', 'pre-release'].includes(tagName.toLowerCase())) { 
              finalVersion = tagVer; 
            }
            else {
              const d = new Date(foundRelease.published_at || foundRelease.created_at || Date.now());
              finalVersion = `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`;
            }
            
            return { 
              ...app, 
              version: finalVersion, 
              latestVersion: finalVersion, 
              downloadUrl: variants[0].url, 
              variants, 
              size: `${(targetAssets[0].size/1048576).toFixed(1)} MB` 
            };
          }
        }
        return app;
      };

      if (isMounted.current) {
        const processedApps = rawApps.map(sanitizeApp).map(processItem);
        setApps(processedApps);
        
        // Cache
        try {
          localStorage.setItem('orion_cached_apps_v2', JSON.stringify(processedApps));
          localStorage.setItem('orion_cache_ver', CACHE_VERSION);
        } catch (e) {}
      }
    } catch (error) {
      if (isMounted.current && apps.length === 0) {
        setErrorMsg('Failed to load apps');
        setShowErrorToast(true);
      }
    } finally {
      if (isMounted.current) { 
        setIsLoading(false); 
        setIsRefreshing(false); 
      }
    }
  }, [useRemoteJson, githubToken, importedApps]);

  const clearError = useCallback(() => {
    setShowErrorToast(false);
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => loadApps(false), 500);
    return () => clearTimeout(timer);
  }, []);

  return {
    apps,
    isLoading,
    isRefreshing,
    errorMsg,
    showErrorToast,
    loadApps,
    clearError
  };
};

export default useApps;
