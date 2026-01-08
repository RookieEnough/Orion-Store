
import React, { useState, useEffect, useRef } from 'react';
import { Capacitor } from '@capacitor/core';
import { App } from '@capacitor/app';
import { LocalNotifications } from '@capacitor/local-notifications';
import { Haptics, ImpactStyle, NotificationType } from '@capacitor/haptics';
import { AppItem, Platform } from '../types';
import { MICROG_DEPENDENT_APPS, MICROG_INFO_URL, CATEGORY_GRADIENTS } from '../constants';
import AppTracker from '../plugins/AppTracker';

// Polyfill for hashCode
if (!String.prototype.hashCode) {
    String.prototype.hashCode = function() {
        let hash = 0;
        for (let i = 0; i < this.length; i++) {
            const char = this.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash;
        }
        return Math.abs(hash);
    };
}

interface AppDetailProps {
  app: AppItem;
  onClose: () => void;
  onDownload: (app: AppItem, url?: string) => void;
  isInstalling: boolean;
  localVersion?: string; 
  supportEmail: string;
  isUpdateAvailable: boolean;
  activeDownloadId?: string;
  cleanupFileName?: string;
  onCleanupDone?: () => void;
  currentProgress?: number;
  currentStatus?: string;
  readyFileName?: string;
  onCancelDownload?: (app: AppItem, id: string) => void;
  onDeleteReadyFile?: (app: AppItem, fileName: string) => void; 
  onNavigateToApp?: (appId: string) => void;
}

const getOptimizedImageUrl = (url: string, height: number = 600) => {
    if (!url || typeof url !== 'string' || url.includes('data:') || url.includes('blob:')) return url;
    return `https://wsrv.nl/?url=${encodeURIComponent(url)}&h=${height}&output=webp&q=80`;
};

interface LazyScreenshotProps {
    src: string;
    index: number;
    platform: Platform;
}

const LazyScreenshot: React.FC<LazyScreenshotProps> = ({ src, index, platform }) => {
    const [isVisible, setIsVisible] = useState(false);
    const [isLoaded, setIsLoaded] = useState(false);
    const [hasError, setHasError] = useState(false);
    const containerRef = useRef<HTMLDivElement>(null);

    const heightClass = platform === Platform.PC ? 'h-40' : 'h-72';
    const widthClass = platform === Platform.PC ? 'w-64' : 'w-36'; 
    const targetRequestHeight = platform === Platform.PC ? 400 : 600;

    useEffect(() => {
        const observer = new IntersectionObserver(
            ([entry]) => {
                if (entry.isIntersecting) {
                    setIsVisible(true);
                    observer.disconnect();
                }
            },
            { root: null, rootMargin: '0px 200px 0px 0px', threshold: 0.01 }
        );
        if (containerRef.current) observer.observe(containerRef.current);
        return () => { if (observer) observer.disconnect(); };
    }, []);

    if (hasError) return null;
    const optimizedSrc = getOptimizedImageUrl(src, targetRequestHeight);

    return (
        <div ref={containerRef} className={`relative shrink-0 snap-center ${heightClass} ${widthClass} rounded-2xl overflow-hidden border border-theme-border bg-theme-element`}>
            {(!isLoaded || !isVisible) && <div className={`absolute inset-0 bg-theme-element animate-pulse`} />}
            {isVisible && (
                <img
                    src={optimizedSrc}
                    alt={`Screenshot ${index + 1}`}
                    loading="lazy"
                    onLoad={() => setIsLoaded(true)}
                    onError={() => setHasError(true)}
                    className={`w-full h-full object-cover transition-opacity duration-500 ${isLoaded ? 'opacity-100' : 'opacity-0'}`}
                />
            )}
        </div>
    );
};

