
import React from 'react';
import { Tab } from '../types';

interface HeaderProps {
  onTitleClick: () => void;
  storeUpdateAvailable: boolean;
  onUpdateStore: () => void;
  theme: 'light' | 'dusk' | 'dark' | 'oled';
  toggleTheme: () => void;
  activeTab: Tab;
  onOpenSettings: () => void;
  updateCount?: number;
  activeDownloadCount?: number;
}

const Header: React.FC<HeaderProps> = ({ 
  onTitleClick, 
  storeUpdateAvailable, 
  onUpdateStore, 
  theme, 
  toggleTheme,
  activeTab,
  onOpenSettings,
  updateCount = 0,
  activeDownloadCount = 0
}) => {
  const hasNotifications = updateCount > 0 || activeDownloadCount > 0 || storeUpdateAvailable;

  return (
    <header className="relative z-30 w-full px-6 pb-6 pt-[calc(1.5rem+env(safe-area-inset-top))] flex justify-between items-center bg-surface transition-all duration-300">
        <div className="flex items-center gap-3 select-none relative group">
            <div className="relative">
                <div className="w-10 h-10 bg-primary text-white rounded-xl flex items-center justify-center shadow-lg shadow-primary/30 transform rotate-3">
                    <i className="fas fa-shapes text-lg"></i>
                </div>
            </div>
            
            <div className="relative">
                <h1 
                    onClick={onTitleClick}
                    className="text-2xl font-black tracking-tighter text-theme-text cursor-pointer active:scale-95 transition-transform relative z-10"
                >
                    Orion<span className="text-primary">Store</span>
                </h1>
            </div>
        </div>
        
        <div className="flex items-center gap-3">
            {storeUpdateAvailable && (
                <button
                    onClick={onUpdateStore}
                    className="px-3 py-2 rounded-xl bg-acid/20 text-lime-700 dark:text-acid border border-acid/30 font-bold text-xs flex items-center gap-2 animate-pulse"
                    title="Update Orion Store"
                >
                    <i className="fas fa-arrow-circle-up"></i>
                    <span className="hidden sm:inline">Store Update</span>
                </button>
            )}

            <div className="relative">
                <button 
                    onClick={onOpenSettings}
                    className={`w-10 h-10 rounded-full bg-theme-element hover:bg-theme-hover flex items-center justify-center text-theme-sub hover:text-primary transition-all hover:scale-110 active:scale-95 shadow-sm ${activeDownloadCount > 0 ? 'animate-pulse text-primary' : ''}`}
                    title="Settings & Updates"
                >
                    <i className={`fas ${activeDownloadCount > 0 ? 'fa-spinner fa-spin' : 'fa-cog'} text-lg`}></i>
                </button>
                {hasNotifications && (
                    <span className="absolute -top-1 -right-1 flex h-4 w-4">
                        <span className={`animate-ping absolute inline-flex h-full w-full rounded-full opacity-75 ${activeDownloadCount > 0 ? 'bg-primary' : 'bg-acid'}`}></span>
                        <span className={`relative inline-flex rounded-full h-4 w-4 border-2 border-surface flex items-center justify-center text-[8px] font-black text-white ${activeDownloadCount > 0 ? 'bg-primary' : 'bg-acid text-black'}`}>
                            {activeDownloadCount > 0 ? activeDownloadCount : updateCount > 0 ? updateCount : '!'}
                        </span>
                    </span>
                )}
            </div>

            {activeTab !== 'about' && (
                <button 
                    onClick={toggleTheme}
                    className="w-10 h-10 rounded-full bg-theme-element hover:bg-theme-hover flex items-center justify-center text-theme-sub hover:text-acid transition-all hover:scale-110 active:scale-95"
                    title={`Theme: ${theme}`}
                >
                    <i className={`fas ${theme === 'light' ? 'fa-sun' : theme === 'dusk' ? 'fa-cloud-sun' : 'fa-moon'}`}></i>
                </button>
            )}
        </div>
    </header>
  );
};

export default Header;
