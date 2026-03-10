import React, { useState, useEffect, useRef, lazy, Suspense } from 'react';
import { createPortal } from 'react-dom';
import { Haptics, NotificationType } from '@capacitor/haptics';
import { Capacitor } from '@capacitor/core';
import { App as CapacitorApp } from '@capacitor/app';
import AppTracker, { NetworkSecurityResult } from '../plugins/AppTracker';
import SentinelWorker from '../workers/sentinel.worker?worker';
import { useScrollLock } from '../hooks/useScrollLock';

interface SentinelModalProps {
  onClose: () => void;
}

type ScanStatus = 'idle' | 'scanning' | 'clean' | 'threats';
type DbStatus = 'checking' | 'updating' | 'ready' | 'error';
type SentinelView = 'permissions' | 'database' | 'main' | 'scanning' | 'results' | 'network';

// --- NETWORK SENTRY UTILS ---
const TRUSTED_DNS_PROVIDERS = [
    '8.8.8.8', '8.8.4.4', // Google
    '1.1.1.1', '1.0.0.1', // Cloudflare
    '9.9.9.9', '149.112.112.112', // Quad9
    '208.67.222.222', '208.67.220.220' // OpenDNS
];

const getEncryptionInfo = (type: NetworkSecurityResult['encryptionType']) => {
    switch(type) {
        case 'WPA3': return { text: 'Secure', color: 'emerald', icon: 'fa-shield-check', desc: 'Latest security standard.' };
        case 'WPA2': return { text: 'Good', color: 'blue', icon: 'fa-shield-alt', desc: 'Industry standard protection.' };
        case 'WPA': return { text: 'Weak', color: 'amber', icon: 'fa-shield-exclamation', desc: 'Outdated and vulnerable.' };
        case 'WEP': return { text: 'Insecure', color: 'red', icon: 'fa-shield-slash', desc: 'Easily compromised.' };
        case 'OPEN': return { text: 'Open', color: 'red', icon: 'fa-unlock', desc: 'No encryption. High risk.' };
        default: return { text: 'Unknown', color: 'gray', icon: 'fa-question-circle', desc: 'Could not determine status.' };
    }
};

const getDnsInfo = (servers: string[]) => {
    if (!servers || servers.length === 0) return { text: 'Unknown', color: 'gray', icon: 'fa-server', desc: 'No DNS servers detected.' };
    const isTrusted = servers.some(s => TRUSTED_DNS_PROVIDERS.includes(s));
    if (isTrusted) return { text: 'Trusted', color: 'emerald', icon: 'fa-server', desc: 'Using a known, secure DNS.' };
    return { text: 'Unknown', color: 'amber', icon: 'fa-server', desc: 'Using default or ISP DNS.' };
};

interface SecurityCardProps {
    title: string;
    desc: string;
    status: string;
    color: 'emerald' | 'blue' | 'amber' | 'red' | 'gray';
    icon: string;
}

const SecurityCard: React.FC<SecurityCardProps> = ({ title, desc, status, color, icon }) => {
    const colorClasses = {
        emerald: { icon: 'bg-emerald-500/10 text-emerald-500', pill: 'bg-emerald-500/10 text-emerald-500 border-emerald-500/20' },
        blue: { icon: 'bg-blue-500/10 text-blue-500', pill: 'bg-blue-500/10 text-blue-500 border-blue-500/20' },
        amber: { icon: 'bg-amber-500/10 text-amber-500', pill: 'bg-amber-500/10 text-amber-500 border-amber-500/20' },
        red: { icon: 'bg-red-500/10 text-red-500', pill: 'bg-red-500/10 text-red-500 border-red-500/20' },
        gray: { icon: 'bg-gray-500/10 text-gray-500', pill: 'bg-gray-500/10 text-gray-500 border-gray-500/20' },
    };
    const classes = colorClasses[color] || colorClasses.gray;

    return (
        <div className="bg-card border border-theme-border rounded-2xl p-4 flex items-center gap-4 shadow-sm">
             <div className={`w-12 h-12 rounded-2xl flex items-center justify-center text-lg shrink-0 ${classes.icon}`}>
                <i className={`fas ${icon}`}></i>
            </div>
            <div className="flex-1 min-w-0">
                <h4 className="font-bold text-theme-text text-base">{title}</h4>
                <p className="text-xs text-theme-sub mt-0.5">{desc}</p>
            </div>
            <div className={`px-2.5 py-1 ${classes.pill} rounded-lg text-[10px] font-bold uppercase`}>
                {status}
            </div>
        </div>
    );
};

