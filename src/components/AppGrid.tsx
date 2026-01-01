import { memo, useMemo } from 'react';
import { useAppContext } from '@/context/AppContext';
import { AppCard } from './AppCard';
import { SearchBar } from './SearchBar';
import { CategoryFilter } from './CategoryFilter';
import type { Platform } from '@/types';

interface AppGridProps {
  platform: Platform;
  title: string;
  searchPlaceholder: string;
  showBanner?: boolean;
}

export const AppGrid = memo(function AppGrid({ platform, title, searchPlaceholder, showBanner }: AppGridProps) {
  const { apps, isLoading, searchQuery, selectedCategory, setSelectedApp, checkHasUpdate, installedVersions, config } = useAppContext();

  const filteredApps = useMemo(() => {
    const query = searchQuery.toLowerCase();
    return apps.filter(app =>
      app.platform === platform &&
      (selectedCategory === 'All' || app.category === selectedCategory) &&
      (app.name.toLowerCase().includes(query) || app.description.toLowerCase().includes(query))
    );
  }, [apps, platform, searchQuery, selectedCategory]);

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-24 animate-fade-in">
        <div className="w-12 h-12 border-4 border-theme-element border-t-primary rounded-full animate-spin mb-4" />
        <p className="text-theme-sub font-bold animate-pulse">Loading Store...</p>
      </div>
    );
  }

  return (
    <div className="px-6 pt-2 pb-28 space-y-2 animate-fade-in">
      {config?.announcement && (
        <div className="bg-indigo-500/10 border border-indigo-500/30 text-indigo-600 dark:text-indigo-300 p-4 rounded-2xl mb-6 flex items-start gap-3">
          <i className="fas fa-bullhorn mt-1" />
          <div>
            <p className="font-bold text-sm">Announcement</p>
            <p className="text-xs opacity-90">{config.announcement}</p>
          </div>
        </div>
      )}

      {showBanner && (
        <div className="bg-gradient-to-r from-primary to-purple-600 p-6 rounded-3xl mb-8 text-white relative overflow-hidden shadow-2xl shadow-primary/20">
          <i className="fas fa-laptop-code absolute -right-10 -bottom-10 opacity-20 text-9xl" />
          <h3 className="text-2xl font-bold mb-2 relative z-10">Desktop Station</h3>
          <p className="text-indigo-100 font-medium max-w-xs relative z-10">Professional grade open-source tools for your workstation.</p>
        </div>
      )}

      <SearchBar placeholder={searchPlaceholder} />
      <CategoryFilter platform={platform} />

      <div className="flex items-center justify-between mb-4 mt-4">
        <h2 className="text-xl font-bold text-theme-text">{selectedCategory === 'All' ? title : `${selectedCategory} Apps`}</h2>
        <span className="text-xs font-bold bg-acid text-black px-3 py-1 rounded-full">{filteredApps.length}</span>
      </div>

      {filteredApps.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredApps.map(app => (
            <AppCard key={app.id} app={app} onClick={setSelectedApp} hasUpdate={checkHasUpdate(app)} localVersion={installedVersions[app.id]} />
          ))}
        </div>
      ) : (
        <div className="flex flex-col items-center justify-center py-12 text-center opacity-60">
          <div className="w-20 h-20 bg-theme-element rounded-full flex items-center justify-center mb-4">
            <i className="fas fa-search text-3xl text-theme-sub" />
          </div>
          <h3 className="text-lg font-bold text-theme-text mb-1">No apps found</h3>
          <p className="text-theme-sub text-sm">Try searching for something else</p>
        </div>
      )}
    </div>
  );
});
