import React, { useCallback, useMemo, useState } from 'react';
import { shallow } from 'zustand/shallow';
import { AppItem } from '../types';
import { CATEGORY_GRADIENTS } from '../constants';
import { Capacitor } from '@capacitor/core';
import { ENABLE_DUMMY_MY_APPS, DUMMY_MY_APPS_ROWS } from '../utils/dummyMyApps';
import { useDataStore, useSettingsStore } from '../store/useAppStore';
import useAvailableUpdates from '../hooks/useAvailableUpdates';
import { getOptimizedImageUrl } from '../utils/image';
import {
  buildMyAppsRows,
  filterMyAppsRows,
  getMyAppsCounts,
  MyAppsFilter,
  MyAppsRow
} from '../utils/appLibrary';

interface MyAppsViewProps {
  apps: AppItem[];
  layout: 'classic' | 'modern';
  onOpenApp: (app: AppItem) => void;
  onInstall: (app: AppItem, fileName: string) => void;
  onUpdate: (app: AppItem) => void;
  onUpdateAll?: () => void;
  onDeleteApk: (app: AppItem) => void;
  onCancelDownload: (appId: string, compositeId: string) => void;
  onOpenQueue: () => void;
}

const FILTER_OPTIONS: Array<{ id: MyAppsFilter; label: string; icon: string }> = [
  { id: 'all', label: 'All', icon: 'fa-box-archive' },
  { id: 'installed', label: 'Installed', icon: 'fa-circle-check' },
  { id: 'downloaded', label: 'Downloaded', icon: 'fa-download' },
  { id: 'updates', label: 'Updates', icon: 'fa-arrow-up' },
];

type PrimaryAction = 'install' | 'update' | 'download' | 'delete' | 'view';

const getPrimaryAction = (row: MyAppsRow): PrimaryAction => {
  if (row.isDownloading) return 'view';
  if (row.readyFileName) return 'install';
  if (row.hasUpdate) return 'update';
  if (row.isInstalledNow && row.hasSavedApk) return 'delete';
  if (row.libraryEntry?.status === 'downloaded') return row.hasSavedApk ? 'install' : 'download';
  return 'view';
};

const getStatusLabel = (row: MyAppsRow) => {
  if (row.isDownloading) return `${Math.round(row.progress)}%`;
  if (row.readyFileName) return 'Ready to install';
  if (row.hasUpdate) return 'Update available';
  if (row.isInstalledNow) return `Installed v${row.installedVersion}`;
  if (row.libraryEntry?.status === 'installed') return 'Installed previously';
  return row.hasSavedApk ? 'APK saved' : 'Downloaded';
};

const getStatusIcon = (row: MyAppsRow) => {
  if (row.isDownloading) return 'fa-arrow-down';
  if (row.readyFileName) return 'fa-box-open';
  if (row.hasUpdate) return 'fa-arrow-up';
  if (row.isInstalledNow) return 'fa-circle-check';
  if (row.libraryEntry?.status === 'installed') return 'fa-clock-rotate-left';
  return 'fa-file-arrow-down';
};

const getStatusTone = (row: MyAppsRow) => {
  if (row.isDownloading) return 'bg-primary/10 text-primary border-primary/20';
  if (row.readyFileName) return 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20';
  if (row.hasUpdate) return 'bg-amber-500/10 text-amber-600 dark:text-amber-300 border-amber-500/20';
  if (row.isInstalledNow) return 'bg-sky-500/10 text-sky-600 dark:text-sky-400 border-sky-500/20';
  return 'bg-theme-element text-theme-sub border-theme-border';
};

