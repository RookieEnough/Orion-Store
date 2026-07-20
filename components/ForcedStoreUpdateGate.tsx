import React, { useState } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { ForcedStoreUpdateStatus } from '../utils/forcedStoreUpdate';
import {
  buildForcedStoreUpdateViewModel,
  type ForcedStoreUpdateAction
} from '../utils/forcedStoreUpdateViewModel';
import type { VirusTotalResult } from '../hooks/useVirusTotalScan';

interface ForcedStoreUpdateGateProps {
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
  /** Saved VirusTotal API key (for inline BYOK). */
  apiKey?: string;
  /** Live scan progress (0–100) while scanning. */
  scanProgress?: number;
  /** Latest scan result, surfaced on scan_complete. */
  scanResult?: VirusTotalResult | null;
  /** Rotating status note from the scan hook while scanning. */
  scanNote?: string;
  onRetryDownload: () => void;
  onScanBeforeInstall: () => void;
  onInstallNow: () => void;
  onRetryInstall: () => void;
  onExportApk: () => void;
  onOpenByokSettings: () => void;
  onSaveApiKey: (key: string) => void;
  onRevokeApiKey: () => void;
  onOpenReport: () => void;
  showDeveloperBypass?: boolean;
  onDeveloperBypass?: () => void;
}

type Tone = 'neutral' | 'progress' | 'success' | 'warning';

interface GateBoundaryState {
  error: Error | null;
}

class ForcedStoreUpdateGateBoundary extends React.Component<
  ForcedStoreUpdateGateProps & { children: React.ReactNode },
  GateBoundaryState
> {
  state: GateBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): GateBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error) {
    // Keep a breadcrumb on-device; the fallback below prevents a blank WebView.
    try {
      localStorage.setItem('orion-forced-update-render-error', error.message || String(error));
    } catch {
      // Ignore storage failures.
    }
  }

  componentDidUpdate(prevProps: ForcedStoreUpdateGateProps) {
    if (prevProps.status !== this.props.status && this.state.error) {
      this.setState({ error: null });
    }
  }

  render() {
    if (!this.state.error) return this.props.children;

    return (
      <ForcedStoreUpdateRecoveryView
        status={this.props.status}
        currentVersion={this.props.currentVersion}
        targetVersion={this.props.targetVersion}
        onInstallNow={this.props.onInstallNow}
        onOpenReport={this.props.onOpenReport}
        onRetryDownload={this.props.onRetryDownload}
        canInstall={this.props.canInstall}
      />
    );
  }
}

