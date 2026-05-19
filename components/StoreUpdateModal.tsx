
import React, { useState, useRef, useCallback, useEffect } from 'react';
import { Capacitor } from '@capacitor/core';
import AppTracker from '../plugins/AppTracker';

interface StoreUpdateModalProps {
  currentVersion: string;
  newVersion: string;
  downloadUrl: string;
  onClose: () => void;
}

type UpdateState = 'idle' | 'downloading' | 'ready' | 'installing' | 'error';

const StoreUpdateModal: React.FC<StoreUpdateModalProps> = ({ currentVersion, newVersion, downloadUrl, onClose }) => {
  const [state, setState] = useState<UpdateState>('idle');
  const [progress, setProgress] = useState(0);
  const [errorMsg, setErrorMsg] = useState('');
  const downloadIdRef = useRef<string | null>(null);
  const pollRef = useRef<number | null>(null);

  const startDownload = useCallback(async () => {
    if (!downloadUrl || downloadUrl === '#') {
      // Fallback to browser for non-native or missing URL
      window.location.href = downloadUrl;
      return;
    }

    if (!Capacitor.isNativePlatform()) {
      window.location.href = downloadUrl;
      return;
    }

    try {
      setState('downloading');
      setProgress(0);
      const fileName = `OrionStore_${newVersion}.apk`;
      const result = await AppTracker.downloadFile({ url: downloadUrl, fileName });
      downloadIdRef.current = result.downloadId;

      // Start polling for progress
      pollRef.current = window.setInterval(async () => {
        try {
          if (!downloadIdRef.current) return;
          const res = await AppTracker.getDownloadProgress({ downloadId: downloadIdRef.current });
          setProgress(Math.min(res.progress, 100));
          if (res.status === 'SUCCESSFUL' || res.progress >= 100) {
            if (pollRef.current) clearInterval(pollRef.current);
            setState('ready');
          } else if (res.status === 'FAILED') {
            if (pollRef.current) clearInterval(pollRef.current);
            setState('error');
            setErrorMsg('Download failed. Check your connection.');
          }
        } catch {
          // Polling error — keep trying
        }
      }, 800);
    } catch (e: any) {
      setState('error');
      setErrorMsg(e.message || 'Download failed to start.');
    }
  }, [downloadUrl, newVersion]);

  const handleInstall = useCallback(async () => {
    if (!Capacitor.isNativePlatform()) return;
    try {
      setState('installing');
      const fileName = `OrionStore_${newVersion}.apk`;
      await AppTracker.installPackage({ fileName });
      // User will be taken to system installer — close modal
      onClose();
    } catch (e: any) {
      setState('error');
      setErrorMsg('Installation failed.');
    }
  }, [newVersion, onClose]);

  useEffect(() => {
    return () => {
      if (pollRef.current) clearInterval(pollRef.current);
    };
  }, []);

  const canClose = state === 'idle' || state === 'error';

  return (
    <div className="fixed inset-0 z-[150] flex items-center justify-center p-6 animate-fade-in">
      <div className={`backdrop-scrim absolute inset-0 bg-black/80 backdrop-blur-md ${canClose ? '' : 'pointer-events-none'}`} onClick={canClose ? onClose : undefined}></div>
      
      <div className="relative bg-surface border border-theme-border dusk:border-transparent dark:border-transparent w-full max-w-sm rounded-[2.5rem] shadow-2xl overflow-hidden animate-slide-up flex flex-col">
        <div className="absolute top-0 left-0 right-0 h-32 bg-gradient-to-b from-primary/20 to-transparent -z-10"></div>
        
        <div className="p-8 flex flex-col items-center text-center">
            <div className={`w-20 h-20 rounded-3xl flex items-center justify-center text-4xl mb-6 shadow-2xl shadow-primary/40 transform -rotate-3 ring-1 ring-white/10 ${state === 'downloading' ? 'bg-primary text-white animate-pulse' : state === 'ready' ? 'bg-acid text-black' : state === 'error' ? 'bg-red-500 text-white' : 'bg-primary text-white'}`}>
                <i className={`fas ${state === 'downloading' ? 'fa-spinner fa-spin' : state === 'ready' ? 'fa-check' : state === 'error' ? 'fa-exclamation-triangle' : state === 'installing' ? 'fa-cog fa-spin' : 'fa-rocket'}`}></i>
            </div>
            
            <h3 className="text-2xl font-black text-theme-text mb-2 tracking-tight">
              {state === 'downloading' ? 'Downloading...' : state === 'ready' ? 'Ready to Install' : state === 'installing' ? 'Installing...' : state === 'error' ? 'Update Failed' : 'New Update Live!'}
            </h3>
            
            <div className="flex items-center gap-3 mb-4 bg-theme-element px-4 py-2 rounded-2xl border border-theme-border dusk:border-transparent dark:border-transparent">
                <span className="text-xs font-bold text-theme-sub font-mono">{currentVersion}</span>
                <i className="fas fa-arrow-right text-[10px] text-primary"></i>
                <span className="text-xs font-black text-primary font-mono">{newVersion}</span>
            </div>

            {state === 'downloading' && (
              <div className="w-full mb-4">
                <div className="h-2 bg-theme-element rounded-full overflow-hidden">
                  <div className="h-full bg-primary rounded-full transition-all duration-300 ease-out" style={{ width: `${progress}%` }}></div>
                </div>
                <p className="text-[10px] font-bold text-theme-sub mt-2">{progress}%</p>
              </div>
            )}

            {state === 'error' && (
              <p className="text-red-400 text-sm mb-4 font-medium">{errorMsg}</p>
            )}

            {state === 'idle' && (
              <p className="text-theme-sub text-sm leading-relaxed mb-6 font-medium">
                A new version of Orion Store is available. Update now to stay synced!
              </p>
            )}

            <div className="w-full space-y-3">
              {state === 'idle' && (
                <button 
                    onClick={startDownload}
                    className="w-full py-4 rounded-2xl bg-primary text-white font-bold text-lg shadow-xl shadow-primary/20 transition-colors hover:bg-primary/90 flex items-center justify-center gap-2"
                >
                    <i className="fas fa-download"></i>
                    <span>Download v{newVersion}</span>
                </button>
              )}

              {state === 'ready' && (
                <button 
                    onClick={handleInstall}
                    className="w-full py-4 rounded-2xl bg-acid text-black font-bold text-lg shadow-xl shadow-acid/20 transition-colors hover:bg-acid/90 flex items-center justify-center gap-2"
                >
                    <i className="fas fa-check-circle"></i>
                    <span>Install v{newVersion}</span>
                </button>
              )}

              {state === 'error' && (
                <button 
                    onClick={() => { setState('idle'); setErrorMsg(''); }}
                    className="w-full py-4 rounded-2xl bg-primary text-white font-bold shadow-xl shadow-primary/20 transition-colors hover:bg-primary/90 flex items-center justify-center gap-2"
                >
                    <i className="fas fa-redo"></i>
                    <span>Try Again</span>
                </button>
              )}
                
                {canClose && (
                    <button 
                        onClick={onClose}
                        className="w-full py-3 rounded-2xl text-theme-sub font-bold hover:bg-theme-element transition-colors text-xs uppercase tracking-widest"
                    >
                        Maybe Later
                    </button>
                )}
            </div>
        </div>
        
        <div className="absolute -bottom-6 -right-6 w-24 h-24 bg-acid/10 rounded-full blur-2xl"></div>
      </div>
    </div>
  );
};

export default StoreUpdateModal;
