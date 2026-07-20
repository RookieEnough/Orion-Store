import { useCallback, useState } from 'react';
import { CapacitorHttp } from '@capacitor/core';
import { Haptics, NotificationType } from '@capacitor/haptics';
import AppTracker from '../plugins/AppTracker';
import { useSettingsStore } from '../store/useAppStore';

export type VtView = 'key' | 'ready' | 'scanning' | 'results' | 'error';
export type ScanTargetType = 'apk' | 'downloaded-apk' | 'remote-apk';

export interface ScanTarget {
  type: ScanTargetType;
  label: string;
  detail: string;
}

export interface VirusTotalStats {
  harmless?: number;
  malicious?: number;
  suspicious?: number;
  undetected?: number;
  timeout?: number;
}

export interface VirusTotalEngineResult {
  engine_name?: string;
  category?: string;
  result?: string | null;
}

export interface VirusTotalResult {
  type: ScanTargetType;
  hash?: string;
  url?: string;
  stats: VirusTotalStats;
  results: Record<string, VirusTotalEngineResult>;
  source: 'existing-report' | 'fresh-analysis';
  permalink?: string;
}

interface UseVirusTotalScanOptions {
  apiKey: string;
  appId: string;
  appName: string;
  packageName?: string;
}

const VT_API_BASE = 'https://www.virustotal.com/api/v3';
const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

const parseVtBody = (body: unknown) => {
  if (typeof body === 'string') {
    if (!body) return {};
    // Detect HTML responses (Cloudflare blocks, error pages, rate limit pages)
    const trimmed = body.trimStart();
    if (trimmed.startsWith('<') || trimmed.startsWith('<!')) {
      throw new Error(
        'VirusTotal returned an HTML page instead of JSON. This usually means the request was blocked by Cloudflare or the API is temporarily unavailable. Try again in a minute.'
      );
    }
    try {
      return JSON.parse(body);
    } catch {
      throw new Error(
        'VirusTotal returned an unexpected response. The API may be temporarily unavailable. Try again later.'
      );
    }
  }
  return body || {};
};

const getApiErrorMessage = (status: number, fallback = 'VirusTotal request failed.') => {
  if (status === 401 || status === 403) return 'API key rejected. Check the key and try again.';
  if (status === 404) return 'No VirusTotal report exists for this target yet.';
  if (status === 429) return 'VirusTotal quota reached. Free keys are commonly limited to 4 requests per minute.';
  if (status >= 500) return 'VirusTotal is having trouble right now. Try again later.';
  return fallback;
};

const getFlaggedCount = (stats: VirusTotalStats) => (stats.malicious || 0) + (stats.suspicious || 0);

