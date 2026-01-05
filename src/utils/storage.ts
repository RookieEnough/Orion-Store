const CACHED_APPS_KEY = 'orion_cached_apps_v2';
const CACHE_VERSION_KEY = 'orion_cache_ver';

export const storage = {
  get: (key: string) => { try { return localStorage.getItem(key); } catch { return null; } },
  set: (key: string, value: string) => { try { localStorage.setItem(key, value); } catch { /* quota exceeded */ } },
  remove: (key: string) => { try { localStorage.removeItem(key); } catch { /* ignore */ } },
  clear: () => localStorage.clear(),
  
  getCachedApps: () => storage.get(CACHED_APPS_KEY),
  setCachedApps: (value: string) => storage.set(CACHED_APPS_KEY, value),
  getCacheVersion: () => storage.get(CACHE_VERSION_KEY),
  setCacheVersion: (value: string) => storage.set(CACHE_VERSION_KEY, value),
};
