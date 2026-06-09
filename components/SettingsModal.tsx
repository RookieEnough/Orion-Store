import React, { useState, useEffect, memo, useRef, lazy, Suspense } from 'react';
import { Capacitor } from '@capacitor/core';
import { useSettingsStore, useDataStore } from '../store/useAppStore';
import { AppItem } from '../types';
import AppTracker from '../plugins/AppTracker';
import { APP_FONT_OPTIONS, getAppFontDefinition } from '../constants';

// Lazy load heavy power modals
const ShizukuPowerModal = lazy(() => import('./ShizukuPowerModal'));
const SentinelModal = lazy(() => import('./SentinelModal'));

interface SettingsModalProps {
    onClose: () => void;
    allApps: AppItem[];
    availableUpdates: AppItem[];
    onTriggerUpdate: (app: AppItem) => void;
    onInstallApp: (app: AppItem, file: string) => void;
    onCancelDownloadById: (appId: string, dlId: string) => void;
    installingId: string | null;
    onUpdateAll: () => void;
    onNavigateToApp: (appId: string) => void;
    initialMenu?: SubMenu;
}

type SubMenu = 'none' | 'network' | 'storage' | 'visuals' | 'interface' | 'queue' | 'identity' | 'installer' | 'developer';

// --- ANTI-CHEAT CONFIGURATION ---
const SALT_KEY = "ORION_PROTOCOL_OMEGA_8842_SECURE_HASH_V1";

// --- CRYPTO UTILS ---
const generateHash = async (message: string): Promise<string> => {
    const msgBuffer = new TextEncoder().encode(message + SALT_KEY);
    const hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
};

const Toggle = memo(({ checked, onChange }: { checked: boolean; onChange: () => void }) => (
    <button
        type="button"
        onClick={onChange}
        className={`flex h-7 w-12 items-center rounded-full p-1 transition-all duration-200 ${checked
            ? 'border-primary/30 bg-primary shadow-lg shadow-primary/20'
            : 'bg-theme-element'
            }`}
    >
        <div
            className={`h-5 w-5 rounded-full bg-white shadow-sm transition-transform duration-200 ${checked ? 'translate-x-5' : 'translate-x-0'
                }`}
        ></div>
    </button>
));

const SettingsCard = memo(({ children, className = '' }: { children: React.ReactNode; className?: string }) => (
    <div className={`rounded-[1.35rem] bg-card px-4 py-3.5 transition-all ${className}`}>
        {children}
    </div>
));

const SettingsSection = memo(({
    eyebrow,
    title,
    children
}: {
    eyebrow?: string;
    title: string;
    children: React.ReactNode;
}) => (
    <section className="space-y-3">
        <div className="px-1">
            {eyebrow && (
                <span className="text-[10px] font-black uppercase tracking-[0.24em] text-primary/80">
                    {eyebrow}
                </span>
            )}
            <h4 className="mt-1 text-base font-black tracking-tight text-theme-text">{title}</h4>
            <div className="mt-2 h-px bg-gradient-to-r from-primary/30 via-primary/10 to-transparent"></div>
        </div>
        <div className="space-y-3">{children}</div>
    </section>
));

const SettingsRow = memo(({
    icon,
    accentClass,
    title,
    desc,
    meta,
    action,
    onClick
}: {
    icon: string;
    accentClass: string;
    title: string;
    desc?: string;
    meta?: React.ReactNode;
    action?: React.ReactNode;
    onClick?: () => void;
}) => {
    const content = (
        <div className="flex items-center justify-between gap-3">
            <div className="flex min-w-0 flex-1 items-start gap-3.5">
                <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl ${accentClass}`}>
                    <i className={`fas ${icon} text-base`}></i>
                </div>
                <div className="min-w-0 flex-1">
                    <h5 className="text-[15px] font-black leading-tight text-theme-text">{title}</h5>
                    {desc && <p className="mt-1 text-[12px] font-bold leading-snug text-theme-sub">{desc}</p>}
                </div>
            </div>
            <div className="flex shrink-0 items-center gap-2 self-center">
                {meta}
                {action}
            </div>
        </div>
    );

    if (onClick) {
        return (
            <button
                type="button"
                onClick={onClick}
                className="w-full rounded-[1.35rem] px-4 py-4 text-left bg-card hover:bg-theme-element/35 transition-colors active:scale-[0.99]"
            >
                {content}
            </button>
        );
    }

    return (
        <div className="rounded-[1.35rem] bg-card px-4 py-4">
            {content}
        </div>
    );
});

const FeaturePanel = memo(({
    icon,
    title,
    desc,
    accentClass,
    onClick
}: {
    icon: string;
    title: string;
    desc?: string;
    accentClass: string;
    onClick: () => void;
}) => (
    <button
        type="button"
        onClick={onClick}
        className="w-full rounded-[1.35rem] px-4 py-4 text-left bg-card hover:bg-theme-element/35 transition-colors active:scale-[0.99]"
    >
        <div className="flex items-center justify-between gap-3">
            <div className="flex min-w-0 flex-1 items-start gap-3.5">
                <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl ${accentClass}`}>
                    <i className={`fas ${icon} text-base`}></i>
                </div>
                <div className="min-w-0 flex-1">
                    <h5 className="text-[15px] font-black leading-tight text-theme-text">{title}</h5>
                    {desc && <p className="mt-1 text-[12px] font-bold leading-snug text-theme-sub">{desc}</p>}
                </div>
            </div>
            <i className="fas fa-chevron-right text-xs text-theme-sub opacity-60 self-center"></i>
        </div>
    </button>
));

const SelectablePanel = memo(({
    selected,
    title,
    meta,
    badge,
    onClick
}: {
    selected: boolean;
    title: string;
    meta?: string;
    badge?: string;
    onClick: () => void;
}) => (
    <button
        type="button"
        onClick={onClick}
        className={`w-full rounded-[1.35rem] px-4 py-4 text-left transition-colors active:scale-[0.99] ${selected
            ? 'bg-primary/10'
            : 'bg-card hover:bg-theme-element/45'
            }`}
    >
        <div className="flex items-center justify-between gap-3">
            <div className="min-w-0 flex-1 pr-1">
                <div className="text-[15px] font-black leading-tight text-theme-text">{title}</div>
            </div>
            <div className="flex items-center gap-2 shrink-0 self-center">
                {meta && (
                    <span className="max-w-[10rem] truncate rounded-full border border-theme-border bg-theme-element px-2.5 py-1 text-[10px] font-bold text-theme-sub">
                        {meta}
                    </span>
                )}
                {badge && (
                    <span className="rounded-full border border-theme-border bg-theme-element px-2 py-1 text-[9px] font-black uppercase tracking-[0.16em] text-theme-sub">
                        {badge}
                    </span>
                )}
                <div className={`flex h-8 w-8 items-center justify-center rounded-full border ${selected ? 'border-primary/20 bg-primary text-white' : 'border-theme-border bg-card text-theme-sub'
                    }`}>
                    <i className={`fas ${selected ? 'fa-check' : 'fa-circle'} text-[9px]`}></i>
                </div>
            </div>
        </div>
    </button>
));

