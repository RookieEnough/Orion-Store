import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { get, set, del } from 'idb-keyval';
export type DevConfigLoadStatus = 'idle' | 'loading' | 'success' | 'error';

export interface DevConfigState {
  enabled: boolean;
  url: string;
  status: DevConfigLoadStatus;
  lastError: string | null;
  lastLoadedUrl: string | null;
  hasHydrated: boolean;
  setHasHydrated: (value: boolean) => void;
  setEnabled: (enabled: boolean) => void;
  setUrl: (url: string) => void;
  setStatus: (status: DevConfigLoadStatus) => void;
  setLastError: (message: string | null) => void;
  markLoaded: (url: string) => void;
  clearRuntimeState: () => void;
  reset: () => void;
}

const idbStorage = {
    getItem: async (name: string) => {
        const val = await get(name);
        return val ? String(val) : null;
    },
    setItem: async (name: string, value: string) => { await set(name, value); },
    removeItem: async (name: string) => { await del(name); }
} as const;

export const useDevConfigStore = create<DevConfigState>()(
  persist(
    (set) => ({
      enabled: false,
      url: '',
      status: 'idle',
      lastError: null,
      lastLoadedUrl: null,
      hasHydrated: false,
      setHasHydrated: (hasHydrated) => set({ hasHydrated }),
      setEnabled: (enabled) => set({ enabled }),
      setUrl: (url) => set({ url }),
      setStatus: (status) => set({ status }),
      setLastError: (lastError) => set({ lastError }),
      markLoaded: (url) => set({
        status: 'success',
        lastError: null,
        lastLoadedUrl: url
      }),
      clearRuntimeState: () => set({
        status: 'idle',
        lastError: null,
        lastLoadedUrl: null
      }),
      reset: () => set((state) => ({
        enabled: false,
        url: '',
        status: 'idle',
        lastError: null,
        lastLoadedUrl: null,
        hasHydrated: state.hasHydrated
      }))
    }),
    {
      name: 'orion-dev-config-storage',
      storage: createJSONStorage(() => idbStorage),
      partialize: (state) => ({
        enabled: state.enabled,
        url: state.url
      }),
      onRehydrateStorage: () => (state) => {
        state?.setHasHydrated(true);
      }
    }
  )
);