const ThreatVerificationAdvisory: React.FC = () => {
    const [expandedSection, setExpandedSection] = useState<string | null>(null);

    const toggleSection = (section: string) => {
        setExpandedSection(prev => (prev === section ? null : section));
    };

    return (
        <div className="bg-amber-500/10 border-2 border-dashed border-amber-500/20 rounded-2xl p-4 mb-6 space-y-4 animate-fade-in">
            <div className="flex items-center gap-3">
                <i className="fas fa-balance-scale text-xl text-amber-500"></i>
                <h3 className="font-black text-amber-600 dark:text-amber-300 tracking-tight text-lg">A Note on These Findings</h3>
            </div>
            <p className="text-xs font-medium text-theme-sub leading-relaxed">
                Sentinel's analysis uses a global threat database. While powerful, automated systems can sometimes produce **false positives**. This means a flagged file may not actually be malicious. We strongly recommend you verify these findings before taking action.
            </p>
            <div className="space-y-3 pt-3 border-t border-amber-500/10">
                <h4 className="text-[10px] font-bold text-theme-sub uppercase tracking-wider">How to Double-Check:</h4>
                
                <div className="bg-card/50 rounded-lg border border-theme-border overflow-hidden">
                    <button onClick={() => toggleSection('virustotal')} className="w-full p-3 flex items-center justify-between text-left">
                        <h5 className="font-bold text-theme-text text-sm">1. Use VirusTotal</h5>
                        <i className={`fas fa-chevron-down text-xs text-theme-sub transition-transform ${expandedSection === 'virustotal' ? 'rotate-180' : ''}`}></i>
                    </button>
                    <div className={`transition-all duration-300 ease-in-out ${expandedSection === 'virustotal' ? 'max-h-96 opacity-100' : 'max-h-0 opacity-0'}`}>
                        <div className="px-3 pb-3">
                            <a href="https://www.virustotal.com/" target="_blank" rel="noopener noreferrer" className="inline-block px-3 py-1 mb-2 bg-blue-500/10 text-blue-500 rounded-md text-[10px] font-bold border border-blue-500/20 hover:bg-blue-500/20">Scan Online</a>
                            <p className="text-xs text-theme-sub leading-snug">
                                This service analyzes files with 70+ scanners. Use this guide:
                                <ul className="list-disc list-inside mt-2 space-y-1 pl-1 text-[11px]">
                                    <li><span className="font-bold text-emerald-500">0 detections:</span> Generally safe.</li>
                                    <li><span className="font-bold text-amber-500">1-5 detections:</span> Likely a false positive.</li>
                                    <li><span className="font-bold text-red-500">5+ detections:</span> High caution. Treat as dangerous.</li>
                                </ul>
                            </p>
                        </div>
                    </div>
                </div>
                
                <div className="bg-card/50 rounded-lg border border-theme-border overflow-hidden">
                     <button onClick={() => toggleSection('source')} className="w-full p-3 flex items-center justify-between text-left">
                        <h5 className="font-bold text-theme-text text-sm">2. Consider the Source</h5>
                        <i className={`fas fa-chevron-down text-xs text-theme-sub transition-transform ${expandedSection === 'source' ? 'rotate-180' : ''}`}></i>
                    </button>
                    <div className={`transition-all duration-300 ease-in-out ${expandedSection === 'source' ? 'max-h-96 opacity-100' : 'max-h-0 opacity-0'}`}>
                        <div className="px-3 pb-3 space-y-2">
                            <p className="text-xs text-theme-sub leading-snug">
                                Official sources like **GitHub**, **F-Droid**, or the **Play Store** are generally trustworthy. Unofficial websites carry higher risk.
                            </p>
                            <h6 className="text-[10px] font-bold text-theme-sub uppercase tracking-wider pt-2 border-t border-theme-border/50">Why do safe sources get flagged?</h6>
                            <p className="text-xs text-theme-sub leading-snug">
                                Antivirus scanners sometimes use "heuristic" analysis, which means they look for suspicious *behaviors*. Apps that need advanced permissions (like root) or modify other apps (like ReVanced) can trigger these warnings, even if they are perfectly safe.
                            </p>
                        </div>
                    </div>
                </div>
                
                <div className="bg-card/50 rounded-lg border border-theme-border overflow-hidden">
                    <button onClick={() => toggleSection('community')} className="w-full p-3 flex items-center justify-between text-left">
                        <h5 className="font-bold text-theme-text text-sm">3. Search the Community</h5>
                        <i className={`fas fa-chevron-down text-xs text-theme-sub transition-transform ${expandedSection === 'community' ? 'rotate-180' : ''}`}></i>
                    </button>
                    <div className={`transition-all duration-300 ease-in-out ${expandedSection === 'community' ? 'max-h-96 opacity-100' : 'max-h-0 opacity-0'}`}>
                        <div className="px-3 pb-3">
                             <p className="text-xs text-theme-sub leading-snug">
                                Search for the file's hash (use the copy button <i className="fas fa-copy text-[9px]"></i>) online. Communities like Reddit often have discussions about specific files.
                            </p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};


const SentinelModal: React.FC<SentinelModalProps> = ({ onClose }) => {
  useScrollLock(true);
  
  const [view, setView] = useState<SentinelView>('permissions');
  const [threats, setThreats] = useState<{ path: string, hash: string, threat: string }[]>([]);
  const [rapidScanResults, setRapidScanResults] = useState<{name: string, packageName: string}[]>([]);
  const [permissions, setPermissions] = useState({ storage: false, media: false, isLegacy: false, location: false });
  const [dbStatus, setDbStatus] = useState<DbStatus>('checking');
  const [dbProgress, setDbProgress] = useState(0);
  const [scanProgress, setScanProgress] = useState(0);
  const [scanType, setScanType] = useState<'Deep' | 'Rapid' | 'Network Sentry'>('Deep');
  const [signatureCount, setSignatureCount] = useState<number | null>(null);
  
  const [networkStatus, setNetworkStatus] = useState<NetworkSecurityResult | null>(null);
  const [isNetworkScanning, setIsNetworkScanning] = useState(false);
  const [currentRapidScanApp, setCurrentRapidScanApp] = useState('');
  const [toast, setToast] = useState<{ msg: string, type: 'info' | 'error' } | null>(null);
  const [expandedThreat, setExpandedThreat] = useState<string | null>(null);

  const workerRef = useRef<Worker | null>(null);
  const scanIntervalRef = useRef<any>(null);
  const scanTypeRef = useRef(scanType);
  
  // Marquee Refs
  const marqueeRef = useRef<HTMLParagraphElement>(null);
  const marqueeFileBatch = useRef<string[]>([]);
  const marqueeAnimationId = useRef<number>(0);
  const marqueeCurrentIndex = useRef(0);

  useEffect(() => {
    scanTypeRef.current = scanType;
  }, [scanType]);

  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;

    const handler = () => {
        if (view === 'results' || view === 'network' || view === 'scanning') {
            setView('main');
        } else {
            onClose();
        }
    };
    
    const listenerPromise = CapacitorApp.addListener('backButton', handler);
    
    return () => {
        listenerPromise.then(l => l.remove());
    };
  }, [view, onClose]);

  useEffect(() => {
    workerRef.current = new SentinelWorker();
    
    const handleWorkerMessage = (e: MessageEvent) => {
        const { type, payload } = e.data;
        switch (type) {
            case 'DB_STATUS':
                if (payload.ready) setDbStatus('ready');
                else { setDbStatus('updating'); workerRef.current?.postMessage({ type: 'UPDATE_DB' }); }
                break;
            case 'UPDATE_PROGRESS':
                setDbProgress(payload.progress);
                break;
            case 'DB_UPDATE_COMPLETE':
                setDbStatus('ready');
                Haptics.notification({ type: NotificationType.Success });
                break;
            case 'SIGNATURE_COUNT_READY':
                 setSignatureCount(payload.count);
                 break;
            case 'SCAN_COMPLETE':
                setScanProgress(100);
                setTimeout(() => {
                    setThreats(payload);
                    setView('results');
                    Haptics.notification({ type: payload.length > 0 ? NotificationType.Error : NotificationType.Success });
                }, 500);
                break;
            case 'RAPID_SCAN_COMPLETE':
                 setScanProgress(100);
                 setTimeout(() => {
                    setRapidScanResults(payload.threats);
                    setView('results');
                    Haptics.notification({ type: payload.threats.length > 0 ? NotificationType.Error : NotificationType.Success });
                 }, 500);
                 break;
        }
    };

    workerRef.current.onmessage = handleWorkerMessage;
    workerRef.current.postMessage({ type: 'CHECK_DB_STATUS' });
    
    const batchListener = (AppTracker as any).addListener('scanResultBatch', (data: { files: Array<{path: string, hash: string}> }) => {
        workerRef.current?.postMessage({ type: 'CHECK_FILES', payload: { files: data.files } });

        if (scanTypeRef.current !== 'Deep') return;
        const newFiles = data.files.map(f => f.path.split('/').pop() || f.path);
        marqueeFileBatch.current.push(...newFiles);
        if (marqueeAnimationId.current === 0) runMarquee();
    });

    const completeListener = (AppTracker as any).addListener('scanComplete', () => {
        workerRef.current?.postMessage({ type: 'FINALIZE_DEEP_SCAN' });
        if (marqueeAnimationId.current !== 0) {
            cancelAnimationFrame(marqueeAnimationId.current);
            marqueeAnimationId.current = 0;
            if (marqueeRef.current) marqueeRef.current.textContent = "Finalizing results...";
        }
    });

    checkPerms();
    const poll = setInterval(checkPerms, 2000);

    return () => { 
        workerRef.current?.terminate();
        clearInterval(poll);
        if (scanIntervalRef.current) clearInterval(scanIntervalRef.current);
        batchListener.remove();
        completeListener.remove();
        if (marqueeAnimationId.current) cancelAnimationFrame(marqueeAnimationId.current);
    };
  }, []);

  useEffect(() => {
    if (!permissions.storage) setView('permissions');
    else if (dbStatus !== 'ready') setView('database');
    else if (view !== 'scanning' && view !== 'results' && view !== 'network') setView('main');
  }, [permissions.storage, dbStatus, view]);
  
  useEffect(() => {
    if(view === 'network' && !networkStatus) {
      handleNetworkScan();
    }
  }, [view, networkStatus]);

  useEffect(() => {
    if (toast) {
        const timer = setTimeout(() => setToast(null), 3000);
        return () => clearTimeout(timer);
    }
  }, [toast]);

  const runMarquee = () => {
    let lastTime = 0;
    const animate = (timestamp: number) => {
        if (!lastTime) lastTime = timestamp;
        const elapsed = timestamp - lastTime;
        if (elapsed > 30) { // Update every ~30ms for high speed effect
            if (marqueeRef.current) {
                if (marqueeFileBatch.current.length > marqueeCurrentIndex.current) {
                    marqueeRef.current.textContent = marqueeFileBatch.current[marqueeCurrentIndex.current] ?? '';
                    marqueeCurrentIndex.current++;
                } else {
                    marqueeFileBatch.current = [];
                    marqueeCurrentIndex.current = 0;
                    if (marqueeRef.current) marqueeRef.current.textContent = "Searching for files...";
                    marqueeAnimationId.current = 0;
                    return;
                }
            }
            lastTime = timestamp;
        }
        marqueeAnimationId.current = requestAnimationFrame(animate);
    };
    marqueeAnimationId.current = requestAnimationFrame(animate);
  };

  const checkPerms = async () => {
      if (!Capacitor.isNativePlatform()) {
          setPermissions({ storage: true, media: true, isLegacy: false, location: true });
          return;
      }
      try {
          const res = await AppTracker.checkPermissionsStatus();
          setPermissions(res);
      } catch(e) {}
  };

  const requestAccess = () => AppTracker.requestUniversalStorage();

  const resetThreats = () => {
      setThreats([]);
      setRapidScanResults([]);
      setScanProgress(0);
      setCurrentRapidScanApp('');
      if (scanIntervalRef.current) clearInterval(scanIntervalRef.current);
  };

  const handleRapidScan = async () => {
    resetThreats();
    setView('scanning');
    setScanType('Rapid');
    setCurrentRapidScanApp('Getting app list...');
    try {
        const { apps } = await AppTracker.getInstalledPackages();
        let i = 0;
        scanIntervalRef.current = setInterval(() => {
            if (i >= apps.length) {
                clearInterval(scanIntervalRef.current);
                setCurrentRapidScanApp('Finalizing...');
                workerRef.current?.postMessage({ type: 'RAPID_SCAN', payload: { apps } });
                return;
            }
            setCurrentRapidScanApp(apps[i]!.name);
            setScanProgress((i / apps.length) * 100);
            i++;
        }, 50);

    } catch(e) {
        setView('results');
    }
  };

  const handleDeepScan = async () => {
    resetThreats();
    marqueeFileBatch.current = [];
    marqueeCurrentIndex.current = 0;
    if (marqueeAnimationId.current) cancelAnimationFrame(marqueeAnimationId.current);
    marqueeAnimationId.current = 0;
    
    setView('scanning');
    setScanType('Deep');
    workerRef.current?.postMessage({ type: 'START_DEEP_SCAN' });
    
    let progress = 0;
    scanIntervalRef.current = setInterval(() => {
        progress += 1;
        setScanProgress(progress);
        if (progress >= 95) {
            clearInterval(scanIntervalRef.current);
        }
    }, 400);

    await AppTracker.scanDirectory();
  };

  const handleNetworkScan = async () => {
      if (!Capacitor.isNativePlatform()) {
        setToast({ msg: 'Network scan unavailable in web.', type: 'info' });
        return;
      }
      setIsNetworkScanning(true);
      setNetworkStatus(null);
      try {
          const status = await AppTracker.checkNetworkSecurity();
          setNetworkStatus(status);
      } catch (e) {
          setToast({ msg: 'Failed to run network scan.', type: 'error' });
      } finally {
          setIsNetworkScanning(false);
      }
  }

  const renderPermissionsView = () => (
    <div className="w-full max-w-sm flex flex-col items-center text-center animate-fade-in">
        <div className="w-32 h-32 bg-primary rounded-full flex items-center justify-center mx-auto mb-6 border-4 border-surface shadow-lg shadow-primary/30">
            <i className="fas fa-shield-alt text-6xl text-white"></i>
        </div>
        <h2 className="text-3xl font-black text-theme-text -mt-2">Access Required</h2>
        <p className="text-sm text-theme-sub mt-2 mb-6 max-w-xs">Sentinel needs permission to scan your device's storage for threats.</p>
        <button 
            onClick={requestAccess}
            className="w-full py-4 bg-primary text-white font-bold rounded-2xl shadow-lg shadow-primary/30 hover:scale-105 active:scale-95 transition-all"
        >
            Grant Storage Access
        </button>
        <p className="text-[10px] text-theme-sub/50 mt-4">This is a one-time request.</p>
    </div>
  );

  const renderDatabaseView = () => (
    <div className="w-full max-w-sm flex flex-col items-center text-center animate-fade-in">
        <div className="w-32 h-32 bg-card rounded-full flex items-center justify-center mx-auto mb-8 border-4 border-theme-border shadow-lg">
            <i className={`fas fa-database text-5xl text-primary ${dbStatus !== 'updating' ? 'animate-pulse' : ''}`}></i>
        </div>
        <h2 className="text-2xl font-black text-theme-text mb-2 tracking-tight">Syncing Threat Intel</h2>
        <p className="text-xs text-theme-sub font-medium mb-6">
            {dbStatus === 'updating' ? 'Downloading latest threat signatures...' : 'Connecting to Sentinel network...'}
        </p>
        <div className="w-full bg-theme-element h-3 rounded-full overflow-hidden border border-theme-border">
            {dbStatus === 'updating' ? (
                <div 
                    className="h-full bg-primary rounded-full transition-all duration-300"
                    style={{ width: `${dbProgress}%` }}
                ></div>
            ) : (
                <div className="h-full bg-primary rounded-full animate-pulse w-full"></div>
            )}
        </div>
        {dbStatus === 'updating' && (
            <p className="text-xs font-bold text-theme-sub mt-2">{Math.floor(dbProgress)}%</p>
        )}
    </div>
  );

  const renderMainView = () => (
    <div className="w-full max-w-sm space-y-4 animate-fade-in text-center">
        <div className="w-32 h-32 bg-card rounded-full flex items-center justify-center mx-auto mb-4 border-4 border-theme-border shadow-lg">
            <i className="fas fa-shield-alt text-6xl text-primary"></i>
        </div>
        <h2 className="text-3xl font-black text-theme-text -mt-2">System Protected</h2>
        
        <div className="pt-6 space-y-3">
            <button onClick={handleRapidScan} className="w-full p-5 bg-card border border-theme-border rounded-2xl flex items-center gap-4 text-left hover:border-primary/50 hover:bg-primary/5 transition-all active:scale-[0.98]">
                <div className="w-12 h-12 bg-primary/10 text-primary rounded-xl flex items-center justify-center text-xl shrink-0"><i className="fas fa-bolt"></i></div>
                <div className="min-w-0"><h4 className="font-bold text-theme-text">Rapid Scan</h4><p className="text-xs text-theme-sub">Checks installed apps & APKs</p></div>
            </button>
            <button onClick={handleDeepScan} className="w-full p-5 bg-card border border-theme-border rounded-2xl flex items-center gap-4 text-left hover:border-red-500/50 hover:bg-red-500/5 transition-all active:scale-[0.98]">
                <div className="w-12 h-12 bg-red-500/10 text-red-500 rounded-xl flex items-center justify-center text-xl shrink-0"><i className="fas fa-microscope"></i></div>
                <div className="min-w-0"><h4 className="font-bold text-theme-text">Deep Scan</h4><p className="text-xs text-theme-sub">Analyzes file storage & downloads</p></div>
            </button>
            <button onClick={() => setView('network')} className="w-full p-5 bg-card border border-theme-border rounded-2xl flex items-center gap-4 text-left hover:border-amber-500/50 hover:bg-amber-500/5 transition-all active:scale-[0.98]">
                <div className="w-12 h-12 bg-amber-500/10 text-amber-500 rounded-xl flex items-center justify-center text-xl relative shrink-0">
                    <i className="fas fa-tower-broadcast"></i>
                    <i className="fas fa-wifi text-[8px] absolute -bottom-1 -right-1 bg-surface text-amber-500 rounded-full p-0.5 border border-theme-border"></i>
                </div>
                <div className="min-w-0"><h4 className="font-bold text-theme-text">Network Sentry</h4><p className="text-xs text-theme-sub">Connection & Security Audit</p></div>
            </button>
        </div>
    </div>
  );

  const renderScanningView = () => (
      <div className="w-full max-w-sm flex flex-col items-center justify-center text-center">
          <div className="water-container mb-6">
              <div className="water-wave" style={{ top: `${100 - scanProgress}%` }}></div>
              <span className="relative z-10 text-3xl font-black text-theme-text mix-blend-overlay">{Math.floor(scanProgress)}%</span>
          </div>
          <h2 className="text-2xl font-black text-theme-text mb-2 tracking-tight">{scanType} Scan...</h2>
          <div className="h-8 w-full max-w-xs bg-theme-element border border-theme-border rounded-full flex items-center justify-center px-4 overflow-hidden">
              {scanType === 'Rapid' ? (
                <p className="text-xs text-theme-sub font-medium truncate animate-fade-in">{currentRapidScanApp}</p>
              ) : (
                <p ref={marqueeRef} className="text-xs text-theme-sub font-mono truncate whitespace-nowrap">Initializing scanner...</p>
              )}
          </div>
      </div>
  );
  
  const renderResultsView = () => (
      <div className="w-full max-w-sm animate-fade-in h-full flex flex-col justify-between min-h-0">
          {(threats.length === 0 && rapidScanResults.length === 0) ? (
              <div className="flex flex-col items-center flex-1 justify-center"><div className="w-36 h-36 rounded-full flex items-center justify-center mb-8 bg-theme-element"><i className="fas fa-shield-check text-5xl text-emerald-500"></i></div><h2 className="text-2xl font-black text-theme-text mb-2 tracking-tight">System Clean</h2><p className="text-theme-sub text-xs text-center max-w-xs font-medium">No threats detected.</p></div>
          ) : (
              <div className="w-full flex-1 overflow-y-auto no-scrollbar pb-4">
                  <ThreatVerificationAdvisory />
                  <h3 className="text-[10px] font-black text-red-500 uppercase tracking-[0.2em] mb-4 text-center">Threat Report</h3>
                  <div className="space-y-3">
                      {threats.length > 0 && (
                          <div className="space-y-3">
                              <h4 className="text-xs font-bold text-theme-sub uppercase tracking-wider pl-1">Infected Files (Deep Scan)</h4>
                              {threats.map((t, i) => {
                                  const key = `file-${i}`;
                                  const isExpanded = expandedThreat === key;
                                  return (
                                      <div key={key} className="bg-red-500/5 border border-red-500/20 rounded-2xl overflow-hidden transition-all duration-300">
                                          <button onClick={() => setExpandedThreat(isExpanded ? null : key)} className="w-full p-4 flex items-center gap-4 text-left group">
                                              <i className="fas fa-virus text-red-500 text-lg"></i>
                                              <div className="flex-1 min-w-0">
                                                  <p className="text-sm font-bold text-theme-text truncate">{t.path.split('/').pop()}</p>
                                                  <p className="text-[10px] text-red-400 font-mono uppercase font-black mt-1">{t.threat}</p>
                                              </div>
                                              <i className={`fas fa-chevron-down text-theme-sub transition-transform duration-300 ${isExpanded ? 'rotate-180' : ''}`}></i>
                                          </button>
                                          <div className={`transition-all duration-300 ease-in-out ${isExpanded ? 'max-h-96 opacity-100' : 'max-h-0 opacity-0'}`}>
                                              <div className="px-4 pb-4 pt-0 space-y-3">
                                                  <div className="h-px bg-red-500/10"></div>
                                                  <div><p className="text-[9px] font-bold text-red-400 uppercase tracking-wider">Full Path</p><p className="text-[10px] font-mono text-theme-sub break-all">{t.path}</p></div>
                                                  {t.hash && (<div><p className="text-[9px] font-bold text-red-400 uppercase tracking-wider">SHA-256 Hash</p><p className="text-[10px] font-mono text-theme-sub break-all">{t.hash}</p></div>)}
                                                  {t.hash && <button onClick={() => navigator.clipboard.writeText(t.hash)} title="Copy SHA-256 Hash" className="w-full mt-2 py-2 rounded-lg flex items-center justify-center gap-2 bg-theme-element text-theme-sub hover:bg-theme-hover transition-colors active:scale-95 border border-theme-border"><i className="fas fa-copy text-xs"></i><span className="text-xs font-bold">Copy Hash</span></button>}
                                              </div>
                                          </div>
                                      </div>
                                  );
                              })}
                          </div>
                      )}
                      {rapidScanResults.length > 0 && (
                          <div className="space-y-3 mt-4">
                               <h4 className="text-xs font-bold text-theme-sub uppercase tracking-wider pl-1">Suspicious Apps (Rapid Scan)</h4>
                              {rapidScanResults.map((threat, i) => {
                                   const key = `app-${i}`;
                                   const isExpanded = expandedThreat === key;
                                   return (
                                       <div key={key} className="bg-red-500/5 border border-red-500/20 rounded-2xl overflow-hidden transition-all duration-300">
                                           <button onClick={() => setExpandedThreat(isExpanded ? null : key)} className="w-full p-4 flex items-center gap-4 text-left group">
                                              <i className="fas fa-bug text-red-500 text-lg"></i>
                                              <div className="flex-1 min-w-0"><p className="text-sm font-bold text-theme-text truncate">{threat.name}</p></div>
                                              <i className={`fas fa-chevron-down text-theme-sub transition-transform duration-300 ${isExpanded ? 'rotate-180' : ''}`}></i>
                                           </button>
                                           <div className={`transition-all duration-300 ease-in-out ${isExpanded ? 'max-h-96 opacity-100' : 'max-h-0 opacity-0'}`}>
                                                <div className="px-4 pb-4 pt-0 space-y-3">
                                                    <div className="h-px bg-red-500/10"></div>
                                                    <div><p className="text-[9px] font-bold text-red-400 uppercase tracking-wider">Package Name</p><p className="text-[10px] font-mono text-theme-sub break-all">{threat.packageName}</p></div>
                                                    <button onClick={() => navigator.clipboard.writeText(threat.packageName)} title="Copy Package Name" className="w-full mt-2 py-2 rounded-lg flex items-center justify-center gap-2 bg-theme-element text-theme-sub hover:bg-theme-hover transition-colors active:scale-95 border border-theme-border"><i className="fas fa-copy text-xs"></i><span className="text-xs font-bold">Copy Package Name</span></button>
                                                </div>
                                           </div>
                                       </div>
                                   )
                              })}
                          </div>
                      )}
                  </div>
              </div>
          )}
          <button onClick={() => setView('main')} className="w-full py-4 mt-4 text-xs font-bold uppercase tracking-widest text-theme-sub hover:text-theme-text bg-theme-element rounded-2xl border border-theme-border">Return</button>
      </div>
  );

  const renderNetworkSentryView = () => {
    if (isNetworkScanning) {
        return (
            <div className="w-full max-w-sm flex flex-col items-center justify-center text-center">
                <div className="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin mb-6"></div>
                <h2 className="text-2xl font-black text-theme-text tracking-tight">Auditing Network...</h2>
                <p className="text-xs font-bold text-theme-sub mt-2">Checking connection integrity</p>
            </div>
        );
    }

    if (!networkStatus) {
        return (
             <div className="w-full max-w-sm flex flex-col items-center justify-center text-center">
                <div className="w-20 h-20 bg-red-500/10 text-red-500 rounded-full flex items-center justify-center mb-6"><i className="fas fa-wifi-slash text-3xl"></i></div>
                <h2 className="text-xl font-black text-theme-text tracking-tight">Scan Failed</h2>
                <p className="text-xs text-theme-sub mt-2 mb-6">Could not retrieve network information.</p>
                <button onClick={handleNetworkScan} className="w-full py-3 bg-primary text-white rounded-xl text-xs font-bold shadow-lg shadow-primary/20">Rescan</button>
            </div>
        );
    }
    
    const encryption = getEncryptionInfo(networkStatus.encryptionType);
    const dns = getDnsInfo(networkStatus.dnsServers);

    return (
        <div className="w-full max-w-sm animate-fade-in h-full flex flex-col">
            <div className="w-full flex-1 overflow-y-auto no-scrollbar pt-8 pb-4 space-y-3 relative">
                <div className="absolute top-0 left-1/2 -translate-x-1/2 -translate-y-1/2 opacity-[0.03] dark:opacity-[0.02] pointer-events-none -z-10">
                    <svg width="300" height="300" viewBox="0 0 24 24" fill="currentColor" className="text-primary">
                        <path d="M12 2L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-3z"></path>
                    </svg>
                </div>
                <div className="text-center mb-6">
                    <h2 className="text-3xl font-black text-theme-text">Connection Security</h2>
                    <p className="text-xs text-theme-sub mt-1">Live analysis of your current network</p>
                </div>
                
                <SecurityCard title="Encryption" desc={encryption.desc} status={encryption.text} color={encryption.color as any} icon={encryption.icon} />
                <SecurityCard title="DNS Integrity" desc={dns.desc} status={dns.text} color={dns.color as any} icon={dns.icon} />
                <SecurityCard title="Captive Portal" desc={networkStatus.isCaptivePortal ? 'Web login page detected.' : 'Direct network access.'} status={networkStatus.isCaptivePortal ? 'Active' : 'None'} color={networkStatus.isCaptivePortal ? 'amber' : 'emerald'} icon={networkStatus.isCaptivePortal ? 'fa-file-signature' : 'fa-network-wired'} />
                <SecurityCard title="ADB Watchdog" desc="Developer bridge status" status={(networkStatus.adbEnabled || networkStatus.adbWifiEnabled) ? 'Active' : 'Inactive'} color={(networkStatus.adbEnabled || networkStatus.adbWifiEnabled) ? 'red' : 'emerald'} icon="fa-terminal" />
                <SecurityCard title="Proxy Check" desc="Detects traffic redirection" status={networkStatus.hasProxy ? 'Active' : 'Inactive'} color={networkStatus.hasProxy ? 'red' : 'emerald'} icon="fa-sitemap" />
                <SecurityCard title="VPN Status" desc="Checks for active VPN tunnel" status={networkStatus.isVpnActive ? 'Active' : 'Inactive'} color={networkStatus.isVpnActive ? 'blue' : 'emerald'} icon="fa-user-secret" />

            </div>
             <button onClick={() => setView('main')} className="w-full py-4 mt-4 text-xs font-bold uppercase tracking-widest text-theme-sub hover:text-theme-text bg-theme-element rounded-2xl border border-theme-border">Return</button>
        </div>
    );
  };

  return createPortal(
    <div className="fixed inset-0 z-[200] bg-surface flex flex-col animate-fade-in">
        <div className="pt-6 px-6 flex justify-between items-center z-20">
            <div className={`px-3 py-1 rounded-full border flex items-center gap-2 ${permissions.storage ? 'bg-emerald-500/10 text-emerald-500 border-emerald-500/30' : 'bg-red-500/10 text-red-500 border-red-500/30'}`}>
                <i className={`fas ${permissions.storage ? 'fa-shield-check' : 'fa-lock'} text-[10px]`}></i>
                <span className="text-[9px] font-black uppercase tracking-widest">{permissions.storage ? 'Sensors Online' : 'Sensors Locked'}</span>
            </div>
            <button onClick={onClose} className="w-10 h-10 rounded-full bg-theme-element flex items-center justify-center text-theme-text active:scale-95 transition-transform"><i className="fas fa-times"></i></button>
        </div>
        <div className="flex-1 flex flex-col items-center justify-center px-6 pb-6 min-h-0">
            {view === 'permissions' && renderPermissionsView()}
            {view === 'database' && renderDatabaseView()}
            {view === 'main' && renderMainView()}
            {view === 'scanning' && renderScanningView()}
            {view === 'results' && renderResultsView()}
            {view === 'network' && renderNetworkSentryView()}
        </div>
        <div className="pb-6 px-6 z-20">
            <div className="bg-theme-element border border-theme-border rounded-xl p-2 flex items-center justify-center text-center">
                <p className="text-[10px] font-bold text-theme-sub uppercase tracking-wider">
                    {signatureCount ? `${signatureCount.toLocaleString()} Signatures Live` : `...`}
                </p>
            </div>
        </div>
        
        {toast && (
            <div className="absolute bottom-28 left-0 right-0 flex justify-center animate-slide-up pointer-events-none">
                <div className={`px-4 py-3 rounded-xl border flex items-center gap-3 shadow-2xl ${toast.type === 'error' ? 'bg-red-500/10 border-red-500/20 text-red-500' : 'bg-primary/10 border-primary/20 text-primary'}`}>
                    <i className={`fas ${toast.type === 'error' ? 'fa-exclamation-circle' : 'fa-info-circle'}`}></i>
                    <span className="text-xs font-bold">{toast.msg}</span>
                </div>
            </div>
        )}

    </div>,
    document.body
  );
};

export default SentinelModal;