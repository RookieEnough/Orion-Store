import { useEffect, useCallback } from 'react';
import { useLocalStorage } from './useLocalStorage';
import { STORAGE_KEYS } from '@/utils/storage';

export type Theme = 'light' | 'dusk' | 'dark';

export function useTheme() {
  const [theme, setTheme] = useLocalStorage<Theme>(STORAGE_KEYS.THEME, 'light');

  useEffect(() => {
    document.documentElement.classList.remove('light', 'dusk', 'dark');
    document.documentElement.classList.add(theme);
  }, [theme]);

  const cycleTheme = useCallback(() => {
    setTheme(t => t === 'light' ? 'dusk' : t === 'dusk' ? 'dark' : 'light');
  }, [setTheme]);

  return { theme, setTheme, cycleTheme };
}
