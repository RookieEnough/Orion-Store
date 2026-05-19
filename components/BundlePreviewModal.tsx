import React from 'react';
import { BundleItem, AppItem } from '../types';
import { getOptimizedImageUrl } from '../utils/image';

interface BundlePreviewModalProps {
  bundle: BundleItem;
  onClose: () => void;
  onOpenApp: (app: AppItem) => void;
  onDownloadAll: (bundle: BundleItem) => void;
}

const isFontAwesomeIcon = (icon?: string) => !!icon && icon.includes('fa-');

const BundlePreviewModal: React.FC<BundlePreviewModalProps> = ({
  bundle,
  onClose,
  onOpenApp,
  onDownloadAll
}) => {
  const apps = bundle.apps || [];
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
            onClick={() => onDownloadAll(bundle)}
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
          <div className="space-y-2 pb-2">
            {apps.map((app) => (
              <button
                key={app.id}
                type="button"
                onClick={() => onOpenApp(app)}
                className="flex w-full items-center gap-3 rounded-[1.7rem] bg-card px-3 py-3 text-left transition-all active:scale-[0.985] hover:bg-theme-element/80"
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
                  <span className="block truncate text-[10px] font-bold uppercase tracking-[0.18em] text-theme-sub">
                    {app.category}
                  </span>
                </div>
                <i className="fas fa-chevron-right text-xs text-theme-sub/70"></i>
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export default BundlePreviewModal;