const AppIcon = ({ row, priority }: { row: MyAppsRow; priority: boolean }) => {
  const [imgStatus, setImgStatus] = useState<'loading' | 'loaded' | 'error'>('loading');
  const bgGradient = CATEGORY_GRADIENTS[row.app.category] || CATEGORY_GRADIENTS.Default;
  const optimizedUrl = getOptimizedImageUrl(row.app.icon, 128, 128);
  const fallbackIconUrl = row.app.icon;

  return (
    <div className="relative shrink-0 w-14 h-14 sm:w-16 sm:h-16">
      {imgStatus !== 'loaded' && (
        <div className={`absolute inset-0 rounded-2xl flex items-center justify-center text-white text-xl font-bold shadow-inner ${imgStatus === 'error' ? bgGradient : 'bg-theme-element'}`}>
          {imgStatus === 'error' && row.app.name.charAt(0).toUpperCase()}
        </div>
      )}
      <img
        src={optimizedUrl}
        alt={row.app.name}
        className={`w-full h-full object-cover rounded-2xl transition-opacity duration-300 ${imgStatus === 'loaded' ? 'opacity-100' : 'opacity-0'}`}
        style={{ background: 'transparent' }}
        loading={priority ? 'eager' : 'lazy'}
        fetchPriority={priority ? 'high' : 'low'}
        decoding={priority ? 'sync' : 'async'}
        onLoad={() => setImgStatus('loaded')}
        onError={(event) => {
          const target = event.currentTarget;
          if (fallbackIconUrl && target.dataset.fallback !== 'raw') {
            target.dataset.fallback = 'raw';
            target.src = fallbackIconUrl;
            setImgStatus('loading');
            return;
          }
          setImgStatus('error');
        }}
      />
    </div>
  );
};

const QueueShortcut = ({
  layout,
  activeCount,
  readyCount,
  onOpenQueue
}: {
  layout: 'classic' | 'modern';
  activeCount: number;
  readyCount: number;
  onOpenQueue: () => void;
}) => {
  const queueCount = activeCount + readyCount;
  return (
    <button
      type="button"
      onClick={onOpenQueue}
      className={`flex h-11 shrink-0 items-center justify-center gap-2 rounded-full border px-4 text-xs font-black transition-colors ${
        layout === 'modern'
          ? 'border-theme-border/80 bg-theme-element/60 text-theme-text hover:border-primary/50 hover:bg-theme-element'
          : 'border-primary/20 bg-primary/10 text-primary hover:bg-primary/15'
      }`}
      title="Download Queue"
    >
      <i className="fas fa-list-check text-sm" />
      <span className="hidden min-[420px]:inline">Queue</span>
      <span className="flex h-6 min-w-6 items-center justify-center rounded-full bg-primary px-1.5 text-[10px] text-white">
        {queueCount}
      </span>
    </button>
  );
};

const FilterTabs = ({
  layout,
  activeFilter,
  counts,
  onChange
}: {
  layout: 'classic' | 'modern';
  activeFilter: MyAppsFilter;
  counts: ReturnType<typeof getMyAppsCounts>;
  onChange: (filter: MyAppsFilter) => void;
}) => (
  <div className={`no-scrollbar -my-3 flex gap-1.5 overflow-x-auto py-3 ${layout === 'modern' ? 'px-4 lg:px-0' : ''}`}>
    {FILTER_OPTIONS.map((option) => {
      const active = activeFilter === option.id;
      const count = counts[option.id];
      return (
        <button
          key={option.id}
          type="button"
          onClick={() => onChange(option.id)}
          className={`flex h-11 shrink-0 items-center gap-2 rounded-full px-3.5 text-xs font-black transition-all ${
            active
              ? 'bg-primary text-white'
              : 'border border-theme-border bg-card text-theme-sub hover:bg-theme-element'
          }`}
        >
          <i className={`fas ${option.icon} text-[11px]`} />
          <span>{option.label}</span>
          <span className={`flex h-5 min-w-5 items-center justify-center rounded-full px-1 text-[9px] ${active ? 'bg-white/20 text-white' : 'bg-theme-element text-theme-sub'}`}>
            {count}
          </span>
        </button>
      );
    })}
  </div>
);

const EmptyLibrary = ({ filter }: { filter: MyAppsFilter }) => {
  const copy: Record<MyAppsFilter, { icon: string; title: string }> = {
    all: { icon: 'fa-box-archive', title: 'No apps yet' },
    installed: { icon: 'fa-circle-check', title: 'No installed apps' },
    downloaded: { icon: 'fa-download', title: 'No downloaded apps' },
    updates: { icon: 'fa-arrow-up', title: 'No pending updates' },
  };
  const current = copy[filter];
  return (
    <div className="flex min-h-60 flex-col items-center justify-center py-12 text-center">
      <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-3xl bg-theme-element text-2xl text-theme-sub">
        <i className={`fas ${current.icon}`} />
      </div>
      <p className="text-base font-black text-theme-text">{current.title}</p>
    </div>
  );
};

