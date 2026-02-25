import { useEffect, useCallback } from 'react';
import { useSettingsStore } from '../stores/settingsStore';

type Theme = 'light' | 'dusk' | 'dark' | 'oled';

/**
 * Custom hook for theme management
 * Handles theme switching and applies CSS classes
 */
export const useTheme = () => {
  const { 
    theme, 
    setTheme, 
    isOled, 
    setIsOled,
    disableAnimations 
  } = useSettingsStore();

  // Apply theme to document
  useEffect(() => {
    const root = document.documentElement;
    const body = document.body;
    
    // Remove all theme classes
    root.classList.remove('light', 'dusk', 'dark', 'oled');
    body.classList.remove('perf-mode');
    
    // Apply theme class
    if (theme === 'light') {
      root.classList.add('light');
    } else if (theme === 'dusk') {
      root.classList.add('dusk');
    } else if (theme === 'dark') {
      if (isOled) {
        root.classList.add('oled', 'dark');
      } else {
        root.classList.add('dark');
      }
    } else {
      root.classList.add(theme);
    }
    
    // Apply performance mode
    if (disableAnimations) {
      body.classList.add('perf-mode');
    }
  }, [theme, isOled, disableAnimations]);

  // Cycle through themes
  const cycleTheme = useCallback(() => {
    const themes: Theme[] = ['light', 'dusk', 'dark', 'oled'];
    const currentIndex = themes.indexOf(theme);
    const nextIndex = (currentIndex + 1) % themes.length;
    setTheme(themes[nextIndex]);
  }, [theme, setTheme]);

  // Toggle OLED mode
  const toggleOled = useCallback(() => {
    if (theme !== 'dark' && theme !== 'oled') {
      setTheme('dark');
    }
    setIsOled(!isOled);
  }, [theme, isOled, setTheme, setIsOled]);

  return {
    theme,
    isOled,
    setTheme,
    setIsOled,
    cycleTheme,
    toggleOled,
    disableAnimations
  };
};

export default useTheme;
