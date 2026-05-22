import React, { useState, memo, useRef, lazy, Suspense } from 'react';
import { Capacitor } from '@capacitor/core';
import { useSettingsStore, useDataStore } from '../store/useAppStore';
import { AppItem } from '../types';
import AppTracker from '../plugins/AppTracker';
import { APP_FONT_OPTIONS, getAppFontDefinition } from '../constants';

// Lazy load heavy power modals
const ShizukuPowerModal = lazy(() => import('./ShizukuPowerModal'));
const SentinelModal = lazy(() => import('./SentinelModal'));

interface SettingsModalProps {
  onClose: () => void;
  allApps: AppItem[];
  availableUpdates: AppItem[];
  onTriggerUpdate: (app: AppItem) => void;
  onInstallApp: (app: AppItem, file: string) => void;
  onCancelDownloadById: (appId: string, dlId: string) => void;
  installingId: string | null;
  onUpdateAll: () => void;
  onNavigateToApp: (appId: string) => void;
  initialMenu?: SubMenu;
}

type SubMenu = 'none' | 'network' | 'storage' | 'visuals' | 'interface' | 'queue' | 'identity' | 'installer' | 'developer';

// --- ANTI-CHEAT CONFIGURATION ---
const SALT_KEY = "ORION_PROTOCOL_OMEGA_8842_SECURE_HASH_V1"; 

// --- CRYPTO UTILS ---
const generateHash = async (message: string): Promise<string> => {
    const msgBuffer = new TextEncoder().encode(message + SALT_KEY);
    const hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
};

const Toggle = memo(({ checked, onChange }: { checked: boolean; onChange: () => void }) => (
    <button
        onClick={onChange}
        className={`w-12 h-7 rounded-full p-1 transition-colors duration-200 ${checked ? 'bg-primary' : 'bg-theme-element border border-theme-border'}`}
    >
        <div className={`w-5 h-5 bg-white rounded-full shadow-md transform transition-transform duration-200 ${checked ? 'translate-x-5' : 'translate-x-0'}`}></div>
    </button>
));

// --- Identity panel: backup / restore only. Profile editing lives on the About tab. ---
interface IdentityPanelProps {
    handleExportIdentity: () => void;
    handleImportIdentity: (e: React.ChangeEvent<HTMLInputElement>) => void;
    fileInputRef: React.RefObject<HTMLInputElement>;
    importStatus: { msg: string; type: 'success' | 'error' | 'neutral' };
}

const IdentityPanel: React.FC<IdentityPanelProps> = memo(({ handleExportIdentity, handleImportIdentity, fileInputRef, importStatus }) => {
    return (
        <div className="space-y-6 animate-slide-up">
            {/* Backup / Restore */}
            <div className="p-5 bg-gradient-to-br from-pink-500/10 to-rose-500/10 border border-pink-500/20 rounded-2xl flex flex-col items-center text-center relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-pink-500/10">
                <div className="w-16 h-16 rounded-full bg-pink-500 text-white flex items-center justify-center text-2xl mb-3 shadow-lg shadow-pink-500/30"><i className="fas fa-fingerprint"></i></div>
                <h4 className="font-black text-theme-text text-lg">Save Your Progress</h4>
                <p className="text-xs text-theme-sub mt-2 leading-relaxed max-w-xs">Orion is serverless. Your badges, levels, and stats live on this device. Export your <b>Identity File (.osf)</b> to keep them safe.</p>
            </div>
            <div className="space-y-3">
                <button onClick={handleExportIdentity} className="w-full py-4 rounded-2xl bg-card border border-theme-border flex items-center justify-between px-6 hover:bg-theme-element transition-all active:scale-95 group relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5">
                    <div className="flex items-center gap-4"><div className="w-10 h-10 rounded-full bg-blue-500/10 text-blue-500 flex items-center justify-center"><i className="fas fa-file-export"></i></div><div className="text-left"><span className="block font-bold text-theme-text">Backup Identity</span><span className="text-[10px] text-theme-sub">Save .osf file</span></div></div><i className="fas fa-download text-theme-sub group-hover:text-primary"></i>
                </button>
                <button onClick={() => fileInputRef.current?.click()} className="w-full py-4 rounded-2xl bg-card border border-theme-border flex items-center justify-between px-6 hover:bg-theme-element transition-all active:scale-95 group relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5">
                    <div className="flex items-center gap-4"><div className="w-10 h-10 rounded-full bg-emerald-500/10 text-emerald-500 flex items-center justify-center"><i className="fas fa-file-import"></i></div><div className="text-left"><span className="block font-bold text-theme-text">Restore Identity</span><span className="text-[10px] text-theme-sub">Load .osf file</span></div></div><i className="fas fa-upload text-theme-sub group-hover:text-primary"></i>
                </button>
                <input type="file" ref={fileInputRef} className="hidden" accept=".osf" onChange={handleImportIdentity} />
            </div>
            {importStatus.msg && (<div className={`p-4 rounded-xl border flex items-center gap-3 ${importStatus.type === 'success' ? 'bg-green-500/10 border-green-500/20 text-green-600' : importStatus.type === 'error' ? 'bg-red-500/10 border-red-500/20 text-red-500' : 'bg-theme-element border-theme-border text-theme-sub'}`}><i className={`fas ${importStatus.type === 'success' ? 'fa-check-circle' : importStatus.type === 'error' ? 'fa-exclamation-triangle' : 'fa-info-circle'}`}></i><span className="text-xs font-bold">{importStatus.msg}</span></div>)}
        </div>
    );
});

