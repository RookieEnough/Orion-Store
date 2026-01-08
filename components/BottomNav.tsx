
import React from 'react';
import { Tab } from '../types';

interface BottomNavProps {
  activeTab: Tab;
  onTabChange: (tab: Tab) => void;
  hiddenTabs?: string[];
}

const BottomNav: React.FC<BottomNavProps> = ({ activeTab, onTabChange, hiddenTabs = [] }) => {
  return (
    <div className="fixed bottom-6 left-0 right-0 z-40 flex justify-center pointer-events-none safe-area-pb">
       {/* Optimized: Reduced blur to lg (16px) or less, used darker bg opacity to compensate */}
       <nav className="bg-surface/95 backdrop-blur-lg border border-theme-border p-2 rounded-[2rem] shadow-2xl flex items-center gap-1 animate-slide-up pointer-events-auto transform translate-z-0">
         
         {!hiddenTabs.includes('android') && (
             <button 
                onClick={() => onTabChange('android')}
                className={`group px-5 py-3 rounded-[1.5rem] font-bold transition-all duration-200 flex items-center justify-center ${activeTab === 'android' ? 'bg-primary text-white shadow-lg shadow-primary/30 scale-105' : 'text-theme-sub hover:bg-theme-element'}`}
             >
                <i className="fab fa-android text-lg"></i>
                {activeTab === 'android' && <span className="animate-fade-in text-sm ml-2">Apps</span>}
             </button>
         )}

         {!hiddenTabs.includes('pc') && (
             <button 
                onClick={() => onTabChange('pc')}
                className={`group px-5 py-3 rounded-[1.5rem] font-bold transition-all duration-200 flex items-center justify-center ${activeTab === 'pc' ? 'bg-primary text-white shadow-lg shadow-primary/30 scale-105' : 'text-theme-sub hover:bg-theme-element'}`}
             >
                <i className="fas fa-desktop text-lg"></i>
                {activeTab === 'pc' && <span className="animate-fade-in text-sm ml-2">PC</span>}
             </button>
         )}

         {!hiddenTabs.includes('tv') && (
             <button 
                onClick={() => onTabChange('tv')}
                className={`group px-5 py-3 rounded-[1.5rem] font-bold transition-all duration-200 flex items-center justify-center ${activeTab === 'tv' ? 'bg-primary text-white shadow-lg shadow-primary/30 scale-105' : 'text-theme-sub hover:bg-theme-element'}`}
             >
                <i className="fas fa-tv text-lg"></i>
                {activeTab === 'tv' && <span className="animate-fade-in text-sm ml-2">TV</span>}
             </button>
         )}

         <button 
            onClick={() => onTabChange('about')}
            className={`px-6 py-3 rounded-[1.5rem] font-bold transition-all duration-200 flex items-center gap-2 ${activeTab === 'about' ? 'bg-primary text-white shadow-lg shadow-primary/30 scale-105' : 'text-theme-sub hover:bg-theme-element'}`}
         >
            <i className="fas fa-code text-lg"></i>
            {activeTab === 'about' && <span className="animate-fade-in text-sm">Dev</span>}
         </button>
      </nav>
    </div>
  );
};

export default BottomNav;