const LibraryRow = ({
  row,
  index,
  layout,
  onOpenApp,
  onInstall,
  onUpdate,
  onDeleteApk,
  onCancelDownload
}: {
  row: MyAppsRow;
  index: number;
  layout: 'classic' | 'modern';
  onOpenApp: (app: AppItem) => void;
  onInstall: (app: AppItem, fileName: string) => void;
  onUpdate: (app: AppItem) => void;
  onDeleteApk: (app: AppItem) => void;
  onCancelDownload: (appId: string, compositeId: string) => void;
}) => {
  const primaryAction = getPrimaryAction(row);
  const statusTone = getStatusTone(row);
  const statusLabel = getStatusLabel(row);
  const versionDetail = row.hasUpdate
    ? row.installedVersion
      ? `v${row.installedVersion} → v${row.app.latestVersion}`
      : `v${row.app.latestVersion}`
    : row.installedVersion
      ? `v${row.installedVersion}`
      : row.lastRemoteVersion
        ? `v${row.lastRemoteVersion}`
        : 'Saved';

  return (
    <article className="app-card-optimized app-card orion-shadow-frame relative rounded-2xl sm:rounded-3xl group isolate">
      <div className="orion-shadow-surface relative flex items-center justify-between gap-3 sm:gap-4 rounded-[inherit] bg-card p-3 sm:p-3.5 border border-theme-border/60 hover:border-theme-border transition-all">
        <div
          onClick={() => onOpenApp(row.app)}
          className="flex min-w-0 flex-1 cursor-pointer items-center gap-3 sm:gap-3.5 active:scale-[0.99] transition-transform"
        >
          <AppIcon row={row} priority={index < 8} />
          <div className="min-w-0 flex-1 flex flex-col gap-0.5">
            <h3 className="truncate text-sm sm:text-base font-bold text-theme-text group-hover:text-primary transition-colors leading-snug">
              {row.app.name}
            </h3>
            <p className="truncate text-xs font-medium text-theme-sub">
              {row.app.author}
            </p>
            <div className="mt-1 flex flex-wrap items-center gap-1.5">
              <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[9px] font-bold uppercase tracking-wider bg-theme-element text-theme-sub">
                {row.app.platform}
              </span>
              <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[9px] font-bold max-w-[130px] truncate bg-theme-element/80 text-theme-sub">
                {versionDetail}
              </span>
              <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[9px] font-bold uppercase tracking-wider border ${statusTone}`}>
                <i className={`fas ${getStatusIcon(row)} text-[8px]`} />
                <span>{statusLabel}</span>
              </span>
            </div>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-1.5 sm:gap-2">
          {row.isDownloading ? (
            <>
              <div className="flex h-9 items-center gap-2 rounded-xl bg-primary/10 px-2.5 text-primary">
                <div className="h-3.5 w-3.5 rounded-full border-2 border-primary border-t-transparent animate-spin" />
                <span className="text-xs font-black">{Math.round(row.progress)}%</span>
              </div>
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  row.activeDownloadKey && onCancelDownload(row.app.id, row.activeDownloadKey);
                }}
                className="flex h-9 w-9 items-center justify-center rounded-xl bg-red-500/10 text-red-500 hover:bg-red-500 hover:text-white transition-colors active:scale-95"
                title="Cancel download"
              >
                <i className="fas fa-times text-xs" />
              </button>
            </>
          ) : primaryAction === 'install' ? (
            <>
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  const fileName = row.readyFileName || row.cleanupFileName || row.libraryEntry?.fileName;
                  if (fileName) onInstall(row.app, fileName);
                }}
                className="flex h-9 items-center gap-1.5 rounded-xl bg-primary px-3 sm:px-3.5 text-xs font-bold text-white shadow-sm shadow-primary/25 transition-all active:scale-95 hover:bg-primary/90"
              >
                <i className="fas fa-box-open text-xs" />
                <span>Install</span>
              </button>
              {row.hasSavedApk && (
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    onDeleteApk(row.app);
                  }}
                  className="flex h-9 w-9 items-center justify-center rounded-xl bg-theme-element text-theme-sub hover:bg-red-500/10 hover:text-red-500 transition-colors active:scale-95"
                  title="Delete APK"
                >
                  <i className="fas fa-trash-alt text-xs" />
                </button>
              )}
            </>
          ) : primaryAction === 'update' ? (
            <>
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  onUpdate(row.app);
                }}
                className="flex h-9 items-center gap-1.5 rounded-xl bg-acid text-black px-3 sm:px-3.5 text-xs font-black shadow-sm shadow-acid/25 transition-all active:scale-95 hover:bg-acid/90"
              >
                <i className="fas fa-sync-alt text-xs" />
                <span>Update</span>
              </button>
              {row.hasSavedApk && (
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    onDeleteApk(row.app);
                  }}
                  className="flex h-9 w-9 items-center justify-center rounded-xl bg-theme-element text-theme-sub hover:bg-red-500/10 hover:text-red-500 transition-colors active:scale-95"
                  title="Delete APK"
                >
                  <i className="fas fa-trash-alt text-xs" />
                </button>
              )}
            </>
          ) : primaryAction === 'download' ? (
            <>
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  onUpdate(row.app);
                }}
                className="flex h-9 items-center gap-1.5 rounded-xl bg-primary px-3 sm:px-3.5 text-xs font-bold text-white shadow-sm shadow-primary/25 transition-all active:scale-95 hover:bg-primary/90"
              >
                <i className="fas fa-download text-xs" />
                <span>Download</span>
              </button>
              {row.hasSavedApk && (
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    onDeleteApk(row.app);
                  }}
                  className="flex h-9 w-9 items-center justify-center rounded-xl bg-theme-element text-theme-sub hover:bg-red-500/10 hover:text-red-500 transition-colors active:scale-95"
                  title="Delete APK"
                >
                  <i className="fas fa-trash-alt text-xs" />
                </button>
              )}
            </>
          ) : primaryAction === 'delete' ? (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onDeleteApk(row.app);
              }}
              className="flex h-9 items-center gap-1.5 rounded-xl bg-red-500/10 border border-red-500/20 px-3 text-xs font-bold text-red-500 hover:bg-red-500 hover:text-white transition-colors active:scale-95"
            >
              <i className="fas fa-trash-alt text-xs" />
              <span>Delete APK</span>
            </button>
          ) : (
            <button
              type="button"
              onClick={() => onOpenApp(row.app)}
              className="flex h-9 w-9 items-center justify-center rounded-full bg-theme-element text-theme-sub/60 hover:bg-theme-hover hover:text-theme-text transition-colors active:scale-95"
              title="Open details"
            >
              <i className="fas fa-chevron-right text-xs" />
            </button>
          )}
        </div>
      </div>
    </article>
  );
};

const MyAppsView: React.FC<MyAppsViewProps> = ({
  apps,
  layout,
  onOpenApp,
  onInstall,
  onUpdate,
  onUpdateAll,
  onDeleteApk,
  onCancelDownload,
  onOpenQueue
}) => {
  const [activeFilter, setActiveFilter] = useState<MyAppsFilter>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const availableUpdates = useAvailableUpdates(apps);
  const { installedVersions, lastRemoteVersions } = useSettingsStore((state) => ({
    installedVersions: state.installedVersions,
    lastRemoteVersions: state.lastRemoteVersions
  }), shallow);
  const appLibrary = useDataStore((state) => state.appLibrary);
  const activeDownloads = useDataStore((state) => state.activeDownloads);
  const downloadProgress = useDataStore((state) => state.downloadProgress);
  const readyToInstall = useDataStore((state) => state.readyToInstall);
  const pendingCleanup = useDataStore((state) => state.pendingCleanup);

  const rows = useMemo(() => {
    const builtRows = buildMyAppsRows({
      apps,
      availableUpdates,
      installedVersions,
      lastRemoteVersions,
      appLibrary,
      activeDownloads,
      downloadProgress,
      readyToInstall,
      pendingCleanup
    });

    if (ENABLE_DUMMY_MY_APPS && (!Capacitor.isNativePlatform() || builtRows.length === 0)) {
      return [...builtRows, ...DUMMY_MY_APPS_ROWS];
    }

    return builtRows;
  }, [apps, availableUpdates, installedVersions, lastRemoteVersions, appLibrary, activeDownloads, downloadProgress, readyToInstall, pendingCleanup]);

  const pendingUpdates = useMemo(() => rows.filter((r) => r.hasUpdate && !r.isDownloading), [rows]);

  const handleUpdateAll = useCallback(() => {
    if (onUpdateAll) {
      onUpdateAll();
      return;
    }
    pendingUpdates.forEach((row) => {
      onUpdate(row.app);
    });
  }, [onUpdateAll, onUpdate, pendingUpdates]);

  const visibleRows = useMemo(() => {
    let result = filterMyAppsRows(rows, activeFilter);
    const query = searchQuery.trim().toLowerCase();
    if (query) {
      result = result.filter((row) =>
        row.app.name.toLowerCase().includes(query) ||
        row.app.author.toLowerCase().includes(query) ||
        (row.app.category && row.app.category.toLowerCase().includes(query)) ||
        (row.app.packageName && row.app.packageName.toLowerCase().includes(query))
      );
    }
    return result;
  }, [rows, activeFilter, searchQuery]);

  const counts = useMemo(() => getMyAppsCounts(rows), [rows]);
  const activeDownloadCount = Object.keys(activeDownloads).length;
  const readyCount = Object.keys(readyToInstall).length;
  const shellClassName = layout === 'classic'
    ? 'mx-auto w-full max-w-[34rem] px-4 sm:max-w-[48rem] sm:px-6 lg:max-w-[82rem] lg:px-8 2xl:max-w-[94rem]'
    : 'w-full lg:mx-auto lg:max-w-[78rem]';
  const modernHeaderClassName = layout === 'modern' ? 'px-4 lg:px-0' : '';

  return (
    <div className={`${shellClassName} pb-6`}>
      <div className={`mb-3.5 flex items-end justify-between gap-3 ${modernHeaderClassName}`}>
        <div>
          <span className="text-[10px] font-black uppercase tracking-[0.2em] text-primary/80">Library</span>
          <h2 className="mt-1 text-2xl font-black text-theme-text">My Apps</h2>
        </div>
        <QueueShortcut
          layout={layout}
          activeCount={activeDownloadCount}
          readyCount={readyCount}
          onOpenQueue={onOpenQueue}
        />
      </div>

      {/* Search Bar */}
      <div className={`mb-3 ${modernHeaderClassName}`}>
        <div className="relative flex h-11 items-center rounded-2xl border border-theme-border bg-theme-input pl-3.5 pr-2 shadow-sm transition-all focus-within:border-primary/50 focus-within:ring-2 focus-within:ring-primary/10">
          <i className="fas fa-search text-theme-sub/70 text-sm shrink-0" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search my apps..."
            className="h-full min-w-0 flex-1 border-none bg-transparent px-2.5 text-sm font-medium text-theme-text outline-none placeholder:text-theme-sub/50"
          />
          {searchQuery && (
            <button
              type="button"
              onClick={() => setSearchQuery('')}
              className="flex h-7 w-7 items-center justify-center rounded-full bg-theme-element text-theme-sub hover:text-theme-text transition-colors"
              title="Clear search"
            >
              <i className="fas fa-times text-xs" />
            </button>
          )}
        </div>
      </div>

      <FilterTabs layout={layout} activeFilter={activeFilter} counts={counts} onChange={setActiveFilter} />

      {/* Update All button in Updates section */}
      {activeFilter === 'updates' && pendingUpdates.length > 0 && (
        <div className={`mt-3.5 ${modernHeaderClassName}`}>
          <button
            type="button"
            onClick={handleUpdateAll}
            className="flex w-full items-center justify-center gap-2 rounded-2xl bg-acid py-3.5 px-4 text-sm font-black text-black shadow-lg shadow-acid/20 transition-all hover:brightness-105 active:scale-[0.985]"
          >
            <i className="fas fa-sync-alt" />
            <span>Update All ({pendingUpdates.length})</span>
          </button>
        </div>
      )}

      <div className={`mt-4 ${layout === 'modern' ? 'px-4 lg:px-0' : ''}`}>
        {visibleRows.length === 0 ? (
          searchQuery ? (
            <div className="flex min-h-60 flex-col items-center justify-center py-12 text-center">
              <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-3xl bg-theme-element text-2xl text-theme-sub">
                <i className="fas fa-search" />
              </div>
              <p className="text-base font-black text-theme-text">No apps matching &ldquo;{searchQuery}&rdquo;</p>
              <button
                type="button"
                onClick={() => setSearchQuery('')}
                className="mt-3 rounded-full bg-primary/10 px-4 py-2 text-xs font-bold text-primary hover:bg-primary/20 transition-colors"
              >
                Clear search
              </button>
            </div>
          ) : (
            <EmptyLibrary filter={activeFilter} />
          )
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
            {visibleRows.map((row, index) => (
              <LibraryRow
                key={row.app.id}
                row={row}
                index={index}
                layout={layout}
                onOpenApp={onOpenApp}
                onInstall={onInstall}
                onUpdate={onUpdate}
                onDeleteApk={onDeleteApk}
                onCancelDownload={onCancelDownload}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default React.memo(MyAppsView);
