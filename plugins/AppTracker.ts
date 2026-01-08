import { registerPlugin } from '@capacitor/core';

export interface AppInfoResult {
  installed: boolean;
  version: string;
  versionCode?: number;
}

export interface DownloadProgressResult {
  progress: number;
  status: 'PENDING' | 'RUNNING' | 'SUCCESSFUL' | 'FAILED';
  downloaded: number;
  total: number;
}

export interface AppTrackerPlugin {
  getAppInfo(options: { packageName: string }): Promise<AppInfoResult>;
  downloadFile(options: { url: string, fileName: string }): Promise<{ downloadId: string }>;
  getDownloadProgress(options: { downloadId: string }): Promise<DownloadProgressResult>;
  installPackage(options: { fileName: string }): Promise<void>;
  deleteFile(options: { fileName: string }): Promise<void>;
  cancelDownload(options: { downloadId: string }): Promise<void>;
  requestPermissions(): Promise<{ storage: string }>;
  setHighRefreshRate(options: { enable: boolean }): Promise<void>;
  shareApp(options: { title: string, text: string, url: string }): Promise<void>;
  launchApp(options: { packageName: string }): Promise<void>;
  uninstallApp(options: { packageName: string }): Promise<void>;
}

const AppTracker = registerPlugin<AppTrackerPlugin>('AppTracker');

export default AppTracker;