const ForcedStoreUpdateGate: React.FC<ForcedStoreUpdateGateProps> = ({
  currentVersion,
  targetVersion,
  status,
  progress,
  statusText,
  errorText,
  exportPath,
  canInstall,
  canScan,
  isExported,
  apiKey,
  scanProgress,
  scanResult,
  scanNote,
  onRetryDownload,
  onScanBeforeInstall,
  onInstallNow,
  onExportApk,
  onOpenByokSettings,
  onSaveApiKey,
  onRevokeApiKey,
  onOpenReport,
  showDeveloperBypass,
  onDeveloperBypass
}) => {
  const viewModel = buildForcedStoreUpdateViewModel({
    currentVersion,
    targetVersion,
    status,
    progress,
    statusText,
    errorText,
    exportPath,
    canInstall,
    canScan,
    isExported,
    scanProgress,
    scanStats: scanResult?.stats ?? null
  });
  const prefersReducedMotion = useReducedMotion();
  const clampedProgress = Math.max(0, Math.min(progress, 100));
  const clampedScan = Math.max(0, Math.min(scanProgress ?? 0, 100));
  const isScanning = status === 'scanning';
  const tone = viewModel.tone;

  const fade = (y: number) =>
    prefersReducedMotion ? undefined : { opacity: 0, y };
  const ease = prefersReducedMotion
    ? { duration: 0.2 }
    : { duration: 0.28, ease: [0.22, 1, 0.36, 1] as const };

  if (status === 'scan_complete') {
    return (
      <div
        className="fsug-shell fixed inset-0 z-[220] grid place-items-center overflow-hidden bg-surface px-6 py-10 text-theme-text"
        data-status={status}
        style={{
          paddingBottom: 'calc(2.5rem + env(safe-area-inset-bottom))',
          paddingTop: 'calc(2.5rem + env(safe-area-inset-top))'
        }}
      >
        <ScanCompleteView
          currentVersion={currentVersion}
          targetVersion={targetVersion}
          scanResult={scanResult}
          canInstall={canInstall}
          onInstallNow={onInstallNow}
          onOpenReport={onOpenReport}
          prefersReducedMotion={!!prefersReducedMotion}
        />
      </div>
    );
  }

  return (
    <div
      className="fsug-shell fixed inset-0 z-[220] grid place-items-center overflow-hidden bg-surface px-6 py-10 text-theme-text"
      data-status={status}
      style={{
        paddingBottom: 'calc(2.5rem + env(safe-area-inset-bottom))',
        paddingTop: 'calc(2.5rem + env(safe-area-inset-top))'
      }}
    >
      <motion.section
        className="relative flex w-full max-w-[440px] flex-col gap-9"
        role="dialog"
        aria-modal="true"
        aria-live="polite"
        initial={fade(16)}
        animate={{ opacity: 1, y: 0 }}
        transition={ease}
      >
        {/* Main stack */}
        <div className="flex flex-col gap-5">
          {/* Focal icon */}
          <StatusIcon status={status} tone={tone} prefersReducedMotion={!!prefersReducedMotion} />

          {/* Headline */}
          <motion.h1
            key={`title-${status}`}
            className="m-0 max-w-[16ch] text-[2rem] font-bold leading-[1.05] tracking-tight text-theme-text"
            initial={fade(10)}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.24 }}
          >
            {viewModel.title}
          </motion.h1>

          <motion.p
            key={`copy-${status}`}
            className="m-0 max-w-[40ch] text-[0.95rem] leading-relaxed text-theme-sub"
            initial={fade(8)}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.2 }}
          >
            {viewModel.body}
          </motion.p>

          {/* Inline version line */}
          {viewModel.showVersionRow && (
            <p className="m-0 font-mono text-xs text-theme-sub">
              v{currentVersion} <span className="text-theme-sub/60">&rarr;</span> v{targetVersion}
            </p>
          )}

          {/* Progress — download (checking/downloading) or scan (scanning) */}
          {viewModel.showProgress && (
            <div className="flex flex-col gap-2">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-semibold uppercase tracking-[0.18em] text-theme-sub">
                  {isScanning ? 'Scanning' : 'In progress'}
                </span>
                <span className="font-mono text-xs font-semibold text-theme-text">
                  {isScanning ? Math.round(clampedScan) : Math.round(clampedProgress)}%
                </span>
              </div>
              <div className="h-1.5 w-full overflow-hidden rounded-full bg-theme-element">
                <motion.div
                  className="h-full rounded-full bg-primary"
                  initial={false}
                  animate={{ width: `${isScanning ? clampedScan : clampedProgress}%` }}
                  transition={{ type: 'spring', stiffness: 140, damping: 24 }}
                />
              </div>
              {isScanning && scanNote && (
                <p className="m-0 text-xs leading-relaxed text-theme-sub">{scanNote}</p>
              )}
            </div>
          )}

          {/* Scan verdict — scan_complete */}
          {false && viewModel.scanVerdict && (
            <div
              className={`flex items-center gap-2.5 rounded-2xl px-4 py-3 text-sm font-semibold ${viewModel.scanVerdict === 'safe'
                  ? 'bg-emerald-500/10 text-emerald-500'
                  : 'bg-amber-500/10 text-amber-500'
                }`}
            >
              <i
                className={`fas ${viewModel.scanVerdict === 'safe' ? 'fa-circle-check' : 'fa-triangle-exclamation'}`}
                aria-hidden="true"
              />
              <span>
                {viewModel.scanVerdict === 'safe'
                  ? `Safe — 0 of ${viewModel.scanTotal} engines flagged`
                  : `Caution — ${viewModel.scanFlagged} of ${viewModel.scanTotal} engines flagged`}
              </span>
            </div>
          )}

          {/* Inline BYOK key entry — byok_required */}
          {status === 'byok_required' && (
            <ByokKeyField
              apiKey={apiKey}
              onSave={onSaveApiKey}
              onRevoke={onRevokeApiKey}
            />
          )}

          {/* Actions */}
          {viewModel.actions.length > 0 && (
            <div className="mt-1 flex flex-col gap-2.5">
              {viewModel.actions.map((action) => (
                <ActionButton
                  key={action.intent}
                  action={action}
                  onRetryDownload={onRetryDownload}
                  onScanBeforeInstall={onScanBeforeInstall}
                  onInstallNow={onInstallNow}
                  onExportApk={onExportApk}
                  onOpenByokSettings={onOpenByokSettings}
                  onOpenReport={onOpenReport}
                />
              ))}
              {showDeveloperBypass && onDeveloperBypass && (
                <button
                  type="button"
                  onClick={onDeveloperBypass}
                  className="mt-1 pb-1 inline-flex items-center justify-center text-[10px] uppercase tracking-widest font-bold text-theme-sub hover:text-primary transition-colors hover:bg-transparent shadow-none"
                  aria-label="Developer bypass"
                >
                  [ Developer Bypass ]
                </button>
              )}
            </div>
          )}

          {/* Support / export path */}
          {viewModel.showSupportBlock && (
            <div className="flex flex-col gap-1.5">
              {viewModel.showExportPath && exportPath ? (
                <>
                  <span className="text-[11px] font-semibold uppercase tracking-[0.18em] text-theme-sub">
                    {viewModel.exportPathLabel}
                  </span>
                  <p className="m-0 break-all font-mono text-xs text-theme-sub">{exportPath}</p>
                </>
              ) : (
                <p className="m-0 max-w-[40ch] text-xs leading-relaxed text-theme-sub">
                  If the installer keeps failing, save the APK to Downloads and install it manually from Files.
                </p>
              )}
            </div>
          )}
        </div>
      </motion.section>
    </div>
  );
};

