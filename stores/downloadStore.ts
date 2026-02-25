import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { AppItem } from '../types';

interface DownloadState {
  // Active downloads
  activeDownloads: Record<string, string>; // appId -> "downloadId|fileName"
  downloadProgressMap: Record<string, number>;
  downloadStatusMap: Record<string, string>;
  
  // Ready to install
  readyToInstall: Record<string, string>; // appId -> fileName
  
  // Pending cleanup
  pendingCleanup: Record<string, string>; // appId -> fileName
  
  // Installing
  installingId: string | null;
  showInstallToast: { app: AppItem; file: string } | null;
  
  // Installed versions
  installedVersions: Record<string, string>;
  
  // Actions
  startDownload: (appId: string, downloadId: string, fileName: string) => void;
  updateProgress: (appId: string, progress: number, status: string) => void;
  completeDownload: (appId: string, success: boolean) => void;
  cancelDownload: (appId: string) => void;
  
  setReadyToInstall: (appId: string, fileName: string) => void;
  removeReadyToInstall: (appId: string) => void;
  
  setPendingCleanup: (appId: string, fileName: string) => void;
  removePendingCleanup: (appId: string) => void;
  
  setInstallingId: (appId: string | null) => void;
  setShowInstallToast: (toast: { app: AppItem; file: string } | null) => void;
  
  setInstalledVersion: (appId: string, version: string) => void;
  removeInstalledVersion: (appId: string) => void;
  
  clearAllDownloads: () => void;
}

export const useDownloadStore = create<DownloadState>()(
  persist(
    (set, get) => ({
      // Initial state
      activeDownloads: {},
      downloadProgressMap: {},
      downloadStatusMap: {},
      readyToInstall: {},
      pendingCleanup: {},
      installingId: null,
      showInstallToast: null,
      installedVersions: {},
      
      // Download actions
      startDownload: (appId, downloadId, fileName) => set((state) => ({
        activeDownloads: {
          ...state.activeDownloads,
          [appId]: `${downloadId}|${fileName}`
        }
      })),
      
      updateProgress: (appId, progress, status) => set((state) => ({
        downloadProgressMap: { ...state.downloadProgressMap, [appId]: progress },
        downloadStatusMap: { ...state.downloadStatusMap, [appId]: status }
      })),
      
      completeDownload: (appId, success) => {
        const state = get();
        const downloadInfo = state.activeDownloads[appId];
        if (!downloadInfo) return;
        
        const [downloadId, fileName] = downloadInfo.split('|');
        
        set((state) => {
          const newActive = { ...state.activeDownloads };
          delete newActive[appId];
          
          const newProgress = { ...state.downloadProgressMap };
          delete newProgress[appId];
          
          const newStatus = { ...state.downloadStatusMap };
          delete newStatus[appId];
          
          if (success) {
            return {
              activeDownloads: newActive,
              downloadProgressMap: newProgress,
              downloadStatusMap: newStatus,
              readyToInstall: { ...state.readyToInstall, [appId]: fileName }
            };
          }
          
          return {
            activeDownloads: newActive,
            downloadProgressMap: newProgress,
            downloadStatusMap: newStatus
          };
        });
      },
      
      cancelDownload: (appId) => set((state) => {
        const newActive = { ...state.activeDownloads };
        delete newActive[appId];
        
        const newProgress = { ...state.downloadProgressMap };
        delete newProgress[appId];
        
        const newStatus = { ...state.downloadStatusMap };
        delete newStatus[appId];
        
        return {
          activeDownloads: newActive,
          downloadProgressMap: newProgress,
          downloadStatusMap: newStatus
        };
      }),
      
      // Ready to install
      setReadyToInstall: (appId, fileName) => set((state) => ({
        readyToInstall: { ...state.readyToInstall, [appId]: fileName }
      })),
      
      removeReadyToInstall: (appId) => set((state) => {
        const newReady = { ...state.readyToInstall };
        delete newReady[appId];
        return { readyToInstall: newReady };
      }),
      
      // Pending cleanup
      setPendingCleanup: (appId, fileName) => set((state) => ({
        pendingCleanup: { ...state.pendingCleanup, [appId]: fileName }
      })),
      
      removePendingCleanup: (appId) => set((state) => {
        const newCleanup = { ...state.pendingCleanup };
        delete newCleanup[appId];
        return { pendingCleanup: newCleanup };
      }),
      
      // Installing
      setInstallingId: (installingId) => set({ installingId }),
      setShowInstallToast: (showInstallToast) => set({ showInstallToast }),
      
      // Installed versions
      setInstalledVersion: (appId, version) => set((state) => ({
        installedVersions: { ...state.installedVersions, [appId]: version }
      })),
      
      removeInstalledVersion: (appId) => set((state) => {
        const newVersions = { ...state.installedVersions };
        delete newVersions[appId];
        return { installedVersions: newVersions };
      }),
      
      clearAllDownloads: () => set({
        activeDownloads: {},
        downloadProgressMap: {},
        downloadStatusMap: {},
        readyToInstall: {},
        pendingCleanup: {},
        installingId: null
      })
    }),
    {
      name: 'orion-download-storage',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        readyToInstall: state.readyToInstall,
        pendingCleanup: state.pendingCleanup,
        installedVersions: state.installedVersions
      }),
    }
  )
);

export default useDownloadStore;
