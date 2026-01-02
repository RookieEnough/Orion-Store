import { memo } from 'react';
import { useStore, useTheme, useConfig } from '@/store';
import { compareVersions } from '@/utils';
import { CURRENT_STORE_VERSION } from '@/constants';

export const Header = memo(function Header() {
  const theme = useTheme();
  const config = useConfig();
  const { activeTab, setActiveTab, cycleTheme, handleDevTap } = useStore();

  const hasStoreUpdate = config?.latestStoreVersion !== undefined && compareVersions(config.latestStoreVersion, CURRENT_STORE_VERSION) > 0;
  const downloadUrl = config?.storeDownloadUrl;

  return (
    <header className="absolute top-0 left-0 w-full z-20 px-6 py-6 flex justify-between items-center animate-fade-in entrance-header">
      <div className="flex items-center gap-3 select-none">
        <div className="w-10 h-10 bg-primary text-white rounded-xl flex items-center justify-center shadow-lg shadow-primary/30 transform rotate-3 animate-float">
          <i className="fas fa-shapes text-lg" />
        </div>
        <h1 onClick={handleDevTap} className="text-2xl font-black tracking-tighter text-theme-text cursor-pointer active:scale-95 transition-transform">
          Orion<span className="text-primary">Store</span>
        </h1>
      </div>

      <div className="flex items-center gap-2">
        {hasStoreUpdate && downloadUrl && (
          <button
            onClick={() => { window.location.href = downloadUrl; }}
            className="px-3 py-2 rounded-xl bg-acid/20 text-acid-dark dusk:text-acid border border-acid/30 font-bold text-xs flex items-center gap-2 animate-pulse"
          >
            <i className="fas fa-arrow-circle-up" />
            <span className="hidden sm:inline">Update</span>
          </button>
        )}
        <button
          onClick={() => setActiveTab('about')}
          className={`w-10 h-10 rounded-xl flex items-center justify-center transition-all active:scale-95 ${
            activeTab === 'about'
              ? 'bg-primary text-white shadow-lg shadow-primary/25'
              : 'bg-theme-element text-theme-sub hover:text-theme-text'
          }`}
          title="About"
        >
          <i className="fas fa-user" />
        </button>
        <button
          onClick={cycleTheme}
          className="w-10 h-10 rounded-xl bg-theme-element hover:bg-theme-hover flex items-center justify-center text-theme-sub hover:text-primary transition-all active:scale-95"
          title={`Theme: ${theme}`}
        >
          <i className={`fas ${theme === 'light' ? 'fa-sun' : theme === 'dusk' ? 'fa-moon' : 'fa-circle-half-stroke'}`} />
        </button>
      </div>
    </header>
  );
});
