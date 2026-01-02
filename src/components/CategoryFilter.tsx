import { memo, useMemo } from 'react';
import { useStore, useApps } from '@/store';

export const CategoryFilter = memo(function CategoryFilter() {
  const apps = useApps();
  const { activeTab, selectedCategory, setSelectedCategory } = useStore();

  const platform = activeTab === 'pc' ? 'PC' : 'Android';
  const categories = useMemo(() => {
    const cats = new Set(apps.filter(a => a.platform === platform).map(a => a.category));
    return ['All', ...cats];
  }, [apps, platform]);

  return (
    <div className="flex gap-2 overflow-x-auto no-scrollbar pb-2 mb-4 animate-fade-in entrance-filter">
      {categories.map(cat => (
        <button
          key={cat}
          onClick={() => setSelectedCategory(cat)}
          className={`shrink-0 px-4 py-2 rounded-xl font-medium text-sm transition-all active:scale-95 ${
            selectedCategory === cat
              ? 'bg-primary text-white shadow-lg shadow-primary/25'
              : 'bg-card border border-theme-border text-theme-sub hover:text-theme-text hover:border-theme-hover'
          }`}
        >
          {cat}
        </button>
      ))}
    </div>
  );
});
