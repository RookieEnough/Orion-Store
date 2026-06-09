import React, { memo, useState } from 'react';
import { shallow } from 'zustand/shallow';
import { AppItem } from '../types';
import { motion, useReducedMotion } from 'motion/react';
import { useDataStore, useSettingsStore } from '../store/useAppStore';
import { getOptimizedImageUrl } from '../utils/image';

const getHueFromSeed = (seed: string) => {
    let hash = 0;
    for (let i = 0; i < seed.length; i++) {
        hash = seed.charCodeAt(i) + ((hash << 5) - hash);
        hash |= 0;
    }
    return Math.abs(hash) % 360;
};

interface CompactAppCardProps {
    app: AppItem;
    onClick: () => void;
    index?: number;
    priority?: boolean;
    className?: string;
}

const CompactAppCard: React.FC<CompactAppCardProps> = ({
    app,
    onClick,
    index = 0,
    priority = false,
    className = 'w-28'
}) => {
    const [iconStatus, setIconStatus] = useState<'loading' | 'loaded' | 'error'>('loading');
    const disableAnimations = useSettingsStore((state) => state.disableAnimations);
    const {
        isFavorite,
        toggleFavorite,
        downloadProgress,
        downloadStatus,
        isDownloading,
        isReadyToInstall
    } = useDataStore((state) => ({
        isFavorite: state.favorites.includes(app.id),
        toggleFavorite: state.toggleFavorite,
        downloadProgress: state.downloadProgress[app.id] || 0,
        downloadStatus: state.downloadStatus[app.id],
        isDownloading: !!state.activeDownloads[app.id],
        isReadyToInstall: !!state.readyToInstall[app.id]
    }), shallow);
    const prefersReducedMotion = useReducedMotion();
    const motionEnabled = !disableAnimations && !prefersReducedMotion;

    const isPending = downloadStatus === 'PENDING';
    const isFeatured = app.tags?.includes('Featured') || app.tags?.includes('Editor\'s Choice');

    const baseHue = getHueFromSeed(`${app.id}-${app.name}-${app.icon}`);
    const glowStyle = {
        background: `radial-gradient(120% 120% at 50% -20%, hsla(${baseHue}, 80%, 50%, 0.12), transparent 70%)`
    };
    const optimizedIconUrl = getOptimizedImageUrl(app.icon, 96, 96);
    const fallbackIconUrl = app.icon;

    return (
        <motion.div
            initial={motionEnabled ? { opacity: 0, y: 20 } : undefined}
            animate={motionEnabled ? { opacity: 1, y: 0 } : undefined}
            transition={motionEnabled ? { delay: index * 0.05, duration: 0.4, ease: 'easeOut' } : undefined}
            whileHover={motionEnabled ? { y: -4, scale: 1.02 } : undefined}
            whileTap={motionEnabled ? { scale: 0.98 } : undefined}
            onClick={onClick}
            className={`app-card-optimized orion-shadow-frame flex-shrink-0 snap-start group relative rounded-[1.6rem] ${className}`}
        >
            <div
                className={`orion-shadow-surface relative flex h-full cursor-pointer flex-col gap-2 overflow-hidden rounded-[inherit] bg-card p-3 transition-all ${isFeatured ? 'ring-1 ring-primary/35' : ''}`}
            >
                <div
                    className="absolute inset-0 rounded-[inherit] opacity-40 pointer-events-none"
                    style={glowStyle}
                />

                {isFeatured && (
                    <div className="pointer-events-none absolute inset-0 rounded-[inherit] bg-gradient-to-br from-primary/10 via-transparent to-transparent" />
                )}

                <div className="absolute inset-0 rounded-[inherit] overflow-hidden pointer-events-none">
                    <div className="absolute inset-0 translate-x-[-100%] group-hover:translate-x-[100%] transition-transform duration-1000 bg-gradient-to-r from-transparent via-white/5 to-transparent skew-x-12" />
                </div>

                {isFeatured && (
                    <div className="absolute top-2 right-2 z-30">
                        <div className="bg-primary text-[5px] font-black text-white px-1 py-0.5 rounded-full uppercase tracking-tighter shadow-sm animate-pulse">
                            Featured
                        </div>
                    </div>
                )}

                <div className="relative w-full flex items-center justify-center pt-1 pb-1">
                    {iconStatus !== 'loaded' && (
                        <div className="absolute inset-0 z-0 flex items-center justify-center rounded-2xl bg-theme-element text-sm font-black text-theme-text">
                            {app.name.charAt(0).toUpperCase()}
                        </div>
                    )}
                    <img
                        src={optimizedIconUrl}
                        alt={app.name}
                        className={`w-12 h-12 object-contain rounded-2xl bg-transparent relative z-10 group-hover:scale-105 transition-all duration-500 ${iconStatus === 'loaded' ? 'opacity-100' : 'opacity-0'}`}
                        loading={priority ? 'eager' : 'lazy'}
                        fetchPriority={priority ? 'high' : 'low'}
                        decoding={priority ? 'sync' : 'async'}
                        onLoad={() => setIconStatus('loaded')}
                        onError={(event) => {
                            const target = event.currentTarget;
                            if (fallbackIconUrl && target.dataset.fallback !== 'raw') {
                                target.dataset.fallback = 'raw';
                                target.src = fallbackIconUrl;
                                setIconStatus('loading');
                                return;
                            }
                            setIconStatus('error');
                        }}
                    />

                    {(isDownloading || isPending) && (
                        <div className="absolute inset-0 z-20 flex items-center justify-center rounded-2xl bg-black/70">
                            <div className="flex flex-col items-center gap-1">
                                <div className="w-6 h-6 rounded-full border-2 border-white/20 border-t-white animate-spin" />
                                <span className="text-[9px] font-black text-white">{Math.round(downloadProgress)}%</span>
                            </div>
                        </div>
                    )}
                </div>

                <div className="flex flex-col gap-0.5 relative z-10 text-center">
                    <h4 className="text-[11px] font-black text-theme-text truncate leading-tight">{app.name}</h4>
                    <p className="text-[9px] font-bold text-theme-sub truncate opacity-70">{app.author}</p>
                </div>

                <div className="flex flex-col items-center gap-1.5 mt-auto pt-0.5 relative z-10">
                    <button
                        onClick={(e) => { 
                            e.stopPropagation(); 
                            toggleFavorite(app.id); 
                            if (useSettingsStore.getState().hapticEnabled) {
                                import('@capacitor/haptics').then(({ Haptics, ImpactStyle }) => {
                                    Haptics.impact({ style: ImpactStyle.Medium });
                                });
                            }
                        }}
                        className={`w-6 h-6 rounded-full flex items-center justify-center transition-all active:scale-90 ${isFavorite ? 'text-rose-500 bg-rose-500/10' : 'text-theme-sub hover:text-rose-500 hover:bg-rose-500/10'}`}
                    >
                        <i className={`${isFavorite ? 'fas' : 'far'} fa-heart text-[9px]`}></i>
                    </button>

                    <div className="px-2 py-0.5 rounded-full bg-primary/10 border border-primary/20 w-full overflow-hidden">
                        <span className="text-[7px] font-black uppercase tracking-tighter text-primary text-center block truncate w-full">
                            {app.category}
                        </span>
                    </div>
                </div>
            </div>
        </motion.div>
    );
};

export default memo(CompactAppCard, (prev, next) => (
    prev.app === next.app &&
    prev.index === next.index &&
    prev.priority === next.priority &&
    prev.className === next.className
));
