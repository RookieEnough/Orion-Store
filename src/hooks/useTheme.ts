import { useEffect, useCallback } from 'react';
import { useLocalStorage } from './useLocalStorage';
import { STORAGE_KEYS } from '@/utils/storage';

export type Theme = 'light' | 'dusk' | 'dark';

export function useTheme() {
  const [theme, setTheme] = useLocalStorage<Theme>(STORAGE_KEYS.THEME, 'light');

  useEffect(() => {
    const root = document.documentElement;
    root.classList.remove('light', 'dusk', 'dark');
    root.classList.add(theme);
  }, [theme]);

  const cycleTheme = useCallback(() => {
    setTheme((current) => {
      if (current === 'light') return 'dusk';
      if (current === 'dusk') return 'dark';
      return 'light';
    });
  }, [setTheme]);

  return { theme, setTheme, cycleTheme };
}
