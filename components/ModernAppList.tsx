import React, { useEffect, useRef, useState } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { AppItem, BundleItem, StoreCategoryCard, StoreCollection } from '../types';
import CompactAppCard from './CompactAppCard';
import { useSettingsStore } from '../store/useAppStore';
import { getOptimizedImageUrl, prefetchImage } from '../utils/image';

interface ModernAppListProps {
    collections: StoreCollection[];
    onAppClick: (app: AppItem) => void;
    onSeeAllCategory?: (category: string) => void;
    onBundleClick?: (bundle: BundleItem) => void;
    onShowAll?: () => void;
    onShowCollectionAll?: (collection: StoreCollection) => void;
}

const getLaneId = (title: string) => `swimlane-${title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '')}`;

const getHueFromSeed = (seed: string) => {
    let hash = 0;
    for (let i = 0; i < seed.length; i++) {
        hash = (hash << 5) - hash + seed.charCodeAt(i);
        hash |= 0;
    }
    return Math.abs(hash) % 360;
};

const getHeroGlow = (app: AppItem) => {
    const baseHue = getHueFromSeed(`${app.id}-${app.name}-${app.icon}`);
    const altHue = (baseHue + 46) % 360;
    return `radial-gradient(62% 65% at 14% 20%, hsla(${baseHue}, 88%, 62%, 0.38), transparent 72%), radial-gradient(58% 70% at 86% 85%, hsla(${altHue}, 84%, 60%, 0.24), transparent 74%)`;
};

const useMotionEnabled = () => {
    const disableAnimations = useSettingsStore((state) => state.disableAnimations);
    const prefersReducedMotion = useReducedMotion();
    return !disableAnimations && !prefersReducedMotion;
};

export const ModernHomeSkeleton: React.FC = () => (
    <div className="relative flex w-full flex-col animate-fade-in pb-12 lg:pb-16">
        <div className="pointer-events-none relative z-20 mb-3 mt-1 w-full">
            <div className="flex gap-3 overflow-hidden -mx-3 px-4 py-6 -my-4">
                {[...Array(2)].map((_, index) => (
                    <div
                        key={index}
                        className="orion-shadow-frame orion-shadow-frame-strong h-[172px] w-[84vw] max-w-[20rem] flex-shrink-0 rounded-[1.75rem] sm:h-[208px] sm:w-[24rem] md:w-[26rem] lg:h-[224px] lg:w-[30rem] lg:max-w-[32rem]"
                    >
                        <div className="orion-shadow-surface h-full w-full rounded-[inherit] skeleton-shimmer" />
                    </div>
                ))}
            </div>
        </div>

        <div className="relative z-10 mt-1 lg:mx-auto lg:w-full lg:max-w-[78rem]">
            <div className="mb-6 flex gap-3 overflow-hidden -mx-3 px-4 py-2">
                {[...Array(3)].map((_, index) => (
                    <div
                        key={index}
                        className="orion-shadow-frame h-[9.6rem] w-[13.2rem] shrink-0 rounded-[1.9rem]"
                    >
                        <div className="orion-shadow-surface h-full w-full rounded-[inherit] skeleton-shimmer" />
                    </div>
                ))}
            </div>

            {[...Array(3)].map((_, laneIndex) => (
                <section key={laneIndex} className="relative mb-7 flex flex-col gap-2">
                    <div className="px-4">
                        <div className="h-5 w-36 rounded-full skeleton-shimmer" />
                    </div>
                    <div className="flex gap-3 overflow-hidden -mx-3 px-4 py-6 -my-3.5">
                        {[...Array(5)].map((__, cardIndex) => (
                            <div
                                key={cardIndex}
                                className="orion-shadow-frame flex-shrink-0 w-28 rounded-[1.6rem]"
                            >
                                <div className="orion-shadow-surface flex flex-col items-center gap-2 rounded-[inherit] bg-card p-3">
                                    <div className="w-12 h-12 rounded-2xl skeleton-shimmer" />
                                    <div className="flex flex-col gap-1 w-full items-center">
                                        <div className="h-2.5 w-4/5 rounded-full skeleton-shimmer" />
                                        <div className="h-2 w-3/5 rounded-full skeleton-shimmer" />
                                    </div>
                                    <div className="h-4 w-full rounded-full skeleton-shimmer mt-auto" />
                                </div>
                            </div>
                        ))}
                    </div>
                </section>
            ))}
        </div>
    </div>
);