interface ActionButtonProps {
  action: ForcedStoreUpdateAction;
  onRetryDownload: () => void;
  onScanBeforeInstall: () => void;
  onInstallNow: () => void;
  onExportApk: () => void;
  onOpenByokSettings: () => void;
  onOpenReport: () => void;
}

const actionHandlers: Record<ForcedStoreUpdateAction['intent'], keyof Omit<ActionButtonProps, 'action'>> = {
  'retry-download': 'onRetryDownload',
  scan: 'onScanBeforeInstall',
  install: 'onInstallNow',
  export: 'onExportApk',
  settings: 'onOpenByokSettings',
  saved: 'onInstallNow',
  'open-report': 'onOpenReport'
};

const ActionButton: React.FC<ActionButtonProps> = ({ action, ...handlers }) => {
  const prefersReducedMotion = useReducedMotion();
  const handlerKey = actionHandlers[action.intent];
  const onClick = action.intent === 'saved' ? undefined : handlers[handlerKey];

  const base =
    'inline-flex min-h-[52px] w-full items-center justify-center gap-2.5 rounded-2xl px-5 text-[0.95rem] font-bold tracking-tight transition-colors duration-200 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary';
  const variant =
    action.kind === 'primary'
      ? 'bg-primary text-white shadow-lg shadow-primary/20 hover:brightness-105 focus-visible:outline-primary'
      : action.kind === 'secondary'
        ? 'bg-theme-element text-theme-text hover:bg-theme-hover focus-visible:outline-primary'
        : action.kind === 'outline'
          ? 'bg-primary/10 text-primary hover:bg-primary/20 focus-visible:outline-primary'
          : 'bg-primary/10 text-primary border border-primary/25 focus-visible:outline-primary';

  return (
    <motion.button
      type="button"
      className={`${base} ${variant}`}
      disabled={action.disabled}
      onClick={() => onClick?.()}
      whileHover={action.disabled || prefersReducedMotion ? undefined : { y: -1 }}
      whileTap={action.disabled || prefersReducedMotion ? undefined : { scale: 0.985 }}
      transition={{ type: 'spring', stiffness: 360, damping: 24 }}
    >
      <i className={`fas ${action.icon} text-sm`} aria-hidden="true" />
      <span>{action.label}</span>
    </motion.button>
  );
};

