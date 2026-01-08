import React, { useState, useEffect } from 'react';
import { Capacitor } from '@capacitor/core';
import { LocalNotifications } from '@capacitor/local-notifications';
import { DevProfile, SocialLinks, FAQItem, AppItem, Platform } from '../types';
import AppTracker from '../plugins/AppTracker';

interface AboutViewProps {
  devProfile: DevProfile;
  socialLinks: SocialLinks;
  faqs: FAQItem[];
  isLegend: boolean;
  isContributor: boolean; 
  adWatchCount: number;
  profileImgError: boolean;
  setProfileImgError: (hasError: boolean) => void;
  handleProfileClick: () => void;
  setShowFAQ: (show: boolean) => void;
  onOpenAdDonation: () => void; 
  isDevUnlocked: boolean;
  useRemoteJson: boolean;
  toggleSourceMode: () => void;
  githubToken: string;
  isEditingToken: boolean;
  setIsEditingToken: (isEditing: boolean) => void;
  saveGithubToken: (token: string) => void;
  currentStoreVersion: string;
  onWipeCache: () => void;
  onTestStoreUpdate: () => void;
  mirrorSource: string;
  // Props kept for compatibility
  hiddenTabs: string[];
  toggleHiddenTab: (tab: string) => void;
  autoUpdateEnabled: boolean;
  toggleAutoUpdate: () => void;
  availableUpdates: AppItem[];
  onTriggerUpdate: (app: AppItem) => void;
}

// Custom Sword Icon for Cosmic Guardian
const SwordIcon = ({ className }: { className?: string }) => (
  <svg 
    viewBox="0 0 24 24" 
    fill="currentColor" 
    className={className}
    xmlns="http://www.w3.org/2000/svg"
  >
    <path d="M12 2L14.5 16H9.5L12 2Z" />
    <path d="M7 16H17L16.5 18H7.5L7 16Z" />
    <rect x="11" y="18" width="2" height="3" />
    <circle cx="12" cy="22" r="1.5" />
  </svg>
);

