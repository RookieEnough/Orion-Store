
import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Capacitor } from '@capacitor/core';
import { Haptics, ImpactStyle, NotificationType } from '@capacitor/haptics';
import { AppItem, Platform, VersionOption } from '../types';
import { MICROG_DEPENDENT_APPS, MICROG_INFO_URL, CATEGORY_GRADIENTS } from '../constants';
import AppTracker from '../plugins/AppTracker';
import { getOptimizedImageUrl } from '../utils/image';
import { useDataStore, useSettingsStore } from '../store/useAppStore';



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
    onExportAPK?: (app: AppItem, fileName: string) => void;
    isScanning?: boolean; // New prop
}

interface LazyScreenshotProps {
    src: string;
    index: number;
    platform: Platform;
    onClick: () => void;
    priority?: boolean;
}

const LazyScreenshot: React.FC<LazyScreenshotProps> = ({ src, index, platform, onClick, priority = false }) => {
    const [isVisible, setIsVisible] = useState(priority);
    const [isLoaded, setIsLoaded] = useState(false);
    const [hasError, setHasError] = useState(false);
    const containerRef = useRef<HTMLDivElement>(null);

    const isLandscape = platform === Platform.PC || platform === Platform.TV;
    const heightClass = isLandscape ? 'h-48' : 'h-72';
    const widthClass = isLandscape ? 'w-80' : 'w-36';
    const targetRequestHeight = isLandscape ? 600 : 800;

    useEffect(() => {
        if (priority) return;
        const observer = new IntersectionObserver(
            (entries) => {
                const entry = entries[0];
                if (entry && entry.isIntersecting) {
                    setIsVisible(true);
                    observer.disconnect();
                }
            },
            { root: null, rootMargin: '0px 200px 0px 0px', threshold: 0.01 }
        );
        if (containerRef.current) observer.observe(containerRef.current);
        return () => { if (observer) observer.disconnect(); };
    }, [priority]);

    if (hasError) return null;
    const optimizedSrc = getOptimizedImageUrl(src, undefined, targetRequestHeight);

    return (
        <div
            ref={containerRef}
            onClick={onClick}
            className={`relative shrink-0 snap-center ${heightClass} ${widthClass} flex items-center justify-center cursor-zoom-in active:scale-[0.98] transition-transform`}
        >
            {(!isLoaded || !isVisible) && <div className={`absolute inset-0 bg-theme-element animate-pulse border border-theme-border rounded-2xl`} />}
            {isVisible && (
                <img
                    src={optimizedSrc}
                    alt={`Screenshot ${index + 1}`}
                    loading={priority ? "eager" : "lazy"}
                    onLoad={() => setIsLoaded(true)}
                    onError={() => setHasError(true)}
                    className={`max-w-full max-h-full w-auto h-auto rounded-2xl shadow-sm transition-opacity duration-500 ${isLoaded ? 'opacity-100' : 'opacity-0'}`}
                />
            )}
        </div>
    );
};