const ForcedStoreUpdateGateWithBoundary: React.FC<ForcedStoreUpdateGateProps> = (props) => (
  <ForcedStoreUpdateGateBoundary {...props} key={`gate-boundary-${props.status}`}>
    <ForcedStoreUpdateGate {...props} />
  </ForcedStoreUpdateGateBoundary>
);

export default ForcedStoreUpdateGateWithBoundary;

interface ForcedStoreUpdateRecoveryViewProps {
  status: ForcedStoreUpdateStatus;
  currentVersion: string;
  targetVersion: string;
  canInstall: boolean;
  onInstallNow: () => void;
  onOpenReport: () => void;
  onRetryDownload: () => void;
}

const ForcedStoreUpdateRecoveryView: React.FC<ForcedStoreUpdateRecoveryViewProps> = ({
  status,
  currentVersion,
  targetVersion,
  canInstall,
  onInstallNow,
  onOpenReport,
  onRetryDownload
}) => {
  const isScanComplete = status === 'scan_complete';

  return (
    <div
      className="fsug-shell fixed inset-0 z-[220] grid place-items-center overflow-hidden bg-surface px-6 py-10 text-theme-text"
      data-status={status}
      style={{
        paddingBottom: 'calc(2.5rem + env(safe-area-inset-bottom))',
        paddingTop: 'calc(2.5rem + env(safe-area-inset-top))'
      }}
    >
      <section className="relative flex w-full max-w-[440px] flex-col gap-6" role="dialog" aria-modal="true">
        <div className={`grid h-28 w-28 place-items-center rounded-[2.25rem] ${isScanComplete ? 'bg-emerald-500/12 text-emerald-500' : 'bg-amber-500/12 text-amber-500'}`}>
          <i className={`fas ${isScanComplete ? 'fa-circle-check' : 'fa-triangle-exclamation'} text-5xl`} aria-hidden="true" />
        </div>
        <div className="flex flex-col gap-3">
          <h1 className="m-0 max-w-[16ch] text-[2rem] font-bold leading-[1.05] tracking-tight text-theme-text">
            {isScanComplete ? 'Scan complete' : 'Update screen recovered'}
          </h1>
          <p className="m-0 max-w-[40ch] text-[0.95rem] leading-relaxed text-theme-sub">
            {isScanComplete
              ? 'VirusTotal finished scanning this update. You can continue installing.'
              : 'Orion recovered the update screen. Retry the update action to continue.'}
          </p>
          <p className="m-0 font-mono text-xs text-theme-sub">
            v{currentVersion} <span className="text-theme-sub/60">&rarr;</span> v{targetVersion}
          </p>
        </div>

        <div className="mt-1 flex flex-col gap-2.5">
          {isScanComplete && (
            <button
              type="button"
              onClick={() => onOpenReport()}
              className="inline-flex min-h-[52px] w-full items-center justify-center gap-2.5 rounded-2xl bg-theme-element px-5 text-[0.95rem] font-bold tracking-tight text-theme-text transition-colors duration-200 hover:bg-theme-hover focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            >
              <i className="fas fa-arrow-up-right-from-square text-sm" aria-hidden="true" />
              <span>Open VirusTotal report</span>
            </button>
          )}
          {canInstall && (
            <button
              type="button"
              onClick={() => onInstallNow()}
              className="inline-flex min-h-[52px] w-full items-center justify-center gap-2.5 rounded-2xl bg-primary px-5 text-[0.95rem] font-bold tracking-tight text-white shadow-lg shadow-primary/20 transition-colors duration-200 hover:brightness-105 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            >
              <i className="fas fa-arrow-up-right-from-square text-sm" aria-hidden="true" />
              <span>Install now</span>
            </button>
          )}
          {!isScanComplete && (
            <button
              type="button"
              onClick={() => onRetryDownload()}
              className="inline-flex min-h-[52px] w-full items-center justify-center gap-2.5 rounded-2xl bg-primary px-5 text-[0.95rem] font-bold tracking-tight text-white shadow-lg shadow-primary/20 transition-colors duration-200 hover:brightness-105 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            >
              <i className="fas fa-rotate-right text-sm" aria-hidden="true" />
              <span>Retry update</span>
            </button>
          )}
        </div>
      </section>
    </div>
  );
};

