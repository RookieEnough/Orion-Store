import { memo, useState, useEffect, useMemo } from 'react';
import { useStore, useApps } from '@/store';

export const CategoryFilter = memo(function CategoryFilter() {
  const apps = useApps();
  const { activeTab, selectedCategory, setSelectedCategory, isRefreshing, loadApps } = useStore();
  const [isOpen, setIsOpen] = useState(false);

  const platform = activeTab === 'pc' ? 'PC' : 'Android';
  const categories = useMemo(() => {
    const cats = new Set(apps.filter(a => a.platform === platform).map(a => a.category));
    return ['All', ...cats];
  }, [apps, platform]);

  useEffect(() => {
    if (!isOpen) return;
    const close = (e: MouseEvent) => !(e.target as HTMLElement).closest('#category-dropdown') && setIsOpen(false);
    document.addEventListener('click', close);
    return () => document.removeEventListener('click', close);
  }, [isOpen]);

  return (
    <div className="relative z-10 mb-6 animate-fade-in flex items-center gap-3">
      <span className="text-sm font-bold text-theme-sub uppercase tracking-wider hidden md:block">Category:</span>
      <div id="category-dropdown" className="relative flex-1 md:flex-none md:w-64">
        <button onClick={() => setIsOpen(!isOpen)} className="w-full flex justify-between items-center py-3 px-4 bg-card border border-theme-border rounded-2xl font-bold text-theme-text shadow-xs hover:shadow-lg transition-all">
          {selectedCategory}
          <i className={`fas fa-chevron-${isOpen ? 'up' : 'down'} text-xs`} />
        </button>
        {isOpen && (
          <ul className="absolute mt-2 w-full bg-card/95 backdrop-blur-xl rounded-2xl shadow-2xl border border-theme-border max-h-60 overflow-y-auto z-50 animate-fade-in no-scrollbar">
            {categories.map(cat => (
              <li key={cat} onClick={() => { setSelectedCategory(cat); setIsOpen(false); }} className={`px-4 py-3 cursor-pointer hover:bg-primary/20 transition-colors ${selectedCategory === cat ? 'font-bold text-primary' : 'text-theme-text'}`}>
                {cat}
              </li>
            ))}
          </ul>
        )}
      </div>
      <button onClick={() => { void loadApps(true); }} className={`shrink-0 w-12 h-12 rounded-2xl border border-theme-border bg-card flex items-center justify-center text-theme-sub hover:text-primary hover:border-primary transition-all shadow-xs active:scale-95 ${isRefreshing ? 'animate-spin text-primary' : ''}`} title="Refresh">
        <i className="fas fa-sync-alt" />
      </button>
    </div>
  );
});