const AboutView: React.FC<AboutViewProps> = ({
  devProfile,
  socialLinks,
  isLegend,
  adWatchCount,
  profileImgError,
  setProfileImgError,
  handleProfileClick,
  setShowFAQ,
  onOpenAdDonation,
  isDevUnlocked,
  useRemoteJson,
  toggleSourceMode,
  githubToken,
  isEditingToken,
  setIsEditingToken,
  saveGithubToken,
  currentStoreVersion,
  onWipeCache,
  onTestStoreUpdate,
  mirrorSource
}) => {
  // Badge Logic
  const hasBackerBadge = adWatchCount >= 3;
  const hasSupernovaBadge = adWatchCount >= 6;
  const hasVoidBadge = adWatchCount >= 12;
  const hasCosmicBadge = adWatchCount >= 25;

  const getContributionLabel = () => {
      if (hasCosmicBadge) return "Rank: Cosmic Guardian";
      if (hasVoidBadge) return `Rank: Void Walker`;
      if (hasSupernovaBadge) return `Rank: Supernova`;
      if (hasBackerBadge) return `Rank: Backer`;
      return `Start your journey`;
  };

  // --- DEV TOOLS STATE ---
  const [rateLimit, setRateLimit] = useState<{ limit: number, remaining: number, reset: number } | null>(null);
  const [pkgQuery, setPkgQuery] = useState('');
  const [pkgResult, setPkgResult] = useState<string>('');
  const [showJson, setShowJson] = useState(false);
  const [deviceInfo, setDeviceInfo] = useState<any>(null);

  // --- DEV TOOLS LOGIC ---
  useEffect(() => {
      if (isDevUnlocked) {
          fetchRateLimit();
          gatherDeviceInfo();
      }
  }, [isDevUnlocked, githubToken]);

  const fetchRateLimit = async () => {
      try {
          const headers: HeadersInit = {};
          if (githubToken) headers['Authorization'] = `token ${githubToken}`;
          
          const res = await fetch('https://api.github.com/rate_limit', { headers });
          if (res.ok) {
              const data = await res.json();
              setRateLimit(data.resources.core);
          }
      } catch (e) {
          console.error("Rate limit check failed", e);
      }
  };

  const gatherDeviceInfo = async () => {
      const info = {
          userAgent: navigator.userAgent,
          platform: Capacitor.getPlatform(),
          screen: `${window.screen.width}x${window.screen.height}`,
          pixelRatio: window.devicePixelRatio,
          touch: navigator.maxTouchPoints > 0 ? 'Yes' : 'No',
          language: navigator.language
      };
      setDeviceInfo(info);
  };

  const checkPackage = async () => {
      if (!pkgQuery) return;
      setPkgResult("Checking...");
      try {
          if (Capacitor.isNativePlatform()) {
              const res = await AppTracker.getAppInfo({ packageName: pkgQuery });
              setPkgResult(JSON.stringify(res, null, 2));
          } else {
              setPkgResult("Native Plugin Unavailable (Web Mode)");
          }
      } catch (e: any) {
          setPkgResult(`Error: ${e.message}`);
      }
  };

  const testNotification = async (type: 'progress' | 'complete') => {
      if (!Capacitor.isNativePlatform()) {
          alert("Notifications only work on native Android.");
          return;
      }
      try {
          if (type === 'progress') {
              await LocalNotifications.schedule({
                  notifications: [{
                      title: "Test Download",
                      body: "Downloading... 50%",
                      id: 9999,
                      schedule: { at: new Date(Date.now() + 100) },
                      channelId: "orion_downloads"
                  }]
              });
          } else {
              await LocalNotifications.schedule({
                  notifications: [{
                      title: "Install Ready",
                      body: "Test App is ready to install.",
                      id: 10000,
                      schedule: { at: new Date(Date.now() + 100) },
                      channelId: "orion_updates",
                      extra: { appId: "test", fileName: "test.apk" }
                  }]
              });
          }
      } catch (e) {
          alert("Notification failed");
      }
  };

  const getCachedJson = () => {
      try {
          const cached = localStorage.getItem('orion_cached_apps_v2');
          return cached ? JSON.stringify(JSON.parse(cached), null, 2) : "Cache Empty";
      } catch { return "Error parsing cache"; }
  };

  const formatResetTime = (timestamp: number) => {
      const date = new Date(timestamp * 1000);
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  return (
    // UPDATED: Reduced padding bottom from pb-28 to pb-12
    <div className="p-5 pb-12 flex flex-col items-center text-center animate-fade-in">
        <div 
            onClick={handleProfileClick}
            className="w-32 h-32 rounded-full p-1 mb-4 bg-gradient-to-br from-acid to-primary animate-pulse-slow cursor-pointer transition-transform active:scale-90 select-none relative z-30"
        >
            {profileImgError ? (
                <div className="w-full h-full rounded-full bg-card border-4 border-card flex items-center justify-center relative overflow-hidden">
                    <div className="absolute inset-0 bg-gradient-to-br from-gray-900 to-black opacity-90"></div>
                    <div className="absolute w-full h-full bg-gradient-to-tr from-acid/20 to-neon/20 animate-pulse"></div>
                    <span className="relative text-7xl font-black text-transparent bg-clip-text bg-gradient-to-r from-acid via-primary to-neon tracking-tighter filter drop-shadow-lg">
                        R
                    </span>
                </div>
            ) : (
                <img 
                    src={devProfile.image} 
                    alt={devProfile.name} 
                    onError={() => setProfileImgError(true)}
                    className="w-full h-full rounded-full object-cover border-4 border-card bg-theme-element relative z-10"
                />
            )}
        </div>
        
        <div className="relative z-0 flex flex-col items-center w-full">
            <h2 className="text-3xl font-black text-theme-text mb-3">{devProfile.name}</h2>
            
            {/* Unified Badge Row */}
            {(isLegend || hasBackerBadge || isDevUnlocked) && (
                <div className="flex flex-wrap justify-center gap-2 mb-4 animate-fade-in max-w-sm">
                    {isLegend && (
                        <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-gradient-to-r from-yellow-500/10 to-orange-500/10 border border-yellow-500/30 text-yellow-600 dark:text-yellow-400 shadow-[0_0_10px_rgba(234,179,8,0.15)] backdrop-blur-sm">
                            <i className="fas fa-crown text-[10px] animate-bounce"></i>
                            <span className="text-[9px] font-black tracking-widest uppercase">Legend</span>
                        </div>
                    )}
                    {hasCosmicBadge && (
                        <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-gradient-to-r from-red-500/10 via-orange-500/10 to-red-500/10 dark:from-red-500/20 dark:via-orange-500/20 dark:to-red-500/20 border border-red-500/30 dark:border-red-500/50 shadow-[0_0_15px_rgba(239,68,68,0.2)] dark:shadow-[0_0_15px_rgba(239,68,68,0.4)] backdrop-blur-md animate-pulse">
                            <SwordIcon className="w-3 h-3 text-red-600 dark:text-red-400" />
                            <span className="text-[9px] font-black tracking-widest uppercase text-transparent bg-clip-text bg-gradient-to-r from-red-600 to-orange-600 dark:from-red-400 dark:to-orange-400">Guardian</span>
                        </div>
                    )}
                    {hasVoidBadge && !hasCosmicBadge && (
                        <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-gradient-to-r from-purple-500/10 to-indigo-500/10 border border-purple-500/30 text-purple-600 dark:text-purple-400 shadow-[0_0_15px_rgba(168,85,247,0.2)] backdrop-blur-sm">
                            <i className="fas fa-dragon text-[10px]"></i>
                            <span className="text-[9px] font-black tracking-widest uppercase">Void Walker</span>
                        </div>
                    )}
                    {hasSupernovaBadge && !hasVoidBadge && (
                        <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-gradient-to-r from-pink-500/10 to-rose-500/10 border border-pink-500/30 text-pink-600 dark:text-pink-400 shadow-[0_0_15px_rgba(236,72,153,0.2)] backdrop-blur-sm animate-pulse">
                            <i className="fas fa-meteor text-[10px]"></i>
                            <span className="text-[9px] font-black tracking-widest uppercase">Supernova</span>
                        </div>
                    )}
                    {hasBackerBadge && !hasSupernovaBadge && (
                        <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-gradient-to-r from-cyan-500/10 to-blue-500/10 border border-cyan-500/30 text-cyan-600 dark:text-cyan-400 shadow-[0_0_10px_rgba(6,182,212,0.15)] backdrop-blur-sm">
                            <i className="fas fa-gem text-[10px] animate-pulse"></i>
                            <span className="text-[9px] font-black tracking-widest uppercase">Backer</span>
                        </div>
                    )}
                    {isDevUnlocked && (
                        <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-gradient-to-r from-fuchsia-500/10 to-pink-500/10 border border-fuchsia-500/30 text-fuchsia-600 dark:text-fuchsia-400 shadow-[0_0_10px_rgba(217,70,239,0.15)] backdrop-blur-sm">
                            <i className="fas fa-terminal text-[10px]"></i>
                            <span className="text-[9px] font-black tracking-widest uppercase">Dev Mode</span>
                        </div>
                    )}
                </div>
            )}

            {/* UPDATED: Reduced margin bottom from mb-8 to mb-5 */}
            <p className="text-theme-sub max-w-md mb-5 text-lg">
                {devProfile.bio}
            </p>

            {/* UPDATED: Reduced spacing from space-y-6 to space-y-4 */}
            <div className="w-full max-w-md space-y-4">
                
                {/* Technology Stack Pill */}
                <div className="flex justify-center gap-2 mb-2 pt-2">
                    <span className="px-3 py-1 rounded-lg bg-blue-500/10 text-blue-500 border border-blue-500/20 text-xs font-bold flex items-center gap-1">
                        <i className="fab fa-react"></i> React
                    </span>
                    <span className="px-3 py-1 rounded-lg bg-sky-500/10 text-sky-500 border border-sky-500/20 text-xs font-bold flex items-center gap-1">
                        <i className="fas fa-wind"></i> Tailwind
                    </span>
                    <span className="px-3 py-1 rounded-lg bg-indigo-500/10 text-indigo-500 border border-indigo-500/20 text-xs font-bold flex items-center gap-1">
                        <i className="fas fa-mobile-alt"></i> Capacitor
                    </span>
                </div>

                <div className="space-y-3">
                    <div className="flex items-center gap-2 px-2">
                        <div className="h-px bg-theme-border flex-1"></div>
                        <span className="text-xs font-bold text-theme-sub uppercase tracking-widest">Connect</span>
                        <div className="h-px bg-theme-border flex-1"></div>
                    </div>
                    
                    <div className="grid grid-cols-2 gap-3">
                        <a href={socialLinks.github} target="_blank" rel="noreferrer" className="flex items-center justify-center gap-3 p-4 bg-card border border-theme-border rounded-2xl hover:scale-[1.02] transition-all cursor-pointer group shadow-sm">
                            <i className="fab fa-github text-2xl text-theme-text"></i>
                            <span className="font-bold text-theme-text">GitHub</span>
                        </a>
                        
                        <a href={socialLinks.x} target="_blank" rel="noreferrer" className="flex items-center justify-center gap-3 p-4 bg-theme-text text-surface rounded-2xl hover:scale-[1.02] transition-all cursor-pointer shadow-lg shadow-black/10 group border border-theme-border">
                            <div className="w-6 h-6 flex items-center justify-center">
                                <svg viewBox="0 0 24 24" aria-hidden="true" className="w-full h-full fill-current">
                                    <path d="M18.901 1.153h3.68l-8.04 9.19L24 22.846h-7.406l-5.8-7.584-6.638 7.584H.474l8.6-9.83L0 1.154h7.594l5.243 6.932ZM17.61 20.644h2.039L6.486 3.24H4.298Z"></path>
                                </svg>
                            </div>
                        </a>

                        <a href={socialLinks.discord} target="_blank" rel="noreferrer" className="col-span-2 flex items-center justify-between p-4 bg-[#5865F2]/10 rounded-2xl hover:scale-[1.01] transition-all cursor-pointer border border-[#5865F2]/20">
                            <div className="flex items-center gap-3">
                                <div className="w-8 h-8 rounded-full bg-[#5865F2] text-white flex items-center justify-center text-sm">
                                    <i className="fab fa-discord"></i>
                                </div>
                                <span className="font-bold text-[#5865F2]">Join Discord Community</span>
                            </div>
                            <i className="fas fa-arrow-right text-[#5865F2] text-sm opacity-50"></i>
                        </a>
                    </div>
                </div>

                <div className="space-y-3">
                    <div className="flex items-center gap-2 px-2">
                        <div className="h-px bg-theme-border flex-1"></div>
                        <span className="text-xs font-bold text-theme-sub uppercase tracking-widest">Resources</span>
                        <div className="h-px bg-theme-border flex-1"></div>
                    </div>
                    <div className="flex flex-col gap-3">
                        <a href={socialLinks.coffee} target="_blank" rel="noreferrer" className="flex items-center justify-between p-4 bg-yellow-50 dark:bg-yellow-900/20 border-2 border-yellow-400 rounded-2xl hover:scale-[1.01] transition-all cursor-pointer shadow-lg shadow-yellow-400/20 group">
                            <div className="flex items-center gap-4">
                                <div className="w-10 h-10 rounded-full bg-yellow-400 text-yellow-900 flex items-center justify-center text-xl group-hover:rotate-12 transition-transform">
                                    <i className="fas fa-coffee"></i>
                                </div>
                                <div className="text-left">
                                    <span className="font-bold text-gray-900 dark:text-yellow-100 text-lg block">Buy me a coffee</span>
                                    <span className="text-xs text-yellow-600 dark:text-yellow-200 font-semibold">Support development</span>
                                </div>
                            </div>
                            <i className="fas fa-heart text-red-500 animate-bounce"></i>
                        </a>

                        <button 
                            onClick={onOpenAdDonation}
                            className="flex items-center justify-between p-4 bg-indigo-50 dark:bg-indigo-900/20 border-2 border-indigo-400 rounded-2xl hover:scale-[1.01] transition-all cursor-pointer shadow-lg shadow-indigo-400/20 group w-full text-left"
                        >
                            <div className="flex items-center gap-4">
                                <div className="w-10 h-10 rounded-full bg-indigo-500 text-white flex items-center justify-center text-lg group-hover:scale-110 transition-transform">
                                    <i className="fas fa-play-circle"></i>
                                </div>
                                <div className="text-left">
                                    <span className="font-bold text-gray-900 dark:text-indigo-100 text-lg block">Fuel the Code</span>
                                    <span className="text-xs text-indigo-600 dark:text-indigo-300 font-semibold">
                                        {getContributionLabel()}
                                    </span>
                                </div>
                            </div>
                            {hasCosmicBadge ? (
                                <SwordIcon className="w-5 h-5 text-red-500 animate-pulse" />
                            ) : hasVoidBadge ? (
                                <i className="fas fa-dragon text-purple-500 animate-pulse"></i>
                            ) : hasSupernovaBadge ? (
                                <i className="fas fa-meteor text-pink-500 animate-pulse"></i>
                            ) : hasBackerBadge ? (
                                <i className="fas fa-gem text-cyan-400 animate-pulse"></i>
                            ) : (
                                <i className="fas fa-arrow-right text-indigo-500 group-hover:translate-x-1 transition-transform"></i>
                            )}
                        </button>

                        <button 
                            onClick={() => setShowFAQ(true)}
                            className="flex items-center justify-between p-4 bg-purple-50 dark:bg-purple-900/20 border-2 border-dashed border-purple-300 dark:border-purple-700 rounded-2xl hover:scale-[1.01] transition-all cursor-pointer w-full group text-left"
                        >
                            <div className="flex items-center gap-4">
                                <div className="w-10 h-10 rounded-full bg-purple-400 text-white flex items-center justify-center text-xl group-hover:bg-purple-500 transition-colors">
                                    <i className="fas fa-question"></i>
                                </div>
                                <div>
                                    <span className="font-bold text-gray-900 dark:text-purple-100 text-lg block">FAQs</span>
                                    <span className="text-xs text-purple-600 dark:text-purple-300 font-semibold">Secrets & Safety</span>
                                </div>
                            </div>
                            <i className="fas fa-chevron-right text-purple-400 group-hover:translate-x-1 transition-transform"></i>
                        </button>
                    </div>
                </div>
            </div>
            
            {/* Developer Command Center */}
            {isDevUnlocked && (
                <div className="flex flex-col items-center gap-4 mt-8 w-full max-w-md animate-fade-in">
                     <div className="flex items-center gap-2 px-2 w-full">
                        <div className="h-px bg-theme-border flex-1"></div>
                        <span className="text-xs font-black text-primary uppercase tracking-widest flex items-center gap-2">
                            <i className="fas fa-code-branch"></i> Developer Options
                        </span>
                        <div className="h-px bg-theme-border flex-1"></div>
                    </div>

                    {/* RESTORED: GitHub Token Card (Standalone & Larger) */}
                    <div className="w-full bg-card border border-theme-border rounded-2xl p-5 shadow-sm text-left">
                        <div className="flex justify-between items-start mb-2">
                            <div>
                                <h3 className="font-bold text-theme-text text-sm flex items-center gap-2">
                                    <i className="fab fa-github"></i> GitHub Access
                                </h3>
                                <p className="text-xs text-theme-sub mt-1">Unlock 5000 requests/hr (Authenticated)</p>
                            </div>
                            <button onClick={() => setIsEditingToken(!isEditingToken)} className="text-xs text-primary font-bold hover:underline">
                                {isEditingToken ? 'Cancel' : 'Edit Token'}
                            </button>
                        </div>
                        
                        {isEditingToken ? (
                            <div className="flex gap-2 mt-3">
                                <input 
                                    type="password" 
                                    placeholder="ghp_..."
                                    className="flex-1 bg-theme-input border border-theme-border rounded-xl px-3 py-2 text-xs font-mono focus:border-primary outline-none"
                                    onKeyDown={(e) => {
                                        if(e.key === 'Enter') saveGithubToken((e.target as HTMLInputElement).value)
                                    }}
                                />
                                <button 
                                    className="bg-primary text-white px-4 rounded-xl text-xs font-bold shadow-lg shadow-primary/20"
                                    onClick={(e) => {
                                            const input = (e.currentTarget.previousElementSibling as HTMLInputElement);
                                            saveGithubToken(input.value);
                                    }}
                                >
                                    Save
                                </button>
                            </div>
                        ) : (
                            <div className="flex items-center gap-3 mt-3 bg-theme-element p-3 rounded-xl border border-theme-border">
                                <div className={`w-2 h-2 rounded-full ${githubToken ? 'bg-green-500' : 'bg-red-500'}`}></div>
                                <span className="text-xs font-mono text-theme-sub flex-1 truncate">
                                    {githubToken ? `••••••••••••${githubToken.slice(-4)}` : 'No Token Set (Rate Limited)'}
                                </span>
                                {githubToken && (
                                    <button onClick={() => saveGithubToken('')} className="text-red-500 hover:bg-red-500/10 p-1.5 rounded-full transition-colors">
                                        <i className="fas fa-trash-alt text-xs"></i>
                                    </button>
                                )}
                            </div>
                        )}
                    </div>

                    <div className="flex flex-col gap-4 w-full p-5 bg-card border border-theme-border rounded-2xl shadow-lg shadow-primary/5 text-left">
                         
                         {/* API Monitor */}
                         {rateLimit && (
                             <div className="space-y-2">
                                 <div className="flex justify-between items-center">
                                     <span className="text-[10px] font-bold text-theme-sub uppercase tracking-wider">GitHub API Quota</span>
                                     <span className="text-[10px] font-mono text-theme-text">{rateLimit.remaining}/{rateLimit.limit}</span>
                                 </div>
                                 <div className="w-full h-1.5 bg-theme-element rounded-full overflow-hidden">
                                     <div 
                                        className={`h-full rounded-full transition-all duration-500 ${rateLimit.remaining < 10 ? 'bg-red-500' : 'bg-green-500'}`} 
                                        style={{ width: `${(rateLimit.remaining / rateLimit.limit) * 100}%` }}
                                     ></div>
                                 </div>
                                 <div className="flex justify-between text-[9px] text-theme-sub font-mono">
                                     <span>{githubToken ? 'Authenticated' : 'Public IP'}</span>
                                     <span>Resets: {formatResetTime(rateLimit.reset)}</span>
                                 </div>
                             </div>
                         )}

                         <div className="h-px bg-theme-border w-full"></div>

                         {/* Package Detective */}
                         <div className="space-y-2">
                             <span className="text-[10px] font-bold text-theme-sub uppercase tracking-wider">Package Detective</span>
                             <div className="flex gap-2">
                                 <input 
                                    type="text" 
                                    className="flex-1 bg-theme-input border border-theme-border rounded-lg px-2 py-1.5 text-xs font-mono focus:border-primary outline-none"
                                    placeholder="com.example.app"
                                    value={pkgQuery}
                                    onChange={(e) => setPkgQuery(e.target.value)}
                                 />
                                 <button onClick={checkPackage} className="px-3 py-1.5 bg-theme-element hover:bg-theme-hover rounded-lg text-xs font-bold border border-theme-border"><i className="fas fa-search"></i></button>
                             </div>
                             {pkgResult && (
                                 <pre className="bg-black/50 p-2 rounded-lg text-[10px] font-mono text-green-400 overflow-x-auto border border-white/10">
                                     {pkgResult}
                                 </pre>
                             )}
                         </div>

                         <div className="h-px bg-theme-border w-full"></div>

                         {/* Notification Lab */}
                         <div className="space-y-2">
                             <span className="text-[10px] font-bold text-theme-sub uppercase tracking-wider">Notification Lab</span>
                             <div className="grid grid-cols-2 gap-2">
                                 <button onClick={() => testNotification('progress')} className="py-2 bg-theme-element hover:bg-theme-hover rounded-lg text-[10px] font-bold border border-theme-border">Test Progress</button>
                                 <button onClick={() => testNotification('complete')} className="py-2 bg-theme-element hover:bg-theme-hover rounded-lg text-[10px] font-bold border border-theme-border">Test Complete</button>
                             </div>
                         </div>

                         <div className="h-px bg-theme-border w-full"></div>

                         {/* Device Dump */}
                         <div className="space-y-2">
                             <span className="text-[10px] font-bold text-theme-sub uppercase tracking-wider">Device Dump</span>
                             <div className="bg-theme-input p-3 rounded-xl border border-theme-border text-[9px] font-mono text-theme-sub grid grid-cols-2 gap-y-1">
                                 {deviceInfo && Object.entries(deviceInfo).map(([k, v]) => (
                                     <React.Fragment key={k}>
                                         <span className="font-bold opacity-60 capitalize">{k}:</span>
                                         <span className="text-right truncate">{String(v)}</span>
                                     </React.Fragment>
                                 ))}
                             </div>
                         </div>

                         <div className="h-px bg-theme-border w-full"></div>

                         {/* JSON Explorer */}
                         <div>
                             <button onClick={() => setShowJson(!showJson)} className="w-full flex justify-between items-center text-xs font-bold text-theme-text py-1">
                                 <span>Raw Metadata Explorer</span>
                                 <i className={`fas fa-chevron-${showJson ? 'up' : 'down'}`}></i>
                             </button>
                             {showJson && (
                                 <textarea 
                                    readOnly 
                                    className="w-full h-32 mt-2 bg-black text-green-500 font-mono text-[9px] p-2 rounded-lg border border-theme-border resize-none focus:outline-none"
                                    value={getCachedJson()}
                                 />
                             )}
                         </div>

                         <div className="h-px bg-theme-border w-full my-2"></div>

                         {/* Core Controls */}
                         <div className="grid grid-cols-2 gap-3">
                            <button 
                                onClick={toggleSourceMode}
                                className={`px-4 py-2 rounded-xl text-xs font-bold transition-all border ${useRemoteJson ? 'bg-primary text-white border-primary' : 'bg-theme-element text-theme-sub border-theme-border'}`}
                            >
                                {useRemoteJson ? "Remote Mode" : "Local Mode"}
                            </button>
                            <button 
                                onClick={onTestStoreUpdate}
                                className="px-4 py-2 rounded-xl text-xs font-bold bg-theme-element text-theme-sub border border-theme-border hover:text-primary"
                            >
                                Test Update UI
                            </button>
                         </div>
                    </div>

                    {/* Nuclear Button */}
                    <button
                       onClick={onWipeCache}
                       className="w-full px-4 py-3 rounded-xl bg-red-500/10 text-red-500 text-xs font-bold hover:bg-red-500/20 transition-colors flex items-center justify-center gap-2 border border-red-500/20"
                    >
                       <i className="fas fa-radiation"></i>
                       NUCLEAR RESET (Wipe & Restart)
                    </button>
                </div>
            )}

            {/* UPDATED: Reduced top margin from mt-12 to mt-6 */}
            <div className="mt-6 mb-2 flex flex-col items-center gap-4 animate-fade-in">
                <div className="flex items-center gap-3 text-sm font-medium text-theme-sub">
                    <span className="opacity-60 font-mono">v{currentStoreVersion}</span>
                    <span className="w-1 h-1 rounded-full bg-theme-border"></span>
                    <div className={`px-3 py-1 rounded-full border text-xs font-bold flex items-center gap-2 shadow-sm ${
                        useRemoteJson 
                        ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-600 dark:text-emerald-400' 
                        : 'bg-amber-500/10 border-amber-500/20 text-amber-600 dark:text-amber-400'
                    }`}>
                        <span className="uppercase tracking-wider opacity-80">Source:</span>
                        <span>{useRemoteJson ? "Remote" : "Local"}</span>
                    </div>
                </div>
                <span className="text-xs font-mono text-theme-sub opacity-40">Made with 💜 for Geeks</span>
            </div>
        </div>
    </div>
  );
};

export default AboutView;