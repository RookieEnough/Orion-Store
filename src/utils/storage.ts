export const STORAGE_KEYS = {
  THEME: 'theme_preference',
  DEV_UNLOCKED: 'isDevUnlocked',
  LEGEND: 'isLegend',
  GH_TOKEN: 'gh_token',
  INSTALLED_APPS: 'installed_apps',
  USE_REMOTE: 'use_remote_json',
  CACHED_APPS: 'orion_cached_apps_v2',
  CACHE_VERSION: 'orion_cache_ver',
} as const;

const KEYS_TO_PRESERVE: string[] = [
  STORAGE_KEYS.THEME,
  STORAGE_KEYS.DEV_UNLOCKED,
  STORAGE_KEYS.LEGEND,
  STORAGE_KEYS.GH_TOKEN,
  STORAGE_KEYS.INSTALLED_APPS,
  STORAGE_KEYS.USE_REMOTE,
];

export const storage = {
  get: (key: string) => { try { return localStorage.getItem(key); } catch { return null; } },
  set: (key: string, value: string) => {
    try { localStorage.setItem(key, value); }
    catch (e) {
      if ((e as DOMException).name === 'QuotaExceededError') {
        const saved = KEYS_TO_PRESERVE.map(k => [k, localStorage.getItem(k)] as const).filter((pair): pair is [string, string] => pair[1] !== null);
        localStorage.clear();
        saved.forEach(([k, v]) => localStorage.setItem(k, v));
        try { localStorage.setItem(key, value); } catch { /* full */ }
      }
    }
  },
  remove: (key: string) => { try { localStorage.removeItem(key); } catch { /* ignore */ } },
  clear: () => localStorage.clear(),
};