const CurvedSixAppModule: React.FC<{
    collection: StoreCollection;
    onAppClick: (app: AppItem) => void;
}> = ({ collection, onAppClick }) => {
    const apps = (collection.apps || []).slice(0, 6);
    const motionEnabled = useMotionEnabled();
    if (apps.length === 0) return null;

    return (
        <section className="relative mb-8 flex flex-col gap-3" style={{ contentVisibility: 'auto', containIntrinsicSize: '0 400px' }}>
            <div className="px-4">
                <h2 className="text-lg font-black leading-none tracking-tight text-theme-text">
                    {collection.title}
                </h2>
            </div>
            <div className="flex snap-x snap-mandatory gap-3 overflow-x-auto -mx-3 px-4 py-2 no-scrollbar [scroll-padding-inline:1rem]">
                {apps.map((app, appIndex) => {
                    const hue = getHueFromSeed(`${app.id}-${app.name}-curated`);
                    return (
                        <motion.div
                            key={app.id}
                            initial={motionEnabled ? { opacity: 0, y: 14 } : undefined}
                            whileInView={motionEnabled ? { opacity: 1, y: 0 } : undefined}
                            viewport={{ once: true }}
                            transition={motionEnabled ? { delay: appIndex * 0.04 } : undefined}
                            whileHover={motionEnabled ? { y: -4, scale: 1.02 } : undefined}
                            whileTap={motionEnabled ? { scale: 0.97 } : undefined}
                            className="orion-shadow-frame h-[8.85rem] w-[8.65rem] shrink-0 snap-start rounded-[1.9rem]"
                        >
                            <button
                                type="button"
                                onClick={() => onAppClick(app)}
                                className="no-light-border orion-shadow-surface relative h-full w-full overflow-hidden rounded-[inherit] bg-card px-3 py-4 text-center"
                                style={{
                                    backgroundImage: `radial-gradient(110% 110% at 50% -10%, hsla(${hue}, 88%, 58%, 0.16), transparent 66%)`
                                }}
                            >
                                <div className="absolute inset-0 bg-gradient-to-br from-white/10 via-transparent to-transparent pointer-events-none" />
                                <div className="relative z-10 flex h-full flex-col items-center justify-center gap-3">
                                    <img
                                        src={getOptimizedImageUrl(app.icon, 112, 112)}
                                        alt={app.name}
                                        className="h-14 w-14 rounded-[1.15rem] object-contain"
                                        loading={appIndex < 2 ? 'eager' : 'lazy'}
                                        fetchPriority={appIndex < 2 ? 'high' : 'low'}
                                        decoding={appIndex < 2 ? 'sync' : 'async'}
                                        onError={(event) => {
                                            const target = event.currentTarget;
                                            if (target.dataset.fallback !== 'raw') {
                                                target.dataset.fallback = 'raw';
                                                target.src = app.icon;
                                            }
                                        }}
                                    />
                                    <p className="line-clamp-2 text-[12px] font-black leading-tight text-theme-text [text-wrap:balance]">
                                        {app.name}
                                    </p>
                                </div>
                            </button>
                        </motion.div>
                    );
                })}
            </div>
        </section>
    );
};

