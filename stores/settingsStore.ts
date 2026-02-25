import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

type Theme = 'light' | 'dusk' | 'dark' | 'oled';

interface SettingsState {
  // Theme
  theme: Theme;
  isOled: boolean;
  
  // Network
  wifiOnly: boolean;
  autoUpdateEnabled: boolean;
  
  // Storage
  deleteApk: boolean;
  
  // Interface
  disableAnimations: boolean;
  compactMode: boolean;
  highRefreshRate: boolean;
  
  // Tabs
  hiddenTabs: string[];
  
  // Remote
  useRemoteJson: boolean;
  githubToken: string;
  
  // Actions
  setTheme: (theme: Theme) => void;
  setIsOled: (isOled: boolean) => void;
  toggleWifiOnly: () => void;
  toggleAutoUpdate: () => void;
  toggleDeleteApk: () => void;
  toggleDisableAnimations: () => void;
  toggleCompactMode: () => void;
  toggleHighRefreshRate: () => void;
  toggleHiddenTab: (tabName: string) => void;
  setUseRemoteJson: (use: boolean) => void;
  setGithubToken: (token: string) => void;
}

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set, get) => ({
      // Initial state
      theme: 'light',
      isOled: false,
      wifiOnly: false,
      autoUpdateEnabled: false,
      deleteApk: true,
      disableAnimations: false,
      compactMode: false,
      highRefreshRate: false,
      hiddenTabs: [],
      useRemoteJson: true,
      githubToken: '',
      
      // Actions
      setTheme: (theme) => set({ theme }),
      setIsOled: (isOled) => set({ isOled }),
      toggleWifiOnly: () => set((state) => ({ wifiOnly: !state.wifiOnly })),
      toggleAutoUpdate: () => set((state) => ({ autoUpdateEnabled: !state.autoUpdateEnabled })),
      toggleDeleteApk: () => set((state) => ({ deleteApk: !state.deleteApk })),
      toggleDisableAnimations: () => set((state) => ({ disableAnimations: !state.disableAnimations })),
      toggleCompactMode: () => set((state) => ({ compactMode: !state.compactMode })),
      toggleHighRefreshRate: () => set((state) => ({ highRefreshRate: !state.highRefreshRate })),
      toggleHiddenTab: (tabName) => set((state) => {
        const tabs = state.hiddenTabs;
        if (tabs.includes(tabName)) {
          return { hiddenTabs: tabs.filter(t => t !== tabName) };
        }
        return { hiddenTabs: [...tabs, tabName] };
      }),
      setUseRemoteJson: (useRemoteJson) => set({ useRemoteJson }),
      setGithubToken: (githubToken) => set({ githubToken }),
    }),
    {
      name: 'orion-settings-storage',
      storage: createJSONStorage(() => localStorage),
    }
  )
);

export default useSettingsStore;
