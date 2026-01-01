import { memo } from 'react';
import { useAppContext } from '@/context/AppContext';

interface SearchBarProps {
  placeholder: string;
}

export const SearchBar = memo(function SearchBar({ placeholder }: SearchBarProps) {
  const { searchQuery, setSearchQuery } = useAppContext();

  return (
    <div className="relative mb-6 group z-10 animate-fade-in">
      <div className="absolute -inset-0.5 bg-gradient-to-r from-acid via-primary to-neon rounded-2xl opacity-20 group-focus-within:opacity-100 transition duration-500 blur group-focus-within:blur-md" />
      <div className="relative flex items-center bg-theme-input rounded-2xl border border-theme-border p-1 shadow-lg transition-transform group-focus-within:scale-[1.01]">
        <div className="pl-4 pr-3 text-theme-sub group-focus-within:text-acid transition-colors">
          <i className="fas fa-search text-lg" />
        </div>
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder={placeholder}
          className="w-full bg-transparent border-none outline-none focus:outline-none focus:ring-0 text-theme-text placeholder-gray-500 h-12 font-medium text-lg"
        />
        {searchQuery && (
          <button
            onClick={() => setSearchQuery('')}
            className="w-10 h-10 flex items-center justify-center text-theme-sub hover:text-red-500 transition-colors"
          >
            <i className="fas fa-times" />
          </button>
        )}
      </div>
    </div>
  );
});