const CategoryCardsModule: React.FC<{
    collection: StoreCollection;
    onSelectCategory?: (category: string) => void;
}> = ({ collection, onSelectCategory }) => {
    const motionEnabled = useMotionEnabled();
    const cards = collection.categoryCards || [];
    if (cards.length === 0) return null;

    return (
        <section className="relative mb-6" style={{ contentVisibility: 'auto', containIntrinsicSize: '0 400px' }}>
            <div className="flex snap-x snap-mandatory gap-3 overflow-x-auto -mx-3 px-4 py-2 no-scrollbar [scroll-padding-inline:1rem]">
                {cards.map((card, index) => (
                    <CategoryCardButton
                        key={card.id}
                        card={card}
                        motionEnabled={motionEnabled}
                        index={index}
                        onSelectCategory={onSelectCategory}
                    />
                ))}
            </div>
        </section>
    );
};

const CategoryCardButton: React.FC<{
    card: StoreCategoryCard;
    motionEnabled: boolean;
    index: number;
    onSelectCategory?: (category: string) => void;
}> = ({ card, motionEnabled, index, onSelectCategory }) => (
    <motion.div
        initial={motionEnabled ? { opacity: 0, y: 14 } : undefined}
        whileInView={motionEnabled ? { opacity: 1, y: 0 } : undefined}
        viewport={{ once: true }}
        transition={motionEnabled ? { delay: index * 0.05 } : undefined}
        whileHover={motionEnabled ? { y: -4, scale: 1.01 } : undefined}
        whileTap={motionEnabled ? { scale: 0.97 } : undefined}
        className="orion-shadow-frame h-[9.6rem] w-[13.2rem] shrink-0 snap-start rounded-[1.9rem]"
    >
        <button
            type="button"
            onClick={() => onSelectCategory && onSelectCategory(card.label)}
            className="no-light-border orion-shadow-surface relative h-full w-full overflow-hidden rounded-[inherit] p-4 text-left text-white"
            style={{ background: card.gradient }}
        >
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(255,255,255,0.16),transparent_42%)]" />
            <div className="absolute -right-6 bottom-[-2.5rem] h-24 w-24 rounded-full border border-white/[0.05] bg-white/10 opacity-70" />
            <div className="relative z-10 flex h-full flex-col justify-between">
                <div className="flex items-start justify-between gap-3">
                    <div className="flex h-12 w-12 items-center justify-center rounded-2xl border border-white/[0.08] bg-black/5 text-lg">
                        <i className={`fas ${card.icon}`}></i>
                    </div>
                    <i className="fas fa-arrow-right text-sm text-white/70"></i>
                </div>

                <div>
                    <h3 className="text-2xl font-black tracking-tight text-white">{card.label}</h3>
                </div>
            </div>
        </button>
    </motion.div>
);

const getBundleCardBackground = (bundle: NonNullable<StoreCollection['bundles']>[number], index: number) => {
    if (bundle.color) {
        return `linear-gradient(140deg, ${bundle.color} 0%, rgba(15,23,42,0.96) 100%)`;
    }

    const palette = [
        'linear-gradient(140deg, #111827 0%, #2563eb 100%)',
        'linear-gradient(140deg, #1f2937 0%, #7c3aed 100%)',
        'linear-gradient(140deg, #172554 0%, #0f766e 100%)',
        'linear-gradient(140deg, #3f1d2e 0%, #ea580c 100%)'
    ];
    return palette[index % palette.length]!;
};

const BundleSignature: React.FC<{ bundle: NonNullable<StoreCollection['bundles']>[number] }> = ({ bundle }) => {
    const mark = bundle.monogram || bundle.title.trim().slice(0, 1).toUpperCase();
    const usesFontAwesomeIcon = typeof bundle.icon === 'string' && bundle.icon.includes('fa-');

    return (
        <div className="relative flex h-14 w-14 items-center justify-center overflow-hidden rounded-[1.35rem] border border-white/[0.08] bg-black/10 text-white">
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_left,rgba(255,255,255,0.18),transparent_55%)]" />
            {usesFontAwesomeIcon ? (
                <i className={`fas ${bundle.icon} relative z-10 text-lg`}></i>
            ) : (
                <span className="relative z-10 text-2xl font-black tracking-tight">
                    {(bundle.icon || mark).slice(0, 2).toUpperCase()}
                </span>
            )}
        </div>
    );
};