interface ScanCompleteViewProps {
  currentVersion: string;
  targetVersion: string;
  scanResult?: VirusTotalResult | null;
  canInstall: boolean;
  onInstallNow: () => void;
  onOpenReport: () => void;
  prefersReducedMotion: boolean;
}

const readStat = (value: unknown) => (typeof value === 'number' && Number.isFinite(value) ? value : 0);

const ScanCompleteView: React.FC<ScanCompleteViewProps> = ({
  currentVersion,
  targetVersion,
  scanResult,
  canInstall,
  onInstallNow,
  onOpenReport,
  prefersReducedMotion
}) => {
  const stats = scanResult?.stats ?? {};
  const malicious = readStat(stats.malicious);
  const suspicious = readStat(stats.suspicious);
  const harmless = readStat(stats.harmless);
  const undetected = readStat(stats.undetected);
  const timeout = readStat(stats.timeout);
  const flagged = malicious + suspicious;
  const total = harmless + malicious + suspicious + undetected + timeout;
  const hasReport = Boolean(scanResult?.permalink);
  const isClean = flagged === 0;

  return (
    <motion.section
      className="relative flex w-full max-w-[440px] flex-col gap-6"
      role="dialog"
      aria-modal="true"
      aria-live="polite"
      initial={prefersReducedMotion ? { opacity: 0 } : { opacity: 0, y: 18, scale: 0.98 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
    >
      <div className={`grid h-28 w-28 place-items-center rounded-[2.25rem] ${isClean ? 'bg-emerald-500/12 text-emerald-500' : 'bg-amber-500/12 text-amber-500'}`}>
        <i className={`fas ${isClean ? 'fa-circle-check' : 'fa-triangle-exclamation'} text-5xl`} aria-hidden="true" />
      </div>

      <div className="flex flex-col gap-3">
        <h1 className="m-0 max-w-[16ch] text-[2rem] font-bold leading-[1.05] tracking-tight text-theme-text">
          {isClean ? 'Scan complete' : 'Scan complete with caution'}
        </h1>
        <p className="m-0 max-w-[40ch] text-[0.95rem] leading-relaxed text-theme-sub">
          {total > 0
            ? `${flagged} of ${total} VirusTotal engines flagged this update.`
            : 'VirusTotal finished scanning this update. You can open the report or continue installing.'}
        </p>
        <p className="m-0 font-mono text-xs text-theme-sub">
          v{currentVersion} <span className="text-theme-sub/60">&rarr;</span> v{targetVersion}
        </p>
      </div>

      <div className={`flex items-center gap-2.5 rounded-2xl px-4 py-3 text-sm font-semibold ${isClean ? 'bg-emerald-500/10 text-emerald-500' : 'bg-amber-500/10 text-amber-500'}`}>
        <i className={`fas ${isClean ? 'fa-circle-check' : 'fa-shield-halved'}`} aria-hidden="true" />
        <span>{isClean ? 'No engines flagged the APK' : 'Review the report before installing'}</span>
      </div>

      <div className="grid grid-cols-3 gap-2">
        <div className="rounded-2xl bg-theme-element px-3 py-3 text-center">
          <p className="m-0 text-lg font-black text-red-500">{flagged}</p>
          <p className="m-0 text-[10px] font-bold uppercase tracking-wider text-theme-sub">Flagged</p>
        </div>
        <div className="rounded-2xl bg-theme-element px-3 py-3 text-center">
          <p className="m-0 text-lg font-black text-emerald-500">{harmless}</p>
          <p className="m-0 text-[10px] font-bold uppercase tracking-wider text-theme-sub">Clean</p>
        </div>
        <div className="rounded-2xl bg-theme-element px-3 py-3 text-center">
          <p className="m-0 text-lg font-black text-theme-text">{total}</p>
          <p className="m-0 text-[10px] font-bold uppercase tracking-wider text-theme-sub">Engines</p>
        </div>
      </div>

      <div className="mt-1 flex flex-col gap-2.5">
        {hasReport && (
          <button
            type="button"
            onClick={() => onOpenReport()}
            className="inline-flex min-h-[52px] w-full items-center justify-center gap-2.5 rounded-2xl bg-theme-element px-5 text-[0.95rem] font-bold tracking-tight text-theme-text transition-colors duration-200 hover:bg-theme-hover focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            <i className="fas fa-arrow-up-right-from-square text-sm" aria-hidden="true" />
            <span>Open VirusTotal report</span>
          </button>
        )}
        {canInstall && (
          <button
            type="button"
            onClick={() => onInstallNow()}
            className="inline-flex min-h-[52px] w-full items-center justify-center gap-2.5 rounded-2xl bg-primary px-5 text-[0.95rem] font-bold tracking-tight text-white shadow-lg shadow-primary/20 transition-colors duration-200 hover:brightness-105 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            <i className="fas fa-arrow-up-right-from-square text-sm" aria-hidden="true" />
            <span>Install now</span>
          </button>
        )}
      </div>
    </motion.section>
  );
};

// ---------------------------------------------------------------------------
// StatusIcon — MD3-style tonal focal chip. One filled Font Awesome icon
// inside a soft tonal disc. No looping animation; static, calm, premium.
// ---------------------------------------------------------------------------
const STATUS_ICON: Record<ForcedStoreUpdateStatus, string> = {
  inactive: 'fa-moon',
  checking: 'fa-magnifying-glass',
  downloading: 'fa-arrow-down',
  download_failed: 'fa-triangle-exclamation',
  ready_to_scan_or_install: 'fa-circle-check',
  byok_required: 'fa-key',
  scanning: 'fa-shield-halved',
  scan_complete: 'fa-circle-check',
  installing: 'fa-spinner',
  install_failed: 'fa-triangle-exclamation',
  export_ready: 'fa-floppy-disk',
  installed: 'fa-check'
};

// MD3-style tonal pair per tone: chip background tint + icon color.
const TONE_CHIP: Record<Tone, string> = {
  neutral: 'bg-theme-element text-theme-sub',
  progress: 'bg-primary/12 text-primary',
  success: 'bg-emerald-500/12 text-emerald-500',
  warning: 'bg-amber-500/12 text-amber-500'
};

interface StatusIconProps {
  status: ForcedStoreUpdateStatus;
  tone: Tone;
  prefersReducedMotion: boolean;
}

const StatusIcon: React.FC<StatusIconProps> = ({ status, tone, prefersReducedMotion }) => {
  const icon = STATUS_ICON[status] ?? STATUS_ICON.checking;

  return (
    <motion.div
      key={`icon-${status}`}
      className={`grid h-28 w-28 place-items-center rounded-[2.25rem] ${TONE_CHIP[tone]}`}
      initial={prefersReducedMotion ? { opacity: 0 } : { opacity: 0, scale: 0.9 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.28, ease: [0.2, 0, 0, 1] }}
      aria-hidden="true"
    >
      <i className={`fas ${icon} text-5xl`} />
    </motion.div>
  );
};

// ---------------------------------------------------------------------------
// ByokKeyField — inline VirusTotal API key entry for byok_required.
// Mirrors the key-input UI from VirusTotalScanModal's key view.
// ---------------------------------------------------------------------------
interface ByokKeyFieldProps {
  apiKey?: string;
  onSave: (key: string) => void;
  onRevoke: () => void;
}

const ByokKeyField: React.FC<ByokKeyFieldProps> = ({ apiKey, onSave, onRevoke }) => {
  const [input, setInput] = useState(apiKey ?? '');
  const [showKey, setShowKey] = useState(false);
  const hasSavedKey = !!apiKey;

  const maskedLabel = hasSavedKey
    ? `${apiKey!.slice(0, 4)}\u2022\u2022\u2022\u2022${apiKey!.slice(-4)}`
    : '';

  return (
    <div className="flex flex-col gap-3">
      <label className="flex flex-col gap-1.5">
        <span className="text-[11px] font-semibold uppercase tracking-[0.18em] text-theme-sub">
          VirusTotal API key
        </span>
        <div className="flex items-center gap-2">
          <div className="relative flex-1">
            <input
              type={hasSavedKey ? 'text' : (showKey ? 'text' : 'password')}
              value={hasSavedKey ? (showKey ? apiKey! : maskedLabel) : input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Enter your VirusTotal API key"
              disabled={hasSavedKey}
              readOnly={hasSavedKey}
              className="h-11 w-full rounded-xl border border-theme-border bg-theme-input px-3.5 pr-9 text-sm text-theme-text placeholder:text-theme-sub/50 focus:border-primary focus:outline-none disabled:opacity-60"
            />
            <button
              type="button"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => setShowKey(v => !v)}
              className="absolute inset-y-0 right-0 flex items-center px-2.5 text-theme-sub hover:text-theme-text transition-colors"
              aria-label={showKey ? 'Hide API key' : 'Show API key'}
            >
              <i className={`fas ${showKey ? 'fa-eye-slash' : 'fa-eye'} text-xs`}></i>
            </button>
          </div>
          {!hasSavedKey && (
            <button
              type="button"
              onClick={() => { if (input.trim()) onSave(input.trim()); }}
              disabled={!input.trim()}
              className="h-11 rounded-xl bg-primary px-4 text-sm font-bold text-white transition-opacity hover:brightness-105 disabled:opacity-40"
            >
              Save
            </button>
          )}
          {hasSavedKey && (
            <button
              type="button"
              onClick={() => navigator.clipboard.writeText(apiKey!)}
              className="h-11 shrink-0 rounded-xl bg-theme-element border border-theme-border px-3 text-sm text-theme-sub hover:text-theme-text transition-colors"
              aria-label="Copy API key"
            >
              <i className="fas fa-copy text-xs"></i>
            </button>
          )}
        </div>
      </label>
      {hasSavedKey && (
        <button
          type="button"
          onClick={onRevoke}
          className="self-start text-xs font-semibold text-theme-sub hover:text-theme-text transition-colors"
        >
          Revoke saved key
        </button>
      )}
      <p className="m-0 text-xs leading-relaxed text-theme-sub">
        Free keys are available at{' '}
        <a
          href="https://www.virustotal.com/gui/join-us"
          target="_blank"
          rel="noopener noreferrer"
          className="text-primary underline decoration-primary/30"
        >
          virustotal.com
        </a>
      </p>
    </div>
  );
};
