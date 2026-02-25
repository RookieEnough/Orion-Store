import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { AppItem } from '../types';

interface AppState {
  // Apps data
  apps: AppItem[];
  importedApps: AppItem[];
  isLoading: boolean;
  isRefreshing: boolean;
  errorMsg: string;
  showErrorToast: boolean;
  
  // Search & Filter
  searchQuery: string;
  selectedCategory: string;
  
  // Actions
  setApps: (apps: AppItem[]) => void;
  setImportedApps: (apps: AppItem[]) => void;
  addImportedApp: (app: AppItem) => void;
  setLoading: (loading: boolean) => void;
  setRefreshing: (refreshing: boolean) => void;
  setError: (msg: string, show: boolean) => void;
  setSearchQuery: (query: string) => void;
  setSelectedCategory: (category: string) => void;
  clearError: () => void;
}

export const useAppStore = create<AppState>()(
  persist(
    (set) => ({
      // Initial state
      apps: [],
      importedApps: [],
      isLoading: false,
      isRefreshing: false,
      errorMsg: 'Failed to load apps',
      showErrorToast: false,
      searchQuery: '',
      selectedCategory: 'All',
      
      // Actions
      setApps: (apps) => set({ apps }),
      setImportedApps: (apps) => set({ importedApps: apps }),
      addImportedApp: (app) => set((state) => ({ 
        importedApps: [...state.importedApps, app] 
      })),
      setLoading: (isLoading) => set({ isLoading }),
      setRefreshing: (isRefreshing) => set({ isRefreshing }),
      setError: (errorMsg, showErrorToast) => set({ errorMsg, showErrorToast }),
      setSearchQuery: (searchQuery) => set({ searchQuery }),
      setSelectedCategory: (selectedCategory) => set({ selectedCategory }),
      clearError: () => set({ showErrorToast: false }),
    }),
    {
      name: 'orion-app-storage',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({ 
        importedApps: state.importedApps,
        searchQuery: state.searchQuery,
        selectedCategory: state.selectedCategory,
      }),
    }
  )
);

export default useAppStore;
