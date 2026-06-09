
import React, { useState, useEffect, useRef } from 'react';
import { SortOption } from '../types';
import { useSettingsStore } from '../store/useAppStore';

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
  variant?: 'classic' | 'modern';
  onProfileClick?: () => void;
  profileAvatarUrl?: string;
  profileInitial?: string;
}

const getModernCategoryTag = (selectedCategory: string) => (
  selectedCategory !== 'All' ? selectedCategory : null
);

const MODERN_SORT_OPTIONS = [
  SortOption.NEWEST,
  SortOption.NAME_ASC,
  SortOption.NAME_DESC,
  SortOption.SIZE_ASC,
  SortOption.SIZE_DESC,
  SortOption.OLDEST
];

const CLASSIC_SORT_OPTIONS = Object.values(SortOption).filter((option) => option !== SortOption.HOME);

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
  onToggleFavorites,
  variant = 'classic',
  onProfileClick,
  profileAvatarUrl,
  profileInitial,
}) => {
  const [isSortDropdownOpen, setIsSortDropdownOpen] = useState(false);
  const sortRef = useRef<HTMLDivElement>(null);

  const isModern = variant === 'modern';
  const isFiltered = selectedCategory !== 'All' || searchQuery;
  const sortOptions = isModern ? MODERN_SORT_OPTIONS : CLASSIC_SORT_OPTIONS;
  const modernSearchShellClass = 'mx-auto flex w-full max-w-[78rem] justify-center px-4 sm:px-6 lg:px-8';
  const modernSearchRailClass = 'flex w-full max-w-[32rem] min-w-0 items-center gap-2.5';
  const modernCategoryTag = getModernCategoryTag(selectedCategory);
  const handleModernHomeClick = () => {
    if (searchQuery) {
      setSearchQuery('');
    }
    if (selectedCategory !== 'All') {
      setSelectedCategory('All');
    }
    if (showFavorites && onToggleFavorites) {
      onToggleFavorites();
    }
  };

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

  if (isModern && isFiltered) {
    return (
      <div className="animate-fade-in relative z-[70] isolate mb-4 mt-2 flex flex-col gap-3 overflow-visible">
        {/* Modern Filtered Header */}
        <div className="flex items-center justify-between px-1">
          <div className="flex flex-col">
            <h2 className="text-xl font-black tracking-tight text-theme-text">
              {selectedCategory === 'All' ? 'Search Results' : selectedCategory}
            </h2>
            <div className="flex items-center gap-2 mt-0.5">
              <span className="text-[10px] font-black uppercase tracking-widest text-primary bg-primary/10 px-2 py-0.5 rounded-full">
                {count || 0} Apps
              </span>
              {modernCategoryTag && (
                <span className="text-[10px] font-black uppercase tracking-widest text-theme-text bg-card border border-theme-border px-2 py-0.5 rounded-full">
                  {modernCategoryTag}
                </span>
              )}
              {searchQuery && (
                <span className="text-[10px] font-bold text-theme-sub italic truncate max-w-[150px]">
                  "{searchQuery}"
                </span>
              )}
              {onAddApp && !submissionCooldown && (
                <button
                    onClick={onAddApp}
                    className="flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-primary text-white text-[9px] font-black uppercase tracking-widest shadow-lg shadow-primary/20 transition-colors hover:bg-primary/90"
                    title="Add App"
                >
                    <i className="fas fa-plus-circle text-[10px]"></i>
                    <span>Add App</span>
                </button>
              )}
              {submissionCooldown && (
                <span className="inline-flex items-center gap-1.5 rounded-full border border-theme-border bg-card px-2 py-0.5 text-[9px] font-black uppercase tracking-widest text-theme-sub">
                  <i className="fas fa-clock text-[9px] text-primary"></i>
                  <span>{submissionCooldown}</span>
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Search Bar Row - Narrower and Centered */}
        <div className={modernSearchShellClass}>
          <div className={modernSearchRailClass}>
            <div className="relative h-full min-w-0 flex-1 group">
              <div className="relative flex h-9 items-center rounded-2xl border border-theme-border bg-theme-input pl-3 pr-2 shadow-sm transition-all focus-within:border-primary/50 focus-within:ring-2 focus-within:ring-primary/10">
                  <i className="fas fa-search text-theme-sub/70 text-xs shrink-0"></i>
                  <input
                      type="text"
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      placeholder={placeholder}
                      className="h-full min-w-0 flex-1 truncate border-none bg-transparent px-2 text-sm font-medium text-theme-text outline-none placeholder-theme-sub/50"
                  />
                  
                  <div className="flex items-center gap-0.5 shrink-0">
                      <button
                          onClick={() => setSearchQuery('')}
                          className={`w-7 h-7 flex items-center justify-center rounded-full transition-colors active:scale-90 ${searchQuery ? 'text-theme-sub hover:text-red-500 hover:bg-theme-element' : 'invisible pointer-events-none'}`}
                          aria-hidden={!searchQuery}
                          tabIndex={searchQuery ? 0 : -1}
                      >
                          <i className="fas fa-times text-[10px]"></i>
                      </button>
                      <div className="w-px h-3 bg-theme-border mx-0.5"></div>
                      {onToggleFavorites && (
                          <button
                              onClick={() => {
                                  onToggleFavorites();
                                  if (useSettingsStore.getState().hapticEnabled) {
                                      import('@capacitor/haptics').then(({ Haptics }) => {
                                          Haptics.selection();
                                      });
                                  }
                              }}
                              className={`w-7 h-7 flex items-center justify-center transition-all rounded-full hover:bg-theme-element active:scale-90 ${showFavorites ? 'text-rose-500 bg-rose-500/10' : 'text-theme-sub hover:text-rose-500'}`}
                          >
                              <i className={`${showFavorites ? 'fas' : 'far'} fa-heart text-[10px]`}></i>
                          </button>
                      )}
                      <button onClick={onRefresh} disabled={isRefreshing} className={`w-7 h-7 flex items-center justify-center text-theme-sub hover:text-primary transition-colors rounded-full hover:bg-theme-element active:scale-90 ${isRefreshing ? 'animate-spin text-primary' : ''}`}>
                          <i className="fas fa-sync-alt text-[10px]"></i>
                      </button>
                  </div>
              </div>
            </div>
            
            {/* Profile Circle */}
            {onProfileClick && (
              <button
                onClick={onProfileClick}
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-primary to-acid overflow-hidden shadow-sm shadow-primary/20 transition-all active:scale-90 hover:scale-105"
                title="Profile"
              >
                {profileAvatarUrl ? (
                  <img src={profileAvatarUrl} alt="" className="h-full w-full rounded-full object-cover bg-white" />
                ) : (
                  <span className="text-[11px] font-black text-white">{profileInitial || 'U'}</span>
                )}
              </button>
            )}

            {/* Modern Sort Button */}
            <div className="relative shrink-0" ref={sortRef}>
              <button
                  onClick={() => setIsSortDropdownOpen(!isSortDropdownOpen)}
                  className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-2xl border transition-all active:scale-95 ${isSortDropdownOpen ? 'border-primary bg-primary text-white' : 'border-theme-border bg-card text-theme-text hover:bg-theme-element'}`}
                  title="Sort Apps"
              >
                  <i className="fas fa-sliders-h text-xs"></i>
              </button>
              
              {isSortDropdownOpen && (
                  <div className="absolute top-full right-0 mt-2 w-48 bg-surface border border-theme-border rounded-2xl shadow-xl overflow-hidden z-[90] animate-slide-up origin-top-right">
                      {sortOptions.map(option => (
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
          </div>
        </div>

        {categories.length > 0 && (
          <div className="flex min-w-0 items-center gap-2 overflow-x-auto snap-x snap-mandatory -mx-3 px-4 py-1 no-scrollbar [scroll-padding-inline:1rem]">
            {categories.map((cat) => (
              <button
                key={cat}
                onClick={() => {
                  if (cat === 'All') {
                    handleModernHomeClick();
                    return;
                  }
                  setSelectedCategory(cat);
                }}
                className={`snap-start rounded-xl border px-3 py-2 text-[10px] font-black uppercase tracking-widest whitespace-nowrap transition-all active:scale-95 ${
                  selectedCategory === cat
                    ? 'border-primary bg-primary text-white shadow-md shadow-primary/20'
                    : 'border-theme-border bg-card text-theme-sub hover:border-primary/40 hover:text-theme-text'
                }`}
              >
                {cat === 'All' ? (
                  <span className="inline-flex items-center gap-1.5">
                    <i className="fas fa-house text-[9px]"></i>
                    <span>Home</span>
                  </span>
                ) : (
                  cat
                )}
              </button>
            ))}
          </div>
        )}
      </div>
    );
  }

  if (isModern) {
    return (
      <div className="animate-fade-in relative z-[70] isolate mb-4 mt-1 flex flex-col gap-3 overflow-visible">
        {/* Modern Home Header - Constrained to match content width */}
        <div className="mx-auto w-full max-w-[78rem] flex items-center justify-between px-4 sm:px-6 lg:px-8">
          <div className="flex flex-col">
            <h2 className="text-xl font-black tracking-tight text-theme-text uppercase tracking-tight">Discover</h2>
            <div className="flex items-center gap-2 mt-0.5">
              <span className="text-[10px] font-black uppercase tracking-widest text-primary bg-primary/10 px-2 py-0.5 rounded-full">
                {count || 0} Apps
              </span>
              {onAddApp && !submissionCooldown && (
                <button
                    onClick={onAddApp}
                    className="flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-primary text-white text-[9px] font-black uppercase tracking-widest shadow-lg shadow-primary/20 transition-colors hover:bg-primary/90"
                    title="Add App"
                >
                    <i className="fas fa-plus-circle text-[10px]"></i>
                    <span>Add App</span>
                </button>
              )}
              {submissionCooldown && (
                <span className="inline-flex items-center gap-1.5 rounded-full border border-theme-border bg-card px-2 py-0.5 text-[9px] font-black uppercase tracking-widest text-theme-sub">
                  <i className="fas fa-clock text-[9px] text-primary"></i>
                  <span>{submissionCooldown}</span>
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Search Bar Row - Centered and Compact */}
        <div className={modernSearchShellClass}>
          <div className={modernSearchRailClass}>
            {/* Modern Search Bar */}
            <div className="relative h-full min-w-0 flex-1 group">
              <div className="relative flex h-9 items-center rounded-2xl border border-theme-border bg-theme-input pl-3 pr-2 shadow-sm transition-all focus-within:border-primary/50 focus-within:ring-2 focus-within:ring-primary/10">
                  <i className="fas fa-search text-theme-sub/70 text-sm shrink-0"></i>
                  <input
                      type="text"
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      placeholder={placeholder}
                      className="h-full min-w-0 flex-1 truncate border-none bg-transparent px-2 text-sm font-medium text-theme-text outline-none placeholder-theme-sub/50"
                  />
                  
                  <div className="flex items-center gap-0.5 shrink-0">
                      <button
                          onClick={() => setSearchQuery('')}
                          className={`w-7 h-7 flex items-center justify-center rounded-full transition-colors active:scale-90 ${searchQuery ? 'text-theme-sub hover:text-red-500 hover:bg-theme-element' : 'invisible pointer-events-none'}`}
                          aria-hidden={!searchQuery}
                          tabIndex={searchQuery ? 0 : -1}
                      >
                          <i className="fas fa-times text-[10px]"></i>
                      </button>
                      <div className="w-px h-3 bg-theme-border mx-0.5"></div>
                      {onToggleFavorites && (
                          <button
                              onClick={() => {
                                  onToggleFavorites();
                                  if (useSettingsStore.getState().hapticEnabled) {
                                      import('@capacitor/haptics').then(({ Haptics }) => {
                                          Haptics.selection();
                                      });
                                  }
                              }}
                              className={`w-7 h-7 flex items-center justify-center transition-all rounded-full hover:bg-theme-element active:scale-90 ${showFavorites ? 'text-rose-500 bg-rose-500/10' : 'text-theme-sub hover:text-rose-500'}`}
                          >
                              <i className={`${showFavorites ? 'fas' : 'far'} fa-heart text-[10px]`}></i>
                          </button>
                      )}
                      <button onClick={onRefresh} disabled={isRefreshing} className={`w-7 h-7 flex items-center justify-center text-theme-sub hover:text-primary transition-colors rounded-full hover:bg-theme-element active:scale-90 ${isRefreshing ? 'animate-spin text-primary' : ''}`}>
                          <i className="fas fa-sync-alt text-[10px]"></i>
                      </button>
                  </div>
              </div>
            </div>

            {/* Profile Circle */}
            {onProfileClick && (
              <button
                onClick={onProfileClick}
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-primary to-acid overflow-hidden shadow-sm shadow-primary/20 transition-all active:scale-90 hover:scale-105"
                title="Profile"
              >
                {profileAvatarUrl ? (
                  <img src={profileAvatarUrl} alt="" className="h-full w-full rounded-full object-cover bg-white" />
                ) : (
                  <span className="text-[11px] font-black text-white">{profileInitial || 'U'}</span>
                )}
              </button>
            )}

            {/* Modern Sort Button */}
            <div className="relative shrink-0" ref={sortRef}>
              <button
                  onClick={() => setIsSortDropdownOpen(!isSortDropdownOpen)}
                  className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-2xl border transition-all active:scale-95 ${isSortDropdownOpen ? 'border-primary bg-primary text-white' : 'border-theme-border bg-card text-theme-text hover:bg-theme-element'}`}
                  title="Sort Apps"
              >
                  <i className="fas fa-sliders-h text-xs"></i>
              </button>
              
              {isSortDropdownOpen && (
                  <div className="absolute top-full right-0 mt-2 w-48 bg-surface border border-theme-border rounded-2xl shadow-xl overflow-hidden z-[90] animate-slide-up origin-top-right">
                      {sortOptions.map(option => (
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
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="animate-fade-in relative z-[70] isolate mb-3 mt-1 flex flex-col gap-2.5 overflow-visible">
      
      {/* --- Row 1: Search Bar & Primary Actions --- */}
      <div className="mx-auto flex h-10 w-full max-w-[56rem] items-center gap-2 px-1 sm:px-2">
        {/* Integrated Search Bar */}
        <div className="flex-1 relative group h-full min-w-0">
            <div className="relative flex h-full items-center rounded-2xl border border-theme-border bg-theme-input pl-3 pr-2 shadow-sm transition-all focus-within:border-primary/50 focus-within:ring-2 focus-within:ring-primary/10">
              <i className="fas fa-search text-theme-sub/70 text-sm shrink-0"></i>
              <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder={placeholder}
                  className="h-full min-w-0 flex-1 truncate border-none bg-transparent px-2 text-sm font-medium text-theme-text outline-none placeholder-theme-sub/50"
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
                          onClick={() => {
                              onToggleFavorites();
                              if (useSettingsStore.getState().hapticEnabled) {
                                  import('@capacitor/haptics').then(({ Haptics }) => {
                                      Haptics.selection();
                                  });
                              }
                          }}
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
        {onAddApp && !submissionCooldown && !isModern && (
            <button
                onClick={onAddApp}
                className="group flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl border border-theme-border bg-card text-theme-text shadow-sm transition-all active:scale-95 hover:bg-theme-element"
                title="Add App"
            >
                <i className="fas fa-plus text-sm group-hover:rotate-90 transition-transform"></i>
            </button>
        )}
        {/* Cooldown Indicator */}
        {submissionCooldown && (
             <div className="flex h-10 w-10 shrink-0 cursor-not-allowed flex-col items-center justify-center rounded-2xl border border-theme-border bg-theme-element text-theme-sub opacity-70" title={`Next submission in ${submissionCooldown}`}>
                <i className="fas fa-clock text-[10px] mb-0.5"></i>
                <span className="text-[8px] font-bold leading-none">{submissionCooldown}</span>
             </div>
        )}

        {/* App Count Badge (Moved outside) */}
        {count !== undefined && (
            <div className="pointer-events-none flex h-10 shrink-0 select-none items-center justify-center">
                <span className="flex h-full min-w-[2.5rem] items-center justify-center rounded-2xl border border-theme-border bg-theme-element px-2.5 text-center text-xs font-bold text-theme-sub shadow-sm">
                    {count}
                </span>
            </div>
        )}
      </div>

      {/* --- Row 2: Sort & Category Pills --- */}
      {!isModern && (
        <div className="mx-auto flex min-w-0 w-full max-w-[56rem] items-center gap-2 px-1 sm:px-2">
          
          {/* Profile Circle */}
          {onProfileClick && (
            <button
              onClick={onProfileClick}
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-primary to-acid overflow-hidden shadow-sm shadow-primary/20 transition-all active:scale-90 hover:scale-105"
              title="Profile"
            >
              {profileAvatarUrl ? (
                <img src={profileAvatarUrl} alt="" className="h-full w-full rounded-full object-cover bg-white" />
              ) : (
                <span className="text-[11px] font-black text-white">{profileInitial || 'U'}</span>
              )}
            </button>
          )}

          {/* Sort Dropdown (Fixed Position) */}
          <div className="relative shrink-0" ref={sortRef}>
              <button
                  onClick={() => setIsSortDropdownOpen(!isSortDropdownOpen)}
                  className={`flex h-9 items-center gap-2 rounded-xl border px-3 text-xs font-bold whitespace-nowrap transition-all active:scale-95 ${isSortDropdownOpen ? 'border-primary bg-primary text-white shadow-md shadow-primary/20' : 'border-theme-border bg-card text-theme-text hover:bg-theme-element'}`}
              >
                  <i className="fas fa-sort text-[10px] opacity-70"></i>
                  <span className="max-w-[70px] truncate">{selectedSort.split(' ')[0]}</span>
                  <i className={`fas fa-chevron-${isSortDropdownOpen ? 'up' : 'down'} text-[8px] opacity-50`}></i>
              </button>
              
              {isSortDropdownOpen && (
                  <div className="absolute top-full left-0 mt-2 w-48 bg-surface border border-theme-border rounded-2xl shadow-xl overflow-hidden z-[90] animate-slide-up origin-top-left">
                      {sortOptions.map(option => (
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

          {/* Scrollable Category Pills (Only this part scrolls now) */}
          <div className="flex min-w-0 flex-1 items-center gap-2 overflow-x-auto snap-x snap-mandatory -mx-3 px-4 py-4 -my-3 no-scrollbar [scroll-padding-inline:1rem]">
              {categories.map(cat => (
                  <button
                      key={cat}
                      onClick={() => setSelectedCategory(cat)}
                      className={`snap-start h-9 rounded-xl border px-4 text-xs font-bold whitespace-nowrap transition-all active:scale-95 ${
                          selectedCategory === cat
                          ? 'border-theme-text bg-theme-text text-surface shadow-md'
                          : 'border-theme-border bg-card text-theme-sub hover:border-theme-sub hover:text-theme-text'
                      }`}
                  >
                      {cat}
                  </button>
              ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default React.memo(StoreFilters);
