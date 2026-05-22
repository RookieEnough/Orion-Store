import React, { useState, useMemo, useDeferredValue, useEffect } from 'react';
import { AppItem, Platform } from '../types';
import { useScrollLock } from '../hooks/useScrollLock';
import { useSettingsStore } from '../store/useAppStore';
import { getOptimizedImageUrl } from '../utils/image';

const PAGE_SIZE = 20;

interface CustomBundleModalProps {
  onClose: () => void;
  allApps: AppItem[];
  editingBundleId?: string;
  platform?: Platform;
}

const ICON_CHOICES: { id: string; label: string }[] = [
  { id: 'fa-layer-group', label: 'Layers' },
  { id: 'fa-cubes', label: 'Cubes' },
  { id: 'fa-rocket', label: 'Rocket' },
  { id: 'fa-bolt', label: 'Bolt' },
  { id: 'fa-star', label: 'Star' },
  { id: 'fa-heart', label: 'Heart' },
  { id: 'fa-gamepad', label: 'Gaming' },
  { id: 'fa-music', label: 'Music' },
  { id: 'fa-palette', label: 'Creative' },
  { id: 'fa-shield-halved', label: 'Privacy' },
  { id: 'fa-graduation-cap', label: 'Learn' },
  { id: 'fa-briefcase', label: 'Work' },
];

const COLOR_CHOICES: string[] = [
  'linear-gradient(135deg, #6366f1, #ec4899)',
  'linear-gradient(135deg, #0ea5e9, #6366f1)',
  'linear-gradient(135deg, #10b981, #0ea5e9)',
  'linear-gradient(135deg, #f59e0b, #ef4444)',
  'linear-gradient(135deg, #8b5cf6, #ec4899)',
  'linear-gradient(135deg, #111827, #4f46e5)',
];

const buildIssueBody = (params: { title: string; description: string; icon: string; color: string; appIds: string[]; allApps: AppItem[]; submitter?: string; }) => {
  const { title, description, icon, color, appIds, allApps, submitter } = params;
  const appLines = appIds.map((id) => {
    const app = allApps.find((a) => a.id === id);
    if (!app) return `- ${id}`;
    return `- **${app.name}** (\`${app.id}\`)${app.packageName ? ` — \`${app.packageName}\`` : ''}`;
  }).join('\n');
  return [
    '## Custom Bundle Submission',
    '',
    `**Title:** ${title}`,
    `**Description:** ${description}`,
    `**Icon:** \`${icon}\``,
    `**Color:** \`${color}\``,
    submitter ? `**Submitted by:** ${submitter}` : '',
    '',
    `### Apps (${appIds.length})`,
    appLines,
    '',
    '---',
    '_Submitted via Orion Store Custom Bundle Creator. Add the `approve` label to publish._',
  ].filter(Boolean).join('\n');
};

