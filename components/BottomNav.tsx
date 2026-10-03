
import React from 'react';
import { Tab } from '../types';

interface BottomNavProps {
  activeTab: Tab;
  onTabChange: (tab: Tab) => void;
  hiddenTabs?: string[];
  glassEffect?: boolean;
  scale?: number;
}

const BottomNav: React.FC<BottomNavProps> = ({ activeTab, onTabChange, hiddenTabs = [], glassEffect = true, scale = 1 }) => {
  return (
    <div className="fixed left-0 right-0 z-40 flex justify-center pointer-events-none" style={{ bottom: 'calc(1rem + env(safe-area-inset-bottom, 0px))' }}>
      <div
        className="orion-bottom-nav-scale pointer-events-auto"
        style={{ '--bottom-nav-scale': scale } as React.CSSProperties}
      >
       <nav className={`${glassEffect ? 'bg-surface/95 backdrop-blur-lg' : 'bg-surface'} border border-theme-border p-1 rounded-[1.75rem] shadow-2xl flex items-center gap-0.5 animate-slide-up pointer-events-auto`}>
         
         {!hiddenTabs.includes('android') && (
             <button 
                onClick={() => onTabChange('android')}
                className={`group px-3 py-2 rounded-[1.25rem] font-bold transition-all duration-200 flex items-center justify-center ${activeTab === 'android' ? 'bg-primary text-white shadow-md shadow-primary/25' : 'text-theme-sub hover:bg-theme-element'}`}
             >
                <i className="fab fa-android text-base"></i>
                {activeTab === 'android' && <span className="animate-fade-in text-xs ml-1">Apps</span>}
             </button>
         )}

         {!hiddenTabs.includes('pc') && (
             <button 
                onClick={() => onTabChange('pc')}
                className={`group px-3 py-2 rounded-[1.25rem] font-bold transition-all duration-200 flex items-center justify-center ${activeTab === 'pc' ? 'bg-primary text-white shadow-md shadow-primary/25' : 'text-theme-sub hover:bg-theme-element'}`}
             >
                <i className="fab fa-windows text-base"></i>
                {activeTab === 'pc' && <span className="animate-fade-in text-xs ml-1">PC</span>}
             </button>
         )}

         {!hiddenTabs.includes('tv') && (
             <button 
                onClick={() => onTabChange('tv')}
                className={`group px-3 py-2 rounded-[1.25rem] font-bold transition-all duration-200 flex items-center justify-center ${activeTab === 'tv' ? 'bg-primary text-white shadow-md shadow-primary/25' : 'text-theme-sub hover:bg-theme-element'}`}
             >
                <i className="fas fa-tv text-base"></i>
                {activeTab === 'tv' && <span className="animate-fade-in text-xs ml-1">TV</span>}
             </button>
         )}

         {!hiddenTabs.includes('myapps') && (
             <button
                id="nav-tab-my-apps"
                onClick={() => onTabChange('myapps')}
                className={`group px-3 py-2 rounded-[1.25rem] font-bold transition-all duration-200 flex items-center justify-center ${activeTab === 'myapps' ? 'bg-primary text-white shadow-md shadow-primary/25' : 'text-theme-sub hover:bg-theme-element'}`}
             >
                <i className="fas fa-box-open text-base"></i>
                {activeTab === 'myapps' && <span className="animate-fade-in text-xs ml-1">My Apps</span>}
             </button>
         )}

         <button 
            id="nav-tab-about"
            onClick={() => onTabChange('about')}
            className={`px-3 py-2 rounded-[1.25rem] font-bold transition-all duration-200 flex items-center gap-1.5 ${activeTab === 'about' ? 'bg-primary text-white shadow-md shadow-primary/25' : 'text-theme-sub hover:bg-theme-element'}`}
         >
            <i className="fas fa-code text-base"></i>
            {activeTab === 'about' && <span className="animate-fade-in text-xs ml-1">Dev</span>}
         </button>
      </nav>
      </div>
    </div>
  );
};

export default React.memo(BottomNav);
