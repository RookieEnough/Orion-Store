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

export interface DangerousApp {
    packageName: string;
    name: string;
    permissions: string[];
    isSystem: boolean;
}

export interface SystemApp {
    packageName: string;
    name: string;
    isInstalled: boolean;
    isSystem: boolean;	
}

export interface NetworkSecurityResult {
    adbEnabled: boolean;
    adbWifiEnabled: boolean;
    isVpnActive: boolean;
    hasProxy: boolean;
    // WiFi Audit Fields
    encryptionType: 'WPA3' | 'WPA2' | 'WPA' | 'WEP' | 'OPEN' | 'UNKNOWN';
    dnsServers: string[];
    isCaptivePortal: boolean;
    isHiddenSsid: boolean;
}

export interface PermissionsStatusResult {
    storage: boolean;
    location: boolean;
    media: boolean;
    isLegacy: boolean;
}

export interface AppTrackerPlugin {
  getAppInfo(options: { packageName: string }): Promise<AppInfoResult>;
  downloadFile(options: { url: string, fileName: string }): Promise<{ downloadId: string }>;
  getDownloadProgress(options: { downloadId: string }): Promise<DownloadProgressResult>;
  checkActiveDownloads(): Promise<Record<string, boolean>>;
  installPackage(options: { fileName: string }): Promise<void>;
  canRequestPackageInstalls(): Promise<{ value: boolean }>;
  openInstallPermissionSettings(): Promise<void>;
  
  getInstalledPackages(): Promise<{ apps: { name: string, packageName: string }[] }>;

  requestShizukuPermission(): Promise<void>;
  installPackageShizuku(options: { fileName: string }): Promise<void>;
  
  getDangerousApps(): Promise<{ apps: DangerousApp[] }>;
  getSystemApps(): Promise<{ apps: SystemApp[] }>;
  toggleSystemApp(options: { packageName: string, enable: boolean }): Promise<void>; 
  
  revokePermission(options: { packageName: string, permission: string }): Promise<void>;
  extractApk(options: { packageName: string }): Promise<{ path: string }>;

  calculateHash(options: { filePath: string }): Promise<{ hash: string }>;
  scanDirectory(options?: { path?: string }): Promise<void>;
  abortScan(): Promise<void>;
  
  checkNetworkSecurity(): Promise<NetworkSecurityResult>;
  checkPermissionsStatus(): Promise<PermissionsStatusResult>;
  requestManageFilesPermission(): Promise<void>;
  requestUniversalStorage(): Promise<void>;
  getStorageMounts(): Promise<{ paths: string[] }>;
  isRooted(): Promise<{ rooted: boolean }>;
  
  requestBatteryOptimizationBypass(): Promise<void>;
  resetPermissions(): Promise<void>;
  openAppSettings(): Promise<void>;
  getSystemStatus(): Promise<{ isLowPowerMode: boolean, thermalStatus?: number, isBackgroundRestricted: boolean }>;

  deleteFile(options: { fileName: string }): Promise<void>;
  exportFile(options: { fileName: string }): Promise<{ path: string }>;
  saveFile(options: { fileName: string, content: string }): Promise<void>;
  cancelDownload(options: { downloadId: string }): Promise<void>;
  requestPermissions(options?: { alias?: string }): Promise<{ storage: string, location?: string }>;
  setHighRefreshRate(options: { enable: boolean }): Promise<void>;
  shareApp(options: { title: string, text: string, url: string }): Promise<void>;
  launchApp(options: { packageName: string }): Promise<void>;
  uninstallApp(options: { packageName: string }): Promise<void>;
}

const AppTracker = registerPlugin<AppTrackerPlugin>('AppTracker');

export default AppTracker;