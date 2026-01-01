const STORAGE_KEYS = {
  THEME: 'theme_preference',
  DEV_UNLOCKED: 'isDevUnlocked',
  LEGEND: 'isLegend',
  GH_TOKEN: 'gh_token',
  INSTALLED_APPS: 'installed_apps',
  USE_REMOTE: 'use_remote_json',
  CACHED_APPS: 'orion_cached_apps_v2',
  CACHE_VERSION: 'orion_cache_ver',
} as const;

const KEYS_TO_PRESERVE = [
  STORAGE_KEYS.THEME,
  STORAGE_KEYS.DEV_UNLOCKED,
  STORAGE_KEYS.LEGEND,
  STORAGE_KEYS.GH_TOKEN,
  STORAGE_KEYS.INSTALLED_APPS,
  STORAGE_KEYS.USE_REMOTE,
];

export const storage = {
  get: (key: string): string | null => {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  },

  set: (key: string, value: string): void => {
    try {
      localStorage.setItem(key, value);
    } catch (e) {
      if (e instanceof DOMException && e.name === 'QuotaExceededError') {
        clearNonEssentialCache();
        try {
          localStorage.setItem(key, value);
        } catch {
          console.error('Storage full');
        }
      }
    }
  },

  remove: (key: string): void => {
    try {
      localStorage.removeItem(key);
    } catch {
      // Ignore
    }
  },

  clear: (): void => {
    localStorage.clear();
  },
};

function clearNonEssentialCache(): void {
  const preserved: Record<string, string> = {};
  KEYS_TO_PRESERVE.forEach((key) => {
    const val = storage.get(key);
    if (val) preserved[key] = val;
  });

  localStorage.clear();

  Object.entries(preserved).forEach(([k, v]) => {
    localStorage.setItem(k, v);
  });
}

export { STORAGE_KEYS };