const SettingsModal: React.FC<SettingsModalProps> = ({
  onClose,
  allApps,
  availableUpdates,
  onTriggerUpdate,
  onInstallApp,
  onCancelDownloadById,
  installingId,
  onUpdateAll,
  onNavigateToApp,
  initialMenu = 'none'
}) => {
  const settings = useSettingsStore();
  const data = useDataStore();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [activeMenu, setActiveMenu] = useState<SubMenu>(initialMenu);
  const [importStatus, setImportStatus] = useState<{msg: string, type: 'success' | 'error' | 'neutral'}>({ msg: '', type: 'neutral' });
  const [shizukuError, setShizukuError] = useState<string | null>(null);
  const [isFontPickerOpen, setIsFontPickerOpen] = useState(false);
  
  // Modal State Control
  const [activeModal, setActiveModal] = useState<'none' | 'guardian' | 'sentinel'>('none');
  
  const activeDlCount = Object.keys(data.activeDownloads).length;
  const readyCount = Object.keys(data.readyToInstall).length;
  const selectedFont = getAppFontDefinition(settings.appFont);

  const menuItems = [
      { id: 'identity', icon: 'fa-id-card', color: 'text-pink-500', bg: 'bg-pink-500/10', title: 'Backup & Restore', desc: 'Save progress, Transfer profile' },
      { id: 'network', icon: 'fa-wifi', color: 'text-blue-500', bg: 'bg-blue-500/10', title: 'Network & Updates', desc: 'WiFi only, Auto-discovery' },
      { id: 'installer', icon: 'fa-box-open', color: 'text-emerald-500', bg: 'bg-emerald-500/10', title: 'Orion Xtra', desc: 'Security Suite & Shizuku' },
      { id: 'storage', icon: 'fa-broom', color: 'text-orange-500', bg: 'bg-orange-500/10', title: 'Storage & Janitor', desc: 'Auto-cleanup, Space saving' },
      { id: 'queue', icon: 'fa-download', color: 'text-indigo-500', bg: 'bg-indigo-500/10', title: 'Download Queue', desc: `${activeDlCount} active, ${readyCount} ready`, badge: activeDlCount + readyCount },
      { id: 'visuals', icon: 'fa-palette', color: 'text-purple-500', bg: 'bg-purple-500/10', title: 'Visuals & Theme', desc: 'Glass, Haptics, Animations' },
      { id: 'interface', icon: 'fa-layer-group', color: 'text-green-500', bg: 'bg-green-500/10', title: 'Interface', desc: 'Customize tabs' },
  ];

  if (settings.isDevUnlocked) {
      menuItems.push({ id: 'developer', icon: 'fa-code', color: 'text-yellow-500', bg: 'bg-yellow-500/10', title: 'Developer Options', desc: 'Advanced testing & mocks' });
  }

  // --- IDENTITY LOGIC ---
  const handleExportIdentity = async () => {
      try {
          const state = useSettingsStore.getState();
          const dataState = useDataStore.getState();
          const exportData = {
              // Progress & legend
              adWatchCount: state.adWatchCount,
              submissionCount: state.submissionCount,
              lastSubmissionTime: state.lastSubmissionTime,
              lastLeaderboardSubmissionTime: state.lastLeaderboardSubmissionTime,
              isLegend: state.isLegend,
              isContributor: state.isContributor,
              isDevUnlocked: state.isDevUnlocked,
              // Visual preferences
              theme: state.theme,
              appFont: state.appFont,
              storeLayout: state.storeLayout,
              isOled: state.isOled,
              hiddenTabs: state.hiddenTabs,
              disableAnimations: state.disableAnimations,
              compactMode: state.compactMode,
              highRefreshRate: state.highRefreshRate,
              hapticEnabled: state.hapticEnabled,
              glassEffect: state.glassEffect,
              // Network / installer
              autoUpdateEnabled: state.autoUpdateEnabled,
              wifiOnly: state.wifiOnly,
              deleteApk: state.deleteApk,
              useShizuku: state.useShizuku,
              useRemoteJson: state.useRemoteJson,
              loadLocalData: state.loadLocalData,
              // App streams & ignored updates
              appStreams: state.appStreams,
              ignoredUpdates: state.ignoredUpdates,
              // Profile
              userProfile: state.userProfile,
              // Bundles
              customBundles: state.customBundles,
              removedBundleApps: state.removedBundleApps,
              extraBundleApps: state.extraBundleApps,
              // Favorites
              favorites: dataState.favorites,
              timestamp: Date.now()
          };

          const jsonString = JSON.stringify(exportData);
          const signature = await generateHash(jsonString);

          const finalPackage = { data: exportData, sig: signature, ver: "1.1" };
          const rawContent = btoa(unescape(encodeURIComponent(JSON.stringify(finalPackage))));
          const fileName = `orion_identity_${Date.now()}.osf`;

          if (Capacitor.isNativePlatform()) {
              await AppTracker.saveFile({ fileName, content: rawContent });
              setImportStatus({ msg: 'Identity saved successfully.', type: 'success' });
          } else {
              const blob = new Blob([rawContent], { type: 'text/plain' });
              const url = URL.createObjectURL(blob);
              const a = document.createElement('a');
              a.href = url;
              a.download = fileName;
              document.body.appendChild(a);
              a.click();
              document.body.removeChild(a);
              URL.revokeObjectURL(url);
              setImportStatus({ msg: 'Identity exported to Downloads.', type: 'success' });
          }
      } catch (e: any) {
          setImportStatus({ msg: 'Export failed: ' + (e.message || 'Unknown Error'), type: 'error' });
      }
  };

  const handleImportIdentity = async (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (!file) return;

      const reader = new FileReader();
      reader.onload = async (event) => {
          try {
              const raw = event.target?.result as string;
              let jsonStr: string;
              try {
                  jsonStr = decodeURIComponent(escape(atob(raw)));
              } catch {
                  jsonStr = atob(raw);
              }
              const pkg = JSON.parse(jsonStr);

              if (!pkg.data || !pkg.sig) throw new Error("Invalid Save File");

              const reCalcSig = await generateHash(JSON.stringify(pkg.data));
              if (reCalcSig !== pkg.sig) {
                  setImportStatus({ msg: 'Tampering Detected. Save integrity check failed.', type: 'error' });
                  return;
              }

              const d = pkg.data;
              const currentState = useSettingsStore.getState();
              useSettingsStore.setState({
                  ...currentState,
                  // Progress & legend
                  adWatchCount: d.adWatchCount ?? currentState.adWatchCount,
                  submissionCount: d.submissionCount ?? currentState.submissionCount,
                  lastSubmissionTime: d.lastSubmissionTime ?? currentState.lastSubmissionTime,
                  lastLeaderboardSubmissionTime: d.lastLeaderboardSubmissionTime ?? currentState.lastLeaderboardSubmissionTime,
                  isLegend: d.isLegend ?? currentState.isLegend,
                  isContributor: d.isContributor ?? currentState.isContributor,
                  isDevUnlocked: d.isDevUnlocked ?? currentState.isDevUnlocked,
                  // Visual
                  theme: d.theme ?? currentState.theme,
                  appFont: d.appFont ?? currentState.appFont,
                  storeLayout: d.storeLayout ?? currentState.storeLayout,
                  isOled: d.isOled ?? currentState.isOled,
                  hiddenTabs: Array.isArray(d.hiddenTabs) ? d.hiddenTabs : currentState.hiddenTabs,
                  disableAnimations: d.disableAnimations ?? currentState.disableAnimations,
                  compactMode: d.compactMode ?? currentState.compactMode,
                  highRefreshRate: d.highRefreshRate ?? currentState.highRefreshRate,
                  hapticEnabled: d.hapticEnabled ?? currentState.hapticEnabled,
                  glassEffect: d.glassEffect ?? currentState.glassEffect,
                  // Network/installer
                  autoUpdateEnabled: d.autoUpdateEnabled ?? currentState.autoUpdateEnabled,
                  wifiOnly: d.wifiOnly ?? currentState.wifiOnly,
                  deleteApk: d.deleteApk ?? currentState.deleteApk,
                  useShizuku: d.useShizuku ?? currentState.useShizuku,
                  useRemoteJson: d.useRemoteJson ?? currentState.useRemoteJson,
                  loadLocalData: d.loadLocalData ?? currentState.loadLocalData,
                  // Streams / ignored updates
                  appStreams: (d.appStreams && typeof d.appStreams === 'object') ? d.appStreams : currentState.appStreams,
                  ignoredUpdates: (d.ignoredUpdates && typeof d.ignoredUpdates === 'object') ? d.ignoredUpdates : currentState.ignoredUpdates,
                  // Profile
                  userProfile: d.userProfile ?? currentState.userProfile,
                  // Bundles
                  customBundles: Array.isArray(d.customBundles) ? d.customBundles : currentState.customBundles,
                  removedBundleApps: (d.removedBundleApps && typeof d.removedBundleApps === 'object') ? d.removedBundleApps : currentState.removedBundleApps,
                  extraBundleApps: (d.extraBundleApps && typeof d.extraBundleApps === 'object') ? d.extraBundleApps : currentState.extraBundleApps,
              });
              if (Array.isArray(d.favorites)) {
                  useDataStore.setState({ favorites: d.favorites });
              }

              setImportStatus({ msg: 'Identity restored successfully!', type: 'success' });
              setTimeout(() => window.location.reload(), 1500); 

          } catch (err) {
              setImportStatus({ msg: 'Corrupt or incompatible save file.', type: 'error' });
          }
      };
      reader.readAsText(file);
  };

  const handleShizukuToggle = async () => {
      setShizukuError(null);
      if (!settings.useShizuku) {
          if (Capacitor.isNativePlatform()) {
              try {
                  await AppTracker.requestShizukuPermission();
                  settings.toggleUseShizuku();
              } catch (e: any) {
                  const msg = e?.message || "Permission Denied";
                  if (msg.includes("Shizuku is NOT running")) {
                      setShizukuError("Shizuku service is not running.");
                  } else {
                      setShizukuError("Permission was denied by user.");
                  }
              }
          } else {
              settings.toggleUseShizuku();
          }
      } else {
          settings.toggleUseShizuku();
      }
  };

  const renderIdentitySettings = () => <IdentityPanel
      handleExportIdentity={handleExportIdentity}
      handleImportIdentity={handleImportIdentity}
      fileInputRef={fileInputRef}
      importStatus={importStatus}
  />;

  const renderNetworkSettings = () => (
      <div className="space-y-4 animate-slide-up">
          <div className="bg-card border border-theme-border rounded-2xl p-4 flex items-center justify-between shadow-sm relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5"><div><h4 className="font-bold text-theme-text">WiFi-Only Mode</h4><p className="text-[10px] text-theme-sub mt-1">Block downloads on cellular data.</p></div><Toggle checked={settings.wifiOnly} onChange={settings.toggleWifiOnly} /></div>
          <div className="bg-card border border-theme-border rounded-2xl p-4 flex items-center justify-between shadow-sm relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5"><div><h4 className="font-bold text-theme-text">Auto-Update Apps</h4><p className="text-[10px] text-theme-sub mt-1">Automatically download updates in background.</p></div><Toggle checked={settings.autoUpdateEnabled} onChange={settings.toggleAutoUpdate} /></div>
      </div>
  );

  const renderInstallerSettings = () => (
      <div className="space-y-4 animate-slide-up">
          {/* COSMIC SHIELD BUTTON - GUARDIAN */}
          <button 
              onClick={() => setActiveModal('guardian')}
              className="w-full py-5 bg-gradient-to-r from-violet-600 via-indigo-600 to-slate-900 text-white rounded-3xl font-black flex items-center justify-center gap-4 shadow-xl shadow-indigo-500/20 active:scale-95 transition-all group relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-indigo-500/20"
          >
              <div className="absolute inset-0 bg-[url('https://www.transparenttextures.com/patterns/stardust.png')] opacity-20 animate-pulse"></div>
              <div className="relative w-10 h-10 flex items-center justify-center">
                  <i className="fas fa-shield-halved text-3xl text-indigo-300 drop-shadow-lg"></i>
                  <i className="fas fa-meteor text-xs text-white absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 animate-ping"></i>
                  <i className="fas fa-meteor text-xs text-white absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2"></i>
              </div>
              <div className="text-left">
                  <span className="block text-lg tracking-tight">Orion Guardian</span>
                  <span className="text-[10px] font-medium text-indigo-200 uppercase tracking-widest">System Level Control</span>
              </div>
              <i className="fas fa-chevron-right opacity-50 group-hover:translate-x-1 transition-transform"></i>
          </button>

          {/* SENTINEL BUTTON - DEDICATED */}
          <button 
              onClick={() => setActiveModal('sentinel')}
              className="w-full py-5 bg-gradient-to-r from-emerald-400 via-green-500 to-teal-700 text-white rounded-3xl font-black flex items-center justify-center gap-4 shadow-xl shadow-emerald-500/20 active:scale-95 transition-all group relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-emerald-500/20"
          >
              <div className="absolute inset-0 bg-[url('https://www.transparenttextures.com/patterns/carbon-fibre.png')] opacity-10"></div>
              <div className="relative w-10 h-10 flex items-center justify-center">
                  <i className="fas fa-heart-pulse text-3xl text-emerald-100 drop-shadow-lg animate-pulse"></i>
              </div>
              <div className="text-left">
                  <span className="block text-lg tracking-tight">Orion Sentinel</span>
                  <span className="text-[10px] font-medium text-emerald-100 uppercase tracking-widest">Threat Detection</span>
              </div>
              <i className="fas fa-chevron-right opacity-50 group-hover:translate-x-1 transition-transform"></i>
          </button>

          <div className={`bg-card border border-theme-border rounded-2xl p-4 flex items-center justify-between shadow-sm transition-colors ${shizukuError ? 'border-red-500/30 bg-red-500/5' : ''} relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5`}>
              <div><h4 className="font-bold text-theme-text">Silent Install</h4><p className="text-[10px] text-theme-sub mt-1">Updates & Install apps in background without prompts.</p></div>
              <Toggle checked={settings.useShizuku} onChange={handleShizukuToggle} />
          </div>
          {shizukuError && (<div className="px-4 py-3 bg-theme-element rounded-xl border border-theme-border flex items-center gap-3 animate-slide-up"><i className="fas fa-exclamation-circle text-red-500 text-sm"></i><p className="text-[10px] font-bold text-theme-sub">{shizukuError}</p></div>)}
          
          <div className="p-4 bg-theme-element/50 rounded-2xl border border-theme-border flex gap-3 relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5"><i className="fas fa-terminal text-theme-sub text-lg mt-0.5"></i><p className="text-xs text-theme-sub font-medium leading-relaxed">Requires <button onClick={() => onNavigateToApp('shizuku')} className="text-primary underline font-bold hover:text-primary/80 transition-colors inline">Shizuku</button> app. Enabling this allows Orion to install updates & APKs with one tap.</p></div>
          
          {/* Manual Rescan Button */}
          <button 
              onClick={() => { window.location.reload(); onClose(); }}
              className="w-full py-3 bg-card border border-theme-border rounded-2xl font-bold text-xs flex items-center justify-center gap-2 hover:bg-theme-element active:scale-95 transition-all text-theme-sub relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5"
          >
              <i className="fas fa-sync-alt"></i>
              <span>Force Rescan Packages (Reload App)</span>
          </button>
      </div>
  );

  const renderStorageSettings = () => (
      <div className="space-y-4 animate-slide-up">
          <div className="bg-card border border-theme-border rounded-2xl p-4 flex items-center justify-between shadow-sm relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5"><div><h4 className="font-bold text-theme-text">Auto-Cleanup Installer</h4><p className="text-[10px] text-theme-sub mt-1">Silently delete installed APKs on app startup.</p></div><Toggle checked={settings.deleteApk} onChange={settings.toggleDeleteApk} /></div>
          <div className="p-4 bg-theme-element/50 rounded-2xl border border-theme-border flex gap-3 relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5"><i className="fas fa-info-circle text-theme-sub text-lg mt-0.5"></i><p className="text-xs text-theme-sub font-medium leading-relaxed">When enabled, Orion acts as a "Janitor". Files are kept during your session but wiped next time you open the app.</p></div>
      </div>
  );

  const renderVisuals = () => (
      <div className="space-y-4 animate-slide-up">
          <div className="bg-card border border-theme-border rounded-2xl p-4 shadow-sm relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5">
              <button
                  onClick={() => setIsFontPickerOpen(true)}
                  className="w-full flex items-center justify-between gap-3 text-left"
              >
                  <div>
                      <h4 className="font-bold text-theme-text">Change Font</h4>
                      <p className="text-[10px] text-theme-sub mt-1">Apply a font across the entire app.</p>
                  </div>
                  <div className="flex items-center gap-3">
                      <div className="min-w-0 text-right">
                          <span
                              className="block text-sm font-bold text-theme-text truncate"
                              style={{ fontFamily: selectedFont.family }}
                          >
                              {selectedFont.label}
                          </span>
                          <span className="text-[10px] text-theme-sub">Tap to preview</span>
                      </div>
                      <i className="fas fa-chevron-right text-theme-sub text-xs"></i>
                  </div>
              </button>
          </div>
          <div className="bg-card border border-theme-border rounded-2xl p-4 flex items-center justify-between shadow-sm relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5"><div><h4 className="font-bold text-theme-text">Haptic Feedback</h4><p className="text-[10px] text-theme-sub mt-1">Vibration on interactions.</p></div><Toggle checked={settings.hapticEnabled} onChange={settings.toggleHaptic} /></div>
          <div className="bg-card border border-theme-border rounded-2xl p-4 flex items-center justify-between shadow-sm relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5"><div><h4 className="font-bold text-theme-text">Glass Effect</h4><p className="text-[10px] text-theme-sub mt-1">Transparent blur on headers & tabs.</p></div><Toggle checked={settings.glassEffect} onChange={settings.toggleGlass} /></div>
          <div className="h-px bg-theme-border w-full"></div>
          <div className="bg-card border border-theme-border rounded-2xl p-4 flex items-center justify-between shadow-sm relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5"><div><h4 className="font-bold text-theme-text">Smooth Motion</h4><p className="text-[10px] text-theme-sub mt-1">Unlock Maximum Refresh Rate.</p></div><Toggle checked={settings.highRefreshRate} onChange={settings.toggleHighRefreshRate} /></div>
          <div className="bg-card border border-theme-border rounded-2xl p-4 shadow-sm relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5"><div className="flex items-center justify-between mb-2"><div><h4 className="font-bold text-theme-text">OLED Black Mode</h4><p className="text-[10px] text-theme-sub mt-1">True black for Dark theme.</p></div><Toggle checked={settings.isOled} onChange={settings.toggleOled} /></div></div>
          <div className="bg-card border border-theme-border rounded-2xl p-4 flex items-center justify-between shadow-sm relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5"><div><h4 className="font-bold text-theme-text">Disable Animations</h4><p className="text-[10px] text-theme-sub mt-1">Snappier UI on older devices.</p></div><Toggle checked={settings.disableAnimations} onChange={settings.toggleDisableAnimations} /></div>
          <div className="bg-card border border-theme-border rounded-2xl p-4 flex items-center justify-between shadow-sm relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5"><div><h4 className="font-bold text-theme-text">Compact Mode</h4><p className="text-[10px] text-theme-sub mt-1">Shrink app cards for more density.</p></div><Toggle checked={settings.compactMode} onChange={settings.toggleCompactMode} /></div>
      </div>
  );

  const renderFontPicker = () => (
      <div
          className="backdrop-scrim absolute inset-0 z-20 flex items-end justify-center bg-black/45 p-3 backdrop-blur-sm animate-fade-in sm:items-center sm:p-6"
          onClick={() => setIsFontPickerOpen(false)}
      >
          <div
              className="w-full max-w-md overflow-hidden rounded-[2.2rem] border border-theme-border bg-surface shadow-2xl animate-slide-up"
              onClick={(event) => event.stopPropagation()}
          >
              <div className="flex items-center justify-between border-b border-theme-border px-5 py-4">
                  <div>
                      <h4 className="text-lg font-black text-theme-text">Choose Font</h4>
                      <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-theme-sub">Preview before applying</p>
                  </div>
                  <button
                      type="button"
                      onClick={() => setIsFontPickerOpen(false)}
                      className="flex h-10 w-10 items-center justify-center rounded-full border border-theme-border bg-theme-element text-theme-text transition-colors hover:bg-theme-hover"
                  >
                      <i className="fas fa-times"></i>
                  </button>
              </div>

              <div className="space-y-4 p-5">
                  <div
                      className="rounded-[1.8rem] border border-theme-border bg-card px-5 py-5 shadow-sm"
                      style={{ fontFamily: selectedFont.family }}
                  >
                      <span className="text-[10px] font-black uppercase tracking-[0.2em] text-primary/80">Live Preview</span>
                      <h5 className="mt-2 text-2xl font-black tracking-tight text-theme-text">Orion Store</h5>
                      <p className="mt-2 text-sm text-theme-sub">Fast updates, clean cards, and smooth browsing across the whole app.</p>
                      <p className="mt-3 text-xs font-bold text-theme-text">Aa Bb Cc 123</p>
                  </div>

                  <div className="max-h-[52vh] space-y-2 overflow-y-auto no-scrollbar">
                      {APP_FONT_OPTIONS.map((font) => {
                          const isActive = settings.appFont === font.key;
                          return (
                              <button
                                  key={font.key}
                                  type="button"
                                  onClick={() => {
                                      settings.setAppFont(font.key);
                                      setIsFontPickerOpen(false);
                                  }}
                                  className={`flex w-full items-center justify-between gap-4 rounded-[1.6rem] border px-4 py-3 text-left transition-all active:scale-[0.985] ${
                                      isActive
                                          ? 'border-primary bg-primary/10 text-theme-text shadow-lg shadow-primary/10'
                                          : 'border-theme-border bg-card text-theme-text hover:bg-theme-element/70'
                                  }`}
                                  style={{ fontFamily: font.family }}
                              >
                                  <div className="min-w-0">
                                      <span className="block truncate text-base font-black">{font.label}</span>
                                      <span className="block text-[11px] font-medium text-theme-sub">Aa Bb Cc 123</span>
                                      {font.key === 'systemDefault' && (
                                          <span className="mt-1 block text-[10px] font-medium text-theme-sub">
                                              Uses the WebView system font, which is usually Roboto on Android.
                                          </span>
                                      )}
                                  </div>
                                  <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full border ${isActive ? 'border-primary/20 bg-primary text-white' : 'border-theme-border bg-theme-element text-theme-sub'}`}>
                                      <i className={`fas ${isActive ? 'fa-check' : 'fa-font'} text-xs`}></i>
                                  </div>
                              </button>
                          );
                      })}
                  </div>
              </div>
          </div>
      </div>
  );

  const renderQueue = () => (
      <div className="space-y-4 animate-slide-up">
          {activeDlCount === 0 && availableUpdates.length === 0 && readyCount === 0 ? (
              <div className="py-12 flex flex-col items-center justify-center text-theme-sub opacity-40"><i className="fas fa-cloud-check text-5xl mb-4"></i><p className="font-bold">Queue is empty</p></div>
          ) : (
              <div className="space-y-2">
                  {settings.useShizuku && readyCount > 1 && (<div className="mb-4"><button onClick={onUpdateAll} className="w-full py-3 bg-gradient-to-r from-primary to-primary-light text-white rounded-2xl font-bold text-sm shadow-lg shadow-primary/20 hover:scale-[1.01] active:scale-95 transition-all flex items-center justify-center gap-2"><i className="fas fa-rocket"></i><span>Update All ({readyCount})</span></button></div>)}
                  {Object.keys(data.readyToInstall).map(appId => {
                      const app = allApps.find(a => a.id === appId);
                      const isThisInstalling = installingId === appId;
                      return (
                          <div key={appId} className="bg-primary/10 border border-primary/30 rounded-2xl p-4 flex items-center justify-between animate-pulse relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-primary/20">
                              <div className="flex flex-col"><span className="font-black text-primary text-sm">{app?.name || appId}</span><span className="text-[10px] text-primary/70 uppercase font-black">Download Ready</span></div>
                              {isThisInstalling ? (<div className="px-4 py-2 flex items-center gap-2"><div className="w-4 h-4 border-2 border-primary border-t-transparent rounded-full animate-spin"></div><span className="text-[10px] font-bold text-primary">Installing...</span></div>) : (<button onClick={() => app && onInstallApp(app, data.readyToInstall[appId] || '')} className="px-4 py-2 rounded-xl bg-primary text-white font-bold text-xs shadow-lg shadow-primary/30 active:scale-95 transition-transform">Install</button>)}
                          </div>
                      );
                  })}
                  {Object.keys(data.activeDownloads).map(appId => {
                      const app = allApps.find(a => a.id === appId);
                      const rawVal = data.activeDownloads[appId] || '';
                      return (
                        <div key={appId} className="bg-card border border-theme-border rounded-2xl p-4 flex flex-col gap-2 shadow-sm relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5">
                            <div className="flex justify-between items-center"><span className="font-bold text-theme-text text-sm truncate max-w-[150px]">{app?.name || appId}</span><div className="flex items-center gap-2"><span className="text-[10px] font-black text-primary">{data.downloadProgress[appId] || 0}%</span><button onClick={() => onCancelDownloadById(appId, rawVal)} className="w-5 h-5 rounded-full bg-red-500/10 text-red-500 flex items-center justify-center hover:bg-red-500 hover:text-white transition-colors"><i className="fas fa-times text-[10px]"></i></button></div></div>
                            <div className="w-full bg-theme-element h-1.5 rounded-full overflow-hidden"><div className="h-full bg-primary transition-all duration-300" style={{ width: `${data.downloadProgress[appId] || 0}%` }}></div></div>
                        </div>
                      );
                  })}
                  {availableUpdates.filter(u => !data.activeDownloads[u.id] && !data.readyToInstall[u.id]).map(app => (
                      <div key={app.id} className="bg-theme-element/50 border border-dashed border-theme-border rounded-2xl p-4 flex items-center justify-between">
                          <div className="flex flex-col"><span className="font-bold text-theme-text text-sm">{app.name}</span><span className="text-[10px] text-theme-sub">Pending Update v{app.latestVersion}</span></div>
                          <button onClick={() => onTriggerUpdate(app)} className="w-8 h-8 rounded-full bg-primary text-white flex items-center justify-center shadow-lg shadow-primary/20 active:scale-95 transition-transform"><i className="fas fa-download text-[10px]"></i></button>
                      </div>
                  ))}
              </div>
          )}
      </div>
  );

  const renderDeveloperSettings = () => (
      <div className="space-y-4 animate-slide-up">
          <div className="p-4 bg-yellow-500/10 border border-yellow-500/20 rounded-2xl flex gap-3 text-yellow-600 dark:text-yellow-500 relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-yellow-500/5">
              <i className="fas fa-exclamation-triangle mt-0.5"></i>
              <p className="text-xs font-bold leading-relaxed">These options are for testing and modifying core app behavior. They may cause instability.</p>
          </div>
          <div className="bg-card border border-theme-border rounded-2xl p-4 flex items-center justify-between shadow-sm relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5">
              <div><h4 className="font-bold text-theme-text">Enable Built-in Data</h4><p className="text-[10px] text-theme-sub mt-1">Allows loading local data gracefully before fetching remote.</p></div>
              <Toggle checked={settings.loadLocalData} onChange={settings.toggleLoadLocalData} />
          </div>
          <div className="bg-card border border-theme-border rounded-2xl p-4 flex items-center justify-between shadow-sm relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5">
              <div><h4 className="font-bold text-theme-text font-mono text-primary">Local Maintenance Mode</h4><p className="text-[10px] text-theme-sub mt-1">Force trigger the maintenance UI & game locally.</p></div>
              <Toggle checked={settings.localMaintenanceMode} onChange={settings.toggleLocalMaintenance} />
          </div>
          <div className="bg-card border border-theme-border rounded-2xl p-4 flex items-center justify-between shadow-sm relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5">
              <div><h4 className="font-bold text-theme-text">Simulate Network Delay</h4><p className="text-[10px] text-theme-sub mt-1">Mock slow 2G connections.</p></div>
              <Toggle checked={false} onChange={() => {}} />
          </div>
          <div className="bg-card border border-theme-border rounded-2xl p-4 flex items-center justify-between shadow-sm relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5">
              <div><h4 className="font-bold text-theme-text">Mock Remote Failure</h4><p className="text-[10px] text-theme-sub mt-1">Forces Gitlab/JSDelivr Fallback.</p></div>
              <Toggle checked={false} onChange={() => {}} />
          </div>
          <button 
              onClick={() => {
                  useDataStore.getState().setPendingCleanup({});
                  useDataStore.getState().setReadyToInstall({});
              }}
              className="w-full py-4 bg-red-500/10 text-red-500 border border-red-500/20 rounded-2xl font-bold flex items-center justify-center gap-2 hover:bg-red-500/20 active:scale-95 transition-all text-sm relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-red-500/5"
          >
              <i className="fas fa-trash-alt"></i>
              <span>Clear System Cache</span>
          </button>
      </div>
  );

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 animate-fade-in">
        <div className="backdrop-scrim absolute inset-0 bg-black/80 backdrop-blur-md touch-none" onClick={onClose}></div>
        <div className="bg-surface border border-theme-border rounded-3xl w-full max-w-lg relative z-10 animate-slide-up shadow-2xl flex flex-col max-h-[85vh] overflow-hidden compact-allow">
            <div className="p-3 pb-4 border-b border-theme-border flex justify-between items-center bg-surface z-20">
                <div className="flex items-center gap-3">
                    {activeMenu !== 'none' && (
                        <button onClick={() => { setActiveMenu('none'); setImportStatus({msg: '', type: 'neutral'}); setShizukuError(null); setIsFontPickerOpen(false); }} className="w-8 h-8 rounded-full bg-theme-element border border-theme-border flex items-center justify-center text-theme-text hover:bg-theme-hover mr-1 transition-colors"><i className="fas fa-arrow-left"></i></button>
                    )}
                    <h3 className="text-2xl font-black text-theme-text capitalize">{activeMenu === 'none' ? 'Settings' : activeMenu.replace('queue', 'Update Center').replace('identity', 'Identity').replace('installer', 'Orion Xtra').replace('developer', 'Developer Options')}</h3>
                </div>
                <button onClick={onClose} className="w-10 h-10 rounded-full bg-theme-element border border-theme-border flex items-center justify-center text-theme-text hover:bg-theme-hover transition-colors"><i className="fas fa-times"></i></button>
            </div>
            <div className="overflow-y-auto p-3 space-y-6 no-scrollbar flex-1 will-change-transform overscroll-contain">
                {activeMenu === 'none' ? (
                    <div className="grid grid-cols-1 gap-3">
                        {menuItems.map(item => (
                            <button key={item.id} onClick={() => { setActiveMenu(item.id as SubMenu); setIsFontPickerOpen(false); }} className="bg-card border border-theme-border p-4 rounded-2xl flex items-center justify-between hover:bg-theme-element/50 transition-all active:scale-[0.98] group relative shadow-sm isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5">
                                <div className="flex items-center gap-4">
                                    <div className={`w-12 h-12 rounded-xl flex items-center justify-center text-xl ${item.bg} ${item.color}`}><i className={`fas ${item.icon}`}></i></div>
                                    <div className="text-left"><span className="block font-bold text-theme-text text-lg">{item.title}</span><span className="text-[10px] text-theme-sub">{item.desc}</span></div>
                                </div>
                                <div className="flex items-center gap-2">
                                    {item.badge !== undefined && item.badge > 0 && <span className="px-2 py-0.5 rounded-full bg-acid text-black text-[10px] font-black">{item.badge}</span>}
                                    <i className="fas fa-chevron-right text-theme-sub opacity-50 text-xs"></i>
                                </div>
                            </button>
                        ))}
                    </div>
                ) : (
                    <>
                        {activeMenu === 'identity' && renderIdentitySettings()}
                        {activeMenu === 'network' && renderNetworkSettings()}
                        {activeMenu === 'installer' && renderInstallerSettings()}
                        {activeMenu === 'storage' && renderStorageSettings()}
                        {activeMenu === 'queue' && renderQueue()}
                        {activeMenu === 'visuals' && renderVisuals()}
                        {activeMenu === 'interface' && (
                            <div className="flex flex-col gap-4">
                                <div className="bg-card border border-theme-border rounded-2xl shadow-sm p-4 flex items-center justify-between relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5">
                                    <div className="flex flex-col">
                                        <span className="font-bold text-theme-text text-sm">Modern Store Layout</span>
                                        <span className="text-xs text-theme-sub mt-0.5">Use the new curated app store experience</span>
                                    </div>
                                    <Toggle 
                                        checked={settings.storeLayout === 'modern'} 
                                        onChange={() => settings.setStoreLayout(settings.storeLayout === 'modern' ? 'classic' : 'modern')} 
                                    />
                                </div>
                                <div className="bg-card border border-theme-border rounded-2xl shadow-sm relative isolate before:absolute before:inset-0 before:rounded-[inherit] before:-z-10 before:shadow-glow before:shadow-black/5">
                                    {['android', 'pc', 'tv'].map((tab, idx) => (
                                        <div key={tab} className={`flex items-center justify-between p-4 ${idx !== 2 ? 'border-b border-theme-border' : ''}`}>
                                            <span className="font-bold text-theme-text text-sm capitalize">{tab} Tab</span>
                                            <Toggle checked={!settings.hiddenTabs.includes(tab)} onChange={() => settings.toggleHiddenTab(tab)} />
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                        {activeMenu === 'developer' && renderDeveloperSettings()}
                    </>
                )}
            </div>
        </div>
        {isFontPickerOpen && renderFontPicker()}
        <Suspense fallback={null}>
            {activeModal === 'guardian' && <ShizukuPowerModal onClose={() => setActiveModal('none')} />}
            {activeModal === 'sentinel' && <SentinelModal onClose={() => setActiveModal('none')} />}
        </Suspense>
    </div>
  );
};

export default SettingsModal;
