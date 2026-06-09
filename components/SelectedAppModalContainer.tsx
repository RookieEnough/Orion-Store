import React, { useCallback, useMemo } from 'react';
import { shallow } from 'zustand/shallow';
import AppDetail from './AppDetail';
import { AppItem } from '../types';
import { useDataStore, useSettingsStore } from '../store/useAppStore';
import { hasAvailableUpdate } from '../utils/appVersioning';

interface SelectedAppModalContainerProps {
  app: AppItem;
  onClose: () => void;
  onDownload: (app: AppItem, url?: string) => void;
  isInstalling: boolean;
  supportEmail: string;
  onCancelDownload: (app: AppItem, id: string) => void;
  onDeleteReadyFile: (app: AppItem, fileName: string) => void;
  onNavigateToApp: (appId: string) => void;
  onExportAPK: (app: AppItem, fileName: string) => void;
  isScanning: boolean;
  onVirusTotalScan: () => void;
}

const SelectedAppModalContainer: React.FC<SelectedAppModalContainerProps> = ({
  app,
  onClose,
  onDownload,
  isInstalling,
  supportEmail,
  onCancelDownload,
  onDeleteReadyFile,
  onNavigateToApp,
  onExportAPK,
  isScanning,
  onVirusTotalScan
}) => {
  const { resolvedPackageName, localVersion, preferredStream } = useSettingsStore((state) => ({
    resolvedPackageName: state.resolvedPackageNames[app.id],
    localVersion: state.installedVersions[app.id],
    preferredStream: state.appStreams[app.id] || 'Stable'
  }), shallow);
  const {
    activeDownloadId,
    cleanupEntry,
    currentProgress,
    currentStatus,
    readyFileName,
    setPendingCleanup,
    setReadyToInstall
  } = useDataStore((state) => ({
    activeDownloadId: state.activeDownloads[app.id],
    cleanupEntry: state.pendingCleanup[app.id],
    currentProgress: state.downloadProgress[app.id],
    currentStatus: state.downloadStatus[app.id],
    readyFileName: state.readyToInstall[app.id],
    setPendingCleanup: state.setPendingCleanup,
    setReadyToInstall: state.setReadyToInstall
  }), shallow);

  const cleanupFileName = typeof cleanupEntry === 'string' ? cleanupEntry : cleanupEntry?.fileName;
  const displayApp = useMemo(
    () => ({
      ...app,
      packageName: resolvedPackageName || app.packageName
    }),
    [app, resolvedPackageName]
  );
  const isUpdateAvailable = useMemo(
    () => hasAvailableUpdate(app, localVersion, preferredStream),
    [app, localVersion, preferredStream]
  );

  const handleCleanupDone = useCallback(() => {
    const { pendingCleanup, readyToInstall } = useDataStore.getState();
    const nextCleanup = { ...pendingCleanup };
    const nextReady = { ...readyToInstall };
    delete nextCleanup[app.id];
    delete nextReady[app.id];
    setPendingCleanup(nextCleanup);
    setReadyToInstall(nextReady);
  }, [app.id, setPendingCleanup, setReadyToInstall]);

  return (
    <AppDetail
      app={displayApp}
      onClose={onClose}
      onDownload={onDownload}
      isInstalling={isInstalling}
      localVersion={localVersion}
      supportEmail={supportEmail}
      isUpdateAvailable={isUpdateAvailable}
      activeDownloadId={activeDownloadId}
      cleanupFileName={cleanupFileName}
      onCleanupDone={handleCleanupDone}
      currentProgress={currentProgress}
      currentStatus={currentStatus}
      readyFileName={readyFileName}
      onCancelDownload={onCancelDownload}
      onNavigateToApp={onNavigateToApp}
      onDeleteReadyFile={onDeleteReadyFile}
      onExportAPK={onExportAPK}
      isScanning={isScanning}
      onVirusTotalScan={onVirusTotalScan}
    />
  );
};

export default React.memo(SelectedAppModalContainer);
