
import React, { useState, useEffect, useRef } from 'react';
import { SortOption } from '../types';

interface StoreFiltersProps {
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  selectedCategory: string;
  setSelectedCategory: (category: string) => void;
  categories: string[];
  selectedSort: SortOption;
  setSelectedSort: (sort: SortOption) => void;
  onRefresh: (e?: React.MouseEvent) => void;
  isRefreshing: boolean;
  theme: 'light' | 'dusk' | 'dark' | 'oled';
  placeholder: string;
  onAddApp?: () => void;
  submissionCooldown?: string | null;
  count?: number;
  showFavorites?: boolean;
  onToggleFavorites?: () => void;
}

const StoreFilters: React.FC<StoreFiltersProps> = ({
  searchQuery,
  setSearchQuery,
  selectedCategory,
  setSelectedCategory,
  categories,
  selectedSort,
  setSelectedSort,
  onRefresh,
  isRefreshing,
  theme,
  placeholder,
  onAddApp,
  submissionCooldown,
  count,
  showFavorites,
  onToggleFavorites
}) => {
  const [isSortDropdownOpen, setIsSortDropdownOpen] = useState(false);
  const sortRef = useRef<HTMLDivElement>(null);

  // Handle click outside to close sort dropdown
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (sortRef.current && !sortRef.current.contains(e.target as Node)) {
        setIsSortDropdownOpen(false);
      }
    };
    document.addEventListener('click', handleClickOutside);
    return () => document.removeEventListener('click', handleClickOutside);
  }, []);

  return (
    <div className="animate-fade-in relative z-20 flex flex-col gap-2 mb-4 mt-2">
      
      {/* --- Row 1: Search Bar & Primary Actions --- */}
      <div className="flex items-center gap-1.5 h-12">
        {/* Integrated Search Bar */}
        <div className="flex-1 relative group h-full min-w-0">
          {/* Glow Effect */}
          <div className="absolute -inset-0.5 bg-gradient-to-r from-acid/50 to-primary/50 rounded-2xl opacity-0 group-focus-within:opacity-100 transition duration-500 blur-sm"></div>
          
          <div className="relative h-full bg-theme-input border border-theme-border rounded-2xl flex items-center pl-3 pr-2 shadow-sm transition-all focus-within:border-primary/50 focus-within:ring-2 focus-within:ring-primary/10">
              <i className="fas fa-search text-theme-sub/70 text-sm shrink-0"></i>
              <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder={placeholder}
                  className="flex-1 bg-transparent border-none outline-none text-theme-text text-sm font-medium px-2 h-full placeholder-theme-sub/50 min-w-0 truncate"
              />
              
              {/* Integrated Actions Group */}
              <div className="flex items-center gap-0.5 shrink-0">
                  {searchQuery && (
                      <button 
                          onClick={() => setSearchQuery('')}
                          className="w-8 h-8 flex items-center justify-center text-theme-sub hover:text-red-500 transition-colors rounded-full hover:bg-theme-element active:scale-90"
                          title="Clear Search"
                      >
                          <i className="fas fa-times text-xs"></i>
                      </button>
                  )}
                  
                  {/* Divider */}
                  <div className="w-px h-3 bg-theme-border mx-0.5"></div>
                  
                  {/* Favorites Toggle */}
                  {onToggleFavorites && (
                      <button
                          onClick={onToggleFavorites}
                          className={`w-8 h-8 flex items-center justify-center transition-all rounded-full hover:bg-theme-element active:scale-90 ${showFavorites ? 'text-rose-500 bg-rose-500/10' : 'text-theme-sub hover:text-rose-500'}`}
                          title="Show Favorites"
                      >
                          <i className={`${showFavorites ? 'fas' : 'far'} fa-heart text-xs`}></i>
                      </button>
                  )}

                  <button
                      onClick={onRefresh}
                      disabled={isRefreshing}
                      className={`w-8 h-8 flex items-center justify-center text-theme-sub hover:text-primary transition-colors rounded-full hover:bg-theme-element active:scale-90 ${isRefreshing ? 'animate-spin text-primary' : ''}`}
                      title="Refresh"
                  >
                      <i className="fas fa-sync-alt text-xs"></i>
                  </button>
              </div>
          </div>
        </div>

        {/* Add App Button (Desktop/Mobile) */}
        {onAddApp && !submissionCooldown && (
            <button
                onClick={onAddApp}
                className="shrink-0 w-12 h-12 bg-card border border-theme-border rounded-2xl text-theme-text hover:bg-theme-element active:scale-95 transition-all flex items-center justify-center shadow-sm group"
                title="Add App"
            >
                <i className="fas fa-plus text-sm group-hover:rotate-90 transition-transform"></i>
            </button>
        )}
        {/* Cooldown Indicator */}
        {submissionCooldown && (
             <div className="shrink-0 w-12 h-12 bg-theme-element border border-theme-border rounded-2xl flex flex-col items-center justify-center text-theme-sub opacity-70 cursor-not-allowed" title={`Next submission in ${submissionCooldown}`}>
                <i className="fas fa-clock text-[10px] mb-0.5"></i>
                <span className="text-[8px] font-bold leading-none">{submissionCooldown}</span>
             </div>
        )}

        {/* App Count Badge (Moved outside) */}
        {count !== undefined && (
            <div className="shrink-0 h-12 flex items-center justify-center pointer-events-none select-none">
                <span className="text-xs font-bold text-theme-sub bg-theme-element px-2.5 rounded-2xl border border-theme-border min-w-[2.5rem] text-center flex items-center justify-center h-full shadow-sm">
                    {count}
                </span>
            </div>
        )}
      </div>

      {/* --- Row 2: Sort & Category Pills --- */}
      <div className="flex items-center gap-2">
         
         {/* Sort Dropdown (Fixed Position) */}
         <div className="relative shrink-0" ref={sortRef}>
            <button
                onClick={() => setIsSortDropdownOpen(!isSortDropdownOpen)}
                className={`h-9 px-3 rounded-xl border flex items-center gap-2 text-xs font-bold transition-all whitespace-nowrap active:scale-95 ${isSortDropdownOpen ? 'bg-primary text-white border-primary shadow-md shadow-primary/20' : 'bg-card border-theme-border text-theme-text hover:bg-theme-element'}`}
            >
                <i className="fas fa-sort text-[10px] opacity-70"></i>
                <span className="truncate max-w-[80px]">{selectedSort.split(' ')[0]}</span>
                <i className={`fas fa-chevron-${isSortDropdownOpen ? 'up' : 'down'} text-[8px] opacity-50`}></i>
            </button>
            
            {isSortDropdownOpen && (
                <div className="absolute top-full left-0 mt-2 w-48 bg-surface border border-theme-border rounded-2xl shadow-xl overflow-hidden z-50 animate-slide-up origin-top-left">
                    {Object.values(SortOption).map(option => (
                        <button
                            key={option}
                            onClick={() => { setSelectedSort(option); setIsSortDropdownOpen(false); }}
                            className={`w-full text-left px-4 py-3 text-xs font-bold hover:bg-theme-element transition-colors flex justify-between items-center ${selectedSort === option ? 'text-primary bg-primary/5' : 'text-theme-text'}`}
                        >
                            {option}
                            {selectedSort === option && <i className="fas fa-check"></i>}
                        </button>
                    ))}
                </div>
            )}
         </div>

         {/* Vertical Divider */}
         <div className="w-px h-5 bg-theme-border shrink-0"></div>

         {/* Scrollable Category Pills (Only this part scrolls now) */}
         <div className="flex-1 overflow-x-auto no-scrollbar flex items-center gap-2 pb-1">
             {categories.map(cat => (
                 <button
                    key={cat}
                    onClick={() => setSelectedCategory(cat)}
                    className={`h-9 px-4 rounded-xl border text-xs font-bold whitespace-nowrap transition-all active:scale-95 ${
                        selectedCategory === cat
                        ? 'bg-theme-text text-surface border-theme-text shadow-md'
                        : 'bg-card border-theme-border text-theme-sub hover:text-theme-text hover:border-theme-sub'
                    }`}
                 >
                    {cat}
                 </button>
             ))}
         </div>
      </div>
    </div>
  );
};

export default StoreFilters;
