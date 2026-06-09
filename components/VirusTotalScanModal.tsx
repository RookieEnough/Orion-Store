import React, { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Capacitor, CapacitorHttp } from '@capacitor/core';
import { Haptics, NotificationType } from '@capacitor/haptics';
import { AppItem, Platform } from '../types';
import AppTracker from '../plugins/AppTracker';
import { useSettingsStore } from '../store/useAppStore';

interface VirusTotalScanModalProps {
  app: AppItem;
  onClose: () => void;
  localVersion?: string;
  readyFileName?: string;
  cleanupFileName?: string;
}

type VtView = 'warning' | 'key' | 'ready' | 'scanning' | 'results' | 'error';
type ScanTargetType = 'apk' | 'downloaded-apk' | 'remote-apk';

interface VirusTotalStats {
  harmless?: number;
  malicious?: number;
  suspicious?: number;
  undetected?: number;
  timeout?: number;
}

interface VirusTotalEngineResult {
  engine_name?: string;
  category?: string;
  result?: string | null;
}

interface VirusTotalResult {
  type: ScanTargetType;
  hash?: string;
  url?: string;
  stats: VirusTotalStats;
  results: Record<string, VirusTotalEngineResult>;
  source: 'existing-report' | 'fresh-analysis';
  permalink?: string;
}

const VT_TUTORIAL_URL = 'https://www.youtube.com/shorts/Ynqw4dgH5ko';
const VT_API_BASE = 'https://www.virustotal.com/api/v3';

const moddedWarningPages = [
  {
    icon: 'fa-puzzle-piece',
    title: 'Patched apps need context',
    body: 'Morphe builds modify original app code. Antivirus engines can flag those changes even when the build is clean.'
  },
  {
    icon: 'fa-triangle-exclamation',
    title: 'False positives can happen',
    body: 'Heuristic detections may react to patches, signature changes, or bundled compatibility code. Read the result; do not panic from one flag.'
  },
  {
    icon: 'fa-screwdriver-wrench',
    title: 'Best verification path',
    body: 'If flags look suspicious, patch the app yourself with Morphe Manager, keep patches updated, then rescan manually on VirusTotal.'
  }
];

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

const parseVtBody = (body: any) => {
  if (typeof body === 'string') return body ? JSON.parse(body) : {};
  return body || {};
};

const getStatsTotal = (stats: VirusTotalStats) =>
  (stats.harmless || 0) + (stats.malicious || 0) + (stats.suspicious || 0) + (stats.undetected || 0) + (stats.timeout || 0);

const getRiskInfo = (stats: VirusTotalStats) => {
  const flagged = (stats.malicious || 0) + (stats.suspicious || 0);
  const total = getStatsTotal(stats);
  const rate = total > 0 ? Math.round((flagged / total) * 100) : 0;

  if (flagged === 0) {
    return { label: 'Clean', tone: 'emerald', icon: 'fa-shield-check', rate, flagged, note: '0 detections. This is the best VirusTotal outcome.' };
  }
  if (flagged === 1) {
    return { label: 'Low Signal', tone: 'blue', icon: 'fa-circle-info', rate, flagged, note: '1 detection. Often a false positive, especially for modded apps.' };
  }
  if (flagged < 5) {
    return { label: 'Review', tone: 'amber', icon: 'fa-magnifying-glass-chart', rate, flagged, note: '2-4 detections. Check engine names and app source.' };
  }
  return { label: 'High Caution', tone: 'red', icon: 'fa-shield-virus', rate, flagged, note: '5+ detections. Treat as risky until manually verified.' };
};

interface ToneClasses {
  bg: string;
  text: string;
  border: string;
  soft: string;
}

