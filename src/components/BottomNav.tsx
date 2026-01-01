import { memo } from 'react';
import { useStore } from '@/store';
import type { Tab } from '@/types';

const tabs: { id: Tab; icon: string; label: string }[] = [
  { id: 'android', icon: 'fab fa-android', label: 'Apps' },
  { id: 'pc', icon: 'fas fa-desktop', label: 'PC' },
  { id: 'about', icon: 'fas fa-code', label: 'Dev' },
];

export const BottomNav = memo(function BottomNav() {
  const { activeTab, setActiveTab } = useStore();

  return (
    <nav className="fixed bottom-6 left-1/2 -translate-x-1/2 z-40 bg-surface/90 backdrop-blur-xl border border-theme-border p-2 rounded-[2rem] shadow-2xl flex items-center gap-1 animate-slide-up">
      {tabs.map(({ id, icon, label }) => (
        <button
          key={id}
          onClick={() => setActiveTab(id)}
          className={`px-6 py-3 rounded-[1.5rem] font-bold transition-all duration-300 flex items-center gap-2 ${activeTab === id ? 'bg-primary text-white shadow-lg shadow-primary/30 scale-105' : 'text-theme-sub hover:bg-theme-element'}`}
        >
          <i className={`${icon} text-lg`} />
          {activeTab === id && <span className="animate-fade-in text-sm">{label}</span>}
        </button>
      ))}
    </nav>
  );
});