const RecommendationBundlesModule: React.FC<{
    collection: StoreCollection;
    onBundleClick?: (bundle: BundleItem) => void;
}> = ({ collection, onBundleClick }) => {
    const bundles = collection.bundles || [];
    const motionEnabled = useMotionEnabled();
    // Per-bundle customizations so the badge count matches what users will actually see in the preview.
    const removedBundleApps = useSettingsStore((s) => s.removedBundleApps);
    const extraBundleApps = useSettingsStore((s) => s.extraBundleApps);
    if (bundles.length === 0) return null;

    return (
        <section className="relative mb-8 flex flex-col gap-3" style={{ contentVisibility: 'auto', containIntrinsicSize: '0 400px' }}>
            <div className="px-4">
                <h2 className="text-lg font-black leading-none tracking-tight text-theme-text">
                    {collection.title}
                </h2>
            </div>

            <div className="flex snap-x snap-mandatory gap-3 overflow-x-auto -mx-3 px-4 py-2 no-scrollbar [scroll-padding-inline:1rem]">
                {bundles.map((bundle, index) => {
                    const baseApps = bundle.apps || [];
                    const removed = removedBundleApps[bundle.id] || [];
                    const extras = extraBundleApps[bundle.id] || [];
                    // Effective count = base apps minus locally-removed plus locally-added (all unique).
                    const visibleIds = new Set<string>();
                    baseApps.forEach((a) => { if (!removed.includes(a.id)) visibleIds.add(a.id); });
                    extras.forEach((id) => visibleIds.add(id));
                    const effectiveCount = visibleIds.size;
                    if (effectiveCount === 0) return null;

                    return (
                        <motion.div
                            key={bundle.id}
                            initial={motionEnabled ? { opacity: 0, y: 14 } : undefined}
                            whileInView={motionEnabled ? { opacity: 1, y: 0 } : undefined}
                            viewport={{ once: true }}
                            transition={motionEnabled ? { delay: index * 0.05 } : undefined}
                            whileHover={motionEnabled ? { y: -4, scale: 1.01 } : undefined}
                            whileTap={motionEnabled ? { scale: 0.97 } : undefined}
                            className="orion-shadow-frame h-[11.4rem] w-[14.85rem] shrink-0 snap-start rounded-[2rem]"
                        >
                            <button
                                type="button"
                                onClick={() => onBundleClick && onBundleClick(bundle)}
                                className="no-light-border orion-shadow-surface relative h-full w-full overflow-hidden rounded-[inherit] p-4 text-left text-white"
                                style={{ background: getBundleCardBackground(bundle, index) }}
                            >
                                <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(255,255,255,0.16),transparent_42%)]" />
                                <div className="relative z-10 flex h-full flex-col">
                                    <div className="flex items-start justify-between gap-3">
                                        <BundleSignature bundle={bundle} />
                                        {bundle.badge && (
                                            <span className="rounded-full border border-white/10 bg-white/10 px-2.5 py-1 text-[9px] font-black uppercase tracking-[0.18em] text-white/90">
                                                {bundle.badge}
                                            </span>
                                        )}
                                    </div>

                                    <div className="mt-4 min-w-0">
                                        <h3 className="text-xl font-black leading-tight tracking-tight text-white [text-wrap:balance]">
                                            {bundle.title}
                                        </h3>
                                    </div>

                                    <div className="mt-auto flex items-center justify-between gap-3">
                                        <span className="rounded-full border border-white/10 bg-white/10 px-3 py-1 text-[10px] font-black uppercase tracking-[0.18em] text-white">
                                            {effectiveCount} Apps Bundled
                                        </span>
                                        <span className="flex h-9 w-9 items-center justify-center rounded-full border border-white/10 bg-black/10 text-white/90">
                                            <i className="fas fa-arrow-right text-xs"></i>
                                        </span>
                                    </div>
                                </div>
                            </button>
                        </motion.div>
                    );
                })}

                <motion.button
                    type="button"
                    onClick={() => window.dispatchEvent(new Event('orion:open-custom-bundle'))}
                    initial={motionEnabled ? { opacity: 0, y: 14 } : undefined}
                    whileInView={motionEnabled ? { opacity: 1, y: 0 } : undefined}
                    viewport={{ once: true }}
                    whileTap={motionEnabled ? { scale: 0.97 } : undefined}
                    className="relative flex h-[11.4rem] w-[14.85rem] shrink-0 snap-start flex-col items-center justify-center gap-3 rounded-[2rem] border-2 border-dashed border-theme-border bg-theme-card/40 px-4 text-center text-theme-text transition-colors hover:border-theme-accent/60 hover:bg-theme-card/60"
                >
                    <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-theme-accent/10 text-theme-accent">
                        <i className="fas fa-plus text-lg"></i>
                    </span>
                    <span className="flex flex-col gap-1">
                        <span className="text-sm font-black tracking-tight">Create Your Bundle</span>
                        <span className="text-[10px] font-bold uppercase tracking-[0.18em] text-theme-sub">Submit to community</span>
                    </span>
                </motion.button>
            </div>
        </section>
    );
};