export const useVirusTotalScan = ({ apiKey, appId, appName, packageName }: UseVirusTotalScanOptions) => {
  const [view, setView] = useState<VtView>(apiKey ? 'ready' : 'key');
  const [scanNote, setScanNote] = useState('Preparing scan...');
  const [scanProgress, setScanProgress] = useState(0);
  const [result, setResult] = useState<VirusTotalResult | null>(null);
  const [error, setError] = useState('');

  const vtGet = useCallback(async (path: string, key: string) => {
    const response = await CapacitorHttp.get({
      url: `${VT_API_BASE}${path}`,
      headers: { 'x-apikey': key },
      responseType: 'json',
      connectTimeout: 15000,
      readTimeout: 30000
    });

    return { status: response.status, body: parseVtBody(response.data) };
  }, []);

  const readFileReport = useCallback(
    async (hash: string, key: string): Promise<VirusTotalResult | null> => {
      const report = await vtGet(`/files/${hash}`, key);
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
    },
    [vtGet]
  );

  const pollAnalysis = useCallback(
    async (analysisId: string, key: string, target: Pick<VirusTotalResult, 'type' | 'hash' | 'url'>): Promise<VirusTotalResult> => {
      for (let attempt = 0; attempt < 18; attempt += 1) {
        setScanNote(attempt === 0 ? 'Waiting for engines...' : 'Collecting engine verdicts...');
        setScanProgress(Math.min(94, 58 + attempt * 2));
        await sleep(attempt < 3 ? 5000 : 10000);

        const analysis = await vtGet(`/analyses/${analysisId}`, key);
        if (analysis.status < 200 || analysis.status >= 300) {
          throw new Error(getApiErrorMessage(analysis.status, 'Analysis polling failed.'));
        }

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
    },
    [vtGet]
  );

  const uploadAndAnalyzeFile = useCallback(
    async (filePath: string, hash: string, key: string) => {
      setScanNote('Uploading APK only because VirusTotal has no report...');
      setScanProgress(48);

      const upload = await AppTracker.uploadVirusTotalFile({ filePath, apiKey: key });
      const body = parseVtBody(upload.body);
      if (upload.status < 200 || upload.status >= 300) {
        throw new Error(getApiErrorMessage(upload.status, 'Upload failed.'));
      }

      const analysisId = body?.data?.id;
      if (!analysisId) throw new Error('VirusTotal did not return an analysis id.');
      return pollAnalysis(analysisId, key, { type: 'apk', hash });
    },
    [pollAnalysis]
  );

  const scanApkPath = useCallback(
    async (filePath: string, key: string) => {
      setScanNote('Calculating SHA-256...');
      setScanProgress(18);
      const { hash } = await AppTracker.calculateHash({ filePath });

      setScanNote('Checking existing VirusTotal report...');
      setScanProgress(34);
      const existingReport = await readFileReport(hash, key);
      if (existingReport) return existingReport;

      return uploadAndAnalyzeFile(filePath, hash, key);
    },
    [readFileReport, uploadAndAnalyzeFile]
  );

  const getTempScanFileName = useCallback(
    (url: string) => {
      let baseName = `${appId || appName}-vt-scan.apk`;
      try {
        const parsed = new URL(url);
        const urlName = decodeURIComponent(parsed.pathname.split('/').pop() || '');
        if (urlName && /\.(apk|apks|xapk|zip)$/i.test(urlName)) baseName = urlName;
      } catch {
        // Keep fallback filename.
      }

      const safeBase = baseName.replace(/[^a-zA-Z0-9._-]/g, '_');
      const suffix = Date.now().toString(36);
      const dotIndex = safeBase.lastIndexOf('.');
      if (dotIndex > 0) return `${safeBase.slice(0, dotIndex)}_${suffix}${safeBase.slice(dotIndex)}`;
      return `${safeBase}_${suffix}.apk`;
    },
    [appId, appName]
  );

  const waitForDownload = useCallback(async (downloadId: string) => {
    for (let attempt = 0; attempt < 180; attempt += 1) {
      const progress = await AppTracker.getDownloadProgress({ downloadId });
      setScanProgress(Math.max(8, Math.min(40, progress.progress || 0)));
      if (progress.status === 'SUCCESSFUL') return;
      if (progress.status === 'FAILED') throw new Error('APK download failed before scanning.');
      await sleep(1000);
    }

    throw new Error('APK download took too long. Try again on a stronger connection.');
  }, []);

  const downloadAndScanApk = useCallback(
    async (url: string, key: string) => {
      const fileName = getTempScanFileName(url);
      setScanNote('Downloading APK for file scan...');
      setScanProgress(8);

      try {
        const download = await AppTracker.downloadFile({ url, fileName });
        await waitForDownload(download.downloadId || fileName);
        const downloaded = await AppTracker.resolveDownloadFile({ fileName });
        return await scanApkPath(downloaded.path, key);
      } finally {
        AppTracker.deleteFile({ fileName }).catch(() => { });
      }
    },
    [getTempScanFileName, scanApkPath, waitForDownload]
  );

  const reset = useCallback(() => {
    setResult(null);
    setError('');
    setScanProgress(0);
    setScanNote('Preparing scan...');
  }, []);

  const startScan = useCallback(
    async (target: ScanTarget | null, apiKeyOverride?: string) => {
      const trimmedKey = (apiKeyOverride ?? apiKey).trim();

      if (!trimmedKey) {
        setView('key');
        return null;
      }

      if (!target) {
        setError('No installed APK, downloaded APK, or direct APK file was found for this app.');
        setView('error');
        return null;
      }

      reset();
      setView('scanning');
      setScanProgress(8);
      setScanNote('Starting VirusTotal scan...');

      try {
        let nextResult: VirusTotalResult;

        if (target.type === 'apk') {
          if (!packageName) throw new Error('No Android package is available for this scan target.');
          const extracted = await AppTracker.extractApk({ packageName });
          nextResult = await scanApkPath(extracted.path, trimmedKey);
        } else if (target.type === 'downloaded-apk') {
          const downloaded = await AppTracker.resolveDownloadFile({ fileName: target.detail });
          nextResult = await scanApkPath(downloaded.path, trimmedKey);
        } else {
          nextResult = await downloadAndScanApk(target.detail, trimmedKey);
        }

        setScanProgress(100);
        setScanNote('Report ready.');
        await sleep(350);
        setResult(nextResult);
        setView('results');

        if (useSettingsStore.getState().hapticEnabled) {
          Haptics.notification({
            type: getFlaggedCount(nextResult.stats) >= 5 ? NotificationType.Warning : NotificationType.Success
          }).catch(() => { });
        }

        return nextResult;
      } catch (scanError: any) {
        setError(scanError?.message || 'VirusTotal scan failed.');
        setView('error');
        if (useSettingsStore.getState().hapticEnabled) {
          Haptics.notification({ type: NotificationType.Error }).catch(() => { });
        }
        return null;
      }
    },
    [apiKey, downloadAndScanApk, packageName, reset, scanApkPath]
  );

  return {
    view,
    setView,
    scanNote,
    scanProgress,
    result,
    error,
    setError,
    startScan,
    reset
  };
};