const getToneClasses = (tone: string): ToneClasses => {
  const tones: Record<string, ToneClasses> = {
    emerald: { bg: 'bg-emerald-500', text: 'text-emerald-500', border: 'border-emerald-500/25', soft: 'bg-emerald-500/10' },
    blue: { bg: 'bg-blue-500', text: 'text-blue-500', border: 'border-blue-500/25', soft: 'bg-blue-500/10' },
    amber: { bg: 'bg-amber-500', text: 'text-amber-500', border: 'border-amber-500/25', soft: 'bg-amber-500/10' },
    red: { bg: 'bg-red-500', text: 'text-red-500', border: 'border-red-500/25', soft: 'bg-red-500/10' }
  };
  return tones[tone] || tones.blue || { bg: 'bg-blue-500', text: 'text-blue-500', border: 'border-blue-500/25', soft: 'bg-blue-500/10' };
};

const getApiErrorMessage = (status: number, fallback = 'VirusTotal request failed.') => {
  if (status === 401 || status === 403) return 'API key rejected. Check the key and try again.';
  if (status === 404) return 'No VirusTotal report exists for this target yet.';
  if (status === 429) return 'VirusTotal quota reached. Free keys are commonly limited to 4 requests per minute.';
  if (status >= 500) return 'VirusTotal is having trouble right now. Try again later.';
  return fallback;
};

