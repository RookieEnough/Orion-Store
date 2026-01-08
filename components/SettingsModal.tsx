
import React, { useState, memo } from 'react';
import { AppItem } from '../types';

interface SettingsModalProps {
  onClose: () => void;
  theme: 'light' | 'dusk' | 'dark' | 'oled';
  setTheme: (t: any) => void;
  isOled: boolean;
  setIsOled: (v: boolean) => void;
  hiddenTabs: string[];
  toggleHiddenTab: (tab: string) => void;
  autoUpdateEnabled: boolean;
  toggleAutoUpdate: () => void;
  wifiOnly: boolean;
  toggleWifiOnly: () => void;
  deleteApk: boolean;
  toggleDeleteApk: () => void;
  disableAnimations: boolean;
  toggleDisableAnimations: () => void;
  compactMode: boolean;
  toggleCompactMode: () => void;
  highRefreshRate: boolean;
  toggleHighRefreshRate: () => void;
  availableUpdates: AppItem[];
  onTriggerUpdate: (app: AppItem) => void;
  activeDownloads?: Record<string, string>;
  downloadProgress?: Record<string, number>;
  readyToInstall?: Record<string, string>;
  onInstallApp?: (app: AppItem, file: string) => void;
  onCancelDownload?: (app: AppItem, id: string) => void;
}

type SubMenu = 'none' | 'network' | 'visuals' | 'storage' | 'interface' | 'queue';

// PERFORMANCE FIX: Component defined outside to prevent re-creation on every render
const Toggle = memo(({ checked, onChange }: { checked: boolean; onChange: () => void }) => (
    <button 
        onClick={onChange}
        className={`w-12 h-7 rounded-full p-1 transition-colors duration-200 ${checked ? 'bg-primary' : 'bg-theme-element border border-theme-border'}`}
    >
        <div className={`w-5 h-5 bg-white rounded-full shadow-md transform transition-transform duration-200 ${checked ? 'translate-x-5' : 'translate-x-0'}`}></div>
    </button>
));

