import React, { useMemo, useState, useCallback, useRef, useDeferredValue, useEffect } from 'react';
import { BundleItem, AppItem } from '../types';
import { getOptimizedImageUrl } from '../utils/image';
import { useSettingsStore } from '../store/useAppStore';

interface BundlePreviewModalProps {
  bundle: BundleItem;
  onClose: () => void;
  onOpenApp: (app: AppItem) => void;
  onDownloadAll: (bundle: BundleItem) => void;
  allApps?: AppItem[];
  platform?: string;
}

const isFontAwesomeIcon = (icon?: string) => !!icon && icon.includes('fa-');

const normalizePackageName = (name?: string): string => (name || '').trim().toLowerCase();

const BundleAppRow: React.FC<{
  app: AppItem;
  isInstalled: boolean;
  isExtra: boolean;
  showSwipeHint?: boolean;
  onOpenApp: (app: AppItem) => void;
  onRemove: () => void;
}> = ({ app, isInstalled, isExtra, showSwipeHint, onOpenApp, onRemove }) => {
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

  // Auto-hint: briefly slide first row to teach swipe gesture
  React.useEffect(() => {
    if (!showSwipeHint) return;
    const t1 = setTimeout(() => { setIsAnimating(true); setDragX(56); }, 420);
    const t2 = setTimeout(() => { setDragX(0); }, 1180);
    const t3 = setTimeout(() => { setIsAnimating(false); }, 1500);
    return () => { clearTimeout(t1); clearTimeout(t2); clearTimeout(t3); };
  }, [showSwipeHint]);

  React.useEffect(() => () => {
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
    // Lock axis on first significant movement so vertical scroll isn't hijacked
    if (axis.current === null) {
      if (Math.abs(dx) < 6 && Math.abs(dy) < 6) return;
      axis.current = Math.abs(dx) > Math.abs(dy) ? 'h' : 'v';
    }
    if (axis.current === 'v') return;
    // Allow swipe-back: clamp to [0, 160] but compute from current base
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
      {/* Action backdrop revealed during swipe — clean rounded card behind row */}
      <div
        className="absolute inset-0 flex items-center justify-start pl-5 rounded-2xl pointer-events-none"
        style={{
          background: 'linear-gradient(90deg, rgba(239,68,68,0.92), rgba(239,68,68,0.0))',
          opacity: revealOpacity,
          transition: dragX === 0 ? 'opacity 200ms ease-out' : 'none',
        }}
      >
        <span className="inline-flex items-center gap-1.5 text-white text-[10px] font-black uppercase tracking-[0.18em]">
          <i className="fas fa-trash"></i>
          Remove
        </span>
      </div>
      <button
        type="button"
        onClick={(e) => { if (isDragging.current && Math.abs(dragX) > 4) { e.preventDefault(); return; } onOpenApp(app); }}
        onTouchStart={(e) => handleStart(e.touches[0]!.clientX, e.touches[0]!.clientY)}
        onTouchMove={(e) => handleMove(e.touches[0]!.clientX, e.touches[0]!.clientY)}
        onTouchEnd={handleEnd}
        onTouchCancel={handleEnd}
        onMouseDown={(e) => handleStart(e.clientX, e.clientY)}
        onMouseMove={(e) => { if (startX.current !== null && e.buttons === 1) handleMove(e.clientX, e.clientY); }}
        onMouseUp={handleEnd}
        onMouseLeave={() => { if (startX.current !== null) handleEnd(); }}
        className="relative flex w-full items-center gap-3 rounded-2xl bg-card px-3 py-3 text-left hover:bg-theme-element/80"
        style={{
          transform: `translate3d(${dragX}px,0,0)`,
          transition: (axis.current === null && (dragX === 0 || isAnimating)) ? 'transform 280ms cubic-bezier(0.2, 0.8, 0.2, 1)' : 'none',
          willChange: 'transform',
          touchAction: 'pan-y',
        }}
      >
        <img
          src={getOptimizedImageUrl(app.icon, 96, 96)}
          alt={app.name}
          className="h-11 w-11 shrink-0 rounded-[1rem] object-contain"
          loading="lazy"
          decoding="async"
        />
        <div className="min-w-0 flex-1">
          <span className="block truncate text-sm font-black text-theme-text">
            {app.name}
          </span>
          <div className="flex items-center gap-1.5 mt-0.5">
            <span className="truncate text-[10px] font-bold uppercase tracking-[0.18em] text-theme-sub">
              {app.category}
            </span>
            {isInstalled && (
              <span className="shrink-0 inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-emerald-500/15 text-emerald-500 text-[9px] font-black uppercase tracking-wider">
                <i className="fas fa-check text-[8px]"></i> Installed
              </span>
            )}
            {isExtra && (
              <span className="shrink-0 inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-primary/15 text-primary text-[9px] font-black uppercase tracking-wider">
                <i className="fas fa-plus text-[8px]"></i> Added
              </span>
            )}
          </div>
        </div>
        <i className="fas fa-chevron-right text-xs text-theme-sub/70"></i>
      </button>
    </div>
  );
};

const BundlePreviewModal: React.FC<BundlePreviewModalProps> = ({
  bundle,
  onClose,
  onOpenApp,
  onDownloadAll,
  allApps = [],
  platform
}) => {
  const installedVersions = useSettingsStore((s) => s.installedVersions);
  const removedBundleApps = useSettingsStore((s) => s.removedBundleApps);
  const extraBundleApps = useSettingsStore((s) => s.extraBundleApps);
  const toggleBundleAppRemoval = useSettingsStore((s) => s.toggleBundleAppRemoval);
  const addAppToBundle = useSettingsStore((s) => s.addAppToBundle);
  const removeAppFromBundleExtras = useSettingsStore((s) => s.removeAppFromBundleExtras);
  const resetBundleCustomizations = useSettingsStore((s) => s.resetBundleCustomizations);
  const removeCustomBundle = useSettingsStore((s) => s.removeCustomBundle);

  const removedIds = removedBundleApps[bundle.id] || [];
  const extraIds = extraBundleApps[bundle.id] || [];

  const apps: AppItem[] = useMemo(() => {
    const base = (bundle.apps || []).filter((a) => !removedIds.includes(a.id));
    const extras = extraIds
      .map((id) => allApps.find((a) => a.id === id))
      .filter((a): a is AppItem => !!a);
    // Dedupe by id
    const seen = new Set<string>();
    return [...base, ...extras].filter((a) => {
      if (seen.has(a.id)) return false;
      seen.add(a.id);
      return true;
    });
  }, [bundle.apps, bundle.id, removedIds, extraIds, allApps]);

  const isInstalled = useCallback((app: AppItem) => {
    const pkg = normalizePackageName(app.packageName);
    return !!(pkg && installedVersions[pkg]);
  }, [installedVersions]);

  const [showAddPicker, setShowAddPicker] = useState(false);
  const [addSearch, setAddSearch] = useState('');
  const deferredAddSearch = useDeferredValue(addSearch);
  const [addCategory, setAddCategory] = useState<string>('all');
  const ADD_PAGE_SIZE = 20;
  const [addVisibleCount, setAddVisibleCount] = useState(ADD_PAGE_SIZE);

  // Reset search/category/pagination when picker opens
  useEffect(() => {
    if (showAddPicker) {
      setAddSearch('');
      setAddCategory('all');
      setAddVisibleCount(ADD_PAGE_SIZE);
    }
  }, [showAddPicker]);

  // STRICT platform filter for candidate apps - only show apps matching the current tab's platform
  const candidatePoolFull = useMemo(() => {
    if (!showAddPicker) return [];
    const presentIds = new Set(apps.map((a) => a.id));
    let pool = allApps.filter((a) => !presentIds.has(a.id));
    if (platform) {
      const target = String(platform).trim().toLowerCase();
      pool = pool.filter((a) => {
        const p = (a.platform ? String(a.platform) : '').trim().toLowerCase();
        return p === target;
      });
    }
    return pool;
  }, [showAddPicker, apps, allApps, platform]);

  // Category options from the candidate pool
  const addCategoryOptions = useMemo(() => {
    const counts = new Map<string, number>();
    for (const a of candidatePoolFull) {
      const c = (a.category || 'Other').trim();
      counts.set(c, (counts.get(c) || 0) + 1);
    }
    const entries = Array.from(counts.entries()).sort((a, b) => a[0].localeCompare(b[0]));
    return [{ id: 'all', label: 'All', count: candidatePoolFull.length }, ...entries.map(([label, count]) => ({ id: label, label, count }))];
  }, [candidatePoolFull]);

  // Filtered candidate pool: category → search → sort
  const filteredCandidates = useMemo(() => {
    let base = candidatePoolFull;
    if (addCategory !== 'all') {
      base = base.filter((a) => (a.category || 'Other').trim() === addCategory);
    }
    const q = deferredAddSearch.trim().toLowerCase();
    if (q) {
      base = base.filter((a) =>
        a.name.toLowerCase().includes(q) ||
        (a.category || '').toLowerCase().includes(q) ||
        (a.author || '').toLowerCase().includes(q)
      );
    }
    return [...base].sort((a, b) => a.name.localeCompare(b.name));
  }, [candidatePoolFull, addCategory, deferredAddSearch]);

  // Reset visible count when filters change
  useEffect(() => {
    setAddVisibleCount(ADD_PAGE_SIZE);
  }, [addCategory, deferredAddSearch]);

  const visibleCandidates = useMemo(() => filteredCandidates.slice(0, addVisibleCount), [filteredCandidates, addVisibleCount]);
  const hasMoreCandidates = addVisibleCount < filteredCandidates.length;

  const isCustomized = removedIds.length > 0 || extraIds.length > 0;

  const signature = bundle.monogram || bundle.title.trim().slice(0, 1).toUpperCase() || 'B';

  return (
    <div
      className="backdrop-scrim fixed inset-0 z-[95] flex items-end justify-center bg-black/35 px-3 pb-3 pt-8 backdrop-blur-sm animate-fade-in sm:items-center sm:p-6"
      onClick={onClose}
    >
      <div
        className="relative flex max-h-[88vh] w-full max-w-md flex-col overflow-hidden rounded-[2.35rem] bg-surface shadow-2xl animate-slide-up"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="absolute inset-x-0 top-0 h-48 pointer-events-none">
          <div
            className="absolute inset-x-6 top-6 h-28 rounded-full blur-3xl opacity-80"
            style={{ background: bundle.color || 'linear-gradient(135deg, rgba(99,102,241,0.35), rgba(236,72,153,0.22))' }}
          />
        </div>

        <div className="relative z-10 flex items-center justify-between px-5 pb-3 pt-[calc(1rem+env(safe-area-inset-top))]">
          <button
            type="button"
            onClick={onClose}
            className="flex h-10 w-10 items-center justify-center rounded-full bg-theme-element/80 text-theme-text transition-colors hover:bg-theme-hover"
          >
            <i className="fas fa-arrow-left"></i>
          </button>
          <button
            type="button"
            onClick={() => {
              // Only download apps that are not already installed
              const appsToDownload = apps.filter((app) => !isInstalled(app));
              if (appsToDownload.length === 0) return; // nothing to download
              onDownloadAll({ ...bundle, apps: appsToDownload });
            }}
            className="inline-flex items-center gap-2 rounded-full bg-primary px-4 py-2 text-xs font-black uppercase tracking-[0.2em] text-white shadow-lg shadow-primary/20 transition-transform active:scale-[0.97]"
          >
            <i className="fas fa-download"></i>
            <span>Download</span>
          </button>
        </div>

        <div className="relative z-10 px-5 pb-4">
          <div className="flex items-start gap-4">
            <div
              className="flex h-16 w-16 shrink-0 items-center justify-center rounded-[1.7rem] text-white shadow-[0 14px 36px rgba(15,23,42,0.2)]"
              style={{ background: bundle.color || 'linear-gradient(135deg, #111827 0%, #4f46e5 100%)' }}
            >
              {isFontAwesomeIcon(bundle.icon) ? (
                <i className={`fas ${bundle.icon} text-xl`}></i>
              ) : (
                <span className="text-[1.7rem] font-black tracking-tight">
                  {(bundle.icon || signature).slice(0, 2).toUpperCase()}
                </span>
              )}
            </div>
            <div className="min-w-0 flex-1 pt-1">
              <p className="text-[10px] font-black uppercase tracking-[0.22em] text-primary/80">
                Recommended Bundle
              </p>
              <h2 className="mt-1 text-2xl font-black tracking-tight text-theme-text">
                {bundle.title}
              </h2>
              <p className="mt-2 text-sm text-theme-sub">
                {apps.length} app{apps.length === 1 ? '' : 's'}
                {bundle.badge ? ` - ${bundle.badge}` : ''}
              </p>
            </div>
          </div>
        </div>

        <div className="relative z-10 flex-1 overflow-y-auto px-4 pb-[calc(env(safe-area-inset-bottom)+1rem)] no-scrollbar">
          {/* Customization toolbar */}
          <div className="flex items-center justify-end gap-2 mb-3 px-1">
            <div className="flex items-center gap-2">
              {bundle.badge === 'Local' && (
                <button
                  type="button"
                  onClick={() => {
                    if (confirm('Delete this bundle?')) {
                      removeCustomBundle(bundle.id);
                      onClose();
                    }
                  }}
                  className="text-[10px] font-bold uppercase tracking-wider text-red-500 px-2.5 py-1 rounded-full bg-red-500/10 hover:bg-red-500/20 transition-colors mr-1"
                >
                  <i className="fas fa-trash-alt mr-1"></i>Delete
                </button>
              )}
              {isCustomized && (
                <button
                  type="button"
                  onClick={() => resetBundleCustomizations(bundle.id)}
                  className="text-[10px] font-bold uppercase tracking-wider text-theme-sub hover:text-theme-text px-2 py-1 rounded-full bg-theme-element/60 transition-colors"
                >
                  <i className="fas fa-rotate-left mr-1"></i>Reset
                </button>
              )}
              <button
                type="button"
                onClick={() => setShowAddPicker(true)}
                className="text-[10px] font-black uppercase tracking-wider text-primary px-2.5 py-1 rounded-full bg-primary/10 hover:bg-primary/20 transition-colors"
              >
                <i className="fas fa-plus mr-1"></i>Add app
              </button>
            </div>
          </div>

          <div className="space-y-2 pb-2">
            {apps.map((app, idx) => (
              <BundleAppRow
                key={app.id}
                app={app}
                isInstalled={isInstalled(app)}
                isExtra={extraIds.includes(app.id)}
                showSwipeHint={idx === 0}
                onOpenApp={onOpenApp}
                onRemove={() => {
                  if (extraIds.includes(app.id)) {
                    removeAppFromBundleExtras(bundle.id, app.id);
                  } else {
                    toggleBundleAppRemoval(bundle.id, app.id);
                  }
                }}
              />
            ))}
            {apps.length === 0 && (
              <div className="text-center py-12 text-theme-sub text-sm">
                <i className="fas fa-box-open text-3xl mb-3 opacity-50"></i>
                <p className="font-bold">Bundle is empty</p>
                <p className="text-[11px] mt-1">Add apps with the button above.</p>
              </div>
            )}
          </div>
        </div>

        {/* Add-app sheet */}
        {showAddPicker && (
          <div className="absolute inset-0 z-30 flex flex-col bg-surface/95 backdrop-blur-md animate-fade-in">
            <div className="flex items-center justify-between px-5 pt-4 pb-3 border-b border-theme-border">
              <h3 className="text-lg font-black tracking-tight">Add to bundle</h3>
              <button
                type="button"
                onClick={() => setShowAddPicker(false)}
                className="flex h-9 w-9 items-center justify-center rounded-full bg-theme-element text-theme-text hover:bg-theme-hover"
              >
                <i className="fas fa-times text-sm"></i>
              </button>
            </div>

            {/* Search bar */}
            <div className="px-4 pt-3 pb-1 bg-surface">
              <div className="relative">
                <i className="fas fa-search absolute left-3.5 top-1/2 -translate-y-1/2 text-xs text-theme-sub pointer-events-none"></i>
                <input
                  type="text"
                  value={addSearch}
                  onChange={(e) => setAddSearch(e.target.value)}
                  placeholder={`Search ${platform ? String(platform) + ' ' : ''}apps...`}
                  className="w-full rounded-2xl bg-card border border-theme-border pl-9 pr-9 py-2.5 text-sm font-medium text-theme-text placeholder:text-theme-sub focus:outline-none focus:border-primary transition-colors"
                />
                {addSearch && (
                  <button
                    type="button"
                    onClick={() => setAddSearch('')}
                    aria-label="Clear search"
                    className="absolute right-2.5 top-1/2 -translate-y-1/2 h-6 w-6 rounded-full bg-theme-element flex items-center justify-center text-theme-sub hover:text-theme-text transition-colors"
                  >
                    <i className="fas fa-times text-[10px]"></i>
                  </button>
                )}
              </div>

              {/* Category pills */}
              <div className="mt-2.5 flex gap-1.5 overflow-x-auto no-scrollbar -mx-1 px-1 pb-0.5">
                {addCategoryOptions.map((opt) => {
                  const active = addCategory === opt.id;
                  return (
                    <button
                      key={opt.id}
                      type="button"
                      onClick={() => setAddCategory(opt.id)}
                      className={`shrink-0 rounded-full px-3 py-1.5 text-[11px] font-black uppercase tracking-[0.12em] transition-all ${active ? 'bg-primary text-white shadow-md shadow-primary/20' : 'bg-card border border-theme-border text-theme-sub hover:text-theme-text'}`}
                    >
                      {opt.label}
                      <span className={`ml-1.5 text-[9px] tabular-nums ${active ? 'text-white/80' : 'text-theme-sub/70'}`}>{opt.count}</span>
                    </button>
                  );
                })}
              </div>

              <p className="text-[10px] font-bold uppercase tracking-wider text-theme-sub px-1 mt-2">
                Showing {visibleCandidates.length}/{filteredCandidates.length}
              </p>
            </div>

            <div className="flex-1 overflow-y-auto px-3 py-3 no-scrollbar">
              {filteredCandidates.length === 0 ? (
                <p className="text-center text-theme-sub text-sm py-8">No apps match.</p>
              ) : (
                <div className="space-y-1.5" style={{ contain: 'content' }}>
                  {visibleCandidates.map((app) => (
                    <button
                      key={app.id}
                      type="button"
                      onClick={() => { addAppToBundle(bundle.id, app.id); setShowAddPicker(false); }}
                      className="flex w-full items-center gap-3 rounded-2xl bg-card px-3 py-2.5 text-left hover:bg-theme-element/80 active:scale-[0.985] transition-all"
                      style={{ contentVisibility: 'auto', containIntrinsicSize: '0 60px' }}
                    >
                      <img
                        src={getOptimizedImageUrl(app.icon, 80, 80)}
                        alt={app.name}
                        className="h-10 w-10 shrink-0 rounded-xl object-contain"
                        loading="lazy"
                        decoding="async"
                      />
                      <div className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-bold text-theme-text">{app.name}</span>
                        <span className="block truncate text-[10px] uppercase tracking-wider text-theme-sub">{app.category}</span>
                      </div>
                      <div className="h-6 w-6 rounded-full flex items-center justify-center shrink-0 bg-primary/10 text-primary">
                        <i className="fas fa-plus text-[10px]"></i>
                      </div>
                    </button>
                  ))}

                  {hasMoreCandidates && (
                    <button
                      type="button"
                      onClick={() => setAddVisibleCount((c) => Math.min(c + ADD_PAGE_SIZE, filteredCandidates.length))}
                      className="w-full rounded-2xl bg-theme-element border border-theme-border py-3 text-[11px] font-black uppercase tracking-[0.18em] text-theme-text hover:bg-theme-hover transition-colors active:scale-[0.98]"
                    >
                      <i className="fas fa-plus-circle mr-1.5"></i>
                      Load {Math.min(ADD_PAGE_SIZE, filteredCandidates.length - addVisibleCount)} more
                      <span className="ml-1.5 text-[9px] text-theme-sub">({filteredCandidates.length - addVisibleCount} left)</span>
                    </button>
                  )}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default BundlePreviewModal;
