import React, { useState, useEffect, useMemo } from 'react';
import { createPortal } from 'react-dom';
import AppTracker, { DangerousApp, SystemApp } from '../plugins/AppTracker';
import { useScrollLock } from '../hooks/useScrollLock';
import { Haptics, NotificationType } from '@capacitor/haptics';

interface ShizukuPowerModalProps {
  onClose: () => void;
  initialTab?: 'permissions' | 'surgeon' | 'debloater';
}

const LOADING_TEXTS = [
    "Waking up the hamsters...",
    "Consulting the Oracle...",
    "Decrypting Matrix code...",
    "Bribing the CPU...",
    "Sweeping for bugs...",
    "Loading magic spells...",
    "Summoning daemons...",
    "Calculating pi...",
    "Charging flux capacitor...",
    "Reticulating splines...",
    "Checking gravity settings...",
    "Polishing pixels..."
];

const ShizukuPowerModal: React.FC<ShizukuPowerModalProps> = ({ onClose, initialTab = 'permissions' }) => {
  useScrollLock(true);
  const [activeTab, setActiveTab] = useState<'permissions' | 'surgeon' | 'debloater'>(initialTab);
  
  // Data States
  const [apps, setApps] = useState<DangerousApp[]>([]);
  const [systemApps, setSystemApps] = useState<SystemApp[]>([]);
  
  // Memory Caches
  const [dangerousCache, setDangerousCache] = useState<DangerousApp[] | null>(null);
  const [systemCache, setSystemCache] = useState<SystemApp[] | null>(null);

  const [disclaimerAccepted, setDisclaimerAccepted] = useState(false);
  
  const [loading, setLoading] = useState(true);
  const [loadingMsgIndex, setLoadingMsgIndex] = useState(0);
  const [error, setError] = useState('');
  const [expandedApp, setExpandedApp] = useState<string | null>(null);
  const [processing, setProcessing] = useState<string | null>(null);
  const [toast, setToast] = useState<{ msg: string, type: 'info' | 'error' | 'success' | 'confirm', onConfirm?: () => void } | null>(null);

  // --- FILTERS STATE ---
  const [searchQuery, setSearchQuery] = useState('');
  const [appFilter, setAppFilter] = useState<'all' | 'user' | 'system'>('all');

  // UAD List for accurate debloat detection
  const [uadList, setUadList] = useState<any[]>([]);

  const uadMap = useMemo(() => {
    const map = new Map();
    uadList.forEach(item => map.set(item.id, item));
    return map;
  }, [uadList]);

  useEffect(() => {
    if (activeTab === 'debloater') {
      if (!uadList.length) {
        fetch('https://raw.githubusercontent.com/0x192/universal-android-debloater/main/resources/assets/uad_lists.json')
          .then(res => res.json())
          .then(setUadList)
          .catch(() => setToast({ msg: 'Failed to load debloat database', type: 'error' }));
      }
      if (disclaimerAccepted) {
        if (systemCache) {
          setSystemApps(systemCache);
          setLoading(false);
        } else {
          fetchSystemApps();
        }
      }
    } else {
      if (dangerousCache) {
        setApps(dangerousCache);
        setLoading(false);
      } else {
        fetchApps();
      }
    }
  }, [activeTab, disclaimerAccepted]);

  useEffect(() => {
      let interval: any;
      if (loading) {
          interval = setInterval(() => {
              setLoadingMsgIndex(prev => (prev + 1) % LOADING_TEXTS.length);
          }, 1500);
      }
      return () => clearInterval(interval);
  }, [loading]);

  useEffect(() => {
      if (toast && toast.type !== 'confirm') {
          const timer = setTimeout(() => setToast(null), 3000);
          return () => clearTimeout(timer);
      }
  }, [toast]);

  const fetchApps = async () => {
    try {
      setLoading(true);
      const res = await AppTracker.getDangerousApps();
      const sorted = res.apps.sort((a, b) => b.permissions.length - a.permissions.length);
      setApps(sorted);
      setDangerousCache(sorted); 
    } catch (e: any) {
      setError(e.message || "Failed to scan packages.");
    } finally {
      setLoading(false);
    }
  };

  const fetchSystemApps = async () => {
      try {
          setLoading(true);
          const res = await AppTracker.getSystemApps();
          const sorted = res.apps.sort((a, b) => {
              if (a.isInstalled === b.isInstalled) return a.name.localeCompare(b.name);
              return a.isInstalled ? 1 : -1;
          });
          // Filter only system apps for debloater
          const systemOnly = sorted.filter(a => a.isSystem);
          setSystemApps(systemOnly);
          setSystemCache(systemOnly);
      } catch (e: any) {
          setError(e.message || "Failed to fetch system apps.");
      } finally {
          setLoading(false);
      }
  };

  // --- UTILS ---
  const handleCopyPackage = async (e: React.MouseEvent, pkg: string) => {
      e.stopPropagation();
      try {
          await navigator.clipboard.writeText(pkg);
          Haptics.selection();
      } catch (err) {
          setToast({ msg: "Failed to copy", type: 'error' });
      }
  };

  const getDebloatRisk = (pkg: string) => {
    const entry = uadMap.get(pkg);
    if (entry) {
      const removal = entry.removal.toLowerCase();
      if (removal === 'recommended') {
        return { label: 'Recommended', color: 'text-emerald-500', bg: 'bg-emerald-500/10', border: 'border-emerald-500/30' };
      } else if (removal === 'advanced') {
        return { label: 'Advanced', color: 'text-yellow-500', bg: 'bg-yellow-500/10', border: 'border-yellow-500/30' };
      } else if (removal === 'expert') {
        return { label: 'Expert', color: 'text-orange-500', bg: 'bg-orange-500/10', border: 'border-orange-500/30' };
      } else if (removal === 'unsafe') {
        return { label: 'Unsafe', color: 'text-red-500', bg: 'bg-red-500/10', border: 'border-red-500/30' };
      } else if (removal === 'untested') {
        return { label: 'Untested', color: 'text-purple-500', bg: 'bg-purple-500/10', border: 'border-purple-500/30' };
      }
    }
    return { label: 'Unknown', color: 'text-gray-500', bg: 'bg-gray-500/10', border: 'border-gray-500/30' };
  };

  const handleAgreeDisclaimer = async () => {
      try {
          await AppTracker.requestShizukuPermission();
          setDisclaimerAccepted(true);
      } catch (e) {
          setToast({ msg: "Shizuku Permission Denied", type: 'error' });
      }
  };

  const getRiskLevel = (count: number) => {
      if (count >= 3) return { label: 'CRITICAL', color: 'text-red-500', bg: 'bg-red-500/10', border: 'border-red-500/30' };
      if (count >= 1) return { label: 'WARNING', color: 'text-yellow-500', bg: 'bg-yellow-500/10', border: 'border-yellow-500/30' };
      return { label: 'SAFE', color: 'text-emerald-500', bg: 'bg-emerald-500/10', border: 'border-emerald-500/30' };
  };

  const getPermIcon = (perm: string) => {
      if (perm.includes("CAMERA")) return "fa-camera";
      if (perm.includes("RECORD_AUDIO")) return "fa-microphone";
      if (perm.includes("LOCATION")) return "fa-map-marker-alt";
      if (perm.includes("CONTACTS")) return "fa-address-book";
      if (perm.includes("SMS")) return "fa-sms";
      if (perm.includes("STORAGE")) return "fa-sd-card";
      return "fa-shield-alt";
  };

  const cleanPermName = (perm: string) => {
      return perm.replace("android.permission.", "").replace("ACCESS_", "").replace("READ_", "").replace("WRITE_", "").replace("_", " ");
  };

  const triggerRevoke = (pkg: string, perm: string) => {
      setToast({
          msg: `Revoke ${cleanPermName(perm)}? App might crash.`,
          type: 'confirm',
          onConfirm: () => handleRevoke(pkg, perm)
      });
  };

  const handleRevoke = async (pkg: string, perm: string) => {
      setToast(null); 
      setProcessing(pkg);
      try {
          await AppTracker.requestShizukuPermission();
          await AppTracker.revokePermission({ packageName: pkg, permission: perm });
          Haptics.notification({ type: NotificationType.Success });
          setToast({ msg: 'Permission Revoked', type: 'success' });
          setDangerousCache(null); 
          setTimeout(fetchApps, 500);
      } catch (e: any) {
          setToast({ msg: e.message || 'Revoke Failed', type: 'error' });
      } finally {
          setProcessing(null);
      }
  };

  const handleExtract = async (app: DangerousApp) => {
      setProcessing(app.packageName);
      try {
          const res = await AppTracker.extractApk({ packageName: app.packageName });
          Haptics.notification({ type: NotificationType.Success });
          const filename = res.path.split('/').pop();
          setToast({ msg: `Extracted: ${filename}`, type: 'success' });
      } catch (e: any) {
          setToast({ msg: e.message || 'Extraction Failed', type: 'error' });
      } finally {
          setProcessing(null);
      }
  };

  const triggerToggleSystemApp = (app: SystemApp) => {
      const action = app.isInstalled ? "Uninstall" : "Restore";
      const risk = getDebloatRisk(app.packageName);
      let warning = "";
      if (risk.label === 'Unsafe') warning = "⚠️ DANGER: Critical System App. Bootloop likely!";
      else if (risk.label === 'Expert' || risk.label === 'Advanced') warning = "⚠️ Caution: May break features.";
      setToast({
          msg: `${action} ${app.name}? ${warning}`,
          type: 'confirm',
          onConfirm: () => handleToggleSystemApp(app)
      });
  };

  const handleToggleSystemApp = async (app: SystemApp) => {
      setToast(null);
      setProcessing(app.packageName);
      try {
          await AppTracker.toggleSystemApp({ packageName: app.packageName, enable: !app.isInstalled });
          Haptics.notification({ type: NotificationType.Success });
          setToast({ msg: app.isInstalled ? 'Uninstalled (User 0)' : 'Restored', type: 'success' });
          setSystemCache(null);
          setTimeout(fetchSystemApps, 800);
      } catch (e: any) {
          setToast({ msg: e.message || 'Operation Failed', type: 'error' });
      } finally {
          setProcessing(null);
      }
  };

  const filteredList = useMemo(() => {
      if (activeTab === 'debloater') {
          let result = systemApps;
          if (searchQuery.trim()) {
              const q = searchQuery.toLowerCase();
              result = result.filter(a => a.name.toLowerCase().includes(q) || a.packageName.toLowerCase().includes(q));
          }
          return result;
      }
      let result = apps;
      if (activeTab === 'permissions') {
          result = result.filter(a => a.permissions.length > 0);
      }
      if (appFilter === 'user') {
          result = result.filter(a => !a.isSystem);
      } else if (appFilter === 'system') {
          result = result.filter(a => a.isSystem);
      }
      if (searchQuery.trim()) {
          const q = searchQuery.toLowerCase();
          result = result.filter(a => a.name.toLowerCase().includes(q) || a.packageName.toLowerCase().includes(q));
      }
      return result;
  }, [apps, systemApps, activeTab, appFilter, searchQuery]);

  return createPortal(
    <div className="fixed inset-0 z-[200] flex items-center justify-center p-0 md:p-4 touch-none">
        <div className="absolute inset-0 bg-black/90 backdrop-blur-md" onClick={onClose}></div>
        
        {/* INLINE STYLES FOR PACMAN ANIMATION */}
        <style>{`
            .pacman-container { position: relative; width: 200px; height: 60px; display: flex; align-items: center; justify-content: flex-start; overflow: hidden; }
            .pacman-body { position: relative; width: 40px; height: 40px; z-index: 10; }
            .pacman-top, .pacman-bottom { position: absolute; width: 100%; height: 50%; background-color: #fbbf24; left: 0; border-radius: 200px 200px 0 0; animation: chomp 0.3s infinite alternate ease-in-out; }
            .pacman-bottom { top: 50%; border-radius: 0 0 200px 200px; animation-name: chomp-bottom; }
            .dot-stream { position: absolute; right: 0; top: 50%; transform: translateY(-50%); display: flex; gap: 20px; animation: flow 0.6s linear infinite; }
            .dot-pellet { width: 10px; height: 10px; background-color: #fca5a5; border-radius: 50%; box-shadow: 0 0 5px rgba(252, 165, 165, 0.6); }
            @keyframes chomp { 0% { transform: rotate(0deg); } 100% { transform: rotate(-45deg); } }
            @keyframes chomp-bottom { 0% { transform: rotate(0deg); } 100% { transform: rotate(45deg); } }
            @keyframes flow { 0% { transform: translate(0, -50%); } 100% { transform: translate(-30px, -50%); } }
        `}</style>

        <div className="relative w-full h-full md:h-[90vh] md:max-w-xl bg-black/80 backdrop-blur-2xl md:rounded-[2rem] border border-white/10 shadow-[0_0_50px_rgba(0,0,0,0.5)] overflow-hidden flex flex-col animate-slide-up">
            
            {/* Header */}
            <div className="shrink-0 p-6 pb-4 border-b border-white/5 bg-black/20 z-10 flex justify-between items-center relative overflow-hidden">
                <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-red-500 via-purple-500 to-blue-500 opacity-70"></div>
                <div>
                    <h2 className="text-xl font-black text-transparent bg-clip-text bg-gradient-to-r from-white to-gray-400 tracking-widest flex items-center gap-2">
                        <i className="fas fa-shield-virus text-indigo-400 drop-shadow-lg"></i> ORION GUARDIAN
                    </h2>
                    <p className="text-[10px] text-indigo-300 font-mono uppercase mt-1 tracking-wider opacity-80">System Level Access • Rootless</p>
                </div>
                <button onClick={onClose} className="w-10 h-10 rounded-full bg-white/5 border border-white/10 flex items-center justify-center text-white hover:bg-red-500/20 hover:text-red-400 hover:border-red-500/30 transition-all active:scale-90">
                    <i className="fas fa-times"></i>
                </button>
            </div>

            {/* Mode Tabs */}
            <div className="flex p-3 gap-2 bg-black/20 border-b border-white/5 overflow-x-auto no-scrollbar">
                <button onClick={() => setActiveTab('permissions')} className={`flex-1 min-w-[80px] py-3 rounded-xl text-[10px] font-black uppercase tracking-wider flex items-center justify-center gap-2 transition-all relative overflow-hidden group ${activeTab === 'permissions' ? 'bg-red-500/10 text-red-400 border border-red-500/30 shadow-[0_0_15px_rgba(248,113,113,0.1)]' : 'text-gray-500 hover:bg-white/5 hover:text-gray-300'}`}>
                    <i className="fas fa-user-secret text-sm group-hover:scale-110 transition-transform"></i> Perms
                </button>
                <button onClick={() => setActiveTab('surgeon')} className={`flex-1 min-w-[80px] py-3 rounded-xl text-[10px] font-black uppercase tracking-wider flex items-center justify-center gap-2 transition-all relative overflow-hidden group ${activeTab === 'surgeon' ? 'bg-blue-500/10 text-blue-400 border border-blue-500/30 shadow-[0_0_15px_rgba(96,165,250,0.1)]' : 'text-gray-500 hover:bg-white/5 hover:text-gray-300'}`}>
                    <i className="fas fa-syringe text-sm group-hover:scale-110 transition-transform"></i> Surgeon
                </button>
                <button onClick={() => setActiveTab('debloater')} className={`flex-1 min-w-[80px] py-3 rounded-xl text-[10px] font-black uppercase tracking-wider flex items-center justify-center gap-2 transition-all relative overflow-hidden group ${activeTab === 'debloater' ? 'bg-orange-500/10 text-orange-400 border border-orange-500/30 shadow-[0_0_15px_rgba(251,146,60,0.1)]' : 'text-gray-500 hover:bg-white/5 hover:text-gray-300'}`}>
                    <i className="fas fa-trash-alt text-sm group-hover:scale-110 transition-transform"></i> Debloat
                </button>
            </div>

            {/* SEARCH & FILTER BAR */}
            <div className="p-4 border-b border-white/5 bg-black/10 space-y-3">
                <div className="relative group">
                    <div className="absolute -inset-0.5 bg-gradient-to-r from-indigo-500 to-purple-500 rounded-xl opacity-20 group-focus-within:opacity-50 transition duration-500 blur-sm"></div>
                    <div className="relative flex items-center bg-[#111] border border-white/10 rounded-xl">
                        <i className="fas fa-search absolute left-3 text-gray-500 text-xs"></i>
                        <input 
                            type="text" 
                            placeholder={activeTab === 'debloater' ? "Search system apps..." : "Search packages..."}
                            className="w-full bg-transparent py-3 pl-9 pr-4 text-xs font-medium text-white placeholder-gray-600 outline-none transition-colors"
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                        />
                    </div>
                </div>

                {activeTab !== 'debloater' && (
                    <div className="flex gap-2 p-1 bg-black/40 rounded-lg border border-white/5">
                        <button onClick={() => { setAppFilter('all'); Haptics.selection(); }} className={`flex-1 py-1.5 rounded-md text-[10px] font-bold uppercase tracking-wide transition-all ${appFilter === 'all' ? 'bg-white/10 text-white shadow-sm' : 'text-gray-500 hover:text-gray-300'}`}>All</button>
                        <button onClick={() => { setAppFilter('user'); Haptics.selection(); }} className={`flex-1 py-1.5 rounded-md text-[10px] font-bold uppercase tracking-wide transition-all ${appFilter === 'user' ? 'bg-emerald-500/20 text-emerald-400 shadow-sm' : 'text-gray-500 hover:text-gray-300'}`}>User</button>
                        <button onClick={() => { setAppFilter('system'); Haptics.selection(); }} className={`flex-1 py-1.5 rounded-md text-[10px] font-bold uppercase tracking-wide transition-all ${appFilter === 'system' ? 'bg-amber-500/20 text-amber-400 shadow-sm' : 'text-gray-500 hover:text-gray-300'}`}>System</button>
                    </div>
                )}
            </div>

            {/* List Content */}
            <div className="flex-1 overflow-y-auto p-4 no-scrollbar pb-24 relative">
                
                {/* DEBLOATER DISCLAIMER */}
                {activeTab === 'debloater' && !disclaimerAccepted && (
                    <div className="absolute inset-0 z-20 flex items-center justify-center bg-black/90 p-6 animate-fade-in backdrop-blur-sm">
                        <div className="text-center max-w-sm border border-red-500/20 bg-red-950/20 p-6 rounded-3xl shadow-2xl">
                            <div className="w-20 h-20 bg-red-500/10 rounded-full flex items-center justify-center mx-auto mb-6 border border-red-500/30 shadow-[0_0_30px_rgba(239,68,68,0.2)]">
                                <i className="fas fa-radiation text-4xl text-red-500 animate-pulse"></i>
                            </div>
                            <h3 className="text-2xl font-black text-white mb-4 tracking-tight">NUCLEAR ZONE</h3>
                            <div className="bg-black/40 border border-red-500/10 p-4 rounded-xl mb-6 text-left">
                                <p className="text-xs text-red-200 font-medium leading-relaxed">
                                    <strong className="text-red-400 block mb-2 uppercase tracking-wider">⚠️ Critical Warning</strong>
                                    Uninstalling system apps can brick your device. I am not responsible for bootloops or data loss.
                                </p>
                            </div>
                            <button 
                                onClick={handleAgreeDisclaimer}
                                className="w-full py-4 bg-red-600 hover:bg-red-500 text-white font-bold rounded-xl shadow-lg shadow-red-500/20 transition-all active:scale-95 border border-red-400/20"
                            >
                                I Accept the Risk
                            </button>
                        </div>
                    </div>
                )}

                {/* LOADING / ERROR / LIST */}
                {loading ? (
                    <div className="flex flex-col items-center justify-center h-64 gap-8">
                        <div className="pacman-container">
                            <div className="pacman-body"><div className="pacman-top"></div><div className="pacman-bottom"></div></div>
                            <div className="dot-stream"><div className="dot-pellet"></div><div className="dot-pellet"></div><div className="dot-pellet"></div><div className="dot-pellet"></div><div className="dot-pellet"></div></div>
                        </div>
                        <p className="text-xs font-bold font-mono animate-pulse text-indigo-400 uppercase tracking-widest min-w-[200px] text-center opacity-80">{LOADING_TEXTS[loadingMsgIndex]}</p>
                    </div>
                ) : error ? (
                    <div className="p-6 text-center text-red-400 bg-red-500/5 rounded-2xl border border-red-500/20 m-4">
                        <i className="fas fa-bug text-3xl mb-3 opacity-80"></i><p className="text-xs font-bold font-mono">{error}</p>
                    </div>
                ) : (
                    <div className="space-y-3">
                        {(activeTab === 'debloater' ? (filteredList as SystemApp[]) : (filteredList as DangerousApp[])).map((app: any) => {
                            const isBusy = processing === app.packageName;
                            const isExpanded = expandedApp === app.packageName;

                            if (activeTab === 'debloater') {
                                const systemApp = app as SystemApp;
                                const risk = getDebloatRisk(systemApp.packageName);
                                return (
                                    <div key={systemApp.packageName} className={`bg-white/5 border ${risk.border} rounded-2xl overflow-hidden transition-all relative group hover:bg-white/10`}>
                                        <div className="p-4 pl-5 flex items-center gap-4">
                                            <div className={`w-1 h-8 rounded-full ${systemApp.isInstalled ? 'bg-emerald-500 shadow-[0_0_10px_rgba(16,185,129,0.4)]' : 'bg-red-500 shadow-[0_0_10px_rgba(239,68,68,0.4)]'}`}></div>
                                            <div className="flex-1 min-w-0">
                                                <div className="flex items-center gap-2 mb-1">
                                                    <h4 className={`font-bold text-sm truncate ${systemApp.isInstalled ? 'text-white' : 'text-white/40 line-through'}`}>{systemApp.name}</h4>
                                                    <span className={`text-[8px] px-1.5 py-0.5 rounded font-black uppercase tracking-wider ${risk.bg} ${risk.color} border border-current opacity-80`}>{risk.label}</span>
                                                </div>
                                                <p className="text-[10px] text-gray-500 font-mono truncate cursor-pointer hover:text-indigo-300 transition-colors" onClick={(e) => handleCopyPackage(e, systemApp.packageName)}>{systemApp.packageName}</p>
                                            </div>
                                            <button onClick={() => triggerToggleSystemApp(systemApp)} disabled={isBusy} className={`w-10 h-10 rounded-xl flex items-center justify-center transition-all shadow-lg active:scale-90 ${systemApp.isInstalled ? 'bg-red-500/20 text-red-400 hover:bg-red-500 hover:text-white border border-red-500/30' : 'bg-emerald-500/20 text-emerald-400 hover:bg-emerald-500 hover:text-white border border-emerald-500/30'}`}>
                                                <i className={`fas ${isBusy ? 'fa-circle-notch fa-spin' : systemApp.isInstalled ? 'fa-trash' : 'fa-rotate-left'}`}></i>
                                            </button>
                                        </div>
                                    </div>
                                );
                            }

                            const dApp = app as DangerousApp;
                            const risk = getRiskLevel(dApp.permissions.length);
                            return (
                                <div key={dApp.packageName} className={`bg-white/5 border border-white/5 rounded-2xl overflow-hidden transition-all hover:border-white/10 ${isExpanded ? 'bg-white/10 border-white/20' : ''}`}>
                                    <div className="p-4 flex items-center gap-4 cursor-pointer" onClick={() => setExpandedApp(isExpanded ? null : dApp.packageName)}>
                                        <div className={`w-10 h-10 rounded-xl flex items-center justify-center border shadow-inner transition-colors ${isExpanded ? 'bg-indigo-500/20 border-indigo-500/50 text-indigo-400' : 'bg-black/40 border-white/10 text-gray-500'}`}>
                                            <i className={`fas ${activeTab === 'surgeon' ? 'fa-box-open' : 'fa-shield-halved'}`}></i>
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-2">
                                                <h4 className="text-white font-bold text-sm truncate">{dApp.name}</h4>
                                                {dApp.isSystem && <span className="text-[8px] bg-amber-500/10 text-amber-500 px-1.5 py-0.5 rounded border border-amber-500/30 font-black">SYS</span>}
                                            </div>
                                            <p className="text-[10px] text-gray-500 font-mono truncate hover:text-indigo-300 transition-colors" onClick={(e) => handleCopyPackage(e, dApp.packageName)}>{dApp.packageName}</p>
                                        </div>
                                        {activeTab === 'permissions' ? (
                                            <div className={`px-2 py-1 rounded-lg text-[9px] font-black uppercase tracking-wider border ${risk.bg} ${risk.color} ${risk.border}`}>{dApp.permissions.length} Flags</div>
                                        ) : (
                                            <button onClick={(e) => { e.stopPropagation(); handleExtract(dApp); }} disabled={isBusy} className="w-9 h-9 rounded-xl bg-blue-500/10 border border-blue-500/30 text-blue-400 flex items-center justify-center hover:bg-blue-500 hover:text-white transition-all active:scale-90">
                                                <i className={`fas ${isBusy ? 'fa-circle-notch fa-spin' : 'fa-download'}`}></i>
                                            </button>
                                        )}
                                    </div>
                                    {isExpanded && activeTab === 'permissions' && (
                                        <div className="px-4 pb-4 pt-0 animate-fade-in bg-black/20">
                                            <div className="h-px bg-gradient-to-r from-transparent via-white/10 to-transparent mb-3"></div>
                                            <div className="grid grid-cols-1 gap-2">
                                                {dApp.permissions.map(perm => (
                                                    <div key={perm} className="flex items-center justify-between bg-black/40 border border-white/5 px-3 py-2 rounded-xl group hover:border-red-500/30 transition-colors">
                                                        <div className="flex items-center gap-3 overflow-hidden">
                                                            <div className="w-6 h-6 rounded-lg bg-white/5 flex items-center justify-center text-gray-400"><i className={`fas ${getPermIcon(perm)} text-[10px]`}></i></div>
                                                            <span className="text-[10px] text-gray-300 font-mono truncate">{cleanPermName(perm)}</span>
                                                        </div>
                                                        <button onClick={() => triggerRevoke(dApp.packageName, perm)} disabled={isBusy} className="w-7 h-7 rounded-lg flex items-center justify-center bg-red-500/10 text-red-500 hover:bg-red-500 hover:text-white transition-all active:scale-90 border border-red-500/20 opacity-0 group-hover:opacity-100" title="Revoke Permission"><i className="fas fa-ban text-[10px]"></i></button>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            );
                        })}
                        {filteredList.length === 0 && (
                            <div className="text-center py-12 opacity-50">
                                <div className="w-16 h-16 bg-white/5 rounded-full flex items-center justify-center mx-auto mb-4"><i className="fas fa-search text-2xl text-gray-500"></i></div>
                                <p className="text-xs font-bold text-gray-500 uppercase tracking-widest">No signals found</p>
                            </div>
                        )}
                    </div>
                )}
            </div>

            {/* CUSTOM TOAST */}
            {toast && (
                <div className="absolute bottom-6 left-6 right-6 z-50 flex justify-center animate-slide-up">
                    <div className={`bg-black/90 backdrop-blur-xl border pl-4 pr-2 py-2.5 rounded-2xl shadow-2xl flex items-center gap-4 max-w-sm w-full ${toast.type === 'error' ? 'border-red-500/30 shadow-red-900/20' : 'border-white/10 shadow-black/50'}`}>
                        <div className={`w-8 h-8 rounded-xl flex items-center justify-center shrink-0 ${toast.type === 'error' ? 'bg-red-500/20 text-red-500' : toast.type === 'success' ? 'bg-emerald-500/20 text-emerald-500' : 'bg-indigo-500/20 text-indigo-400'}`}>
                            <i className={`fas ${toast.type === 'error' ? 'fa-bug' : toast.type === 'confirm' ? 'fa-question' : 'fa-check'}`}></i>
                        </div>
                        <span className="text-xs font-bold text-white leading-tight flex-1">{toast.msg}</span>
                        {toast.type === 'confirm' && toast.onConfirm ? (
                            <div className="flex gap-2">
                                <button onClick={toast.onConfirm} className="px-4 py-2 rounded-xl bg-red-600 hover:bg-red-500 text-white text-[10px] font-black transition-colors shadow-lg shadow-red-600/20">CONFIRM</button>
                                <button onClick={() => setToast(null)} className="w-8 h-8 rounded-xl hover:bg-white/10 text-gray-400 flex items-center justify-center transition-colors"><i className="fas fa-times"></i></button>
                            </div>
                        ) : (
                            <button onClick={() => setToast(null)} className="w-8 h-8 rounded-xl hover:bg-white/10 flex items-center justify-center text-gray-400 transition-colors"><i className="fas fa-times text-xs"></i></button>
                        )}
                    </div>
                </div>
            )}

        </div>
    </div>,
    document.body
  );
};

export default ShizukuPowerModal;