const SettingsModal: React.FC<SettingsModalProps> = ({
  onClose,
  theme, setTheme,
  isOled, setIsOled,
  hiddenTabs, toggleHiddenTab,
  autoUpdateEnabled, toggleAutoUpdate,
  wifiOnly, toggleWifiOnly,
  deleteApk, toggleDeleteApk,
  disableAnimations, toggleDisableAnimations,
  compactMode, toggleCompactMode,
  highRefreshRate, toggleHighRefreshRate,
  availableUpdates, onTriggerUpdate,
  activeDownloads = {},
  downloadProgress = {},
  readyToInstall = {},
  onInstallApp,
  onCancelDownload
}) => {
  const [activeMenu, setActiveMenu] = useState<SubMenu>('none');
  const activeDlCount = Object.keys(activeDownloads).length;
  const readyCount = Object.keys(readyToInstall).length;

  const menuItems = [
      { id: 'network', icon: 'fa-wifi', color: 'text-blue-500', bg: 'bg-blue-500/10', title: 'Network & Updates', desc: 'WiFi only, Auto-discovery' },
      { id: 'queue', icon: 'fa-download', color: 'text-indigo-500', bg: 'bg-indigo-500/10', title: 'Download Queue', desc: `${activeDlCount} active, ${readyCount} ready`, badge: activeDlCount + readyCount },
      { id: 'visuals', icon: 'fa-palette', color: 'text-purple-500', bg: 'bg-purple-500/10', title: 'Visuals & Theme', desc: 'OLED, Animations, 120Hz' },
      { id: 'storage', icon: 'fa-sd-card', color: 'text-orange-500', bg: 'bg-orange-500/10', title: 'Storage & Cleanup', desc: 'Auto-delete installer APKs' },
      { id: 'interface', icon: 'fa-layer-group', color: 'text-green-500', bg: 'bg-green-500/10', title: 'Interface', desc: 'Customize tabs' },
  ];

  const renderNetworkSettings = () => (
      <div className="space-y-4 animate-slide-up">
          <div className="bg-card border border-theme-border rounded-2xl p-4 flex items-center justify-between shadow-sm">
              <div>
                  <h4 className="font-bold text-theme-text">WiFi-Only Mode</h4>
                  <p className="text-[10px] text-theme-sub mt-1">Block downloads on cellular data.</p>
              </div>
              <Toggle checked={wifiOnly} onChange={toggleWifiOnly} />
          </div>
          <div className="bg-card border border-theme-border rounded-2xl p-4 flex items-center justify-between shadow-sm">
              <div>
                  <h4 className="font-bold text-theme-text">Auto-Check Updates</h4>
                  <p className="text-[10px] text-theme-sub mt-1">Notify when app versions change.</p>
              </div>
              <Toggle checked={autoUpdateEnabled} onChange={toggleAutoUpdate} />
          </div>
      </div>
  );

  const renderVisuals = () => (
      <div className="space-y-4 animate-slide-up">
          <div className="bg-card border border-theme-border rounded-2xl p-4 flex items-center justify-between shadow-sm">
              <div>
                  <h4 className="font-bold text-theme-text">Smooth Motion</h4>
                  <p className="text-[10px] text-theme-sub mt-1">Unlock Maximum Refresh Rate.</p>
              </div>
              <Toggle checked={highRefreshRate} onChange={toggleHighRefreshRate} />
          </div>
          <div className="bg-card border border-theme-border rounded-2xl p-4 shadow-sm">
              <div className="flex items-center justify-between mb-2">
                  <div>
                      <h4 className="font-bold text-theme-text">OLED Black Mode</h4>
                      <p className="text-[10px] text-theme-sub mt-1">True black for Dark theme.</p>
                  </div>
                  <Toggle checked={isOled} onChange={() => setIsOled(!isOled)} />
              </div>
          </div>
          <div className="bg-card border border-theme-border rounded-2xl p-4 flex items-center justify-between shadow-sm">
              <div><h4 className="font-bold text-theme-text">Disable Animations</h4><p className="text-[10px] text-theme-sub mt-1">Snappier UI on older devices.</p></div>
              <Toggle checked={disableAnimations} onChange={toggleDisableAnimations} />
          </div>
          <div className="bg-card border border-theme-border rounded-2xl p-4 flex items-center justify-between shadow-sm">
              <div><h4 className="font-bold text-theme-text">Compact Mode</h4><p className="text-[10px] text-theme-sub mt-1">Shrink app cards for more density.</p></div>
              <Toggle checked={compactMode} onChange={toggleCompactMode} />
          </div>
      </div>
  );

  const renderQueue = () => (
      <div className="space-y-4 animate-slide-up">
          {activeDlCount === 0 && availableUpdates.length === 0 && readyCount === 0 ? (
              <div className="py-12 flex flex-col items-center justify-center text-theme-sub opacity-40">
                  <i className="fas fa-cloud-check text-5xl mb-4"></i>
                  <p className="font-bold">Queue is empty</p>
              </div>
          ) : (
              <div className="space-y-2">
                  {/* Ready to Install */}
                  {Object.keys(readyToInstall).map(appId => {
                      const app = availableUpdates.find(u => u.id === appId);
                      return (
                          <div key={appId} className="bg-primary/10 border border-primary/30 rounded-2xl p-4 flex items-center justify-between animate-pulse">
                              <div className="flex flex-col">
                                  <span className="font-black text-primary text-sm">{app?.name || appId}</span>
                                  <span className="text-[10px] text-primary/70 uppercase font-black">Download Ready</span>
                              </div>
                              <button 
                                  onClick={() => app && onInstallApp && onInstallApp(app, readyToInstall[appId])}
                                  className="px-4 py-2 rounded-xl bg-primary text-white font-bold text-xs shadow-lg shadow-primary/30 active:scale-95 transition-transform"
                              >
                                  Install
                              </button>
                          </div>
                      );
                  })}

                  {/* Active Downloads */}
                  {Object.keys(activeDownloads).map(appId => {
                      const app = availableUpdates.find(u => u.id === appId);
                      return (
                        <div key={appId} className="bg-card border border-theme-border rounded-2xl p-4 flex flex-col gap-2 shadow-sm">
                            <div className="flex justify-between items-center">
                                <span className="font-bold text-theme-text text-sm truncate max-w-[150px]">{app?.name || appId}</span>
                                <div className="flex items-center gap-2">
                                    <span className="text-[10px] font-black text-primary">{downloadProgress[appId] || 0}%</span>
                                    <button 
                                        onClick={() => app && onCancelDownload && onCancelDownload(app, activeDownloads[appId])}
                                        className="w-5 h-5 rounded-full bg-red-500/10 text-red-500 flex items-center justify-center hover:bg-red-500 hover:text-white transition-colors"
                                    >
                                        <i className="fas fa-times text-[10px]"></i>
                                    </button>
                                </div>
                            </div>
                            <div className="w-full bg-theme-element h-1.5 rounded-full overflow-hidden">
                                <div className="h-full bg-primary transition-all duration-300" style={{ width: `${downloadProgress[appId] || 0}%` }}></div>
                            </div>
                        </div>
                      );
                  })}

                  {/* Pending Updates */}
                  {availableUpdates.filter(u => !activeDownloads[u.id] && !readyToInstall[u.id]).map(app => (
                      <div key={app.id} className="bg-theme-element/50 border border-dashed border-theme-border rounded-2xl p-4 flex items-center justify-between">
                          <div className="flex flex-col">
                            <span className="font-bold text-theme-text text-sm">{app.name}</span>
                            <span className="text-[10px] text-theme-sub">Pending Update v{app.latestVersion}</span>
                          </div>
                          <button onClick={() => onTriggerUpdate(app)} className="w-8 h-8 rounded-full bg-primary text-white flex items-center justify-center shadow-lg shadow-primary/20 active:scale-95 transition-transform"><i className="fas fa-download text-[10px]"></i></button>
                      </div>
                  ))}
              </div>
          )}
      </div>
  );

  const renderStorageSettings = () => (
      <div className="space-y-4 animate-slide-up">
          <div className="bg-card border border-theme-border rounded-2xl p-4 shadow-sm">
              <div className="flex items-center justify-between mb-2">
                  <div>
                      <h4 className="font-bold text-theme-text">Smart Cleanup</h4>
                      <p className="text-[10px] text-theme-sub mt-1 max-w-[200px]">Prompt to delete APK files immediately after installation.</p>
                  </div>
                  <Toggle checked={deleteApk} onChange={toggleDeleteApk} />
              </div>
          </div>
      </div>
  );

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 animate-fade-in">
        <div className="absolute inset-0 bg-black/80" onClick={onClose}></div>
        
        <div className="bg-surface border border-theme-border rounded-3xl w-full max-w-lg relative z-10 animate-slide-up shadow-2xl flex flex-col max-h-[85vh] overflow-hidden">
            <div className="p-6 pb-4 border-b border-theme-border flex justify-between items-center bg-surface z-20">
                <div className="flex items-center gap-3">
                    {activeMenu !== 'none' && (
                        <button onClick={() => setActiveMenu('none')} className="w-8 h-8 rounded-full bg-theme-element border border-theme-border flex items-center justify-center text-theme-text hover:bg-theme-hover mr-1 transition-colors">
                            <i className="fas fa-arrow-left"></i>
                        </button>
                    )}
                    <h3 className="text-2xl font-black text-theme-text capitalize">{activeMenu === 'none' ? 'Settings' : activeMenu.replace('queue', 'Update Center')}</h3>
                </div>
                <button onClick={onClose} className="w-10 h-10 rounded-full bg-theme-element border border-theme-border flex items-center justify-center text-theme-text hover:bg-theme-hover transition-colors">
                    <i className="fas fa-times"></i>
                </button>
            </div>
            
            <div className="overflow-y-auto p-6 space-y-6 no-scrollbar flex-1 will-change-transform">
                {activeMenu === 'none' ? (
                    <div className="grid grid-cols-1 gap-3">
                        {menuItems.map(item => (
                            <button key={item.id} onClick={() => setActiveMenu(item.id as SubMenu)} className="bg-card border border-theme-border p-4 rounded-2xl flex items-center justify-between hover:bg-theme-element/50 transition-all active:scale-[0.98] group relative shadow-sm">
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
                        {activeMenu === 'network' && renderNetworkSettings()}
                        {activeMenu === 'queue' && renderQueue()}
                        {activeMenu === 'visuals' && renderVisuals()}
                        {activeMenu === 'storage' && renderStorageSettings()}
                        {activeMenu === 'interface' && (
                            <div className="bg-card border border-theme-border rounded-2xl overflow-hidden shadow-sm">
                                {['android', 'pc', 'tv'].map((tab, idx) => (
                                    <div key={tab} className={`flex items-center justify-between p-4 ${idx !== 2 ? 'border-b border-theme-border' : ''}`}>
                                        <span className="font-bold text-theme-text text-sm capitalize">{tab} Tab</span>
                                        <Toggle checked={!hiddenTabs.includes(tab)} onChange={() => toggleHiddenTab(tab)} />
                                    </div>
                                ))}
                            </div>
                        )}
                    </>
                )}
            </div>
        </div>
    </div>
  );
};

export default SettingsModal;