const AppDetail: React.FC<AppDetailProps> = ({
    app, onClose, onDownload, isInstalling, localVersion, supportEmail, isUpdateAvailable,
    activeDownloadId, cleanupFileName, onCleanupDone,
    currentProgress, currentStatus, readyFileName,
    onCancelDownload, onDeleteReadyFile, onExportAPK, onNavigateToApp, isScanning
}) => {
    const { favorites, toggleFavorite } = useDataStore();
    const { setIgnoredUpdate } = useSettingsStore();
    const [showIgnoreMenu, setShowIgnoreMenu] = useState(false);
    const isFavorite = favorites.includes(app.id);

    const [showVariants, setShowVariants] = useState(false);
    const [showVersionSelector, setShowVersionSelector] = useState(false);
    const [targetVersion, setTargetVersion] = useState<VersionOption | null>(null);
    const [showMicroGNotice, setShowMicroGNotice] = useState(false);
    const [showCleanupPrompt, setShowCleanupPrompt] = useState(!!cleanupFileName);
    const [isCleaning, setIsCleaning] = useState(false);

    // --- LIGHTBOX STATE ---
    const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);

    // Gesture Physics State
    const [zoom, setZoom] = useState(1);
    const [pan, setPan] = useState({ x: 0, y: 0 });
    const [swipeOffset, setSwipeOffset] = useState(0);
    const [isDragging, setIsDragging] = useState(false);
    const [enableTransition, setEnableTransition] = useState(false); // Controls CSS transition

    // Refs for gesture tracking (Mutable values that don't trigger re-renders)
    const gesture = useRef({
        startX: 0,
        startY: 0,
        initialPan: { x: 0, y: 0 },
        initialZoom: 1,
        startDist: 0,
        startTime: 0,
        isPinching: false,
        directionLocked: false as 'horizontal' | 'vertical' | false
    });

    // --- INSTANT ICON LOGIC ---
    const lowResUrl = getOptimizedImageUrl(app.icon, 128, 128);
    const highResUrl = getOptimizedImageUrl(app.icon, 200, 200);
    const [displayIconUrl, setDisplayIconUrl] = useState(lowResUrl);
    const [isHighResLoaded, setIsHighResLoaded] = useState(false);

    useEffect(() => {
        const img = new Image();
        img.src = highResUrl;
        img.onload = () => {
            setDisplayIconUrl(highResUrl);
            setIsHighResLoaded(true);
        };
    }, [highResUrl]);

    const bgGradient = CATEGORY_GRADIENTS[app.category] || CATEGORY_GRADIENTS['Default'];
    const needsUpdate = isUpdateAvailable;
    const isInstalled = !!localVersion;
    const isUpToDate = isInstalled && !needsUpdate;
    const isFallbackMode = app.latestVersion === "Unknown" || app.version === "View on GitHub";

    const rawUrl = app.downloadUrl || '';
    const cleanUrl = rawUrl.toLowerCase();
    const isDirectFile = cleanUrl.endsWith('.apk') || cleanUrl.endsWith('.exe') || cleanUrl.endsWith('.zip') || cleanUrl.endsWith('.dmg');
    const isValidWebUrl = cleanUrl.startsWith('http://') || cleanUrl.startsWith('https://');
    const isExternalSource = isValidWebUrl && !isDirectFile && !cleanUrl.includes('github.com');

    let externalDomain = 'External Source';
    if (isExternalSource) {
        try { externalDomain = new URL(rawUrl).hostname.replace('www.', ''); } catch (e) { externalDomain = 'Source'; }
    }

    // --- LIGHTBOX LIFECYCLE ---
    useEffect(() => {
        if (lightboxIndex !== null) {
            document.body.classList.add('lightbox-open');
            resetGestures();
        } else {
            document.body.classList.remove('lightbox-open');
        }
        return () => document.body.classList.remove('lightbox-open');
    }, [lightboxIndex]);

    useEffect(() => {
        const handler = () => setLightboxIndex(null);
        window.addEventListener('orion-close-lightbox', handler);
        return () => window.removeEventListener('orion-close-lightbox', handler);
    }, []);

    useEffect(() => {
        if (cleanupFileName) setShowCleanupPrompt(true);
        else setShowCleanupPrompt(false);
    }, [cleanupFileName]);

    const resetGestures = () => {
        setZoom(1);
        setPan({ x: 0, y: 0 });
        setSwipeOffset(0);
        setIsDragging(false);
        setEnableTransition(false);
        gesture.current.isPinching = false;
        gesture.current.directionLocked = false;
    };

    // --- NAVIGATION HANDLERS ---
    const handleNextImage = useCallback(() => {
        if (lightboxIndex === null) return;
        setEnableTransition(true); // Animate the slide
        setSwipeOffset(-window.innerWidth); // Push to left
    }, [lightboxIndex]);

    const handlePrevImage = useCallback(() => {
        if (lightboxIndex === null) return;
        setEnableTransition(true);
        setSwipeOffset(window.innerWidth); // Push to right
    }, [lightboxIndex]);

    // Handle the end of the CSS transition (The "Swap" Trick)
    const onTransitionEnd = () => {
        if (lightboxIndex === null) return;

        const width = window.innerWidth;
        // If we moved a full screen width, update the index and reset offset INSTANTLY
        if (Math.abs(swipeOffset) >= width) {
            const direction = swipeOffset > 0 ? -1 : 1;
            const len = app.screenshots.length;
            const newIndex = (lightboxIndex + direction + len) % len;

            setEnableTransition(false); // Disable transition for the reset
            setLightboxIndex(newIndex); // Swap Data
            setSwipeOffset(0); // Reset Position
            setZoom(1); // Reset Zoom
            setPan({ x: 0, y: 0 });
        } else {
            // Snap back case
            setEnableTransition(false);
        }
    };

    // Keyboard navigation
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (lightboxIndex === null) return;
            if (e.key === 'Escape') setLightboxIndex(null);
            if (e.key === 'ArrowRight' && swipeOffset === 0) handleNextImage();
            if (e.key === 'ArrowLeft' && swipeOffset === 0) handlePrevImage();
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [lightboxIndex, handleNextImage, handlePrevImage, swipeOffset]);

    // --- TOUCH PHYSICS ENGINE ---
    const getDistance = (touches: React.TouchList) => {
        const t1 = touches[0];
        const t2 = touches[1];
        if (!t1 || !t2) return 0;
        return Math.hypot(t1.clientX - t2.clientX, t1.clientY - t2.clientY);
    };

    const handleTouchStart = (e: React.TouchEvent) => {
        if (enableTransition) return; // Wait for animation

        const t1 = e.touches[0];
        if (!t1) return;

        gesture.current.startX = t1.clientX;
        gesture.current.startY = t1.clientY;
        gesture.current.startTime = Date.now();
        gesture.current.directionLocked = false;

        if (e.touches.length === 2) {
            gesture.current.isPinching = true;
            gesture.current.startDist = getDistance(e.touches);
            gesture.current.initialZoom = zoom;
            gesture.current.initialPan = { ...pan };
        } else {
            gesture.current.isPinching = false;
            // Capture current pan state for continuation
            gesture.current.initialPan = { ...pan };
            setIsDragging(true);
        }
    };

    const handleTouchMove = (e: React.TouchEvent) => {
        if (enableTransition) return;

        // --- PINCH ZOOM ---
        if (e.touches.length === 2 && gesture.current.isPinching) {
            const dist = getDistance(e.touches);
            if (gesture.current.startDist > 0) {
                const scale = dist / gesture.current.startDist;
                // Smooth clamp 0.5x to 5x
                const newZoom = Math.min(Math.max(gesture.current.initialZoom * scale, 0.5), 5);
                setZoom(newZoom);
            }
            return;
        }

        // --- PAN & SWIPE ---
        if (e.touches.length === 1 && !gesture.current.isPinching) {
            const t1 = e.touches[0];
            if (!t1) return;

            const dx = t1.clientX - gesture.current.startX;
            const dy = t1.clientY - gesture.current.startY;

            // Direction Lock (Prevent accidental swipes while scrolling vertical)
            if (!gesture.current.directionLocked) {
                if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                    gesture.current.directionLocked = Math.abs(dx) > Math.abs(dy) ? 'horizontal' : 'vertical';
                }
            }

            if (zoom > 1.05) {
                // --- PANNING MODE (Zoomed In) ---
                // Strict Clamping: Don't let image fly off screen
                // Max Translate X = (ImageWidth - ScreenWidth) / 2
                // ImageWidth = ScreenWidth * Zoom
                const maxX = (window.innerWidth * zoom - window.innerWidth) / 2;
                const maxY = (window.innerHeight * zoom - window.innerHeight) / 2;

                let newX = gesture.current.initialPan.x + dx;
                let newY = gesture.current.initialPan.y + dy;

                // Dampening if out of bounds (Rubber band effect optional, here we hard clamp for "Stay in position")
                if (newX > maxX) newX = maxX;
                if (newX < -maxX) newX = -maxX;
                if (newY > maxY) newY = maxY;
                if (newY < -maxY) newY = -maxY;

                setPan({ x: newX, y: newY });

            } else {
                // --- SWIPE MODE (Normal Zoom) ---
                // Only swipe if locked horizontally
                if (gesture.current.directionLocked === 'horizontal') {
                    // 1:1 Tracking
                    setSwipeOffset(dx);
                }
            }
        }
    };

    const handleTouchEnd = (e: React.TouchEvent) => {
        setIsDragging(false);

        // Reset Pinch Flag if fingers lifted
        if (e.touches.length < 2) {
            gesture.current.isPinching = false;
        }

        // --- SNAP BACK LOGIC ---
        if (zoom < 1.05) {
            // If zoomed out or normal
            setZoom(1);
            setPan({ x: 0, y: 0 });

            // Carousel Decision
            const time = Date.now() - gesture.current.startTime;
            const dist = swipeOffset;
            const velocity = Math.abs(dist) / time;
            const width = window.innerWidth;

            setEnableTransition(true); // Turn on CSS transition for the snap

            // Thresholds: 25% of screen OR Fast Flick (> 0.5 px/ms)
            if ((Math.abs(dist) > width * 0.25 || velocity > 0.5) && gesture.current.directionLocked === 'horizontal') {
                if (dist > 0) {
                    setSwipeOffset(width); // Animate to Prev
                } else {
                    setSwipeOffset(-width); // Animate to Next
                }
            } else {
                setSwipeOffset(0); // Snap back to center
            }
        }
    };

    // --- DOUBLE TAP ---
    const lastTap = useRef(0);
    const handleDoubleTap = (e: React.MouseEvent | React.TouchEvent) => {
        const now = Date.now();
        if (now - lastTap.current < 300) {
            if (zoom > 1.1) {
                setZoom(1);
                setPan({ x: 0, y: 0 });
            } else {
                setZoom(2.5);
            }
        }
        lastTap.current = now;
    };

    const handleShare = async () => {
        Haptics.selection();
        const shareUrl = app.repoUrl || app.downloadUrl || '#';
        const shareText = `Check out ${app.name} on Orion Store!`;

        if (Capacitor.isNativePlatform()) {
            try {
                await AppTracker.shareApp({ title: app.name, text: shareText, url: shareUrl });
            } catch (e) { console.error("Share failed", e); }
        } else {
            if (navigator.share) { navigator.share({ title: app.name, text: shareText, url: shareUrl }).catch(() => { }); }
            else { navigator.clipboard.writeText(`${shareText} ${shareUrl}`); alert("Link copied to clipboard!"); }
        }
    };

    const handleFavoriteToggle = () => {
        toggleFavorite(app.id);
        Haptics.impact({ style: ImpactStyle.Heavy });
    };

    const proceedWithVersion = (version: VersionOption, url?: string) => {
        if (url) { onDownload(app, url); return; }
        if (version.variants && version.variants.length > 1) { setTargetVersion(version); setShowVariants(true); return; }
        if (version.variants && version.variants.length > 0 && version.variants[0]) { checkMicroGAndDownload(version.variants[0].url); }
        else { checkMicroGAndDownload(app.downloadUrl); }
    };

    const checkMicroGAndDownload = async (finalUrl?: string) => {
        if (MICROG_DEPENDENT_APPS.includes(app.id) && !readyFileName) {
            if (Capacitor.isNativePlatform()) {
                try {
                    const p1 = await AppTracker.getAppInfo({ packageName: 'app.revanced.android.gms' });
                    if (p1.installed) { onDownload(app, finalUrl); return; }
                    const p2 = await AppTracker.getAppInfo({ packageName: 'com.mgoogle.android.gms' });
                    if (p2.installed) { onDownload(app, finalUrl); return; }
                    setShowMicroGNotice(true); return;
                } catch (e) { setShowMicroGNotice(true); return; }
            }
        }
        onDownload(app, finalUrl);
    };

    const handleAction = async (url?: string) => {
        if (isInstalling || activeDownloadId || isScanning) return;
        if (url) { checkMicroGAndDownload(url); return; }
        if (readyFileName) { onDownload(app, url); return; }
        if (!targetVersion && app.availableVersions && app.availableVersions.length > 1) { setShowVersionSelector(true); return; }
        const versionToUse = targetVersion || (app.availableVersions && app.availableVersions.length > 0 ? app.availableVersions[0] : null);
        if (versionToUse) { proceedWithVersion(versionToUse); }
        else { if (app.variants && app.variants.length > 1) { setShowVariants(true); } else { checkMicroGAndDownload(app.downloadUrl); } }
    };

    const handleLaunch = () => {
        if (app.packageName && Capacitor.isNativePlatform()) {
            Haptics.impact({ style: ImpactStyle.Heavy });
            AppTracker.launchApp({ packageName: app.packageName }).catch(() => { alert("Could not launch app. It may be restricted."); });
        }
    };

    const handleExport = () => { if (cleanupFileName && onExportAPK) { onExportAPK(app, cleanupFileName); } };

    const handleCleanupDelete = async () => {
        if (cleanupFileName) {
            setIsCleaning(true); // Start Cleaning Animation
            try {
                await AppTracker.deleteFile({ fileName: cleanupFileName });
                Haptics.notification({ type: NotificationType.Success });
                if (onCleanupDone) onCleanupDone();
                setShowCleanupPrompt(false);
            } catch (e) {
                console.error("Delete failed", e);
                // Still close modal to avoid getting stuck
                if (onCleanupDone) onCleanupDone();
                setShowCleanupPrompt(false);
            } finally {
                setIsCleaning(false);
            }
        }
    };

    const handleKeep = () => { Haptics.selection(); setShowCleanupPrompt(false); };

    const handleRedownload = () => { if (readyFileName && onDeleteReadyFile) { Haptics.impact({ style: ImpactStyle.Medium }); onDeleteReadyFile(app, readyFileName); } };

    const getStreamColor = (stream: string) => {
        switch (stream) {
            case 'Stable': return 'bg-green-500 text-white';
            case 'Beta': return 'bg-blue-500 text-white';
            case 'Alpha': return 'bg-orange-500 text-white';
            case 'Nightly': return 'bg-purple-500 text-white';
            default: return 'bg-gray-500 text-white';
        }
    };

    const renderActionButton = () => {
        const isActuallyDownloading = !!activeDownloadId || currentStatus === 'RUNNING' || currentStatus === 'PENDING';

        // 1. SCANNING (Transition Phase)
        if (isScanning) {
            return (
                <button disabled className="w-full py-4 rounded-2xl font-bold text-lg shadow-xl flex items-center justify-center gap-2 transition-all bg-indigo-500/10 text-indigo-500 border border-indigo-500/30 cursor-wait">
                    <i className="fas fa-microscope animate-bounce"></i>
                    <span>Scanning...</span>
                </button>
            );
        }

        // 2. DOWNLOAD IN PROGRESS
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
                    <button onClick={(e) => { e.stopPropagation(); onCancelDownload && activeDownloadId && onCancelDownload(app, activeDownloadId); }} className="w-12 h-full rounded-r-2xl flex items-center justify-center hover:bg-red-500 hover:text-white text-theme-sub transition-colors border-l border-theme-border group"><i className="fas fa-times text-sm group-active:scale-90"></i></button>
                </div>
            );
        }

        // 3. READY TO INSTALL (Prioritize over Open to prevent optimistic UI flicker)
        if (readyFileName) {
            return (
                <div className="flex gap-3">
                    <button onClick={() => handleAction()} disabled={isInstalling} className="flex-1 py-4 rounded-2xl font-bold text-lg shadow-xl flex items-center justify-center gap-2 transition-all active:scale-95 bg-primary text-white hover:bg-primary/90 shadow-primary/30 animate-fade-in"><i className="fas fa-box-open"></i><span>{isInstalling ? 'Installing...' : 'Install Now'}</span></button>
                    <button onClick={handleRedownload} disabled={isInstalling} className="w-14 rounded-2xl bg-red-500/10 border border-red-500/30 flex items-center justify-center text-red-500 hover:bg-red-500 hover:text-white transition-all active:scale-95"><i className="fas fa-trash-alt"></i></button>
                </div>
            );
        }

        // 4. CLEANUP PHASE
        if (cleanupFileName && app.platform === Platform.ANDROID) {
            return (
                <div className="flex gap-3 animate-fade-in">
                    <button onClick={handleLaunch} className="flex-1 py-4 rounded-2xl font-bold text-lg shadow-sm flex items-center justify-center gap-2 bg-green-500 text-white hover:bg-green-600 transition-colors active:scale-95"><i className="fas fa-play"></i><span>Open</span></button>
                    <button onClick={handleExport} className="w-16 rounded-2xl bg-theme-element border border-theme-border flex items-center justify-center text-theme-sub hover:text-primary transition-colors active:scale-95 group relative" title="Export APK to Downloads"><i className="fas fa-file-export text-xl group-hover:scale-110 transition-transform"></i><span className="absolute -top-1 -right-1 w-3 h-3 bg-primary rounded-full border-2 border-surface"></span></button>
                </div>
            );
        }

        // 5. INSTALLED / UP TO DATE
        if (isUpToDate && app.platform === Platform.ANDROID) {
            return (<button onClick={handleLaunch} className="w-full py-4 rounded-2xl font-bold text-lg shadow-sm flex items-center justify-center gap-2 bg-green-500 text-white hover:bg-green-600 transition-colors active:scale-95"><i className="fas fa-play"></i><span>Open</span></button>);
        }

        // 6. DEFAULT DOWNLOAD
        return (
            <div className="flex gap-3">
                <button
                    onClick={() => handleAction()}
                    disabled={isInstalling || isFallbackMode}
                    className={`flex-1 py-4 rounded-2xl font-bold text-lg shadow-xl flex items-center justify-center gap-2 transition-all active:scale-95 ${isFallbackMode ? 'bg-theme-element text-theme-sub cursor-not-allowed border border-theme-border' : isInstalling ? 'bg-theme-element text-theme-sub cursor-wait border border-theme-border' : isExternalSource ? 'bg-indigo-600 text-white hover:bg-indigo-700 shadow-indigo-500/30' : needsUpdate ? 'bg-acid text-black hover:bg-acid/90 shadow-acid/30' : 'bg-primary text-white hover:bg-primary/90 shadow-primary/30'}`}
                >
                    {isInstalling ? <><div className="w-5 h-5 border-2 border-current border-t-transparent rounded-full animate-spin"></div><span>Installing...</span></> : isFallbackMode ? <><i className="fas fa-external-link-alt"></i><span>View on GitHub</span></> : isExternalSource ? <><i className="fas fa-external-link-alt"></i><span>Get from Source</span></> : needsUpdate ? <><i className="fas fa-sync-alt"></i><span>Update Now</span></> : app.platform === Platform.PC ? <><i className="fas fa-desktop"></i><span>Get on PC</span></> : <><i className="fas fa-download"></i><span>Download</span></>}
                </button>

                {needsUpdate && !isInstalling && !isFallbackMode && (
                    <div className="relative">
                        <button
                            onClick={() => { setShowIgnoreMenu(!showIgnoreMenu); if (!showIgnoreMenu) Haptics.impact({ style: ImpactStyle.Light }); }}
                            className={`w-14 h-full rounded-2xl border transition-all active:scale-95 flex items-center justify-center ${showIgnoreMenu ? 'bg-primary text-white border-primary shadow-lg shadow-primary/20' : 'bg-theme-element text-theme-sub border-theme-border hover:text-primary'}`}
                            title="Ignore Update Options"
                        >
                            <i className={`fas fa-eye-slash transition-transform duration-300 ${showIgnoreMenu ? 'rotate-12' : ''}`}></i>
                        </button>

                        {showIgnoreMenu && (
                            <div className="absolute bottom-full right-0 mb-3 w-60 bg-card border border-theme-border rounded-2xl shadow-2xl overflow-hidden animate-slide-up z-50">
                                <div className="p-3 border-b border-theme-border bg-theme-element/30">
                                    <span className="text-[10px] font-bold text-theme-sub uppercase tracking-widest">Ignore Update</span>
                                </div>
                                <div className="flex flex-col">
                                    <button
                                        onClick={() => { setIgnoredUpdate(app.id, 'week'); setShowIgnoreMenu(false); onClose(); Haptics.notification({ type: NotificationType.Success }); }}
                                        className="px-4 py-3.5 text-left text-sm font-bold hover:bg-theme-element transition-colors flex items-center gap-3 text-theme-text"
                                    >
                                        <i className="fas fa-calendar-alt text-primary w-4"></i>
                                        <span>Ignore for 1 week</span>
                                    </button>
                                    <button
                                        onClick={() => { setIgnoredUpdate(app.id, 'version', app.latestVersion); setShowIgnoreMenu(false); onClose(); Haptics.notification({ type: NotificationType.Success }); }}
                                        className="px-4 py-3.5 text-left text-sm font-bold hover:bg-theme-element transition-colors flex items-center gap-3 text-theme-text"
                                    >
                                        <i className="fas fa-code-branch text-primary w-4"></i>
                                        <span>Until next version</span>
                                    </button>
                                    <button
                                        onClick={() => { setIgnoredUpdate(app.id, 'never'); setShowIgnoreMenu(false); onClose(); Haptics.notification({ type: NotificationType.Success }); }}
                                        className="px-4 py-3.5 text-left text-sm font-bold hover:bg-theme-element transition-colors flex items-center gap-3 text-red-500"
                                    >
                                        <i className="fas fa-ban w-4"></i>
                                        <span>Never show again</span>
                                    </button>
                                </div>
                            </div>
                        )}
                    </div>
                )}
            </div>
        );
    };

    // --- CAROUSEL RENDERER ---
    const renderLightboxContent = () => {
        if (lightboxIndex === null) return null;

        const safeIndex = (i: number) => (i + app.screenshots.length) % app.screenshots.length;

        // We only render 3 slides: Prev, Current, Next.
        // They are absolutely positioned relative to the container.
        const slides = [
            { type: 'prev', i: safeIndex(lightboxIndex - 1), offset: -100 },
            { type: 'curr', i: lightboxIndex, offset: 0 },
            { type: 'next', i: safeIndex(lightboxIndex + 1), offset: 100 }
        ];

        return (
            <div
                className="absolute inset-0 w-full h-full flex items-center"
                style={{
                    // Move the whole train based on swipeOffset
                    transform: `translateX(${swipeOffset}px)`,
                    // Natural Spring Physics for the snap back / slide
                    transition: enableTransition ? 'transform 0.35s cubic-bezier(0.25, 0.46, 0.45, 0.94)' : 'none'
                }}
                onTransitionEnd={onTransitionEnd}
            >
                {slides.map(slide => (
                    <div
                        key={`${slide.type}-${app.screenshots[slide.i]}`} // Unique key ensures distinct DOM elements
                        className="absolute top-0 bottom-0 w-full flex items-center justify-center p-2"
                        style={{
                            left: `${slide.offset}%`,
                            // Only the current slide gets the Zoom/Pan transforms
                            visibility: (zoom > 1 && slide.type !== 'curr') ? 'hidden' : 'visible'
                        }}
                    >
                        <img
                            src={getOptimizedImageUrl(app.screenshots[slide.i] || '', undefined, 1600)}
                            alt=""
                            className="max-w-full max-h-full object-contain shadow-2xl pointer-events-none select-none"
                            style={{
                                // Apply Zoom & Pan ONLY to the current slide
                                transform: slide.type === 'curr' ? `scale(${zoom}) translate(${pan.x / zoom}px, ${pan.y / zoom}px)` : 'none',
                                transition: isDragging ? 'none' : 'transform 0.2s ease-out'
                            }}
                        />
                    </div>
                ))}
            </div>
        );
    };

    return (
        <div className="fixed inset-0 z-[100] bg-surface flex flex-col animate-slide-up overflow-hidden">
            <div className="absolute top-0 left-0 right-0 h-96 overflow-hidden -z-10 opacity-30 dark:opacity-20 pointer-events-none">
                <div className="absolute inset-0 bg-gradient-to-b from-current to-surface text-primary/30"></div>
                <img src={displayIconUrl} className="w-full h-full object-cover blur-3xl scale-150 transition-opacity duration-1000" alt="" />
                <div className="absolute inset-0 bg-gradient-to-b from-transparent via-surface/80 to-surface"></div>
            </div>
            <div className="flex-1 overflow-y-auto pb-40 no-scrollbar relative">
                <div className="px-4 pb-0 pt-[calc(1rem+env(safe-area-inset-top))] flex items-center justify-between z-10 relative">
                    <button onClick={onClose} className="w-10 h-10 rounded-full bg-theme-element/80 border border-theme-border backdrop-blur-md flex items-center justify-center hover:bg-theme-hover transition-colors text-theme-text shadow-sm"><i className="fas fa-arrow-left"></i></button>
                    <div className="flex gap-3">
                        <button onClick={handleFavoriteToggle} className={`w-10 h-10 rounded-full bg-theme-element/80 border border-theme-border backdrop-blur-md flex items-center justify-center transition-colors shadow-sm ${isFavorite ? 'text-rose-500' : 'text-theme-text hover:bg-theme-hover'}`}><i className={`${isFavorite ? 'fas' : 'far'} fa-heart`}></i></button>
                        <button onClick={handleShare} className="w-10 h-10 rounded-full bg-theme-element/80 border border-theme-border backdrop-blur-md flex items-center justify-center hover:bg-theme-hover transition-colors text-theme-text shadow-sm"><i className="fas fa-share-alt"></i></button>
                        <button onClick={() => { Haptics.impact({ style: ImpactStyle.Light }); const subject = `Report Issue: ${app.name}`; window.location.href = `mailto:${supportEmail}?subject=${encodeURIComponent(subject)}`; }} className="w-10 h-10 rounded-full bg-theme-element/80 border border-theme-border backdrop-blur-md text-theme-sub flex items-center justify-center hover:text-red-500 transition-colors shadow-sm"><i className="fas fa-flag"></i></button>
                    </div>
                </div>
                <div className="px-6 pt-6 pb-6 flex gap-5 items-start">
                    <div className="relative shrink-0 w-24 h-24">
                        <img src={displayIconUrl} alt={app.name} className={`w-full h-full object-contain drop-shadow-md rounded-2xl transition-all duration-700 ${isHighResLoaded ? 'scale-100' : 'scale-95 blur-[2px]'}`} />
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
                    {cleanupFileName ? <span className="px-3 py-1 rounded-lg bg-acid/20 text-acid-dark dark:text-acid text-xs font-bold uppercase tracking-wide border border-acid/30 animate-pulse">Pending Cleanup</span> : readyFileName ? <span className="px-3 py-1 rounded-lg bg-primary/20 text-primary text-xs font-bold uppercase tracking-wide border border-primary/30 animate-pulse">{isInstalling ? 'Installing...' : 'Ready to Install'}</span> : needsUpdate ? <span className="px-3 py-1 rounded-lg bg-acid/20 text-acid-dark dark:text-acid text-xs font-bold uppercase tracking-wide border border-acid/30 animate-pulse">Update Available</span> : isUpToDate ? <span className="px-3 py-1 rounded-lg bg-green-500/10 text-green-600 dark:text-green-400 text-xs font-bold uppercase tracking-wide border border-green-500/20">Installed v{localVersion}</span> : null}
                </div>
                <div className="px-6 mb-6">
                    <div className="flex items-center justify-between bg-card border border-theme-border rounded-2xl p-4 shadow-sm">
                        <div className="flex flex-col items-center flex-1 border-r border-theme-border"><span className="font-black text-theme-text text-lg">{app.platform === Platform.ANDROID ? <i className="fab fa-android text-green-500 text-2xl"></i> : app.platform === Platform.TV ? <i className="fas fa-tv text-indigo-500 text-2xl"></i> : <i className="fab fa-windows text-blue-500 text-2xl"></i>}</span><span className="text-[10px] text-theme-sub font-bold uppercase mt-1">{app.platform}</span></div>
                        <div className="flex flex-col items-center flex-1 border-r border-theme-border"><span className="font-black text-theme-text text-lg truncate max-w-[80px]">{app.latestVersion.replace(/^v/, '')}</span><span className="text-[10px] text-theme-sub font-bold uppercase mt-1">Version</span></div>
                        <div className="flex flex-col items-center flex-1"><span className="font-black text-theme-text text-lg">{app.size}</span><span className="text-[10px] text-theme-sub font-bold uppercase mt-1">Size</span></div>
                    </div>
                </div>

                {app.repoUrl && app.repoUrl !== '#' && (
                    <div className="px-6 mb-6">
                        <button onClick={() => { Haptics.selection(); window.open(app.repoUrl, '_blank'); }} className="w-full py-4 bg-card border border-theme-border rounded-2xl flex items-center justify-center gap-3 hover:bg-theme-element transition-all active:scale-[0.98] shadow-sm group">
                            {app.repoUrl.includes('gitlab') ? (
                                <i className="fab fa-gitlab text-orange-500 text-2xl group-hover:scale-110 transition-transform"></i>
                            ) : app.repoUrl.includes('codeberg') ? (
                                <svg viewBox="0 0 24 24" fill="currentColor" className="w-7 h-7 text-[#2185D0] group-hover:scale-110 transition-transform">
                                    <path d="M11.955.49A12 12 0 0 0 0 12.49a12 12 0 0 0 1.832 6.373L11.838 5.928a.187.187 0 0 1 .324 0l10.006 12.935A12 12 0 0 0 24 12.49a12 12 0 0 0-12-12 12 12 0 0 0-.045 0zm.375 6.467 4.416 5.694-4.8 7.18-4.798-7.18z" />
                                </svg>
                            ) : (
                                <i className="fab fa-github text-theme-text text-2xl group-hover:scale-110 transition-transform"></i>
                            )}
                            <span className="font-bold text-theme-text">
                                {app.repoUrl.includes('gitlab') ? 'View on GitLab' : app.repoUrl.includes('codeberg') ? 'View on Codeberg' : 'Show Repository'}
                            </span>
                        </button>
                    </div>
                )}

                <div className="mb-8">
                    <h3 className="px-6 text-lg font-bold text-theme-text mb-4">Preview</h3>
                    <div className="flex gap-4 overflow-x-auto px-6 pb-4 no-scrollbar snap-x">
                        {app.screenshots.map((src: string, idx: number) => (
                            <LazyScreenshot key={idx} src={src} index={idx} platform={app.platform} priority={idx === 0} onClick={() => { setLightboxIndex(idx); Haptics.selection(); }} />
                        ))}
                    </div>
                </div>
                <div className="px-6 mb-8"><h3 className="text-lg font-bold text-theme-text mb-3">About this app</h3><p className="text-theme-sub leading-relaxed whitespace-pre-wrap font-medium text-sm">{app.description}</p></div>
                <div className="h-px bg-theme-border mx-6 mb-8"></div>
                <div className="px-6 mb-8">
                    <h3 className="text-lg font-bold text-theme-text mb-4">App info</h3>
                    <div className="grid grid-cols-1 gap-1">
                        {isInstalled && <div className="flex justify-between items-center py-3 border-b border-theme-border"><span className="text-theme-sub font-medium text-sm">Installed Version</span><span className="text-theme-text font-bold text-sm">{localVersion}</span></div>}
                        <div className="flex justify-between items-center py-3 border-b border-theme-border"><span className="text-theme-sub font-medium text-sm">Developer</span><span className="text-theme-text font-bold text-sm text-primary">{app.author}</span></div>
                        <div className="flex justify-between items-center py-3 border-b border-theme-border"><span className="text-theme-sub font-medium text-sm">Size</span><span className="text-theme-text font-bold text-sm">{app.size}</span></div>

                        {/* Source Display Logic */}
                        {app.githubRepo && <div className="flex justify-between items-center py-3 border-b border-theme-border"><span className="text-theme-sub font-medium text-sm">GitHub</span><span className="text-theme-text font-mono text-xs opacity-70 truncate max-w-[150px]">{app.githubRepo}</span></div>}
                        {app.gitlabRepo && <div className="flex justify-between items-center py-3 border-b border-theme-border"><span className="text-theme-sub font-medium text-sm">GitLab</span><span className="text-theme-text font-mono text-xs opacity-70 truncate max-w-[150px]">{app.gitlabRepo}</span></div>}
                        {app.codebergRepo && <div className="flex justify-between items-center py-3 border-b border-theme-border"><span className="text-theme-sub font-medium text-sm">Codeberg</span><span className="text-theme-text font-mono text-xs opacity-70 truncate max-w-[150px]">{app.codebergRepo}</span></div>}

                        {app.packageName && <div className="flex justify-between items-center py-3 border-b border-theme-border"><span className="text-theme-sub font-medium text-sm">Package</span><span className="text-theme-text font-mono text-xs opacity-70 truncate max-w-[150px]">{app.packageName}</span></div>}
                    </div>
                </div>
            </div>
            <div className="absolute bottom-0 left-0 right-0 p-6 bg-surface/90 backdrop-blur-xl border-t border-theme-border z-20">{renderActionButton()}</div>

            {showCleanupPrompt && (
                <div className="absolute inset-0 z-[110] flex items-center justify-center p-6 bg-black/70 animate-fade-in">
                    <div className="bg-surface border border-theme-border rounded-[2.5rem] p-8 w-full max-w-sm shadow-2xl relative animate-slide-up overflow-hidden" onClick={e => e.stopPropagation()}>
                        {isCleaning ? (
                            <div className="flex flex-col items-center justify-center py-4">
                                <div className="relative mb-6">
                                    <div className="w-20 h-20 bg-primary/20 rounded-full animate-ping absolute inset-0"></div>
                                    <div className="w-20 h-20 bg-primary text-white rounded-3xl flex items-center justify-center text-4xl shadow-2xl shadow-primary/40 relative z-10 animate-bounce">
                                        <i className="fas fa-broom"></i>
                                    </div>
                                </div>
                                <h3 className="text-xl font-black text-theme-text mb-1 animate-pulse">Scrubbing bits...</h3>
                                <p className="text-xs font-bold text-theme-sub">Reclaiming space for you</p>
                            </div>
                        ) : (
                            <>
                                <div className="absolute top-0 right-0 w-32 h-32 bg-primary/5 rounded-full -translate-y-1/2 translate-x-1/2"></div>
                                <div className="w-20 h-20 bg-primary text-white rounded-3xl flex items-center justify-center text-4xl mb-6 shadow-2xl shadow-primary/40 mx-auto transform -rotate-3"><i className="fas fa-broom"></i></div>
                                <h3 className="text-2xl font-black text-theme-text text-center mb-2 tracking-tight">Cleanup Time!</h3>
                                <p className="text-theme-sub text-center text-sm mb-8 leading-relaxed font-medium">The APK for <b>{app.name}</b> is no longer needed.</p>
                                <div className="flex flex-col gap-3">
                                    <button onClick={handleCleanupDelete} className="w-full py-4 rounded-2xl font-bold bg-acid text-black shadow-lg shadow-acid/20 hover:scale-[1.02] active:scale-95 transition-all flex items-center justify-center gap-2"><i className="fas fa-trash-alt"></i><span>Delete APK</span></button>
                                    <button onClick={handleKeep} className="w-full py-3 rounded-2xl font-bold bg-theme-element text-theme-sub hover:bg-theme-hover transition-colors text-xs uppercase tracking-widest">Keep for now</button>
                                </div>
                            </>
                        )}
                    </div>
                </div>
            )}

            {showVersionSelector && (
                <div className="absolute inset-0 z-[110] flex items-end sm:items-center justify-center sm:p-4 bg-black/60 animate-fade-in" onClick={() => setShowVersionSelector(false)}>
                    <div className="bg-surface w-full max-w-sm rounded-t-[2.5rem] sm:rounded-[2.5rem] p-6 pb-8 shadow-2xl animate-slide-up flex flex-col gap-5 relative border-t sm:border border-theme-border" onClick={e => e.stopPropagation()}>
                        <div className="flex justify-between items-center"><div><h3 className="text-xl font-black text-theme-text tracking-tight">Select Version</h3><p className="text-xs text-theme-sub font-bold uppercase tracking-wider mt-1">Choose your release channel</p></div><button onClick={() => setShowVersionSelector(false)} className="w-9 h-9 rounded-full bg-theme-element flex items-center justify-center text-theme-sub hover:text-theme-text transition-colors"><i className="fas fa-times"></i></button></div>
                        <div className="space-y-3">
                            {app.availableVersions?.map(ver => (
                                <button key={ver.version} onClick={() => { setShowVersionSelector(false); setTargetVersion(ver); proceedWithVersion(ver); }} className="w-full p-3 rounded-2xl bg-card border border-theme-border flex items-center justify-between hover:bg-theme-element active:scale-[0.98] transition-all group">
                                    <div className="flex items-center gap-4">
                                        <div className={`w-12 h-12 rounded-full flex items-center justify-center text-xl ${getStreamColor(ver.type).replace('text-white', '').replace('bg-', 'text-').replace('500', '500 bg-opacity-10')}`}><i className={`fas ${ver.type === 'Stable' ? 'fa-check-circle' : 'fa-flask'}`}></i></div>
                                        <div className="flex flex-col items-start"><div className="flex items-center gap-2"><span className="font-bold text-theme-text text-base">{ver.version}</span><span className={`text-[9px] font-black uppercase px-1.5 py-0.5 rounded ${getStreamColor(ver.type)}`}>{ver.type}</span></div><span className="text-[10px] font-bold text-theme-sub uppercase tracking-wider mt-0.5">{ver.date}</span></div>
                                    </div>
                                    <i className="fas fa-chevron-right text-theme-sub group-hover:text-primary transition-colors mr-2"></i>
                                </button>
                            ))}
                        </div>
                    </div>
                </div>
            )}

            {showVariants && (
                <div className="absolute inset-0 z-[110] flex items-end sm:items-center justify-center sm:p-4 bg-black/60 animate-fade-in" onClick={() => setShowVariants(false)}>
                    <div className="bg-surface w-full max-w-sm rounded-t-[2.5rem] sm:rounded-[2.5rem] p-6 pb-8 shadow-2xl animate-slide-up flex flex-col gap-5 relative border-t sm:border border-theme-border" onClick={e => e.stopPropagation()}>
                        <div className="flex justify-between items-center"><div><h3 className="text-xl font-black text-theme-text tracking-tight">Select Architecture</h3><div className="flex items-center gap-2 mt-1"><p className="text-xs text-theme-sub font-bold uppercase tracking-wider">Target:</p><span className="text-[10px] font-mono bg-theme-element px-1.5 rounded text-theme-text">{targetVersion ? targetVersion.version : app.latestVersion}</span><span className={`text-[9px] font-black uppercase px-1.5 py-0.5 rounded ${getStreamColor(targetVersion?.type || 'Stable')}`}>{targetVersion?.type || 'Stable'}</span></div></div><button onClick={() => setShowVariants(false)} className="w-9 h-9 rounded-full bg-theme-element flex items-center justify-center text-theme-sub hover:text-theme-text transition-colors"><i className="fas fa-times"></i></button></div>
                        <div className="space-y-3">{(targetVersion?.variants || app.variants)?.map((v: any) => (<button key={v.url} onClick={() => { setShowVariants(false); handleAction(v.url); }} className="w-full p-3 rounded-2xl bg-card border border-theme-border flex items-center justify-between hover:bg-theme-element active:scale-[0.98] transition-all group"><div className="flex items-center gap-4"><div className="w-12 h-12 rounded-full bg-indigo-500/10 dark:bg-indigo-500/20 text-indigo-600 dark:text-indigo-400 flex items-center justify-center text-xl"><i className="fas fa-microchip"></i></div><div className="flex flex-col items-start"><span className="font-bold text-theme-text text-base">{v.arch}</span><span className="text-[10px] font-bold text-theme-sub uppercase tracking-wider">APK</span></div></div><i className="fas fa-download text-theme-sub group-hover:text-primary transition-colors mr-2"></i></button>))}</div>
                    </div>
                </div>
            )}

            {showMicroGNotice && (
                <div className="absolute inset-0 z-[110] flex items-center justify-center p-6 bg-black/80 animate-fade-in">
                    <div className="bg-surface border border-theme-border rounded-3xl p-6 w-full max-w-sm shadow-2xl animate-slide-up relative">
                        <button onClick={() => setShowMicroGNotice(false)} className="absolute top-4 right-4 w-8 h-8 flex items-center justify-center bg-theme-element rounded-full text-theme-sub hover:text-theme-text transition-colors"><i className="fas fa-times"></i></button>
                        <div className="w-16 h-16 bg-blue-500/20 text-blue-500 rounded-2xl flex items-center justify-center text-3xl mb-4 mx-auto"><i className="fas fa-info-circle"></i></div>
                        <h3 className="text-xl font-black text-theme-text text-center mb-2 tracking-tight">MicroG Required</h3>
                        <p className="text-theme-sub text-center text-sm mb-6 leading-relaxed">This app requires MicroG to sign in to your Google Account. Make sure you have it installed first.</p>
                        <div className="flex flex-col gap-2"><button onClick={() => { setShowMicroGNotice(false); if (onNavigateToApp) onNavigateToApp('microG-re'); }} className="w-full py-4 rounded-2xl bg-primary text-white font-bold shadow-lg shadow-primary/20">Install MicroG</button><button onClick={() => { setShowMicroGNotice(false); window.open(MICROG_INFO_URL, '_blank'); }} className="w-full py-3 rounded-2xl bg-theme-element text-theme-text font-bold hover:bg-theme-hover transition-colors">Learn about MicroG</button></div>
                    </div>
                </div>
            )}

            {/* FULL SCREEN LIGHTBOX (True Carousel) */}
            {lightboxIndex !== null && (
                <div
                    className="fixed inset-0 z-[150] bg-black/95 flex items-center justify-center animate-fade-in touch-none overflow-hidden"
                    onClick={() => setLightboxIndex(null)}
                    onTouchStart={handleTouchStart}
                    onTouchMove={handleTouchMove}
                    onTouchEnd={handleTouchEnd}
                    onClickCapture={handleDoubleTap}
                >
                    <div className="relative w-full h-full">
                        {renderLightboxContent()}

                        {/* Controls - Only show if not zoomed */}
                        {zoom <= 1.05 && (
                            <>
                                <button onClick={() => setLightboxIndex(null)} className="absolute top-6 right-6 w-12 h-12 bg-black/50 hover:bg-black/70 border border-white/10 rounded-full flex items-center justify-center text-white transition-all active:scale-90 z-20"><i className="fas fa-times text-xl"></i></button>
                                {/* Hidden on touch, visible on desktop mouse hover */}
                                {app.screenshots.length > 1 && (
                                    <>
                                        <button onClick={(e) => { e.stopPropagation(); handlePrevImage(); }} className="hidden md:flex absolute left-4 top-1/2 -translate-y-1/2 w-12 h-12 bg-black/50 hover:bg-black/70 border border-white/10 rounded-full items-center justify-center text-white transition-all active:scale-90 z-20"><i className="fas fa-chevron-left text-xl"></i></button>
                                        <button onClick={(e) => { e.stopPropagation(); handleNextImage(); }} className="hidden md:flex absolute right-4 top-1/2 -translate-y-1/2 w-12 h-12 bg-black/50 hover:bg-black/70 border border-white/10 rounded-full items-center justify-center text-white transition-all active:scale-90 z-20"><i className="fas fa-chevron-right text-xl"></i></button>
                                    </>
                                )}
                                <div className="absolute bottom-8 left-0 right-0 flex justify-center gap-2 z-20 pointer-events-none">
                                    {app.screenshots.map((_, idx) => (
                                        <div key={idx} className={`w-2 h-2 rounded-full transition-all ${idx === lightboxIndex ? 'bg-white w-4' : 'bg-white/30'}`} />
                                    ))}
                                </div>
                            </>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
};

export default AppDetail;
