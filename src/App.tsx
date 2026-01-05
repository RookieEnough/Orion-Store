import { useStore, useTheme, useConfig } from '@/store';
import { Header } from '@/components/Header';
import { SearchBar } from '@/components/SearchBar';
import { CategoryFilter } from '@/components/CategoryFilter';
import { AppGrid } from '@/components/AppGrid';
import { AppDetail } from '@/components/AppDetail';
import { BottomNav } from '@/components/BottomNav';
import { Toast } from '@/components/Toast';
import { ScrollToTop } from '@/components/ScrollToTop';
import { FAQModal } from '@/components/FAQModal';
import { AboutView } from '@/components/AboutView';

export default function App() {
  const { activeTab, selectedApp, setSelectedApp, showFAQ, setShowFAQ, devToast, isLoading, isRefreshing } = useStore();
  const theme = useTheme();
  const config = useConfig();

  if (config?.maintenanceMode) return <div className="min-h-screen flex items-center justify-center bg-bg text-text">Maintenance Mode</div>;

  return (
    <div className={`min-h-screen bg-bg text-text transition-colors ${theme}`}>
      <Header />
      {activeTab !== 'about' && (
        <div className="px-6 pt-24">
          <SearchBar />
          <CategoryFilter />
        </div>
      )}
      <main className="pb-24">
        {activeTab === 'about' ? <AboutView /> : <AppGrid />}
      </main>
      <BottomNav />
      <ScrollToTop />
      {selectedApp && <AppDetail app={selectedApp} onClose={() => setSelectedApp(null)} />}
      {showFAQ && <FAQModal onClose={() => setShowFAQ(false)} />}
      {devToast.show && <Toast message={devToast.msg} />}
      {(isLoading || isRefreshing) && (
        <div className="fixed inset-0 bg-black/20 flex items-center justify-center z-50 pointer-events-none">
          <div className="w-8 h-8 border-4 border-accent border-t-transparent rounded-full animate-spin" />
        </div>
      )}
    </div>
  );
}
