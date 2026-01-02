import { memo, useState, useEffect } from 'react';
import { useStore } from '@/store';
import { DEBOUNCE_MS } from '@/constants';

export const SearchBar = memo(function SearchBar() {
  const { searchQuery, setSearchQuery, activeTab, isRefreshing, loadApps } = useStore();
  const [local, setLocal] = useState(searchQuery);

  useEffect(() => {
    const t = setTimeout(() => setSearchQuery(local), DEBOUNCE_MS);
    return () => clearTimeout(t);
  }, [local, setSearchQuery]);

  useEffect(() => setLocal(searchQuery), [searchQuery]);

  const placeholder = activeTab === 'pc' ? 'Search PC apps...' : 'Search apps...';

  return (
    <div className="flex items-center gap-3 mb-4 animate-fade-in entrance-search">
      <div className="relative flex-1 group">
        <div className="absolute -inset-0.5 bg-gradient-to-r from-acid via-primary to-neon rounded-2xl opacity-0 group-focus-within:opacity-50 transition duration-300 blur-md" />
        <div className="relative flex items-center bg-card rounded-2xl border border-theme-border h-12 transition-all group-focus-within:border-primary/50">
          <i className="fas fa-search pl-4 pr-3 text-theme-sub group-focus-within:text-primary transition-colors" />
          <input
            type="text"
            value={local}
            onChange={e => setLocal(e.target.value)}
            placeholder={placeholder}
            className="flex-1 bg-transparent outline-none text-theme-text placeholder-theme-sub h-full font-medium"
          />
          {local && (
            <button onClick={() => { setLocal(''); setSearchQuery(''); }} className="px-3 text-theme-sub hover:text-red-500 transition-colors">
              <i className="fas fa-times" />
            </button>
          )}
        </div>
      </div>
      <button
        onClick={() => { void loadApps(true); }}
        className={`shrink-0 w-12 h-12 rounded-2xl border border-theme-border bg-card flex items-center justify-center text-theme-sub hover:text-primary hover:border-primary/50 transition-all active:scale-95 ${isRefreshing ? 'animate-spin text-primary' : ''}`}
        title="Refresh"
      >
        <i className="fas fa-sync-alt" />
      </button>
    </div>
  );
});