const SwimlaneRow: React.FC<{
    collection: StoreCollection;
    onAppClick: (app: AppItem) => void;
    onSeeAllCategory?: (category: string) => void;
    onShowCollectionAll?: (collection: StoreCollection) => void;
    index: number;
}> = ({ collection, onAppClick, onSeeAllCategory, onShowCollectionAll, index }) => {
    const compactMode = false; // Modern layout ignores global compact mode for its own cards
    const rowRef = useRef<HTMLDivElement>(null);
    const [isVisible, setIsVisible] = useState(false);
    const laneGapClass = compactMode ? 'gap-2 sm:gap-2.5' : 'gap-3';
    const lanePaddingClass = compactMode ? 'px-4 py-4 -my-2.5' : 'px-4 py-6 -my-3.5';
    const skeletonPaddingClass = compactMode ? 'px-4 py-4 -my-3' : 'px-4 py-6 -my-4';

    useEffect(() => {
        const observer = new IntersectionObserver(
            ([entry]) => {
                if (entry?.isIntersecting) {
                    setIsVisible(true);
                    observer.disconnect();
                }
            },
            { rootMargin: '100px' }
        );

        if (rowRef.current) observer.observe(rowRef.current);
        return () => observer.disconnect();
    }, []);

    if (!collection.apps || collection.apps.length === 0) return null;
    const laneId = getLaneId(collection.title || `lane-${index}`);
    const hasCollectionViewAll = !!collection.totalAppCount && collection.totalAppCount > collection.apps.length && !!onShowCollectionAll;

    return (
        <section
            ref={rowRef}
            id={laneId}
            className="relative mb-7 flex flex-col gap-2"
            style={{ contentVisibility: 'auto', containIntrinsicSize: '0 500px' }}
        >
            <div className="flex items-center justify-between px-4">
                <div className="flex cursor-default flex-col group/header">
                    <h2 className="text-lg font-black leading-none tracking-tight text-theme-text transition-colors duration-300 group-hover:text-primary">
                        {collection.title}
                    </h2>
                    <motion.div
                        initial={{ width: 0 }}
                        whileInView={{ width: '2.25rem' }}
                        className="mt-1 h-0.5 origin-left rounded-full bg-primary"
                    />
                </div>
                {collection.filter && (
                    <motion.button
                        whileTap={{ scale: 0.95 }}
                        onClick={() => onSeeAllCategory && collection.filter && onSeeAllCategory(collection.filter)}
                        className="rounded-full border border-theme-border/80 bg-theme-element/60 px-3 py-1 text-[9px] font-black uppercase tracking-widest text-primary transition-colors hover:border-primary hover:bg-primary hover:text-white"
                    >
                        See All
                    </motion.button>
                )}
                {hasCollectionViewAll && (
                    <motion.button
                        whileTap={{ scale: 0.95 }}
                        onClick={() => onShowCollectionAll(collection)}
                        className="rounded-full border border-theme-border/80 bg-theme-element/60 px-3 py-1 text-[9px] font-black uppercase tracking-widest text-primary transition-colors hover:border-primary hover:bg-primary hover:text-white"
                    >
                        View All
                    </motion.button>
                )}
            </div>

            {isVisible ? (
                <div className={`flex snap-x snap-mandatory overflow-x-auto -mx-3 no-scrollbar scroll-smooth [scroll-padding-inline:1rem] ${laneGapClass} ${lanePaddingClass}`}>
                    {collection.apps.map((app, i) => (
                        <CompactAppCard
                            key={app.id}
                            app={app}
                            index={i}
                            priority={i < 4}
                            onClick={() => onAppClick(app)}
                        />
                    ))}
                </div>
            ) : (
                <div className={`flex overflow-hidden -mx-3 ${laneGapClass} ${skeletonPaddingClass}`}>
                    {[...Array(5)].map((_, i) => (
                        <div key={i} className="flex-shrink-0 w-28 rounded-[1.6rem] orion-shadow-frame">
                            <div className="orion-shadow-surface flex flex-col items-center gap-2 rounded-[inherit] bg-card p-3">
                                <div className="w-12 h-12 rounded-2xl skeleton-shimmer" />
                                <div className="flex flex-col gap-1 w-full items-center">
                                    <div className="h-2.5 w-4/5 rounded-full skeleton-shimmer" />
                                    <div className="h-2 w-3/5 rounded-full skeleton-shimmer" />
                                </div>
                                <div className="h-4 w-full rounded-full skeleton-shimmer mt-auto" />
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </section>
    );
};

const HeroCarousel: React.FC<{
    collection: StoreCollection;
    onAppClick: (app: AppItem) => void;
}> = ({ collection, onAppClick }) => {
    if (!collection.apps || collection.apps.length === 0) return null;

    return (
        <div className="pointer-events-none relative z-20 mb-3 mt-1 w-full">
            <div className="pointer-events-auto flex snap-x snap-mandatory gap-3 overflow-x-auto px-4 py-6 -my-4 no-scrollbar [scroll-padding-inline:1rem]">
                {collection.apps.map((app, index) => (
                    <div
                        key={app.id}
                        className="group orion-shadow-frame orion-shadow-frame-strong relative h-[172px] w-[82vw] max-w-[19rem] flex-shrink-0 snap-start rounded-[1.75rem] sm:h-[208px] sm:w-[22rem] md:w-[24rem] lg:h-[224px] lg:w-[28rem] lg:max-w-[30rem]"
                    >
                        <div
                            onClick={() => onAppClick(app)}
                            className="no-light-border orion-shadow-surface relative h-full w-full cursor-pointer overflow-hidden rounded-[inherit] bg-card"
                        >
                            <div className="absolute inset-0 rounded-[inherit] opacity-80" style={{ background: getHeroGlow(app) }} />
                            <div className="absolute inset-0 overflow-hidden rounded-[inherit]">
                                <img
                                    src={getOptimizedImageUrl(app.screenshots?.[0] || app.icon, 1200, 720)}
                                    alt={app.name}
                                    className="h-full w-full object-cover opacity-45 transition-transform duration-700 ease-out group-hover:scale-105"
                                    loading={index === 0 ? 'eager' : 'lazy'}
                                    fetchPriority={index === 0 ? 'high' : 'low'}
                                    decoding="async"
                                    onError={(e) => {
                                        const target = e.target as HTMLImageElement;
                                        const fallback = getOptimizedImageUrl(app.icon, 256, 256);
                                        if (target.dataset.fallback !== 'icon-proxy') {
                                            target.dataset.fallback = 'icon-proxy';
                                            target.src = fallback;
                                            return;
                                        }
                                        target.dataset.fallback = 'raw';
                                        target.src = app.screenshots?.[0] || app.icon;
                                    }}
                                />
                                <div className="absolute inset-0 bg-gradient-to-t from-black/95 via-black/55 to-transparent" />
                            </div>

                            <div className="absolute inset-0 z-10 flex flex-col justify-end p-4 sm:p-5">
                                <div className="flex items-end gap-3">
                                    <img
                                        src={getOptimizedImageUrl(app.icon, 112, 112)}
                                        alt={app.name}
                                        className="h-12 w-12 shrink-0 rounded-[1rem] object-contain sm:h-14 sm:w-14"
                                        loading={index < 2 ? 'eager' : 'lazy'}
                                        fetchPriority={index < 2 ? 'high' : 'low'}
                                        decoding={index < 2 ? 'sync' : 'async'}
                                        onError={(event) => {
                                            const target = event.currentTarget;
                                            if (target.dataset.fallback !== 'raw') {
                                                target.dataset.fallback = 'raw';
                                                target.src = app.icon;
                                            }
                                        }}
                                    />
                                    <div className="flex min-w-0 flex-1 flex-col">
                                        <span className="mb-0.5 truncate text-[9px] font-bold uppercase tracking-[0.18em] text-white/70 sm:text-[10px]">{app.category}</span>
                                        <h3 className="truncate text-lg font-black leading-tight text-white sm:text-xl">{app.name}</h3>
                                        <p className="truncate text-[11px] font-semibold text-white/80 sm:text-xs">{app.author}</p>
                                    </div>
                                </div>

                                <div className="mt-3 flex items-center justify-between gap-3">
                                    <p className="line-clamp-1 min-w-0 flex-1 text-[10px] text-white/65 sm:text-xs">{app.description}</p>
                                    <button
                                        onClick={(e) => { e.stopPropagation(); onAppClick(app); }}
                                        className="shrink-0 rounded-full bg-white px-4 py-2 text-xs font-bold text-black shadow-lg transition-colors hover:bg-white/90"
                                    >
                                        Get
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
};

const SortedGridPreview: React.FC<{
    collection: StoreCollection;
    onAppClick: (app: AppItem) => void;
    onShowAll?: () => void;
}> = ({ collection, onAppClick, onShowAll }) => {
    const apps = collection.apps || [];
    const total = collection.totalAppCount || apps.length;
    const motionEnabled = useMotionEnabled();
    const compactMode = false;
    const gridGapClass = compactMode ? 'gap-1.5 sm:gap-2' : 'gap-2.5 sm:gap-3';

    if (apps.length === 0) return null;

    return (
        <section className="relative mb-8 flex flex-col gap-3" style={{ contentVisibility: 'auto', containIntrinsicSize: '0 400px' }}>
            <div className="px-4">
                <div className="flex items-center justify-between">
                    <h2 className="text-lg font-black leading-none tracking-tight text-theme-text">
                        {collection.title}
                    </h2>
                    <span className="text-[10px] font-bold text-theme-sub">{total} apps</span>
                </div>
            </div>

            <div className="px-4">
                <div className={`grid grid-cols-3 ${gridGapClass}`}>
                    <div className="contents">
                    {apps.map((app, i) => (
                        <motion.div
                            key={app.id}
                            initial={motionEnabled ? { opacity: 0, y: 14 } : undefined}
                            whileInView={motionEnabled ? { opacity: 1, y: 0 } : undefined}
                            viewport={{ once: true }}
                            transition={motionEnabled ? { delay: i * 0.03 } : undefined}
                            whileTap={motionEnabled ? { scale: 0.97 } : undefined}
                            className="flex justify-center"
                        >
                            <div className="w-full min-w-0">
                                <CompactAppCard
                                    app={app}
                                    index={i}
                                    priority={i < 9}
                                    className="w-full"
                                    onClick={() => onAppClick(app)}
                                />
                            </div>
                        </motion.div>
                    ))}
                    </div>
                </div>

                {total > apps.length && onShowAll && (
                    <motion.button
                        whileTap={{ scale: 0.97 }}
                        onClick={onShowAll}
                        className="mt-4 w-full py-3 rounded-2xl bg-theme-element text-theme-text font-bold text-sm transition-all hover:bg-theme-hover flex items-center justify-center gap-2"
                    >
                        <span>Show All {total} Apps</span>
                        <i className="fas fa-arrow-down text-[10px]"></i>
                    </motion.button>
                )}
            </div>
        </section>
    );
};

const ModernAppList: React.FC<ModernAppListProps> = ({
    collections,
    onAppClick,
    onSeeAllCategory,
    onBundleClick,
    onShowAll,
    onShowCollectionAll
}) => {
    const heroCollection = collections.find((collection) => collection.type === 'hero');
    const bodyCollections = collections.filter((collection) => collection.type !== 'hero');

    useEffect(() => {
        const prefetchUrls = new Set<string>();

        collections.forEach((collection) => {
            if (!collection.apps || collection.apps.length === 0) return;

            const priorityApps = collection.type === 'hero'
                ? collection.apps.slice(0, 2)
                : collection.apps.slice(0, collection.type === 'sorted_grid' ? 12 : 6);

            priorityApps.forEach((app) => {
                prefetchUrls.add(getOptimizedImageUrl(app.icon, 96, 96));
                if (collection.type === 'hero') {
                    prefetchUrls.add(getOptimizedImageUrl(app.screenshots?.[0] || app.icon, 1200, 720));
                    prefetchUrls.add(getOptimizedImageUrl(app.icon, 112, 112));
                }
            });
        });

        const urls = Array.from(prefetchUrls).slice(0, 24);
        const idleId = window.requestIdleCallback
            ? window.requestIdleCallback(() => urls.forEach((url) => prefetchImage(url)), { timeout: 1800 })
            : window.setTimeout(() => urls.forEach((url) => prefetchImage(url)), 700);

        return () => {
            if (typeof idleId === 'number') window.clearTimeout(idleId);
            else window.cancelIdleCallback?.(idleId);
        };
    }, [collections]);

    return (
        <div className="relative flex w-full flex-col animate-fade-in pb-12 lg:pb-16">
            {heroCollection && <HeroCarousel collection={heroCollection} onAppClick={onAppClick} />}

            <div className="relative z-10 mt-1 lg:mx-auto lg:w-full lg:max-w-[78rem]">
                {bodyCollections.map((collection, index) => {
                    if (collection.type === 'bundle') {
                        return <CurvedSixAppModule key={collection.id} collection={collection} onAppClick={onAppClick} />;
                    }

                    if (collection.type === 'category_cards') {
                        return (
                            <CategoryCardsModule
                                key={collection.id}
                                collection={collection}
                                onSelectCategory={onSeeAllCategory}
                            />
                        );
                    }

                    if (collection.type === 'recommendation_bundles') {
                        return (
                            <RecommendationBundlesModule
                                key={collection.id}
                                collection={collection}
                                onBundleClick={onBundleClick}
                            />
                        );
                    }

                    if (collection.type === 'sorted_grid') {
                        return (
                            <SortedGridPreview
                                key={collection.id}
                                collection={collection}
                                onAppClick={onAppClick}
                                onShowAll={onShowAll}
                            />
                        );
                    }

                    if (collection.type === 'swimlane' || collection.type === 'auto_category') {
                        return (
                            <SwimlaneRow
                                key={collection.id}
                                collection={collection}
                                onAppClick={onAppClick}
                                onSeeAllCategory={onSeeAllCategory}
                                onShowCollectionAll={onShowCollectionAll}
                                index={index}
                            />
                        );
                    }

                    return null;
                })}
            </div>
        </div>
    );
};

export default React.memo(ModernAppList);