const AppDetail: React.FC<AppDetailProps> = ({ 
    app, onClose, onDownload, isInstalling, localVersion, supportEmail, isUpdateAvailable, 
    activeDownloadId, cleanupFileName, onCleanupDone,
    currentProgress, currentStatus, readyFileName,
    onCancelDownload, onDeleteReadyFile
}) => {
  const [showVariants, setShowVariants] = useState(false);
  const [showMicroGNotice, setShowMicroGNotice] = useState(false);
  const [showCleanupPrompt, setShowCleanupPrompt] = useState(!!cleanupFileName);
  const [iconStatus, setIconStatus] = useState<'loading' | 'loaded' | 'error'>('loading');

  const bgGradient = CATEGORY_GRADIENTS[app.category] || CATEGORY_GRADIENTS['Default'];
  const needsUpdate = isUpdateAvailable;
  const isInstalled = !!localVersion;
  const isUpToDate = isInstalled && !needsUpdate;
  const isFallbackMode = app.latestVersion === "Unknown" || app.version === "View on GitHub";
  
  // FIXED: Safety check for app.downloadUrl
  const rawUrl = app.downloadUrl || '';
  const cleanUrl = rawUrl.toLowerCase();
  const isDirectFile = cleanUrl.endsWith('.apk') || cleanUrl.endsWith('.exe') || cleanUrl.endsWith('.zip') || cleanUrl.endsWith('.dmg');
  const isValidWebUrl = cleanUrl.startsWith('http://') || cleanUrl.startsWith('https://');
  // Check includes on safe string
  const isExternalSource = isValidWebUrl && !isDirectFile && !cleanUrl.includes('github.com');
  
  let externalDomain = 'External Source';
  if (isExternalSource) {
      try { externalDomain = new URL(rawUrl).hostname.replace('www.', ''); } catch (e) { externalDomain = 'Source'; }
  }

  useEffect(() => {
    if (cleanupFileName) setShowCleanupPrompt(true);
  }, [cleanupFileName]);

  const handleAction = (url?: string) => {
      if (isInstalling || activeDownloadId) return;
      if (MICROG_DEPENDENT_APPS.includes(app.id) && !url && !readyFileName) {
          setShowMicroGNotice(true);
          return;
      }
      onDownload(app, url);
  };

  const handleLaunch = () => {
      if (app.packageName && Capacitor.isNativePlatform()) {
          Haptics.impact({ style: ImpactStyle.Heavy });
          AppTracker.launchApp({ packageName: app.packageName }).catch(() => {
              alert("Could not launch app. It may be restricted.");
          });
      }
  };

  const handleUninstall = () => {
      if (app.packageName && Capacitor.isNativePlatform()) {
          if(confirm("Uninstall " + app.name + "?")) {
              AppTracker.uninstallApp({ packageName: app.packageName });
          }
      }
  };

  const handleCleanupDelete = async () => {
      if (cleanupFileName) {
          Haptics.notification({ type: NotificationType.Success });
          try {
              await AppTracker.deleteFile({ fileName: cleanupFileName });
              setShowCleanupPrompt(false);
              if (onCleanupDone) onCleanupDone();
          } catch (e) {
              console.error("Delete failed", e);
              setShowCleanupPrompt(false);
              if (onCleanupDone) onCleanupDone();
          }
      }
  };

  const handleRedownload = () => {
      if (readyFileName && onDeleteReadyFile) {
          Haptics.impact({ style: ImpactStyle.Medium });
          onDeleteReadyFile(app, readyFileName);
      }
  };

  const renderActionButton = () => {
      if (readyFileName) {
          return (
              <div className="flex gap-3">
                  <button 
                      onClick={() => handleAction()} 
                      disabled={isInstalling}
                      className="flex-1 py-4 rounded-2xl font-bold text-lg shadow-xl flex items-center justify-center gap-2 transition-all active:scale-95 bg-primary text-white hover:bg-primary/90 shadow-primary/30 animate-fade-in"
                  >
                      <i className="fas fa-box-open"></i>
                      <span>{isInstalling ? 'Installing...' : 'Install Now'}</span>
                  </button>
                  <button 
                      onClick={handleRedownload}
                      disabled={isInstalling}
                      className="w-14 rounded-2xl bg-red-500/10 border border-red-500/30 flex items-center justify-center text-red-500 hover:bg-red-500 hover:text-white transition-all active:scale-95"
                  >
                      <i className="fas fa-trash-alt"></i>
                  </button>
              </div>
          );
      }

      const isActuallyDownloading = currentStatus === 'RUNNING';
      if (isActuallyDownloading) {
          return (
              <div className="w-full h-14 bg-theme-element rounded-2xl relative overflow-hidden flex items-center justify-between border border-theme-border animate-fade-in">
                  <div className="flex-1 relative h-full flex items-center justify-center">
                        <div className="absolute left-0 top-0 bottom-0 transition-all duration-300 ease-out bg-primary/20" style={{ width: `${currentProgress || 0}%` }}></div>
                        <div className="relative z-10 flex items-center gap-3">
                            <div className="w-5 h-5 border-2 border-primary border-t-transparent rounded-full animate-spin"></div>
                            <span className="font-black text-theme-text text-sm tracking-tighter">{Math.floor(currentProgress || 0)}%</span>
                        </div>
                  </div>
                  <button 
                      onClick={(e) => { e.stopPropagation(); onCancelDownload && activeDownloadId && onCancelDownload(app, activeDownloadId); }}
                      className="w-12 h-full rounded-r-2xl flex items-center justify-center hover:bg-red-500 hover:text-white text-theme-sub transition-colors border-l border-theme-border group"
                  >
                      <i className="fas fa-times text-sm group-active:scale-90"></i>
                  </button>
              </div>
          );
      }

      if (isUpToDate && app.platform === Platform.ANDROID) {
          return (
              <div className="flex gap-3">
                  <button 
                      onClick={handleLaunch}
                      className="flex-1 py-4 rounded-2xl font-bold text-lg shadow-sm flex items-center justify-center gap-2 bg-green-500 text-white hover:bg-green-600 transition-colors"
                  >
                      <i className="fas fa-play"></i><span>Open</span>
                  </button>
                  <button 
                      onClick={handleUninstall}
                      className="w-14 rounded-2xl bg-theme-element border border-theme-border flex items-center justify-center text-theme-sub hover:text-red-500 transition-colors"
                  >
                      <i className="fas fa-trash-alt"></i>
                  </button>
              </div>
          );
      }

      return (
          <button 
              onClick={() => handleAction()} 
              disabled={isInstalling || isFallbackMode} 
              className={`w-full py-4 rounded-2xl font-bold text-lg shadow-xl flex items-center justify-center gap-2 transition-all active:scale-95 ${isFallbackMode ? 'bg-theme-element text-theme-sub cursor-not-allowed border border-theme-border' : isInstalling ? 'bg-theme-element text-theme-sub cursor-wait border border-theme-border' : isExternalSource ? 'bg-indigo-600 text-white hover:bg-indigo-700 shadow-indigo-500/30' : needsUpdate ? 'bg-acid text-black hover:bg-acid/90 shadow-acid/30' : 'bg-primary text-white hover:bg-primary/90 shadow-primary/30'}`}
          >
            {isInstalling ? <><div className="w-5 h-5 border-2 border-current border-t-transparent rounded-full animate-spin"></div><span>Installing...</span></> : isFallbackMode ? <><i className="fas fa-external-link-alt"></i><span>View on GitHub</span></> : isExternalSource ? <><i className="fas fa-external-link-alt"></i><span>Get from Source</span></> : needsUpdate ? <><i className="fas fa-sync-alt"></i><span>Update Now</span></> : app.platform === Platform.PC ? <><i className="fas fa-desktop"></i><span>Get on PC</span></> : <><i className="fas fa-download"></i><span>Download</span></>}
          </button>
      );
  };

  return (
    <div className="fixed inset-0 z-[100] bg-surface flex flex-col animate-slide-up overflow-hidden">
        <div className="absolute top-0 left-0 right-0 h-96 overflow-hidden -z-10 opacity-30 dark:opacity-20 pointer-events-none">
            <div className="absolute inset-0 bg-gradient-to-b from-current to-surface text-primary/30"></div>
            <img src={getOptimizedImageUrl(app.icon, 50)} className="w-full h-full object-cover blur-3xl scale-150" alt="" />
            <div className="absolute inset-0 bg-gradient-to-b from-transparent via-surface/80 to-surface"></div>
        </div>
      <div className="flex-1 overflow-y-auto pb-40 no-scrollbar relative">
        <div className="px-4 pb-0 pt-[calc(1rem+env(safe-area-inset-top))] flex items-center justify-between z-10 relative">
            <button onClick={onClose} className="w-10 h-10 rounded-full bg-theme-element/80 border border-theme-border backdrop-blur-md flex items-center justify-center hover:bg-theme-hover transition-colors text-theme-text shadow-sm"><i className="fas fa-arrow-left"></i></button>
            <div className="flex gap-3">
                <button onClick={() => { Haptics.selection(); setShowVariants(true); }} className="w-10 h-10 rounded-full bg-theme-element/80 border border-theme-border backdrop-blur-md flex items-center justify-center hover:bg-theme-hover transition-colors text-theme-text shadow-sm"><i className="fas fa-share-alt"></i></button>
                <button onClick={() => { Haptics.impact({ style: ImpactStyle.Light }); const subject = `Report Issue: ${app.name}`; window.location.href = `mailto:${supportEmail}?subject=${encodeURIComponent(subject)}`; }} className="w-10 h-10 rounded-full bg-theme-element/80 border border-theme-border backdrop-blur-md text-theme-sub flex items-center justify-center hover:text-red-500 transition-colors shadow-sm"><i className="fas fa-flag"></i></button>
            </div>
        </div>
        <div className="px-6 pt-6 pb-6 flex gap-5 items-start">
          <div className="relative shrink-0 w-24 h-24">
             {iconStatus === 'loading' && <div className="absolute inset-0 bg-theme-element rounded-2xl animate-pulse" />}
             {iconStatus === 'error' ? <div className={`w-full h-full rounded-2xl flex items-center justify-center text-white text-4xl font-bold shadow-inner ${bgGradient}`}>{app.name.charAt(0).toUpperCase()}</div> : <img src={getOptimizedImageUrl(app.icon, 200)} alt={app.name} onLoad={() => setIconStatus('loaded')} onError={() => setIconStatus('error')} className={`w-full h-full object-contain drop-shadow-md rounded-2xl transition-opacity duration-500 ${iconStatus === 'loaded' ? 'opacity-100' : 'opacity-0'}`} />}
          </div>
          <div className="flex-1 pt-1 min-w-0">
              <h1 className="text-2xl font-black text-theme-text mb-1 leading-tight">{app.name}</h1>
              <div className="flex flex-col gap-1">
                  <div className="flex items-center gap-2"><span className="text-primary font-bold text-sm">{app.author}</span><i className="fas fa-check-circle text-acid text-xs"></i></div>
                  {isExternalSource && <span className="inline-flex items-center gap-1 text-[10px] text-orange-500 font-bold uppercase tracking-wider mt-1"><i className="fas fa-external-link-alt"></i>Hosted on {externalDomain}</span>}
              </div>
          </div>
        </div>
        <div className="px-6 mb-6 flex flex-wrap gap-2">
             <span className="px-3 py-1 rounded-lg bg-theme-element text-theme-sub text-xs font-bold uppercase tracking-wide border border-theme-border">{app.category}</span>
             {cleanupFileName ? (
                 <span className="px-3 py-1 rounded-lg bg-acid/20 text-acid-dark dark:text-acid text-xs font-bold uppercase tracking-wide border border-acid/30 animate-pulse">Pending Cleanup</span>
             ) : readyFileName ? (
                 <span className="px-3 py-1 rounded-lg bg-primary/20 text-primary text-xs font-bold uppercase tracking-wide border border-primary/30 animate-pulse">Ready to Install</span>
             ) : needsUpdate ? (
                 <span className="px-3 py-1 rounded-lg bg-acid/20 text-acid-dark dark:text-acid text-xs font-bold uppercase tracking-wide border border-acid/30 animate-pulse">Update Available</span>
             ) : isUpToDate ? (
                 <span className="px-3 py-1 rounded-lg bg-green-500/10 text-green-600 dark:text-green-400 text-xs font-bold uppercase tracking-wide border border-green-500/20">Installed v{localVersion}</span>
             ) : null}
        </div>
        <div className="px-6 mb-6">
            <div className="flex items-center justify-between bg-card border border-theme-border rounded-2xl p-4 shadow-sm">
                <div className="flex flex-col items-center flex-1 border-r border-theme-border"><span className="font-black text-theme-text text-lg">{app.platform === Platform.ANDROID ? <i className="fab fa-android text-green-500 text-2xl"></i> : <i className="fab fa-windows text-blue-500 text-2xl"></i>}</span><span className="text-[10px] text-theme-sub font-bold uppercase mt-1">{app.platform}</span></div>
                <div className="flex flex-col items-center flex-1 border-r border-theme-border"><span className="font-black text-theme-text text-lg truncate max-w-[80px]">{app.latestVersion.replace(/^v/, '')}</span><span className="text-[10px] text-theme-sub font-bold uppercase mt-1">Version</span></div>
                <div className="flex flex-col items-center flex-1"><span className="font-black text-theme-text text-lg">{app.size}</span><span className="text-[10px] text-theme-sub font-bold uppercase mt-1">Size</span></div>
            </div>
        </div>

        {app.repoUrl && app.repoUrl !== '#' && (
            <div className="px-6 mb-6">
                <button 
                    onClick={() => {
                        Haptics.selection();
                        window.open(app.repoUrl, '_blank');
                    }}
                    className="w-full py-4 bg-card border border-theme-border rounded-2xl flex items-center justify-center gap-3 hover:bg-theme-element transition-all active:scale-[0.98] shadow-sm group"
                >
                    <i className="fab fa-github text-2xl text-theme-text group-hover:scale-110 transition-transform"></i>
                    <span className="font-bold text-theme-text">Show Repository</span>
                </button>
            </div>
        )}

        <div className="mb-8"><h3 className="px-6 text-lg font-bold text-theme-text mb-4">Preview</h3><div className="flex gap-4 overflow-x-auto px-6 pb-4 no-scrollbar snap-x">{app.screenshots.map((src, idx) => <LazyScreenshot key={idx} src={src} index={idx} platform={app.platform} />)}</div></div>
        <div className="px-6 mb-8"><h3 className="text-lg font-bold text-theme-text mb-3">About this app</h3><p className="text-theme-sub leading-relaxed whitespace-pre-wrap font-medium text-sm">{app.description}</p></div>
        <div className="h-px bg-theme-border mx-6 mb-8"></div>
        <div className="px-6 mb-8">
            <h3 className="text-lg font-bold text-theme-text mb-4">App info</h3>
            <div className="grid grid-cols-1 gap-1">
                {isInstalled && <div className="flex justify-between items-center py-3 border-b border-theme-border"><span className="text-theme-sub font-medium text-sm">Installed Version</span><span className="text-theme-text font-bold text-sm">{localVersion}</span></div>}
                <div className="flex justify-between items-center py-3 border-b border-theme-border"><span className="text-theme-sub font-medium text-sm">Developer</span><span className="text-theme-text font-bold text-sm text-primary">{app.author}</span></div>
                 <div className="flex justify-between items-center py-3 border-b border-theme-border"><span className="text-theme-sub font-medium text-sm">Size</span><span className="text-theme-text font-bold text-sm">{app.size}</span></div>
                 {app.githubRepo && <div className="flex justify-between items-center py-3 border-b border-theme-border"><span className="text-theme-sub font-medium text-sm">Source</span><span className="text-theme-text font-mono text-xs opacity-70 truncate max-w-[150px]">{app.githubRepo}</span></div>}
                 {app.packageName && <div className="flex justify-between items-center py-3 border-b border-theme-border"><span className="text-theme-sub font-medium text-sm">Package</span><span className="text-theme-text font-mono text-xs opacity-70 truncate max-w-[150px]">{app.packageName}</span></div>}
            </div>
        </div>
      </div>
      <div className="absolute bottom-0 left-0 right-0 p-6 bg-surface/90 backdrop-blur-xl border-t border-theme-border z-20">{renderActionButton()}</div>
      
      {showCleanupPrompt && (
        <div className="absolute inset-0 z-[110] flex items-center justify-center p-6 bg-black/70 backdrop-blur-md animate-fade-in">
            <div className="bg-surface border border-theme-border rounded-[2.5rem] p-8 w-full max-w-sm shadow-2xl relative animate-slide-up overflow-hidden" onClick={e => e.stopPropagation()}>
                <div className="absolute top-0 right-0 w-32 h-32 bg-primary/5 rounded-full -translate-y-1/2 translate-x-1/2"></div>
                <div className="w-20 h-20 bg-primary text-white rounded-3xl flex items-center justify-center text-4xl mb-6 shadow-2xl shadow-primary/40 mx-auto transform -rotate-3"><i className="fas fa-broom"></i></div>
                <h3 className="text-2xl font-black text-theme-text text-center mb-2 tracking-tight">Cleanup Time!</h3>
                <p className="text-theme-sub text-center text-sm mb-8 leading-relaxed font-medium">The APK for <b>{app.name}</b> is no longer needed. Should we wipe it to free up space?</p>
                <div className="flex flex-col gap-3">
                    <button onClick={handleCleanupDelete} className="w-full py-4 rounded-2xl font-bold bg-acid text-black shadow-lg shadow-acid/20 hover:scale-[1.02] active:scale-95 transition-all flex items-center justify-center gap-2"><i className="fas fa-trash-alt"></i><span>Delete APK</span></button>
                    <button onClick={() => { Haptics.selection(); setShowCleanupPrompt(false); if(onCleanupDone) onCleanupDone(); }} className="w-full py-3 rounded-2xl font-bold bg-theme-element text-theme-sub hover:bg-theme-hover transition-colors text-xs uppercase tracking-widest">Keep for now</button>
                </div>
            </div>
        </div>
      )}

      {showVariants && (
          <div className="absolute inset-0 z-[110] flex items-center justify-center p-6 bg-black/80 backdrop-blur-md animate-fade-in">
              <div className="bg-surface border border-theme-border rounded-3xl p-6 w-full max-w-sm shadow-2xl animate-slide-up">
                  <h3 className="text-xl font-black text-theme-text mb-4">Select Architecture</h3>
                  <div className="space-y-2">
                      {app.variants?.map(v => (
                          <button key={v.url} onClick={() => { setShowVariants(false); handleAction(v.url); }} className="w-full p-4 rounded-2xl bg-theme-element border border-theme-border flex justify-between items-center hover:bg-theme-hover transition-all">
                              <span className="font-bold text-theme-text">{v.arch}</span>
                              <i className="fas fa-download text-primary text-sm"></i>
                          </button>
                      ))}
                  </div>
                  <button onClick={() => setShowVariants(false)} className="w-full mt-4 py-3 text-theme-sub font-bold text-sm">Cancel</button>
              </div>
          </div>
      )}

      {showMicroGNotice && (
          <div className="absolute inset-0 z-[110] flex items-center justify-center p-6 bg-black/80 backdrop-blur-md animate-fade-in">
              <div className="bg-surface border border-theme-border rounded-3xl p-6 w-full max-w-sm shadow-2xl animate-slide-up">
                  <div className="w-16 h-16 bg-blue-500/20 text-blue-500 rounded-2xl flex items-center justify-center text-3xl mb-4 mx-auto"><i className="fas fa-info-circle"></i></div>
                  <h3 className="text-xl font-black text-theme-text text-center mb-2 tracking-tight">MicroG Required</h3>
                  <p className="text-theme-sub text-center text-sm mb-6 leading-relaxed">This app requires MicroG to sign in to your Google Account. Make sure you have it installed first.</p>
                  <div className="flex flex-col gap-2">
                      <button onClick={() => { setShowMicroGNotice(false); window.open(MICROG_INFO_URL, '_blank'); }} className="w-full py-4 rounded-2xl bg-primary text-white font-bold shadow-lg shadow-primary/20">Learn about MicroG</button>
                      <button onClick={() => { setShowMicroGNotice(false); handleAction(); }} className="w-full py-3 rounded-2xl bg-theme-element text-theme-text font-bold">Continue Anyway</button>
                  </div>
              </div>
          </div>
      )}
    </div>
  );
};

export default AppDetail;
