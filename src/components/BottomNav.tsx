import { memo } from 'react';
import { useStore } from '@/store';
import type { Tab } from '@/types';

const tabs: { id: Tab; icon: string; label: string }[] = [
  { id: 'android', icon: 'fab fa-android', label: 'Apps' },
  { id: 'pc', icon: 'fas fa-desktop', label: 'PC' },
];

export const BottomNav = memo(function BottomNav() {
  const { activeTab, setActiveTab } = useStore();

  return (
    <nav className="fixed bottom-6 left-1/2 -translate-x-1/2 z-40 bg-card/90 backdrop-blur-xl border border-theme-border p-1.5 rounded-2xl shadow-2xl flex items-center gap-1 animate-slide-up">
      {tabs.map(({ id, icon, label }) => (
        <button
          key={id}
          onClick={() => setActiveTab(id)}
          className={`px-4 py-2.5 rounded-xl font-medium text-sm transition-all flex items-center gap-2 active:scale-95 ${
            activeTab === id
              ? 'bg-primary text-white shadow-lg shadow-primary/25'
              : 'text-theme-sub hover:text-theme-text hover:bg-theme-element'
          }`}
        >
          <i className={icon} />
          <span>{label}</span>
        </button>
      ))}
    </nav>
  );
});
