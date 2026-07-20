import type { ForcedStoreUpdateStatus } from './forcedStoreUpdate';
import type { VirusTotalStats } from '../hooks/useVirusTotalScan';

export interface ForcedStoreUpdateViewModelInput {
  currentVersion: string;
  targetVersion: string;
  status: ForcedStoreUpdateStatus;
  progress: number;
  statusText: string;
  errorText?: string;
  exportPath?: string;
  canInstall: boolean;
  canScan: boolean;
  isExported?: boolean;
  /** Scan progress (0–100) while scanning. */
  scanProgress?: number;
  /** VirusTotal stats once a scan completes. */
  scanStats?: VirusTotalStats | null;
}

export interface ForcedStoreUpdateAction {
  kind: 'primary' | 'secondary' | 'outline' | 'saved';
  intent: 'retry-download' | 'scan' | 'install' | 'export' | 'settings' | 'saved' | 'open-report';
  label: string;
  icon: string;
  disabled?: boolean;
}

export interface ForcedStoreUpdateViewModel {
  eyebrow: string;
  title: string;
  body: string;
  tone: 'neutral' | 'progress' | 'success' | 'warning';
  showProgress: boolean;
  showVersionRow: boolean;
  showSupportBlock: boolean;
  showExportPath: boolean;
  exportPathLabel: string;
  actions: ForcedStoreUpdateAction[];
  /** Active scan progress (0–100); only meaningful when status is scanning. */
  scanProgress: number;
  /** Verdict derived from VT stats once a scan completes. */
  scanVerdict: 'safe' | 'caution' | null;
  /** Number of engines that flagged the file (malicious + suspicious). */
  scanFlagged: number;
  /** Total number of engines that reported. */
  scanTotal: number;
}

const COPY: Record<
  ForcedStoreUpdateStatus,
  Pick<ForcedStoreUpdateViewModel, 'eyebrow' | 'title' | 'tone'> & { body: string }
> = {
  inactive: {
    eyebrow: 'Standby',
    title: '',
    body: '',
    tone: 'neutral'
  },
  checking: {
    eyebrow: 'Checking',
    title: 'Checking for the latest Orion build',
    body: 'Reaching Orion Store to confirm the newest version.',
    tone: 'progress'
  },
  downloading: {
    eyebrow: 'Downloading',
    title: 'Downloading the latest Orion build',
    body: 'The update is being downloaded in the background.',
    tone: 'progress'
  },
  download_failed: {
    eyebrow: 'Retry required',
    title: 'The download needs another try',
    body: 'Check your connection and retry the download.',
    tone: 'warning'
  },
  ready_to_scan_or_install: {
    eyebrow: 'Ready',
    title: 'The update is ready to install',
    body: 'Review the next step, then install when you are ready.',
    tone: 'success'
  },
  byok_required: {
    eyebrow: 'Scan setup',
    title: 'A VirusTotal API key is required to scan',
    body: 'Add your API key in settings to enable scanning before install.',
    tone: 'warning'
  },
  scanning: {
    eyebrow: 'Scanning',
    title: 'Scanning the update before install',
    body: 'Verifying the APK with VirusTotal before installation.',
    tone: 'progress'
  },
  scan_complete: {
    eyebrow: 'Scan complete',
    title: 'The update is ready for install',
    body: 'The scan is complete and the build is ready to install.',
    tone: 'success'
  },
  installing: {
    eyebrow: 'Installing',
    title: 'Android is taking over the install',
    body: 'Continue with the system installer to finish the update.',
    tone: 'progress'
  },
  install_failed: {
    eyebrow: 'Install failed',
    title: 'The install needs a manual fallback',
    body: 'Save the APK to Downloads and install it manually from Files.',
    tone: 'warning'
  },
  export_ready: {
    eyebrow: 'Saved',
    title: 'The APK is saved to your device',
    body: 'Open the saved APK from Files to continue installation.',
    tone: 'success'
  },
  installed: {
    eyebrow: 'Complete',
    title: 'Orion Store is up to date',
    body: 'Reopen Orion Store to continue with the new version.',
    tone: 'success'
  }
};

export function buildForcedStoreUpdateViewModel(input: ForcedStoreUpdateViewModelInput): ForcedStoreUpdateViewModel {
  const base = COPY[input.status];
  const body = input.errorText || input.statusText || base.body;
  const actions: ForcedStoreUpdateAction[] = [];

  if (input.status === 'download_failed') {
    actions.push({
      kind: 'primary',
      intent: 'retry-download',
      label: 'Retry download',
      icon: 'fa-rotate-right'
    });
  }

  if (input.status === 'byok_required') {
    // The API-key entry happens inline on the gate, so the only action here
    // is the option to skip scanning and install directly.
    actions.push({
      kind: 'secondary',
      intent: 'install',
      label: 'Skip scan & install',
      icon: 'fa-arrow-right'
    });
  }

  if (input.status === 'ready_to_scan_or_install' && input.canScan) {
    actions.push({
      kind: 'secondary',
      intent: 'scan',
      label: 'Scan before install',
      icon: 'fa-shield-halved'
    });
  }

  // After a scan completes, offer the full VirusTotal report (only when we have results).
  if (input.status === 'scan_complete' && input.scanStats) {
    actions.push({
      kind: 'secondary',
      intent: 'open-report',
      label: 'Open VirusTotal report',
      icon: 'fa-arrow-up-right-from-square'
    });
  }

  if (
    (input.status === 'ready_to_scan_or_install' ||
      input.status === 'scan_complete' ||
      input.status === 'export_ready') &&
    input.canInstall
  ) {
    actions.push({
      kind: 'primary',
      intent: 'install',
      label: 'Install now',
      icon: 'fa-arrow-up-right-from-square'
    });
  }

  if (input.status === 'install_failed' && !input.isExported) {
    actions.push({
      kind: 'outline',
      intent: 'export',
      label: 'Save APK to downloads',
      icon: 'fa-download'
    });
  }

  if (input.status === 'install_failed' && input.isExported) {
    actions.push({
      kind: 'saved',
      intent: 'saved',
      label: 'Saved to downloads',
      icon: 'fa-check',
      disabled: true
    });
  }

  // Derive a VirusTotal verdict from the scan stats (only on scan_complete).
  const scanStats = input.scanStats ?? null;
  const scanFlagged = scanStats ? (scanStats.malicious ?? 0) + (scanStats.suspicious ?? 0) : 0;
  const scanTotal = scanStats
    ? (scanStats.harmless ?? 0) +
      (scanStats.malicious ?? 0) +
      (scanStats.suspicious ?? 0) +
      (scanStats.undetected ?? 0) +
      (scanStats.timeout ?? 0)
    : 0;
  const scanVerdict: 'safe' | 'caution' | null =
    scanStats && scanTotal > 0 ? (scanFlagged === 0 ? 'safe' : 'caution') : null;

  return {
    eyebrow: base.eyebrow,
    title: base.title,
    body,
    tone: base.tone,
    showProgress:
      input.status === 'checking' ||
      input.status === 'downloading' ||
      input.status === 'scanning',
    showVersionRow: input.status !== 'inactive',
    showSupportBlock:
      input.status === 'install_failed' || input.status === 'download_failed' || input.status === 'export_ready',
    showExportPath: input.status === 'export_ready' && Boolean(input.exportPath),
    exportPathLabel: 'Saved path',
    actions,
    scanProgress: Math.max(0, Math.min(input.scanProgress ?? 0, 100)),
    scanVerdict,
    scanFlagged,
    scanTotal
  };
}
