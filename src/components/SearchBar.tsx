import { memo, useState, useEffect } from 'react';
import { useStore } from '@/store';

export const SearchBar = memo(function SearchBar() {
  const { searchQuery, setSearchQuery, activeTab } = useStore();
  const [local, setLocal] = useState(searchQuery);

  // Debounce search
  useEffect(() => {
    const t = setTimeout(() => setSearchQuery(local), 200);
    return () => clearTimeout(t);
  }, [local, setSearchQuery]);

  // Sync when tab changes
  useEffect(() => setLocal(searchQuery), [searchQuery]);

  const placeholder = activeTab === 'pc' ? 'Search PC apps...' : 'Search apps...';

  return (
    <div className="relative mb-6 group z-10 animate-fade-in">
      <div className="absolute -inset-0.5 bg-gradient-to-r from-acid via-primary to-neon rounded-2xl opacity-20 group-focus-within:opacity-100 transition duration-500 blur group-focus-within:blur-md" />
      <div className="relative flex items-center bg-theme-input rounded-2xl border border-theme-border p-1 shadow-lg transition-transform group-focus-within:scale-[1.01]">
        <i className="fas fa-search text-lg pl-4 pr-3 text-theme-sub group-focus-within:text-acid transition-colors" />
        <input
          type="text"
          value={local}
          onChange={e => setLocal(e.target.value)}
          placeholder={placeholder}
          className="w-full bg-transparent border-none outline-none text-theme-text placeholder-gray-500 h-12 font-medium text-lg"
        />
        {local && (
          <button onClick={() => { setLocal(''); setSearchQuery(''); }} className="w-10 h-10 flex items-center justify-center text-theme-sub hover:text-red-500 transition-colors">
            <i className="fas fa-times" />
          </button>
        )}
      </div>
    </div>
  );
});
