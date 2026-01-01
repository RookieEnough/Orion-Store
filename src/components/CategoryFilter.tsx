import { memo, useState, useEffect, useMemo } from 'react';
import { useAppContext } from '@/context/AppContext';
import type { Platform } from '@/types';

interface CategoryFilterProps {
  platform: Platform;
}

export const CategoryFilter = memo(function CategoryFilter({ platform }: CategoryFilterProps) {
  const { apps, selectedCategory, setSelectedCategory, isRefreshing, refresh, theme } = useAppContext();
  const [isOpen, setIsOpen] = useState(false);

  const categories = useMemo(() => {
    const platformApps = apps.filter((app) => app.platform === platform);
    const cats = Array.from(new Set(platformApps.map((app) => app.category)));
    return ['All', ...cats];
  }, [apps, platform]);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      if (!target.closest('#category-dropdown')) {
        setIsOpen(false);
      }
    };
    if (isOpen) document.addEventListener('click', handleClickOutside);
    return () => document.removeEventListener('click', handleClickOutside);
  }, [isOpen]);

  return (
    <div className="relative z-10 mb-6 animate-fade-in flex items-center gap-3">
      <span className="text-sm font-bold text-theme-sub uppercase tracking-wider hidden md:block">
        Category:
      </span>

      <div id="category-dropdown" className="relative flex-1 md:flex-none md:w-64">
        <button
          onClick={() => setIsOpen(!isOpen)}
          className="w-full flex justify-between items-center py-3 px-4 bg-card border border-theme-border rounded-2xl font-bold text-theme-text shadow-sm hover:shadow-lg transition-all"
        >
          {selectedCategory}
          <i className={`fas fa-chevron-${isOpen ? 'up' : 'down'} text-xs transition-transform`} />
        </button>

        {isOpen && (
          <ul
            className={`absolute mt-2 w-full backdrop-blur-3xl rounded-2xl shadow-2xl border border-theme-border max-h-60 overflow-y-auto z-50 animate-fade-in no-scrollbar ${
              theme === 'light'
                ? 'bg-white/90'
                : theme === 'dusk'
                  ? 'bg-[#2a2d3e]/95'
                  : 'bg-gray-900/90'
            }`}
          >
            {categories.map((category) => (
              <li
                key={category}
                onClick={() => {
                  setSelectedCategory(category);
                  setIsOpen(false);
                }}
                className={`px-4 py-3 cursor-pointer hover:bg-primary/20 transition-colors ${
                  selectedCategory === category ? 'font-bold text-primary' : 'text-theme-text'
                }`}
              >
                {category}
              </li>
            ))}
          </ul>
        )}
      </div>

      <button
        onClick={refresh}
        className={`shrink-0 w-12 h-12 rounded-2xl border border-theme-border bg-card flex items-center justify-center text-theme-sub hover:text-primary hover:border-primary transition-all shadow-sm active:scale-95 ${
          isRefreshing ? 'animate-spin text-primary' : ''
        }`}
        title="Refresh Data"
      >
        <i className="fas fa-sync-alt" />
      </button>
    </div>
  );
});