const InfoCard = memo(({
    icon,
    iconClassName,
    children
}: {
    icon: string;
    iconClassName: string;
    children: React.ReactNode;
}) => (
    <div className="rounded-[1.35rem] bg-theme-element/55 px-4 py-3.5 flex gap-3 shadow-[0_8px_22px_rgba(0,0,0,0.22)]">
        <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-2xl bg-card/85 ${iconClassName}`}>
            <i className={`fas ${icon}`}></i>
        </div>
        <div className="min-w-0 text-xs leading-snug text-theme-sub">{children}</div>
    </div>
));

const InstallerListRow = memo(function InstallerListRow({
    packageName,
    label,
    isSystemInstaller,
    isSelected,
    iconSrc,
    showSwipeHint,
    onSelect,
    onRemove
}: {
    packageName: string;
    label: string;
    isSystemInstaller: boolean;
    isSelected: boolean;
    iconSrc: string | undefined;
    showSwipeHint?: boolean;
    onSelect: () => void;
    onRemove: () => void;
}) {
    const [dragX, setDragX] = useState(0);
    const [isAnimating, setIsAnimating] = useState(false);
    const startX = useRef<number | null>(null);
    const startY = useRef<number | null>(null);
    const baseX = useRef(0);
    const axis = useRef<'h' | 'v' | null>(null);
    const isDragging = useRef(false);
    const rafId = useRef<number | null>(null);
    const pendingX = useRef(0);

    const flushDrag = () => {
        rafId.current = null;
        setDragX(pendingX.current);
    };
    const scheduleDrag = (next: number) => {
        pendingX.current = next;
        if (rafId.current !== null) return;
        rafId.current = requestAnimationFrame(flushDrag);
    };

    useEffect(() => {
        if (!showSwipeHint) return;
        const t1 = setTimeout(() => { setIsAnimating(true); setDragX(56); }, 420);
        const t2 = setTimeout(() => { setDragX(0); }, 1180);
        const t3 = setTimeout(() => { setIsAnimating(false); }, 1500);
        return () => { clearTimeout(t1); clearTimeout(t2); clearTimeout(t3); };
    }, [showSwipeHint]);

    useEffect(() => () => {
        if (rafId.current !== null) cancelAnimationFrame(rafId.current);
    }, []);

    const handleStart = (clientX: number, clientY?: number) => {
        startX.current = clientX;
        startY.current = clientY ?? null;
        baseX.current = dragX;
        axis.current = null;
        isDragging.current = true;
    };
    const handleMove = (clientX: number, clientY?: number) => {
        if (startX.current === null) return;
        const dx = clientX - startX.current;
        const dy = clientY != null && startY.current != null ? clientY - startY.current : 0;
        if (axis.current === null) {
            if (Math.abs(dx) < 6 && Math.abs(dy) < 6) return;
            axis.current = Math.abs(dx) > Math.abs(dy) ? 'h' : 'v';
        }
        if (axis.current === 'v') return;
        const next = Math.max(0, Math.min(160, baseX.current + dx));
        scheduleDrag(next);
    };
    const handleEnd = () => {
        if (rafId.current !== null) {
            cancelAnimationFrame(rafId.current);
            rafId.current = null;
            setDragX(pendingX.current);
        }
        if (pendingX.current > 90 || dragX > 90) {
            onRemove();
        }
        pendingX.current = 0;
        setDragX(0);
        startX.current = null;
        startY.current = null;
        axis.current = null;
        setTimeout(() => { isDragging.current = false; }, 0);
    };

    const revealOpacity = Math.min(1, dragX / 80);

    return (
        <div className="relative">
            <div
                className="absolute inset-0 flex items-center justify-start pl-5 rounded-[1.25rem] pointer-events-none"
                style={{
                    background: 'linear-gradient(90deg, rgba(239,68,68,0.92), rgba(239,68,68,0.0))',
                    opacity: revealOpacity,
                    transition: dragX === 0 ? 'opacity 200ms ease-out' : 'none',
                }}
            >
                <span className="inline-flex items-center gap-1.5 text-white text-[10px] font-black uppercase tracking-[0.18em]">
                    <i className="fas fa-eye-slash"></i>
                    Hide
                </span>
            </div>
            <button
                type="button"
                onClick={(e) => { if (isDragging.current && Math.abs(dragX) > 4) { e.preventDefault(); return; } onSelect(); }}
                onTouchStart={(e) => handleStart(e.touches[0]!.clientX, e.touches[0]!.clientY)}
                onTouchMove={(e) => handleMove(e.touches[0]!.clientX, e.touches[0]!.clientY)}
                onTouchEnd={handleEnd}
                onTouchCancel={handleEnd}
                onMouseDown={(e) => handleStart(e.clientX, e.clientY)}
                onMouseMove={(e) => { if (startX.current !== null && e.buttons === 1) handleMove(e.clientX, e.clientY); }}
                onMouseUp={handleEnd}
                onMouseLeave={() => { if (startX.current !== null) handleEnd(); }}
                className={`relative w-full rounded-[1.25rem] px-4 py-3 text-left bg-card hover:bg-theme-element/45 ${isSelected ? 'bg-primary/10 hover:bg-primary/15' : ''}`}
                style={{
                    transform: `translate3d(${dragX}px,0,0)`,
                    transition: (axis.current === null && (dragX === 0 || isAnimating)) ? 'transform 280ms cubic-bezier(0.2, 0.8, 0.2, 1)' : 'none',
                    willChange: 'transform',
                    touchAction: 'pan-y',
                }}
            >
                <div className="flex items-center justify-between gap-4">
                    <div className="flex items-center gap-3 min-w-0">
                        {iconSrc ? (
                            <img
                                src={iconSrc}
                                alt=""
                                loading="lazy"
                                decoding="async"
                                className="h-8 w-8 rounded-xl border border-theme-border bg-theme-element object-cover"
                            />
                        ) : (
                            <div className="h-8 w-8 rounded-xl border border-theme-border bg-theme-element" />
                        )}
                        <div className="min-w-0">
                            <div className="truncate text-xs font-black text-theme-text">
                                {label || packageName}
                            </div>
                        </div>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                        {isSystemInstaller && (
                            <span className="rounded-full border border-theme-border bg-theme-element px-2 py-1 text-[9px] font-black uppercase tracking-[0.16em] text-theme-sub">
                                System
                            </span>
                        )}
                        <div className={`flex h-8 w-8 items-center justify-center rounded-full border transition-colors ${isSelected ? 'border-primary/20 bg-primary text-white shadow-md shadow-primary/20' : 'border-theme-border bg-card text-theme-sub'}`}>
                            <i className={`fas ${isSelected ? 'fa-check' : 'fa-circle'} text-[9px]`}></i>
                        </div>
                    </div>
                </div>
            </button>
        </div>
    );
});

// --- Identity panel: backup / restore only. Profile editing lives on the About tab. ---
interface IdentityPanelProps {
    handleExportIdentity: () => void;
    handleImportIdentity: (e: React.ChangeEvent<HTMLInputElement>) => void;
    fileInputRef: React.RefObject<HTMLInputElement>;
    importStatus: { msg: string; type: 'success' | 'error' | 'neutral' };
}

const IdentityPanel: React.FC<IdentityPanelProps> = memo(({ handleExportIdentity, handleImportIdentity, fileInputRef, importStatus }) => {
    return (
        <div className="space-y-6 animate-slide-up">
            <SettingsSection
                eyebrow="Identity"
                title="Backup and Restore"
            >
                <div className="rounded-[1.35rem] bg-pink-500/5 px-4 py-4 flex items-start gap-4">
                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-pink-500/15 text-pink-400">
                        <i className="fas fa-fingerprint text-base"></i>
                    </div>
                    <div className="min-w-0">
                        <h5 className="text-sm font-black text-theme-text">Orion is serverless</h5>
                        <p className="mt-1 text-xs font-medium text-theme-sub leading-snug">Save your progress — your identity stays on this device only.</p>
                    </div>
                </div>
                <div className="space-y-3">
                    <SettingsRow
                        icon="fa-file-export"
                        accentClass="bg-blue-500/10 text-blue-400"
                        title="Backup Identity"
                        action={<i className="fas fa-download text-theme-sub text-xs"></i>}
                        onClick={handleExportIdentity}
                    />
                    <SettingsRow
                        icon="fa-file-import"
                        accentClass="bg-emerald-500/10 text-emerald-400"
                        title="Restore Identity"
                        action={<i className="fas fa-upload text-theme-sub text-xs"></i>}
                        onClick={() => fileInputRef.current?.click()}
                    />
                    <input type="file" ref={fileInputRef} className="hidden" accept=".osf" onChange={handleImportIdentity} />
                </div>
                {importStatus.msg && (
                    <div className={`rounded-[1.2rem] px-4 py-3.5 flex items-center gap-3 transition-all active:scale-98 ${importStatus.type === 'success'
                        ? 'bg-green-500/10 text-green-600'
                        : importStatus.type === 'error'
                            ? 'bg-red-500/5 text-red-500'
                            : 'bg-theme-element text-theme-sub'
                        }`}>
                        <i className={`fas ${importStatus.type === 'success'
                            ? 'fa-check-circle'
                            : importStatus.type === 'error'
                                ? 'fa-exclamation-triangle'
                                : 'fa-info-circle'
                            }`}></i>
                        <span className="text-xs font-bold">{importStatus.msg}</span>
                    </div>
                )}
            </SettingsSection>
        </div>
    );
});

const SettingsModal: React.FC<SettingsModalProps> = ({
    onClose,
    allApps,
    availableUpdates,
    onTriggerUpdate,
    onInstallApp,
    onCancelDownloadById,
    installingId,
    onUpdateAll,
    onNavigateToApp,
    initialMenu = 'none'
}) => {
    const settings = useSettingsStore();
    const data = useDataStore();
    const fileInputRef = useRef<HTMLInputElement>(null);

    const [activeMenu, setActiveMenu] = useState<SubMenu>(initialMenu);
    const [importStatus, setImportStatus] = useState<{ msg: string, type: 'success' | 'error' | 'neutral' }>({ msg: '', type: 'neutral' });
    const [shizukuError, setShizukuError] = useState<string | null>(null);
    const [isFontPickerOpen, setIsFontPickerOpen] = useState(false);
    const [apkInstallers, setApkInstallers] = useState<Array<{ packageName: string; label: string; isSystemInstaller: boolean }>>([]);
    const [apkInstallersLoading, setApkInstallersLoading] = useState(false);
    const [apkInstallersError, setApkInstallersError] = useState<string | null>(null);

    // Modal State Control
    const [activeModal, setActiveModal] = useState<'none' | 'guardian' | 'sentinel'>('none');
    const [isInstallerPickerOpen, setIsInstallerPickerOpen] = useState(false);
    const [installerPickerStep, setInstallerPickerStep] = useState<'mode' | 'specific'>('mode');
    // Icons stored in a Map ref to avoid re-rendering the whole list when a single icon arrives.
    // State is only used to trigger a re-render; the Map keeps lookups cheap.
    const installerIconsRef = useRef<Map<string, string>>(new Map());
    const [installerIconsVersion, setInstallerIconsVersion] = useState(0);
    const [installerListVersion, setInstallerListVersion] = useState(0);

    const activeDlCount = Object.keys(data.activeDownloads).length;
    const readyCount = Object.keys(data.readyToInstall).length;
    const selectedFont = getAppFontDefinition(settings.appFont);
    const menuTitleMap: Record<SubMenu, string> = {
        none: 'Settings',
        identity: 'Identity',
        network: 'Network & Updates',
        storage: 'Storage & Cleanup',
        visuals: 'Visuals & Theme',
        interface: 'Interface',
        queue: 'Update Center',
        installer: 'Orion Xtra',
        developer: 'Developer Options'
    };
    const activeMenuTitle = menuTitleMap[activeMenu];

    const pendingUpdatesCount = availableUpdates.filter(u => !data.activeDownloads[u.id] && !data.readyToInstall[u.id]).length;
    const totalQueueItems = activeDlCount + readyCount + pendingUpdatesCount;

    const menuItems = [
        { id: 'identity', icon: 'fa-id-card', color: 'text-pink-500', bg: 'bg-pink-500/10', title: 'Backup & Restore', desc: 'Protect progress and profile data' },
        { id: 'network', icon: 'fa-wifi', color: 'text-blue-500', bg: 'bg-blue-500/10', title: 'Network & Updates', desc: 'Download rules and update behavior' },
        { id: 'installer', icon: 'fa-box-open', color: 'text-emerald-500', bg: 'bg-emerald-500/10', title: 'Orion Xtra', desc: 'Guardian, Sentinel, Shizuku, installers' },
        { id: 'storage', icon: 'fa-broom', color: 'text-orange-500', bg: 'bg-orange-500/10', title: 'Storage & Janitor', desc: 'Cleanup behavior and saved space' },
        { id: 'queue', icon: 'fa-download', color: 'text-indigo-500', bg: 'bg-indigo-500/10', title: 'Download Queue', desc: `${activeDlCount} active, ${readyCount} ready, ${pendingUpdatesCount} updates`, badge: totalQueueItems },
        { id: 'visuals', icon: 'fa-palette', color: 'text-purple-500', bg: 'bg-purple-500/10', title: 'Visuals & Theme', desc: 'Fonts, motion, glass, density' },
        { id: 'interface', icon: 'fa-layer-group', color: 'text-green-500', bg: 'bg-green-500/10', title: 'Interface', desc: 'Layout and visible tabs' },
    ];

    if (settings.isDevUnlocked) {
        menuItems.push({ id: 'developer', icon: 'fa-code', color: 'text-yellow-500', bg: 'bg-yellow-500/10', title: 'Developer Options', desc: 'Testing switches and debug tools' });
    }

    useEffect(() => {
        if (activeMenu !== 'installer') return;
        if (!Capacitor.isNativePlatform()) return;
        let cancelled = false;
        setApkInstallersLoading(true);
        setApkInstallersError(null);
        AppTracker.getApkInstallers()
            .then((res) => {
                if (cancelled) return;
                setApkInstallers(Array.isArray((res as any)?.installers) ? (res as any).installers : []);
            })
            .catch((e: any) => {
                if (cancelled) return;
                setApkInstallersError(e?.message || 'Failed to load installers.');
            })
            .finally(() => {
                if (cancelled) return;
                setApkInstallersLoading(false);
            });
        return () => { cancelled = true; };
    }, [activeMenu]);

    useEffect(() => {
        if (!Capacitor.isNativePlatform()) return;
        if (!isInstallerPickerOpen || installerPickerStep !== 'specific') return;

        const missing = apkInstallers.filter(
            (i) => i?.packageName && !installerIconsRef.current.has(i.packageName)
        );
        if (missing.length === 0) return;

        let cancelled = false;
        const tasks = missing.map(async (ins) => {
            try {
                const res = await AppTracker.getAppIcon({ packageName: ins.packageName });
                if (cancelled) return;
                const icon = (res as any)?.icon;
                if (typeof icon === 'string' && icon.startsWith('data:image')) {
                    installerIconsRef.current.set(ins.packageName, icon);
                    setInstallerIconsVersion((v) => v + 1);
                }
            } catch {
                // ignore
            }
        });

        return () => { cancelled = true; };
    }, [isInstallerPickerOpen, installerPickerStep, apkInstallers]);

    // --- IDENTITY LOGIC ---
    const handleExportIdentity = async () => {
        try {
            const state = useSettingsStore.getState();
            const dataState = useDataStore.getState();
            const exportData = {
                // Progress & legend
                adWatchCount: state.adWatchCount,
                submissionCount: state.submissionCount,
                lastSubmissionTime: state.lastSubmissionTime,
                lastLeaderboardSubmissionTime: state.lastLeaderboardSubmissionTime,
                isLegend: state.isLegend,
                isContributor: state.isContributor,
                isDevUnlocked: state.isDevUnlocked,
                // Visual preferences
                theme: state.theme,
                appFont: state.appFont,
                storeLayout: state.storeLayout,
                isOled: state.isOled,
                hiddenTabs: state.hiddenTabs,
                disableAnimations: state.disableAnimations,
                compactMode: state.compactMode,
                highRefreshRate: state.highRefreshRate,
                hapticEnabled: state.hapticEnabled,
                glassEffect: state.glassEffect,
                // Network / installer
                autoUpdateEnabled: state.autoUpdateEnabled,
                wifiOnly: state.wifiOnly,
                deleteApk: state.deleteApk,
                useShizuku: state.useShizuku,
                installerPreference: state.installerPreference,
                installerPackage: state.installerPackage,
                installerLabel: state.installerLabel,
                useRemoteJson: state.useRemoteJson,
                loadLocalData: state.loadLocalData,
                // App streams & ignored updates
                appStreams: state.appStreams,
                ignoredUpdates: state.ignoredUpdates,
                // Profile
                userProfile: state.userProfile,
                // Bundles
                customBundles: state.customBundles,
                removedBundleApps: state.removedBundleApps,
                extraBundleApps: state.extraBundleApps,
                // Favorites
                favorites: dataState.favorites,
                timestamp: Date.now()
            };

            const jsonString = JSON.stringify(exportData);
            const signature = await generateHash(jsonString);

            const finalPackage = { data: exportData, sig: signature, ver: "1.1" };
            const rawContent = btoa(unescape(encodeURIComponent(JSON.stringify(finalPackage))));
            const fileName = `orion_identity_${Date.now()}.osf`;

            if (Capacitor.isNativePlatform()) {
                await AppTracker.saveFile({ fileName, content: rawContent });
                setImportStatus({ msg: 'Identity saved successfully.', type: 'success' });
            } else {
                const blob = new Blob([rawContent], { type: 'text/plain' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = fileName;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(url);
                setImportStatus({ msg: 'Identity exported to Downloads.', type: 'success' });
            }
        } catch (e: any) {
            setImportStatus({ msg: 'Export failed: ' + (e.message || 'Unknown Error'), type: 'error' });
        }
    };

    const handleImportIdentity = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;

        const reader = new FileReader();
        reader.onload = async (event) => {
            try {
                const raw = event.target?.result as string;
                let jsonStr: string;
                try {
                    jsonStr = decodeURIComponent(escape(atob(raw)));
                } catch {
                    jsonStr = atob(raw);
                }
                const pkg = JSON.parse(jsonStr);

                if (!pkg.data || !pkg.sig) throw new Error("Invalid Save File");

                const reCalcSig = await generateHash(JSON.stringify(pkg.data));
                if (reCalcSig !== pkg.sig) {
                    setImportStatus({ msg: 'Tampering Detected. Save integrity check failed.', type: 'error' });
                    return;
                }

                const d = pkg.data;
                const currentState = useSettingsStore.getState();
                useSettingsStore.setState({
                    ...currentState,
                    // Progress & legend
                    adWatchCount: d.adWatchCount ?? currentState.adWatchCount,
                    submissionCount: d.submissionCount ?? currentState.submissionCount,
                    lastSubmissionTime: d.lastSubmissionTime ?? currentState.lastSubmissionTime,
                    lastLeaderboardSubmissionTime: d.lastLeaderboardSubmissionTime ?? currentState.lastLeaderboardSubmissionTime,
                    isLegend: d.isLegend ?? currentState.isLegend,
                    isContributor: d.isContributor ?? currentState.isContributor,
                    isDevUnlocked: d.isDevUnlocked ?? currentState.isDevUnlocked,
                    // Visual
                    theme: d.theme ?? currentState.theme,
                    appFont: d.appFont ?? currentState.appFont,
                    storeLayout: d.storeLayout ?? currentState.storeLayout,
                    isOled: d.isOled ?? currentState.isOled,
                    hiddenTabs: Array.isArray(d.hiddenTabs) ? d.hiddenTabs : currentState.hiddenTabs,
                    disableAnimations: d.disableAnimations ?? currentState.disableAnimations,
                    compactMode: d.compactMode ?? currentState.compactMode,
                    highRefreshRate: d.highRefreshRate ?? currentState.highRefreshRate,
                    hapticEnabled: d.hapticEnabled ?? currentState.hapticEnabled,
                    glassEffect: d.glassEffect ?? currentState.glassEffect,
                    // Network/installer
                    autoUpdateEnabled: d.autoUpdateEnabled ?? currentState.autoUpdateEnabled,
                    wifiOnly: d.wifiOnly ?? currentState.wifiOnly,
                    deleteApk: d.deleteApk ?? currentState.deleteApk,
                    useShizuku: d.useShizuku ?? currentState.useShizuku,
                    installerPreference: d.installerPreference ?? currentState.installerPreference,
                    installerPackage: d.installerPackage ?? currentState.installerPackage,
                    installerLabel: d.installerLabel ?? currentState.installerLabel,
                    useRemoteJson: d.useRemoteJson ?? currentState.useRemoteJson,
                    loadLocalData: d.loadLocalData ?? currentState.loadLocalData,
                    // Streams / ignored updates
                    appStreams: (d.appStreams && typeof d.appStreams === 'object') ? d.appStreams : currentState.appStreams,
                    ignoredUpdates: (d.ignoredUpdates && typeof d.ignoredUpdates === 'object') ? d.ignoredUpdates : currentState.ignoredUpdates,
                    // Profile
                    userProfile: d.userProfile ?? currentState.userProfile,
                    // Bundles
                    customBundles: Array.isArray(d.customBundles) ? d.customBundles : currentState.customBundles,
                    removedBundleApps: (d.removedBundleApps && typeof d.removedBundleApps === 'object') ? d.removedBundleApps : currentState.removedBundleApps,
                    extraBundleApps: (d.extraBundleApps && typeof d.extraBundleApps === 'object') ? d.extraBundleApps : currentState.extraBundleApps,
                });
                if (Array.isArray(d.favorites)) {
                    useDataStore.setState({ favorites: d.favorites });
                }

                setImportStatus({ msg: 'Identity restored successfully!', type: 'success' });
                setTimeout(() => window.location.reload(), 1500);

            } catch (err) {
                setImportStatus({ msg: 'Corrupt or incompatible save file.', type: 'error' });
            }
        };
        reader.readAsText(file);
    };

    const handleShizukuToggle = async () => {
        setShizukuError(null);
        if (!settings.useShizuku) {
            if (Capacitor.isNativePlatform()) {
                try {
                    await AppTracker.requestShizukuPermission();
                    settings.toggleUseShizuku();
                } catch (e: any) {
                    const msg = e?.message || "Permission Denied";
                    if (msg.includes("Shizuku is NOT running")) {
                        setShizukuError("Shizuku service is not running.");
                    } else {
                        setShizukuError("Permission was denied by user.");
                    }
                }
            } else {
                settings.toggleUseShizuku();
            }
        } else {
            settings.toggleUseShizuku();
        }
    };

    const closeInstallerPicker = () => {
        setIsInstallerPickerOpen(false);
        setInstallerPickerStep('mode');
    };

    const renderIdentitySettings = () => <IdentityPanel
        handleExportIdentity={handleExportIdentity}
        handleImportIdentity={handleImportIdentity}
        fileInputRef={fileInputRef}
        importStatus={importStatus}
    />;

    const renderNetworkSettings = () => (
        <SettingsSection
            eyebrow="Updates"
            title="Network and Downloads"
        >
            <SettingsRow
                icon="fa-wifi"
                accentClass="bg-blue-500/10 text-blue-400"
                title="WiFi-Only Mode"
                desc="Skip downloads on cellular data"
                action={<Toggle checked={settings.wifiOnly} onChange={settings.toggleWifiOnly} />}
            />
            <SettingsRow
                icon="fa-rotate"
                accentClass="bg-indigo-500/10 text-indigo-300"
                title="Auto-Update Apps"
                desc="Refresh apps in the background"
                action={<Toggle checked={settings.autoUpdateEnabled} onChange={settings.toggleAutoUpdate} />}
            />
        </SettingsSection>
    );

    const renderInstallerSettings = () => (
        <div className="space-y-4">
            <SettingsSection
                eyebrow="Orion Xtra"
                title="Security Tools"
            >
                <FeaturePanel
                    icon="fa-shield-halved"
                    title="Orion Guardian"
                    desc="Remove permissions, extract APKs, debloat"
                    accentClass="bg-indigo-500/10 text-indigo-300"
                    onClick={() => setActiveModal('guardian')}
                />
                <FeaturePanel
                    icon="fa-heart-pulse"
                    title="Orion Sentinel"
                    desc="Malware scanner for installed apps"
                    accentClass="bg-emerald-500/10 text-emerald-300"
                    onClick={() => setActiveModal('sentinel')}
                />
            </SettingsSection>

            <SettingsSection
                eyebrow="Install Behavior"
                title="Silent Install and Routing"
            >
                <button
                    type="button"
                    disabled={!Capacitor.isNativePlatform()}
                    onClick={() => { setInstallerPickerStep('mode'); setIsInstallerPickerOpen(true); }}
                    className={`orion-shadow-surface w-full rounded-[1.35rem] p-4 bg-card transition-colors active:scale-[0.99] ${Capacitor.isNativePlatform()
                        ? 'hover:bg-theme-element/25'
                        : 'opacity-70 cursor-not-allowed'
                        }`}
                >
                    <div className="flex items-center justify-between gap-3">
                        <div className="flex min-w-0 flex-1 items-start gap-3.5">
                            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                                <i className="fab fa-android text-base"></i>
                            </div>
                            <div className="min-w-0 flex-1">
                                <h5 className="text-[15px] font-black leading-tight text-theme-text">APK Installer</h5>
                                <span className="mt-1.5 inline-block rounded-full border border-theme-border bg-theme-element px-2.5 py-0.5 text-[9px] font-black uppercase tracking-[0.16em] text-theme-sub">
                                    {settings.installerPreference === 'package'
                                        ? (settings.installerLabel || 'Specific Installer')
                                        : settings.installerPreference === 'chooser'
                                            ? 'Ask Every Time'
                                            : 'System Installer'}
                                </span>
                            </div>
                        </div>
                    </div>
                    {!Capacitor.isNativePlatform() && (
                        <div className="mt-3 text-xs text-theme-sub">Android app only.</div>
                    )}
                </button>

                <SettingsRow
                    icon="fa-bolt"
                    accentClass="bg-primary/10 text-primary"
                    title="Silent Install"
                    desc="Install apps in the background"
                    action={<Toggle checked={settings.useShizuku} onChange={handleShizukuToggle} />}
                />
                {shizukuError && (
                    <div className="rounded-[1.15rem] px-4 py-3.5 flex items-center gap-3 bg-red-500/5">
                        <i className="fas fa-exclamation-circle text-red-500 text-sm"></i>
                        <p className="text-[11px] font-bold text-theme-sub">{shizukuError}</p>
                    </div>
                )}
                <InfoCard icon="fa-circle-info" iconClassName="text-sky-400" >
                    Silent installs require{' '}
                    <button
                        type="button"
                        onClick={() => onNavigateToApp('shizuku')}
                        className="font-bold text-primary underline underline-offset-2 hover:text-primary/80"
                    >
                        Shizuku
                    </button>
                    {' '}to be installed and running.
                </InfoCard>
            </SettingsSection>

            <SettingsSection
                eyebrow="Recovery"
                title="Maintenance"
            >
                <SettingsRow
                    icon="fa-sync-alt"
                    accentClass="bg-orange-500/10 text-orange-400"
                    title="Force Rescan Packages"
                    desc="Reload every installed app list"
                    onClick={() => { window.location.reload(); onClose(); }}
                />
            </SettingsSection>
        </div>
    );

    const renderStorageSettings = () => (
        <SettingsSection
            eyebrow="Storage"
            title="Cleanup"
        >
            <SettingsRow
                icon="fa-broom"
                accentClass="bg-orange-500/10 text-orange-400"
                title="Auto-Cleanup Installer"
                desc="Remove APKs after they install"
                action={<Toggle checked={settings.deleteApk} onChange={settings.toggleDeleteApk} />}
            />
            <InfoCard icon="fa-circle-info" iconClassName="text-sky-400">
                Janitor mode clears installed APK files on next app start.
            </InfoCard>
        </SettingsSection>
    );

    const renderVisuals = () => (
        <SettingsSection
            eyebrow="Appearance"
            title="Visuals and Theme"
        >
            <SettingsRow
                icon="fa-font"
                accentClass="bg-purple-500/10 text-purple-300"
                title="Change Font"
                meta={
                    <div className="flex items-center gap-2 text-theme-sub">
                        <span className="max-w-[9rem] truncate rounded-full bg-theme-element px-3 py-1 text-[10px] font-black text-theme-sub">
                            {selectedFont.label}
                        </span>
                        <i className="fas fa-chevron-right text-xs"></i>
                    </div>
                }
                onClick={() => setIsFontPickerOpen(true)}
            />
            <SettingsRow
                icon="fa-hand-pointer"
                accentClass="bg-fuchsia-500/10 text-fuchsia-300"
                title="Haptic Feedback"
                desc="Tactile taps on buttons and toggles"
                action={<Toggle checked={settings.hapticEnabled} onChange={settings.toggleHaptic} />}
            />
            <SettingsRow
                icon="fa-layer-group"
                accentClass="bg-sky-500/10 text-sky-300"
                title="Glass Effect"
                desc="Frosted translucent surfaces"
                action={<Toggle checked={settings.glassEffect} onChange={settings.toggleGlass} />}
            />
            <SettingsRow
                icon="fa-gauge-high"
                accentClass="bg-cyan-500/10 text-cyan-300"
                title="Smooth Motion"
                desc="Prefer high refresh rate scrolling"
                action={<Toggle checked={settings.highRefreshRate} onChange={settings.toggleHighRefreshRate} />}
            />
            <SettingsRow
                icon="fa-moon"
                accentClass="bg-zinc-500/10 text-zinc-300"
                title="OLED Black Mode"
                desc="Pure black for AMOLED screens"
                action={<Toggle checked={settings.isOled} onChange={settings.toggleOled} />}
            />
            <SettingsRow
                icon="fa-wand-magic-sparkles"
                accentClass="bg-amber-500/10 text-amber-300"
                title="Disable Animations"
                desc="Reduce motion for performance"
                action={<Toggle checked={settings.disableAnimations} onChange={settings.toggleDisableAnimations} />}
            />
            <SettingsRow
                icon="fa-compress"
                accentClass="bg-lime-500/10 text-lime-300"
                title="Compact Mode"
                desc="Tighter spacing across the UI"
                action={<Toggle checked={settings.compactMode} onChange={settings.toggleCompactMode} />}
            />
        </SettingsSection>
    );

    const renderInstallerPicker = () => (
        <div
            className="backdrop-scrim absolute inset-0 z-30 flex items-end justify-center bg-black/55 p-3 sm:items-center sm:p-6"
            onClick={closeInstallerPicker}
        >
            <div
                className="w-full max-w-md overflow-hidden rounded-[2.2rem] bg-surface shadow-2xl"
                onClick={(event) => event.stopPropagation()}
            >
                <div className="flex items-center justify-between border-b border-theme-border px-5 py-4 bg-surface/95 backdrop-blur-sm">
                    <div className="flex items-center gap-3 min-w-0">
                        {installerPickerStep === 'specific' && (
                            <button
                                type="button"
                                onClick={() => setInstallerPickerStep('mode')}
                                className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-theme-element text-theme-text transition-colors hover:bg-theme-hover"
                            >
                                <i className="fas fa-arrow-left"></i>
                            </button>
                        )}
                        <h4 className="truncate text-lg font-black tracking-tight text-theme-text">Choose Installer</h4>
                    </div>
                    <div className="flex items-center gap-2">
                        {installerPickerStep === 'specific' && settings.hiddenInstallers && settings.hiddenInstallers.length > 0 && (
                            <button
                                type="button"
                                onClick={() => {
                                    settings.resetHiddenInstallers();
                                    setInstallerListVersion(v => v + 1);
                                }}
                                className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-theme-element text-theme-sub hover:text-white hover:bg-red-500 transition-colors"
                                title="Reset Hidden Installers"
                            >
                                <i className="fas fa-rotate-left"></i>
                            </button>
                        )}
                        <button
                            type="button"
                            onClick={closeInstallerPicker}
                            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-theme-element text-theme-text transition-colors hover:bg-theme-hover"
                        >
                            <i className="fas fa-times"></i>
                        </button>
                    </div>
                </div>

                <div className="space-y-3 p-5">
                    {installerPickerStep === 'mode' ? (
                        <div className="space-y-2">
                            <SelectablePanel
                                selected={settings.installerPreference === 'system'}
                                title="System Installer"
                                onClick={() => { settings.setInstallerPreference('system'); closeInstallerPicker(); }}
                            />
                            <SelectablePanel
                                selected={settings.installerPreference === 'chooser'}
                                title="Ask Every Time"
                                onClick={() => { settings.setInstallerPreference('chooser'); closeInstallerPicker(); }}
                            />
                            <SelectablePanel
                                selected={settings.installerPreference === 'package'}
                                title="Specific Installer"
                                meta={settings.installerPreference === 'package' ? (settings.installerLabel || 'Selected') : undefined}
                                onClick={() => setInstallerPickerStep('specific')}
                            />
                        </div>
                    ) : (
                        <div className="space-y-2">
                            {apkInstallersLoading && (
                                <div className="rounded-[1.25rem] bg-card px-4 py-3.5 flex items-center justify-between">
                                    <span className="text-xs font-bold text-theme-sub">Loading...</span>
                                    <div className="h-4 w-4 rounded-full border-2 border-primary/60 border-t-transparent animate-spin"></div>
                                </div>
                            )}

                            {apkInstallersError && (
                                <div className="rounded-[1.25rem] px-4 py-3.5 flex items-center gap-3 bg-red-500/5">
                                    <i className="fas fa-exclamation-circle text-red-500 text-sm"></i>
                                    <p className="text-[11px] font-bold text-theme-sub">{apkInstallersError}</p>
                                </div>
                            )}

                            {!apkInstallersLoading && !apkInstallersError && apkInstallers.length === 0 && (
                                <div className="rounded-[1.25rem] px-4 py-3.5 text-xs font-bold text-theme-sub bg-card">
                                    No installers found.
                                </div>
                            )}

                            {!apkInstallersLoading && !apkInstallersError && apkInstallers.length > 0 && (
                                <div className="max-h-[52vh] overflow-y-auto no-scrollbar space-y-2">
                                    {apkInstallers
                                        .filter(i => !settings.hiddenInstallers?.includes(i.packageName))
                                        .map((installer, idx) => {
                                            const isSelected =
                                                settings.installerPreference === 'package' &&
                                                settings.installerPackage === installer.packageName;
                                            const iconSrc = installerIconsRef.current.get(installer.packageName);
                                            return (
                                                <InstallerListRow
                                                    key={installer.packageName + '-' + installerListVersion}
                                                    packageName={installer.packageName}
                                                    label={installer.label}
                                                    isSystemInstaller={installer.isSystemInstaller}
                                                    isSelected={isSelected}
                                                    iconSrc={iconSrc}
                                                    showSwipeHint={idx === 0}
                                                    onSelect={() => { settings.setInstallerPreference('package', installer.packageName, installer.label); closeInstallerPicker(); }}
                                                    onRemove={() => settings.hideInstaller(installer.packageName)}
                                                />
                                            );
                                        })}
                                </div>
                            )}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );

    const renderFontPicker = () => (
        <div
            className="backdrop-scrim absolute inset-0 z-20 flex items-end justify-center bg-black/45 p-3 backdrop-blur-sm animate-fade-in sm:items-center sm:p-6"
            onClick={() => setIsFontPickerOpen(false)}
        >
            <div
                className="w-full max-w-md overflow-hidden rounded-[2.2rem] bg-surface shadow-2xl animate-slide-up"
                onClick={(event) => event.stopPropagation()}
            >
                <div className="flex items-center justify-between border-b border-theme-border px-5 py-4 bg-surface/95 backdrop-blur-sm">
                    <div>
                        <h4 className="text-lg font-black text-theme-text">Choose Font</h4>
                        <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-theme-sub">Preview before applying</p>
                    </div>
                    <button
                        type="button"
                        onClick={() => setIsFontPickerOpen(false)}
                        className="flex h-10 w-10 items-center justify-center rounded-full bg-theme-element text-theme-text transition-colors hover:bg-theme-hover"
                    >
                        <i className="fas fa-times"></i>
                    </button>
                </div>

                <div className="space-y-4 p-5">
                    <div
                        className="rounded-[1.8rem] bg-card px-5 py-5 shadow-sm"
                        style={{ fontFamily: selectedFont.family }}
                    >
                        <span className="text-[10px] font-black uppercase tracking-[0.2em] text-primary/80">Live Preview</span>
                        <h5 className="mt-2 text-2xl font-black tracking-tight text-theme-text">Orion Store</h5>
                        <p className="mt-2 text-sm text-theme-sub">Fast updates, clean cards, and smooth browsing across the whole app.</p>
                        <p className="mt-3 text-xs font-bold text-theme-text">Aa Bb Cc 123</p>
                    </div>

                    <div className="max-h-[52vh] space-y-2 overflow-y-auto no-scrollbar">
                        {APP_FONT_OPTIONS.map((font) => {
                            const isActive = settings.appFont === font.key;
                            return (
                                <button
                                    key={font.key}
                                    type="button"
                                    onClick={() => {
                                        settings.setAppFont(font.key);
                                        setIsFontPickerOpen(false);
                                    }}
                                    className={`flex w-full items-center justify-between gap-4 rounded-[1.6rem] px-4 py-3 text-left transition-all active:scale-98 ${isActive
                                        ? 'bg-primary/10 text-theme-text shadow-lg shadow-primary/10'
                                        : 'bg-card text-theme-text hover:bg-theme-element/70'
                                        }`}
                                    style={{ fontFamily: font.family }}
                                >
                                    <div className="min-w-0">
                                        <span className="block truncate text-base font-black">{font.label}</span>
                                        <span className="block text-[11px] font-medium text-theme-sub">Aa Bb Cc 123</span>
                                        {font.key === 'systemDefault' && (
                                            <span className="mt-1 block text-[10px] font-medium text-theme-sub">
                                                Uses the WebView system font, which is usually Roboto on Android.
                                            </span>
                                        )}
                                    </div>
                                    <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full border ${isActive ? 'border-primary/20 bg-primary text-white' : 'border-theme-border bg-theme-element text-theme-sub'
                                        }`}>
                                        <i className={`fas ${isActive ? 'fa-check' : 'fa-font'} text-xs`}></i>
                                    </div>
                                </button>
                            );
                        })}
                    </div>
                </div>
            </div>
        </div>
    );

    const renderQueue = () => (
        <SettingsSection
            eyebrow="Queue"
            title="Updates in Progress"
        >
            {activeDlCount === 0 && availableUpdates.length === 0 && readyCount === 0 ? (
                <div className="rounded-[1.35rem] bg-card py-12">
                    <div className="flex flex-col items-center justify-center text-theme-sub opacity-50">
                        <i className="fas fa-cloud-check text-5xl mb-4"></i>
                        <p className="font-bold">Queue is empty</p>
                    </div>
                </div>
            ) : (
                <div className="space-y-3">
                    {settings.useShizuku && readyCount > 1 && (
                        <button
                            type="button"
                            onClick={onUpdateAll}
                            className="w-full rounded-[1.6rem] bg-gradient-to-r from-primary to-primary-light px-4 py-4 text-sm font-black text-white shadow-lg shadow-primary/20 transition-all hover:brightness-105 active:scale-[0.985]"
                        >
                            <div className="flex items-center justify-center gap-2">
                                <i className="fas fa-rocket"></i>
                                <span>Update All ({readyCount})</span>
                            </div>
                        </button>
                    )}
                    {Object.keys(data.readyToInstall).map(appId => {
                        const app = allApps.find(a => a.id === appId);
                        const isThisInstalling = installingId === appId;
                        return (
                            <div key={appId} className="rounded-[1.6rem] bg-primary/15 px-4 py-4">
                                <div className="flex items-center justify-between gap-4">
                                    <div>
                                        <div className="text-sm font-black text-primary">{app?.name || appId}</div>
                                        <div className="mt-1 text-[10px] font-black uppercase tracking-[0.18em] text-primary/70">
                                            Ready to install
                                        </div>
                                    </div>
                                    {isThisInstalling ? (
                                        <div className="flex items-center gap-2 px-3 py-2">
                                            <div className="h-4 w-4 rounded-full border-2 border-primary border-t-transparent animate-spin"></div>
                                            <span className="text-[10px] font-bold text-primary">Installing...</span>
                                        </div>
                                    ) : (
                                        <button
                                            type="button"
                                            onClick={() => app && onInstallApp(app, data.readyToInstall[appId] || '')}
                                            className="rounded-xl bg-primary px-4 py-2 text-xs font-black text-white shadow-lg shadow-primary/20 transition-transform active:scale-95"
                                        >
                                            Install
                                        </button>
                                    )}
                                </div>
                            </div>
                        );
                    })}
                    {Object.keys(data.activeDownloads).map(appId => {
                        const app = allApps.find(a => a.id === appId);
                        const rawVal = data.activeDownloads[appId] || '';
                        return (
                            <div key={appId} className="rounded-[1.6rem] bg-theme-element px-4 py-4">
                                <div className="flex flex-col gap-3">
                                    <div className="flex items-center justify-between gap-3">
                                        <span className="max-w-[190px] truncate text-sm font-black text-theme-text">
                                            {app?.name || appId}
                                        </span>
                                        <div className="flex items-center gap-2">
                                            <span className="text-[10px] font-black text-primary">{data.downloadProgress[appId] || 0}%</span>
                                            <button
                                                type="button"
                                                onClick={() => onCancelDownloadById(appId, rawVal)}
                                                className="flex h-6 w-6 items-center justify-center rounded-full bg-red-500/10 text-red-500 transition-colors hover:bg-red-500 hover:text-white"
                                            >
                                                <i className="fas fa-times text-[10px]"></i>
                                            </button>
                                        </div>
                                    </div>
                                    <div className="h-1.5 w-full overflow-hidden rounded-full bg-theme-element">
                                        <div
                                            className="h-full bg-primary transition-all duration-300"
                                            style={{ width: `${data.downloadProgress[appId] || 0}%` }}
                                        ></div>
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                    {availableUpdates.filter(u => !data.activeDownloads[u.id] && !data.readyToInstall[u.id]).map(app => (
                        <div key={app.id} className="rounded-[1.6rem] bg-theme-element px-4 py-4">
                            <div className="flex items-center justify-between gap-4">
                                <div>
                                    <div className="text-sm font-black text-theme-text">{app.name}</div>
                                    <div className="mt-1 text-[10px] text-theme-sub">Pending update v{app.latestVersion}</div>
                                </div>
                                <button
                                    type="button"
                                    onClick={() => onTriggerUpdate(app)}
                                    className="flex h-9 w-9 items-center justify-center rounded-full bg-primary text-white shadow-lg shadow-primary/20 transition-transform active:scale-95"
                                >
                                    <i className="fas fa-download text-[10px]"></i>
                                </button>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </SettingsSection>
    );

    const renderDeveloperSettings = () => (
        <SettingsSection
            eyebrow="Developer"
            title="Testing and Debug Controls"
        >
            <div className="orion-shadow-surface rounded-[1.6rem] px-4 py-4 flex gap-3 text-yellow-600 dark:text-yellow-500 bg-yellow-500/10 transition-colors active:scale-[0.99]">
                <i className="fas fa-exclamation-triangle mt-0.5"></i>
                <p className="text-xs font-bold leading-relaxed">
                    These options are for testing and modifying core app behavior. They may cause instability.
                </p>
            </div>
            <SettingsRow
                icon="fa-database"
                accentClass="bg-yellow-500/10 text-yellow-400"
                title="Enable Built-in Data"
                desc="Load packaged data instead of remote sources"
                action={<Toggle checked={settings.loadLocalData} onChange={settings.toggleLoadLocalData} />}
            />
            <SettingsRow
                icon="fa-screwdriver-wrench"
                accentClass="bg-primary/10 text-primary"
                title="Local Maintenance Mode"
                desc="Work on bundles and data locally"
                action={<Toggle checked={settings.localMaintenanceMode} onChange={settings.toggleLocalMaintenance} />}
            />
            <SettingsRow
                icon="fa-hourglass-half"
                accentClass="bg-orange-500/10 text-orange-400"
                title="Simulate Network Delay"
                desc="Inject latency into network calls"
                action={<Toggle checked={false} onChange={() => { }} />}
            />
            <SettingsRow
                icon="fa-plug-circle-xmark"
                accentClass="bg-red-500/10 text-red-400"
                title="Mock Remote Failure"
                desc="Force remote endpoints to fail"
                action={<Toggle checked={false} onChange={() => { }} />}
            />
            <button
                type="button"
                onClick={() => {
                    useDataStore.getState().setPendingCleanup({});
                    useDataStore.getState().setReadyToInstall({});
                }}
                className="w-full rounded-[1.6rem] px-4 py-4 text-sm font-bold text-red-500 orion-shadow-surface bg-red-500/10 transition-colors hover:bg-red-500/20 active:scale-[0.99]"
            >
                <div className="flex items-center justify-center gap-2">
                    <i className="fas fa-trash-alt"></i>
                    <span>Clear System Cache</span>
                </div>
            </button>
        </SettingsSection>
    );

    const renderInterfaceSettings = () => (
        <SettingsSection
            eyebrow="Navigation"
            title="Layout and Tabs"
        >
            <SettingsRow
                icon="fa-table-cells-large"
                accentClass="bg-green-500/10 text-green-400"
                title="Modern Store Layout"
                desc="Hero cards with editorial look"
                action={
                    <Toggle
                        checked={settings.storeLayout === 'modern'}
                        onChange={() => settings.setStoreLayout(settings.storeLayout === 'modern' ? 'classic' : 'modern')}
                    />
                }
            />
            <div className="orion-shadow-surface rounded-[1.35rem] bg-card overflow-hidden transition-colors">
                {['android', 'pc', 'tv'].map((tab, idx) => (
                    <div key={tab} className={`flex items-center justify-between gap-4 px-4 py-4 ${idx !== 2 ? 'border-b border-theme-border' : ''}`}>
                        <div className="flex items-start gap-3.5">
                            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-theme-element text-theme-sub">
                                <i className={`${tab === 'pc' ? 'fab' : 'fas'} ${tab === 'android' ? 'fa-mobile-screen' : tab === 'pc' ? 'fa-windows' : 'fa-tv'} text-base`}></i>
                            </div>
                            <div className="min-w-0">
                                <div className="text-[15px] font-black leading-tight capitalize text-theme-text">{tab} Tab</div>
                                <div className="mt-0.5 text-[12px] font-bold text-theme-sub">{tab === 'android' ? 'Show in bottom navigation' : tab === 'pc' ? 'PC and desktop apps' : 'TV and big-screen apps'}</div>
                            </div>
                        </div>
                        <Toggle checked={!settings.hiddenTabs.includes(tab)} onChange={() => settings.toggleHiddenTab(tab)} />
                    </div>
                ))}
            </div>
        </SettingsSection>
    );

    return (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 animate-fade-in">
            <div className="backdrop-scrim absolute inset-0 bg-black/80 backdrop-blur-md touch-none" onClick={onClose}></div>
            <div className="orion-shadow-frame bg-surface rounded-[2rem] w-full max-w-xl relative z-10 animate-slide-up shadow-2xl flex flex-col max-h-[88vh] overflow-hidden compact-allow">
                <div className="px-4 py-4 border-b border-theme-border bg-surface/95 backdrop-blur-sm z-20 orion-shadow-surface">
                    <div className="flex items-center justify-between gap-3">
                        {activeMenu !== 'none' && (
                            <button
                                type="button"
                                onClick={() => { setActiveMenu('none'); setImportStatus({ msg: '', type: 'neutral' }); setShizukuError(null); setIsFontPickerOpen(false); closeInstallerPicker(); }}
                                className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-theme-element text-theme-text transition-colors hover:bg-theme-hover"
                            >
                                <i className="fas fa-arrow-left"></i>
                            </button>
                        )}
                        <div className="min-w-0 flex-1">
                            <span className="text-[10px] font-black uppercase tracking-[0.22em] text-primary/80">Orion Settings</span>
                            <h3 className="truncate text-2xl font-black tracking-tight text-theme-text">{activeMenuTitle}</h3>
                        </div>
                        <button
                            type="button"
                            onClick={onClose}
                            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-theme-element text-theme-text transition-colors hover:bg-theme-hover"
                        >
                            <i className="fas fa-times"></i>
                        </button>
                    </div>
                </div>
                <div className="overflow-y-auto p-4 space-y-6 no-scrollbar flex-1 will-change-transform overscroll-contain">
                    {activeMenu === 'none' ? (
                        <div className="space-y-4">
                            {menuItems.map(item => (
                                <div key={item.id} className="orion-shadow-frame rounded-[1.35rem] bg-card transition-colors">
                                    <button
                                        type="button"
                                        onClick={() => { setActiveMenu(item.id as SubMenu); setIsFontPickerOpen(false); }}
                                        className="w-full rounded-[inherit] px-4 py-4 text-left orion-shadow-surface bg-card hover:bg-theme-element/40 transition-colors active:scale-[0.99]"
                                    >
                                        <div className="flex items-center justify-between gap-3">
                                            <div className="flex min-w-0 flex-1 items-start gap-3.5">
                                                <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl ${item.bg} ${item.color}`}>
                                                    <i className={`fas ${item.icon} text-base`}></i>
                                                </div>
                                                <div className="min-w-0 flex-1 text-left">
                                                    <div className="flex items-center gap-2">
                                                        <span className="text-[15px] font-black leading-tight text-theme-text">{item.title}</span>
                                                        {item.badge !== undefined && item.badge > 0 && (
                                                            <span className="rounded-full bg-acid px-2 py-0.5 text-[10px] font-black text-black">
                                                                {item.badge}
                                                            </span>
                                                        )}
                                                    </div>
                                                    {item.desc && (
                                                        <p className="mt-1 text-[12px] font-bold leading-snug text-theme-sub">
                                                            {item.desc}
                                                        </p>
                                                    )}
                                                </div>
                                            </div>
                                            <i className="fas fa-chevron-right text-xs text-theme-sub opacity-60 self-center"></i>
                                        </div>
                                    </button>
                                </div>
                            ))}
                        </div>
                    ) : (
                        <>
                            {activeMenu === 'identity' && renderIdentitySettings()}
                            {activeMenu === 'network' && renderNetworkSettings()}
                            {activeMenu === 'installer' && renderInstallerSettings()}
                            {activeMenu === 'storage' && renderStorageSettings()}
                            {activeMenu === 'queue' && renderQueue()}
                            {activeMenu === 'visuals' && renderVisuals()}
                            {activeMenu === 'interface' && renderInterfaceSettings()}
                            {activeMenu === 'developer' && renderDeveloperSettings()}
                        </>
                    )}
                </div>
            </div>
            {isInstallerPickerOpen && renderInstallerPicker()}
            {isFontPickerOpen && renderFontPicker()}
            <Suspense fallback={null}>
                {activeModal === 'guardian' && <ShizukuPowerModal onClose={() => setActiveModal('none')} />}
                {activeModal === 'sentinel' && <SentinelModal onClose={() => setActiveModal('none')} />}
            </Suspense>
        </div>
    );
};

export default SettingsModal;