const CustomBundleModal: React.FC<CustomBundleModalProps> = ({ onClose, allApps, editingBundleId, platform }) => {
  useScrollLock(true);
  const customBundles = useSettingsStore((s) => s.customBundles);
  const addCustomBundle = useSettingsStore((s) => s.addCustomBundle);
  const updateCustomBundle = useSettingsStore((s) => s.updateCustomBundle);
  const userProfile = useSettingsStore((s) => s.userProfile);

  const editing = useMemo(() => customBundles.find((b) => b.id === editingBundleId) || null, [customBundles, editingBundleId]);

  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [title, setTitle] = useState(editing?.title || '');
  const [description, setDescription] = useState(editing?.description || '');
  const [icon, setIcon] = useState(editing?.icon || ICON_CHOICES[0]!.id);
  const [color, setColor] = useState(COLOR_CHOICES[0]!);
  const [selectedIds, setSelectedIds] = useState<string[]>(editing?.appIds || []);
  const [search, setSearch] = useState('');
  const deferredSearch = useDeferredValue(search);
  const [activeCategory, setActiveCategory] = useState<string>('all');
  const [visibleCount, setVisibleCount] = useState<number>(PAGE_SIZE);
  const [submitting, setSubmitting] = useState(false);
  const [savedLocallyMsg, setSavedLocallyMsg] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // STRICT platform filter — only apps matching the current tab's platform are
  // included. We do NOT fall back to "selected" apps because if a user is on
  // the Android tab, they should never see PC or TV apps even if previously
  // selected. Platform comparison is normalized (case-insensitive, trimmed) to
  // tolerate any data-source inconsistencies in `app.platform`.
  const platformApps = useMemo(() => {
    if (!platform) return allApps;
    const target = String(platform).trim().toLowerCase();
    return allApps.filter((a) => {
      const p = (a.platform ? String(a.platform) : '').trim().toLowerCase();
      return p === target;
    });
  }, [allApps, platform]);

  // Sorted unique category list for pill filters. Sorted alphabetically with
  // "all" pinned to the front. Counts come from the platform-scoped pool.
  const categoryOptions = useMemo(() => {
    const counts = new Map<string, number>();
    for (const a of platformApps) {
      const c = (a.category || 'Other').trim();
      counts.set(c, (counts.get(c) || 0) + 1);
    }
    const entries = Array.from(counts.entries()).sort((a, b) => a[0].localeCompare(b[0]));
    return [{ id: 'all', label: 'All', count: platformApps.length }, ...entries.map(([label, count]) => ({ id: label, label, count }))];
  }, [platformApps]);

  // Filter pipeline: platform → category → search. Sorted alphabetically by
  // name so "load more" always reveals predictable results.
  const filteredApps = useMemo(() => {
    let base = platformApps;
    if (activeCategory !== 'all') {
      base = base.filter((a) => (a.category || 'Other').trim() === activeCategory);
    }
    const q = deferredSearch.trim().toLowerCase();
    if (q) {
      base = base.filter((a) =>
        a.name.toLowerCase().includes(q) ||
        (a.category || '').toLowerCase().includes(q) ||
        (a.author || '').toLowerCase().includes(q)
      );
    }
    return [...base].sort((a, b) => a.name.localeCompare(b.name));
  }, [platformApps, activeCategory, deferredSearch]);

  // Reset visible window whenever filters change so users always see the top
  // of the freshly filtered list.
  useEffect(() => {
    setVisibleCount(PAGE_SIZE);
  }, [activeCategory, deferredSearch, platform]);

  const visibleApps = useMemo(() => filteredApps.slice(0, visibleCount), [filteredApps, visibleCount]);
  const hasMore = visibleCount < filteredApps.length;

  const toggleApp = (id: string) => {
    setSelectedIds((prev) => prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]);
  };

  const canProceed = (() => {
    if (step === 1) return title.trim().length >= 3 && description.trim().length >= 8;
    if (step === 2) return selectedIds.length >= 2 && selectedIds.length <= 24;
    return true;
  })();

  const handleSaveLocal = () => {
    const id = editing?.id || `cb_${Date.now().toString(36)}`;
    const payload = {
      id,
      title: title.trim(),
      description: description.trim(),
      icon,
      appIds: selectedIds,
    };
    if (editing) {
      updateCustomBundle(id, payload);
    } else {
      addCustomBundle(payload);
    }
    return id;
  };

  const handleSaveLocalOnly = () => {
    setErrorMsg(null);
    try {
      handleSaveLocal();
      setSavedLocallyMsg('Saved to your bundles');
      setTimeout(() => onClose(), 650);
    } catch (e: any) {
      setErrorMsg(e?.message || 'Could not save bundle.');
    }
  };

  const handleSubmit = async () => {
    setErrorMsg(null);
    setSubmitting(true);
    try {
      const id = handleSaveLocal();
      const issueTitle = `[Bundle] ${title.trim()}`;
      const body = buildIssueBody({
        title: title.trim(),
        description: description.trim(),
        icon,
        color,
        appIds: selectedIds,
        allApps,
        submitter: userProfile?.name,
      });
      const url = `https://github.com/RookieEnough/Orion-Data/issues/new?title=${encodeURIComponent(issueTitle)}&body=${encodeURIComponent(body)}&labels=bundle-submission`;
      updateCustomBundle(id, { submittedAt: Date.now() });
      window.open(url, '_blank', 'noopener,noreferrer');
      setStep(3);
    } catch (e: any) {
      setErrorMsg(e?.message || 'Could not submit bundle. Try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-end sm:items-center justify-center bg-black/50 backdrop-blur-sm animate-fade-in" onClick={onClose}>
      <div
        className="relative flex max-h-[92vh] w-full max-w-md flex-col overflow-hidden rounded-t-[2rem] sm:rounded-[2rem] bg-surface shadow-2xl animate-slide-up"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 pt-[calc(1rem+env(safe-area-inset-top))] pb-3 border-b border-theme-border">
          <button
            type="button"
            onClick={onClose}
            className="flex h-9 w-9 items-center justify-center rounded-full bg-theme-element text-theme-text hover:bg-theme-hover transition-colors"
            aria-label="Close"
          >
            <i className="fas fa-times text-sm"></i>
          </button>
          <div className="text-center">
            <p className="text-[10px] font-black uppercase tracking-[0.22em] text-primary">Custom Bundle</p>
            <h2 className="text-base font-black tracking-tight">{editing ? 'Edit Bundle' : 'Create Bundle'}</h2>
          </div>
          <div className="w-9" />
        </div>

        {/* Step indicator */}
        <div className="flex items-center justify-center gap-1.5 py-3">
          {[1, 2, 3].map((s) => (
            <div
              key={s}
              className={`h-1 rounded-full transition-all ${step === s ? 'w-8 bg-primary' : step > s ? 'w-4 bg-primary/50' : 'w-4 bg-theme-element'}`}
            />
          ))}
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto px-5 pb-4 no-scrollbar">
          {step === 1 && (
            <div className="space-y-4 animate-fade-in">
              <div>
                <label className="block text-[10px] font-black uppercase tracking-[0.18em] text-theme-sub mb-1.5">Title</label>
                <input
                  type="text"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  maxLength={48}
                  placeholder="e.g. Privacy Essentials"
                  className="w-full rounded-2xl bg-card border border-theme-border px-4 py-3 text-sm font-bold text-theme-text placeholder:text-theme-sub focus:outline-none focus:border-primary transition-colors"
                />
              </div>
              <div>
                <label className="block text-[10px] font-black uppercase tracking-[0.18em] text-theme-sub mb-1.5">Description</label>
                <textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  maxLength={180}
                  rows={3}
                  placeholder="A short pitch for this bundle"
                  className="w-full rounded-2xl bg-card border border-theme-border px-4 py-3 text-sm font-medium text-theme-text placeholder:text-theme-sub focus:outline-none focus:border-primary transition-colors resize-none"
                />
                <p className="text-[10px] text-theme-sub mt-1 text-right">{description.length}/180</p>
              </div>
              <div>
                <label className="block text-[10px] font-black uppercase tracking-[0.18em] text-theme-sub mb-2">Icon</label>
                <div className="grid grid-cols-6 gap-2">
                  {ICON_CHOICES.map((ic) => (
                    <button
                      key={ic.id}
                      type="button"
                      onClick={() => setIcon(ic.id)}
                      className={`aspect-square rounded-2xl flex items-center justify-center transition-all ${icon === ic.id ? 'bg-primary text-white scale-105 shadow-lg shadow-primary/30' : 'bg-card border border-theme-border text-theme-sub hover:text-theme-text'}`}
                      title={ic.label}
                    >
                      <i className={`fas ${ic.id} text-base`}></i>
                    </button>
                  ))}
                </div>
              </div>
              <div>
                <label className="block text-[10px] font-black uppercase tracking-[0.18em] text-theme-sub mb-2">Color</label>
                <div className="flex items-center justify-center gap-3 px-1 py-1">
                  {COLOR_CHOICES.map((c) => (
                    <button
                      key={c}
                      type="button"
                      onClick={() => setColor(c)}
                      className={`h-9 w-9 rounded-full border-2 transition-all ${color === c ? 'border-theme-text scale-110 shadow-md' : 'border-transparent opacity-90'}`}
                      style={{ background: c }}
                      aria-label="Color"
                    />
                  ))}
                </div>
              </div>
            </div>
          )}

          {step === 2 && (
            <div className="space-y-3 animate-fade-in">
              {/* Sticky search bar — always visible while user scrolls list */}
              <div className="sticky top-0 z-10 -mx-5 px-5 pt-1 pb-2 bg-surface">
                <div className="relative">
                  <i className="fas fa-search absolute left-3.5 top-1/2 -translate-y-1/2 text-xs text-theme-sub pointer-events-none"></i>
                  <input
                    type="text"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    placeholder={`Search ${platform ? String(platform) + ' ' : ''}apps...`}
                    className="w-full rounded-2xl bg-card border border-theme-border pl-9 pr-9 py-2.5 text-sm font-medium text-theme-text placeholder:text-theme-sub focus:outline-none focus:border-primary transition-colors"
                  />
                  {search && (
                    <button
                      type="button"
                      onClick={() => setSearch('')}
                      aria-label="Clear search"
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 h-6 w-6 rounded-full bg-theme-element flex items-center justify-center text-theme-sub hover:text-theme-text transition-colors"
                    >
                      <i className="fas fa-times text-[10px]"></i>
                    </button>
                  )}
                </div>

                {/* Horizontal scrollable category pills */}
                <div className="mt-2.5 flex gap-1.5 overflow-x-auto no-scrollbar -mx-1 px-1 pb-0.5">
                  {categoryOptions.map((opt) => {
                    const active = activeCategory === opt.id;
                    return (
                      <button
                        key={opt.id}
                        type="button"
                        onClick={() => setActiveCategory(opt.id)}
                        className={`shrink-0 rounded-full px-3 py-1.5 text-[11px] font-black uppercase tracking-[0.12em] transition-all ${active ? 'bg-primary text-white shadow-md shadow-primary/20' : 'bg-card border border-theme-border text-theme-sub hover:text-theme-text'}`}
                      >
                        {opt.label}
                        <span className={`ml-1.5 text-[9px] tabular-nums ${active ? 'text-white/80' : 'text-theme-sub/70'}`}>{opt.count}</span>
                      </button>
                    );
                  })}
                </div>
              </div>

              <p className="text-[10px] font-bold uppercase tracking-wider text-theme-sub px-1">
                {selectedIds.length} selected · min 2, max 24 · showing {visibleApps.length}/{filteredApps.length}
              </p>

              <div className="space-y-1.5" style={{ contain: 'content' }}>
                {visibleApps.map((app) => {
                  const isSel = selectedIds.includes(app.id);
                  return (
                    <button
                      key={app.id}
                      type="button"
                      onClick={() => toggleApp(app.id)}
                      className={`flex w-full items-center gap-3 rounded-2xl px-3 py-2.5 text-left transition-colors ${isSel ? 'bg-primary/10 border border-primary/40' : 'bg-card border border-theme-border hover:bg-theme-element/60'}`}
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
                      <div className={`h-6 w-6 rounded-full flex items-center justify-center shrink-0 ${isSel ? 'bg-primary text-white' : 'bg-theme-element text-theme-sub'}`}>
                        <i className={`fas ${isSel ? 'fa-check' : 'fa-plus'} text-[10px]`}></i>
                      </div>
                    </button>
                  );
                })}

                {filteredApps.length === 0 && (
                  <p className="text-center text-theme-sub text-sm py-8">No apps match.</p>
                )}

                {hasMore && (
                  <button
                    type="button"
                    onClick={() => setVisibleCount((c) => Math.min(c + PAGE_SIZE, filteredApps.length))}
                    className="w-full rounded-2xl bg-theme-element border border-theme-border py-3 text-[11px] font-black uppercase tracking-[0.18em] text-theme-text hover:bg-theme-hover transition-colors active:scale-[0.98]"
                  >
                    <i className="fas fa-plus-circle mr-1.5"></i>
                    Load {Math.min(PAGE_SIZE, filteredApps.length - visibleCount)} more
                    <span className="ml-1.5 text-[9px] text-theme-sub">({filteredApps.length - visibleCount} left)</span>
                  </button>
                )}
              </div>
            </div>
          )}

          {step === 3 && (
            <div className="text-center py-10 animate-fade-in">
              <div className="mx-auto h-16 w-16 rounded-full bg-emerald-500/15 text-emerald-500 flex items-center justify-center mb-4">
                <i className="fas fa-paper-plane text-2xl"></i>
              </div>
              <h3 className="text-lg font-black tracking-tight">Submitted!</h3>
              <p className="text-sm text-theme-sub mt-1.5 max-w-[18rem] mx-auto">
                Your bundle was saved locally and a GitHub issue was opened. We'll review it for the public catalog.
              </p>
              <p className="text-[10px] text-theme-sub/80 mt-3 px-4">
                You can find your saved bundles in the Bundles tab.
              </p>
            </div>
          )}

          {errorMsg && (
            <div className="mt-3 rounded-2xl border border-red-500/40 bg-red-500/10 px-3 py-2 text-xs text-red-500 font-bold">
              <i className="fas fa-circle-exclamation mr-1.5"></i>{errorMsg}
            </div>
          )}
          {savedLocallyMsg && (
            <div className="mt-3 rounded-2xl border border-emerald-500/40 bg-emerald-500/10 px-3 py-2 text-xs text-emerald-500 font-bold animate-fade-in">
              <i className="fas fa-check-circle mr-1.5"></i>{savedLocallyMsg}
            </div>
          )}
        </div>

        {/* Footer actions */}
        <div className="border-t border-theme-border px-5 py-3 pb-[calc(env(safe-area-inset-bottom)+0.75rem)] flex items-center gap-2">
          {step === 1 && (
            <>
              <button
                type="button"
                onClick={onClose}
                className="flex-1 rounded-full bg-theme-element py-3 text-xs font-black uppercase tracking-[0.18em] text-theme-text hover:bg-theme-hover transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={!canProceed}
                onClick={() => setStep(2)}
                className="flex-[2] rounded-full bg-primary py-3 text-xs font-black uppercase tracking-[0.18em] text-white shadow-lg shadow-primary/20 transition-all active:scale-[0.97] disabled:opacity-40"
              >
                Continue <i className="fas fa-arrow-right ml-1.5"></i>
              </button>
            </>
          )}
          {step === 2 && (
            <>
              <button
                type="button"
                onClick={() => setStep(1)}
                aria-label="Back"
                className="h-12 w-12 shrink-0 rounded-full bg-theme-element flex items-center justify-center text-theme-text hover:bg-theme-hover transition-colors"
              >
                <i className="fas fa-arrow-left text-sm"></i>
              </button>
              <button
                type="button"
                disabled={!canProceed || submitting}
                onClick={handleSaveLocalOnly}
                className="flex-1 rounded-full bg-theme-element py-3 text-[11px] font-black uppercase tracking-[0.16em] text-theme-text hover:bg-theme-hover transition-all active:scale-[0.97] disabled:opacity-40"
              >
                <i className="fas fa-bookmark mr-1.5"></i>Keep Local
              </button>
              <button
                type="button"
                disabled={!canProceed || submitting}
                onClick={handleSubmit}
                className="flex-1 rounded-full bg-primary py-3 text-[11px] font-black uppercase tracking-[0.16em] text-white shadow-lg shadow-primary/20 transition-all active:scale-[0.97] disabled:opacity-40"
              >
                {submitting ? (<><i className="fas fa-spinner fa-spin mr-1.5"></i>Submitting</>) : (<><i className="fas fa-paper-plane mr-1.5"></i>Submit</>)}
              </button>
            </>
          )}
          {step === 3 && (
            <button
              type="button"
              onClick={onClose}
              className="flex-1 rounded-full bg-primary py-3 text-xs font-black uppercase tracking-[0.18em] text-white shadow-lg shadow-primary/20 transition-all active:scale-[0.97]"
            >
              Done
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default CustomBundleModal;