const VirusTotalScanModal: React.FC<VirusTotalScanModalProps> = ({
  app,
  onClose,
  localVersion,
  readyFileName,
  cleanupFileName
}) => {
  const { virusTotalApiKey, setVirusTotalApiKey } = useSettingsStore((state) => ({
    virusTotalApiKey: state.virusTotalApiKey,
    setVirusTotalApiKey: state.setVirusTotalApiKey
  }));

  const isModdedApp = useMemo(() => {
    const haystack = `${app.name} ${app.author} ${app.id}`.toLowerCase();
    return haystack.includes('revanced') || haystack.includes('morphe');
  }, [app.author, app.id, app.name]);

  const [view, setView] = useState<VtView>(isModdedApp ? 'warning' : (virusTotalApiKey ? 'ready' : 'key'));
  const [keyInput, setKeyInput] = useState(virusTotalApiKey);
  const [warningIndex, setWarningIndex] = useState(0);
  const [warningSeconds, setWarningSeconds] = useState(10);
  const [scanNote, setScanNote] = useState('Preparing scan...');
  const [scanProgress, setScanProgress] = useState(0);
  const scanTips = useMemo(
    () => [
      'Tip: Scan only apps you plan to install.',
      'Orion trick: Check the patches tag before installing modded builds.',
      'Fun fact: 1-4 detections on modded apps can be false positives.',
      'Pro move: Open the full VirusTotal report and read engine names.'
    ],
    []
  );
  const [scanTipIndex, setScanTipIndex] = useState(0);
  const [result, setResult] = useState<VirusTotalResult | null>(null);
  const [error, setError] = useState('');
  const scanStartedRef = useRef(false);

  useEffect(() => {
    if (view !== 'warning') return;
    setWarningSeconds(10);
    const interval = window.setInterval(() => {
      setWarningSeconds((seconds) => Math.max(0, seconds - 1));
    }, 1000);
    return () => window.clearInterval(interval);
  }, [view, warningIndex]);

  useEffect(() => {
    if (view !== 'scanning') return;
    setScanTipIndex(0);
    if (scanTips.length === 0) return;
    const interval = window.setInterval(() => {
      setScanTipIndex((idx) => (idx + 1) % scanTips.length);
    }, 2600);
    return () => window.clearInterval(interval);
  }, [scanTips.length, view]);

  const savedKeyLabel = virusTotalApiKey
    ? `${virusTotalApiKey.slice(0, 4)}••••${virusTotalApiKey.slice(-4)}`
    : '';

  const selectedTarget = useMemo(() => {
    const downloadFile = readyFileName || cleanupFileName;
    const latestVersion = app.availableVersions && app.availableVersions.length > 0 ? app.availableVersions[0] : null;
    const latestVariant = latestVersion?.variants?.[0] || app.variants?.[0];
    const remoteUrl = latestVariant?.url || app.downloadUrl || '';
    const isRemoteHttp = /^https?:\/\//i.test(remoteUrl) && remoteUrl !== '#';
    const isLikelyFile = /\.(apk|apks|xapk|zip)(\?|#|$)/i.test(remoteUrl);
    const validRemoteApkUrl = isRemoteHttp && (latestVariant?.url || isLikelyFile) ? remoteUrl : '';

    if (Capacitor.isNativePlatform() && app.platform === Platform.ANDROID && app.packageName && localVersion) {
      return { type: 'apk' as ScanTargetType, label: 'Installed APK', detail: app.packageName };
    }

    if (Capacitor.isNativePlatform() && app.platform === Platform.ANDROID && downloadFile) {
      return { type: 'downloaded-apk' as ScanTargetType, label: 'Downloaded APK', detail: downloadFile };
    }

    if (Capacitor.isNativePlatform() && app.platform === Platform.ANDROID && validRemoteApkUrl) {
      return { type: 'remote-apk' as ScanTargetType, label: 'APK File', detail: validRemoteApkUrl };
    }

    return null;
  }, [app.availableVersions, app.downloadUrl, app.packageName, app.platform, app.variants, cleanupFileName, localVersion, readyFileName]);

  const vtGet = async (path: string, apiKey: string) => {
    const response = await CapacitorHttp.get({
      url: `${VT_API_BASE}${path}`,
      headers: { 'x-apikey': apiKey },
      responseType: 'json',
      connectTimeout: 15000,
      readTimeout: 30000
    });
    return { status: response.status, body: parseVtBody(response.data) };
  };

  const readFileReport = async (hash: string, apiKey: string): Promise<VirusTotalResult | null> => {
    const report = await vtGet(`/files/${hash}`, apiKey);
    if (report.status === 404) return null;
    if (report.status < 200 || report.status >= 300) throw new Error(getApiErrorMessage(report.status));

    const attributes = report.body?.data?.attributes || {};
    return {
      type: 'apk',
      hash,
      stats: attributes.last_analysis_stats || {},
      results: attributes.last_analysis_results || {},
      source: 'existing-report',
      permalink: `https://www.virustotal.com/gui/file/${hash}`
    };
  };

  const pollAnalysis = async (analysisId: string, apiKey: string, target: Pick<VirusTotalResult, 'type' | 'hash' | 'url'>): Promise<VirusTotalResult> => {
    for (let attempt = 0; attempt < 18; attempt += 1) {
      setScanNote(attempt === 0 ? 'Waiting for engines...' : 'Collecting engine verdicts...');
      setScanProgress(Math.min(94, 58 + attempt * 2));
      await sleep(attempt < 3 ? 5000 : 10000);

      const analysis = await vtGet(`/analyses/${analysisId}`, apiKey);
      if (analysis.status < 200 || analysis.status >= 300) throw new Error(getApiErrorMessage(analysis.status, 'Analysis polling failed.'));

      const attributes = analysis.body?.data?.attributes || {};
      if (attributes.status === 'completed') {
        return {
          ...target,
          stats: attributes.stats || {},
          results: attributes.results || {},
          source: 'fresh-analysis',
          permalink: target.hash ? `https://www.virustotal.com/gui/file/${target.hash}` : undefined
        };
      }
    }
    throw new Error('VirusTotal is still analyzing this app. Try opening the VirusTotal report in a minute.');
  };

  const uploadAndAnalyzeFile = async (filePath: string, hash: string, apiKey: string) => {
    setScanNote('Uploading APK only because VirusTotal has no report...');
    setScanProgress(48);

    if (!Capacitor.isNativePlatform()) {
      throw new Error('APK upload is available in the Android app.');
    }

    const upload = await AppTracker.uploadVirusTotalFile({ filePath, apiKey });
    const body = parseVtBody(upload.body);
    if (upload.status < 200 || upload.status >= 300) throw new Error(getApiErrorMessage(upload.status, 'Upload failed.'));

    const analysisId = body?.data?.id;
    if (!analysisId) throw new Error('VirusTotal did not return an analysis id.');
    return pollAnalysis(analysisId, apiKey, { type: 'apk', hash });
  };

  const scanApkPath = async (filePath: string, apiKey: string) => {
    setScanNote('Calculating SHA-256...');
    setScanProgress(18);
    const { hash } = await AppTracker.calculateHash({ filePath });

    setScanNote('Checking existing VirusTotal report...');
    setScanProgress(34);
    const existingReport = await readFileReport(hash, apiKey);
    if (existingReport) return existingReport;

    return uploadAndAnalyzeFile(filePath, hash, apiKey);
  };

  const getTempScanFileName = (url: string) => {
    let baseName = `${app.id || app.name}-vt-scan.apk`;
    try {
      const parsed = new URL(url);
      const urlName = decodeURIComponent(parsed.pathname.split('/').pop() || '');
      if (urlName && /\.(apk|apks|xapk|zip)$/i.test(urlName)) baseName = urlName;
    } catch {}
    const safeBase = baseName.replace(/[^a-zA-Z0-9._-]/g, '_');
    const suffix = Date.now().toString(36);
    const dotIndex = safeBase.lastIndexOf('.');
    if (dotIndex > 0) return `${safeBase.slice(0, dotIndex)}_${suffix}${safeBase.slice(dotIndex)}`;
    return `${safeBase}_${suffix}.apk`;
  };

  const waitForDownload = async (downloadId: string) => {
    for (let attempt = 0; attempt < 180; attempt += 1) {
      const progress = await AppTracker.getDownloadProgress({ downloadId });
      setScanProgress(Math.max(8, Math.min(40, progress.progress || 0)));
      if (progress.status === 'SUCCESSFUL') return;
      if (progress.status === 'FAILED') throw new Error('APK download failed before scanning.');
      await sleep(1000);
    }
    throw new Error('APK download took too long. Try again on a stronger connection.');
  };

  const downloadAndScanApk = async (url: string, apiKey: string) => {
    const fileName = getTempScanFileName(url);
    setScanNote('Downloading APK for file scan...');
    setScanProgress(8);

    try {
      const download = await AppTracker.downloadFile({ url, fileName });
      await waitForDownload(download.downloadId || fileName);
      const downloaded = await AppTracker.resolveDownloadFile({ fileName });
      return await scanApkPath(downloaded.path, apiKey);
    } finally {
      AppTracker.deleteFile({ fileName }).catch(() => {});
    }
  };

  const startScan = async () => {
    const apiKey = virusTotalApiKey.trim();
    if (!apiKey) {
      setView('key');
      return;
    }
    if (!selectedTarget) {
      setError('No installed APK, downloaded APK, or direct APK file was found for this app.');
      setView('error');
      return;
    }

    scanStartedRef.current = true;
    setError('');
    setResult(null);
    setView('scanning');
    setScanProgress(8);
    setScanNote('Starting VirusTotal scan...');

    try {
      let nextResult: VirusTotalResult;
      if (selectedTarget.type === 'apk') {
        const extracted = await AppTracker.extractApk({ packageName: app.packageName || '' });
        nextResult = await scanApkPath(extracted.path, apiKey);
      } else if (selectedTarget.type === 'downloaded-apk') {
        const downloaded = await AppTracker.resolveDownloadFile({ fileName: selectedTarget.detail });
        nextResult = await scanApkPath(downloaded.path, apiKey);
      } else {
        nextResult = await downloadAndScanApk(selectedTarget.detail, apiKey);
      }

      setScanProgress(100);
      setScanNote('Report ready.');
      await sleep(350);
      setResult(nextResult);
      setView('results');
      if (useSettingsStore.getState().hapticEnabled) Haptics.notification({ type: getRiskInfo(nextResult.stats).flagged >= 5 ? NotificationType.Warning : NotificationType.Success });
    } catch (scanError: any) {
      setError(scanError?.message || 'VirusTotal scan failed.');
      setView('error');
      if (useSettingsStore.getState().hapticEnabled) Haptics.notification({ type: NotificationType.Error });
    } finally {
      scanStartedRef.current = false;
    }
  };

  const saveKey = () => {
    const trimmed = keyInput.trim();
    if (!trimmed) {
      setError('Paste your VirusTotal API key first.');
      setView('error');
      return;
    }
    setVirusTotalApiKey(trimmed);
    setView('ready');
    if (useSettingsStore.getState().hapticEnabled) Haptics.notification({ type: NotificationType.Success });
  };

  const revokeKey = () => {
    setVirusTotalApiKey('');
    setKeyInput('');
    setResult(null);
    setView('key');
    if (useSettingsStore.getState().hapticEnabled) Haptics.notification({ type: NotificationType.Success });
  };

  const handleWarningContinue = () => {
    if (warningSeconds > 0) return;
    if (warningIndex < moddedWarningPages.length - 1) {
      setWarningIndex(warningIndex + 1);
    } else {
      setView(virusTotalApiKey ? 'ready' : 'key');
    }
  };

  const renderHeader = (eyebrow: string, title: string, note: string, icon = 'fa-shield-virus') => (
    <div className="text-center">
      <div className="w-20 h-20 mx-auto rounded-3xl bg-[#1a73e8]/15 text-[#1a73e8] flex items-center justify-center text-3xl mb-5 shadow-sm">
        <i className={`fas ${icon}`}></i>
      </div>
      <p className="text-[10px] font-black text-theme-sub uppercase tracking-[0.24em] mb-2">{eyebrow}</p>
      <h2 className="text-2xl font-black text-theme-text tracking-tight">{title}</h2>
      <p className="text-xs text-theme-sub font-medium leading-relaxed mt-2 max-w-xs mx-auto">{note}</p>
    </div>
  );

  const renderWarning = () => {
    const page = moddedWarningPages[warningIndex] || moddedWarningPages[0];
    if (!page) return null;
    return (
      <div className="w-full max-w-sm mx-auto flex flex-col justify-center min-h-full py-6">
        <div className="w-20 h-20 mx-auto rounded-3xl bg-[#1a73e8]/15 text-[#1a73e8] flex items-center justify-center text-3xl mb-5 shadow-sm">
          <i className={`fas ${page.icon}`}></i>
        </div>
        <div className="flex justify-center mb-5">
          <div className="flex gap-1.5">
            {moddedWarningPages.map((_, idx) => (
              <span
                key={idx}
                className={`h-2 rounded-full transition-all ${idx === warningIndex ? 'w-6 bg-[#1a73e8]' : 'w-2 bg-theme-element'}`}
              />
            ))}
          </div>
        </div>
        <div className="bg-card border border-theme-border rounded-3xl p-5">
          <p className="text-[10px] font-black text-theme-sub uppercase tracking-[0.24em] mb-2">{`Notice ${warningIndex + 1} of ${moddedWarningPages.length}`}</p>
          <h2 className="text-xl font-black text-theme-text tracking-tight">{page.title}</h2>
          <p className="text-sm text-theme-sub font-medium leading-relaxed mt-2">{page.body}</p>
        </div>
        <button
          onClick={handleWarningContinue}
          disabled={warningSeconds > 0}
          className={`w-full mt-4 py-4 rounded-2xl font-bold transition-all ${warningSeconds > 0 ? 'bg-theme-element text-theme-sub cursor-not-allowed' : 'bg-[#1a73e8] text-white shadow-lg shadow-[#1a73e8]/20 active:scale-95'}`}
        >
          {warningSeconds > 0 ? `Continue in ${warningSeconds}s` : warningIndex === moddedWarningPages.length - 1 ? 'I Understand' : 'Continue'}
        </button>
      </div>
    );
  };

  const renderKeyView = () => (
    <div className="w-full max-w-sm mx-auto space-y-4">
      <div className="text-center">
        <div className="w-16 h-16 mx-auto rounded-3xl bg-[#1a73e8]/15 text-[#1a73e8] flex items-center justify-center text-2xl mb-4">
          <i className="fas fa-key"></i>
        </div>
        <p className="text-[10px] font-black text-theme-sub uppercase tracking-[0.24em]">BYOK setup</p>
        <h2 className="text-2xl font-black text-theme-text tracking-tight mt-1">Connect VirusTotal</h2>
        <p className="text-sm text-theme-sub font-medium leading-relaxed mt-2">
          Your key stays on this device. Orion uses it only when you start a scan.
        </p>
      </div>

      <div className="bg-card border border-theme-border rounded-3xl p-4 space-y-3">
        <label className="text-[10px] font-black text-theme-sub uppercase tracking-widest">API Key</label>
        <input
          value={keyInput}
          onChange={(event) => setKeyInput(event.target.value)}
          type="password"
          placeholder="Paste your VirusTotal API key"
          className="w-full bg-theme-element border border-theme-border rounded-2xl px-4 py-4 text-sm font-bold text-theme-text outline-none focus:border-[#1a73e8] transition-colors"
        />
        <button onClick={saveKey} className="w-full py-4 rounded-2xl bg-[#1a73e8] text-white font-bold shadow-lg shadow-[#1a73e8]/20 active:scale-95 transition-all">
          Save Key
        </button>
        {virusTotalApiKey && (
          <button onClick={revokeKey} className="w-full py-3 rounded-2xl bg-red-500/10 text-red-500 border border-red-500/20 font-bold text-xs uppercase tracking-widest">
            Remove Saved Key
          </button>
        )}
      </div>

      <a href={VT_TUTORIAL_URL} target="_blank" rel="noopener noreferrer" className="flex items-center gap-3 bg-theme-element border border-theme-border rounded-2xl p-4 active:scale-[0.98] transition-all">
        <i className="fab fa-youtube text-red-500 text-2xl"></i>
        <div className="min-w-0">
          <p className="text-sm font-black text-theme-text">Watch key tutorial</p>
          <p className="text-[11px] text-theme-sub font-medium">Quick help for finding your VirusTotal key.</p>
        </div>
        <i className="fas fa-chevron-right text-theme-sub text-xs ml-auto"></i>
      </a>

      <div className="bg-card border border-theme-border rounded-2xl p-4 text-sm text-theme-sub font-medium leading-relaxed">
        <span className="font-black text-theme-text">Quota tip:</span> Free VirusTotal keys are commonly limited to 4 requests per minute.
      </div>
    </div>
  );

  const renderReadyView = () => (
    <div className="w-full max-w-sm mx-auto space-y-5">
      {renderHeader('Ready', 'Scan with VirusTotal', 'Hash lookup first. Upload only happens if VirusTotal has no APK report yet.', 'fa-microscope')}
      <div className="bg-card border border-theme-border rounded-3xl p-4 space-y-4">
        <div className="flex items-center gap-4">
          <img src={app.icon} alt={app.name} className="w-14 h-14 rounded-2xl object-contain bg-theme-element p-1" />
          <div className="min-w-0">
            <p className="font-black text-theme-text truncate">{app.name}</p>
            <p className="text-[11px] text-theme-sub font-bold truncate">{selectedTarget ? `${selectedTarget.label}: ${selectedTarget.detail}` : 'No scan target found'}</p>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="rounded-2xl bg-theme-element p-3">
            <p className="text-[9px] text-theme-sub font-black uppercase tracking-widest">Key</p>
            <p className="text-xs text-theme-text font-bold mt-1">{savedKeyLabel}</p>
          </div>
          <div className="rounded-2xl bg-theme-element p-3">
            <p className="text-[9px] text-theme-sub font-black uppercase tracking-widest">Limit tip</p>
            <p className="text-xs text-theme-text font-bold mt-1">Use sparingly</p>
          </div>
        </div>
        <button onClick={startScan} disabled={!selectedTarget} className="w-full py-4 rounded-2xl bg-[#1a73e8] text-white font-bold shadow-lg shadow-[#1a73e8]/20 active:scale-95 disabled:bg-theme-element disabled:text-theme-sub transition-all">
          Start VirusTotal Scan
        </button>
        <button onClick={revokeKey} className="w-full py-3 rounded-2xl bg-theme-element text-theme-sub hover:text-red-500 font-bold text-xs uppercase tracking-widest transition-colors">
          Revoke Key
        </button>
      </div>
      <div className="bg-amber-500/10 border border-amber-500/20 rounded-2xl p-4 text-xs text-amber-600 dark:text-amber-300 font-bold leading-relaxed">
        Tip: one scan may need report, upload, and polling calls. Save your free quota for apps you actually plan to install.
      </div>
    </div>
  );

  const renderScanningView = () => (
    <div className="w-full max-w-sm mx-auto min-h-full py-6 flex flex-col items-center justify-center text-center">
      <div className="water-container mb-8">
        <div className="water-wave" style={{ top: `${100 - scanProgress}%`, backgroundColor: '#1a73e8' }}></div>
        <span className="relative z-10 text-3xl font-black text-theme-text mix-blend-overlay">{Math.floor(scanProgress)}%</span>
      </div>
      <h2 className="text-2xl font-black text-theme-text">Scanning softly...</h2>
      <p className="text-xs text-theme-sub font-bold mt-2">{scanNote}</p>
      <div className="mt-5 inline-flex items-center justify-center px-3 py-2 rounded-full bg-yellow-50 dark:bg-yellow-900/20 border-2 border-yellow-400/70 dark:border-yellow-400/50 text-yellow-800 dark:text-yellow-200 text-[11px] font-bold shadow-lg shadow-yellow-400/15">
        {scanTips[scanTipIndex]}
      </div>
      <div className="mt-8 bg-indigo-50 dark:bg-indigo-900/20 border-2 border-indigo-400/40 dark:border-indigo-400/40 rounded-2xl p-4 text-[11px] text-indigo-800 dark:text-indigo-200 font-bold leading-relaxed flex items-start gap-3">
        <span className="w-9 h-9 rounded-xl bg-indigo-400/15 text-indigo-600 dark:text-indigo-200 flex items-center justify-center shrink-0">
          <i className="fas fa-circle-info"></i>
        </span>
        <span>Keep this open. VirusTotal can take a little while when a fresh APK needs analysis.</span>
      </div>
    </div>
  );

  const renderResultsView = () => {
    if (!result) return null;
    const risk = getRiskInfo(result.stats);
    const classes = getToneClasses(risk.tone);
    const detections = Object.entries(result.results)
      .filter(([, engine]) => engine.category === 'malicious' || engine.category === 'suspicious')
      .slice(0, 8);

    return (
      <div className="w-full max-w-sm mx-auto h-full flex flex-col min-h-0">
        <div className="flex-1 overflow-y-auto no-scrollbar pb-4">
          <div className="min-h-full flex flex-col justify-center space-y-3">
            <div className="bg-card border border-theme-border rounded-3xl p-4">
              <p className="text-[10px] font-black uppercase tracking-[0.24em] text-theme-sub">Harmful rate</p>
              <div className="flex items-center gap-4 mt-3">
                <div className={`w-14 h-14 rounded-full border-2 ${classes.border} ${classes.soft} flex items-center justify-center`}>
                  <i className={`fas ${risk.flagged === 0 ? 'fa-check' : risk.icon} ${classes.text}`}></i>
                </div>
                <div className="min-w-0">
                  <h2 className={`text-4xl font-black ${classes.text} leading-none`}>{risk.rate}%</h2>
                  <p className="text-xs font-black uppercase tracking-[0.24em] text-theme-sub mt-2">{risk.label}</p>
                </div>
              </div>
              <p className="text-sm text-theme-sub font-medium mt-3 leading-relaxed">{risk.note}</p>
            </div>

            <div className="grid grid-cols-3 gap-3">
              <div className="bg-card border border-theme-border rounded-2xl p-3 text-center"><p className="text-lg font-black text-red-500">{risk.flagged}</p><p className="text-[9px] font-black text-theme-sub uppercase">Flagged</p></div>
              <div className="bg-card border border-theme-border rounded-2xl p-3 text-center"><p className="text-lg font-black text-emerald-500">{result.stats.harmless || 0}</p><p className="text-[9px] font-black text-theme-sub uppercase">Clean</p></div>
              <div className="bg-card border border-theme-border rounded-2xl p-3 text-center"><p className="text-lg font-black text-theme-text">{getStatsTotal(result.stats)}</p><p className="text-[9px] font-black text-theme-sub uppercase">Engines</p></div>
            </div>

            <div className="bg-theme-element border border-theme-border rounded-2xl p-3">
              <p className="text-[10px] font-black text-theme-sub uppercase tracking-widest mb-2">How to read it</p>
              <div className="space-y-2 text-xs font-bold text-theme-sub">
                <p><span className="text-emerald-500">0</span> detections: generally safe.</p>
                <p><span className="text-amber-500">1-4</span> detections: often false positives.</p>
                <p><span className="text-red-500">5+</span> detections: high caution.</p>
              </div>
            </div>

            {detections.length > 0 && (
              <div className="space-y-2">
                <p className="text-[10px] font-black text-theme-sub uppercase tracking-widest px-1">Flagging engines</p>
                {detections.map(([name, engine]) => (
                  <div key={name} className="bg-card border border-theme-border rounded-2xl p-3 flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-sm font-black text-theme-text truncate">{engine.engine_name || name}</p>
                      <p className="text-[10px] text-red-500 font-bold truncate">{engine.result || engine.category}</p>
                    </div>
                    <span className="text-[9px] font-black uppercase text-red-500 bg-red-500/10 rounded-lg px-2 py-1">{engine.category}</span>
                  </div>
                ))}
              </div>
            )}

            {result.hash && (
              <button onClick={() => navigator.clipboard.writeText(result.hash || '')} className="w-full py-3 rounded-2xl bg-theme-element text-theme-sub font-bold text-xs uppercase tracking-widest">
                Copy SHA-256
              </button>
            )}
          </div>
        </div>
        <div className="space-y-3 pt-2">
          {result.permalink && (
            <button onClick={() => window.open(result.permalink, '_blank')} className="w-full py-4 rounded-2xl bg-[#1a73e8] text-white font-bold shadow-lg shadow-[#1a73e8]/20">
              Open VirusTotal Report
            </button>
          )}
          <button onClick={() => setView('ready')} className="w-full py-3 rounded-2xl bg-theme-element text-theme-sub font-bold text-xs uppercase tracking-widest">
            Scan Again
          </button>
        </div>
      </div>
    );
  };

  const renderErrorView = () => (
    <div className="w-full max-w-sm mx-auto space-y-5 text-center">
      {renderHeader('Scan stopped', 'Needs attention', error || 'Something went wrong.', 'fa-circle-exclamation')}
      <button onClick={() => setView(virusTotalApiKey ? 'ready' : 'key')} className="w-full py-4 rounded-2xl bg-[#1a73e8] text-white font-bold shadow-lg shadow-[#1a73e8]/20">
        Back
      </button>
      <button onClick={revokeKey} className="w-full py-3 rounded-2xl bg-theme-element text-theme-sub font-bold text-xs uppercase tracking-widest">
        Revoke Key
      </button>
    </div>
  );

  return createPortal(
    <div className="fixed inset-0 z-[180] flex items-center justify-center bg-black/70 backdrop-blur-md animate-fade-in p-0 sm:p-4 overflow-y-auto no-scrollbar overscroll-contain">
      <div className="bg-surface w-full min-h-[100dvh] sm:min-h-0 sm:max-h-[92vh] sm:max-w-md sm:rounded-[2rem] shadow-2xl border border-theme-border flex flex-col overflow-hidden">
        {/* Header - No longer sticky, part of the scroll flow if we want, but here we keep it simple */}
        <div className="shrink-0 px-5 pt-[calc(1.5rem+env(safe-area-inset-top))] pb-3 flex items-center justify-center">
          <div className="text-[10px] font-black uppercase tracking-[0.24em] text-theme-sub">
            VirusTotal Scan
          </div>
        </div>
        <div className="flex-1 flex flex-col justify-center px-5 pt-2 pb-[calc(1.5rem+env(safe-area-inset-bottom))]">
          {view === 'warning' && renderWarning()}
          {view === 'key' && renderKeyView()}
          {view === 'ready' && renderReadyView()}
          {view === 'scanning' && renderScanningView()}
          {view === 'results' && renderResultsView()}
          {view === 'error' && renderErrorView()}
        </div>
      </div>
    </div>,
    document.body
  );
};

export default VirusTotalScanModal;
