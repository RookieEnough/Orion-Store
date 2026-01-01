import { AppProvider, useAppContext } from '@/context/AppContext';
import { Header, BottomNav, ScrollToTop, AppGrid, AboutView, AppDetail, FAQModal, Toast } from '@/components';

function AppContent() {
  const {
    activeTab, selectedApp, setSelectedApp, handleDownload, checkHasUpdate,
    installedVersions, supportEmail, showFAQ, setShowFAQ, faqs, showDevToast, devToastMessage,
  } = useAppContext();

  return (
    <div className="min-h-screen bg-surface text-theme-text transition-colors duration-300 font-sans selection:bg-primary/30">
      <Header />
      <main className="max-w-7xl mx-auto w-full pt-24">
        {activeTab === 'android' && <AppGrid platform="Android" title="Featured Apps" searchPlaceholder="Search Android Apps..." />}
        {activeTab === 'pc' && <AppGrid platform="PC" title="PC Software" searchPlaceholder="Search PC Software..." showBanner />}
        {activeTab === 'about' && <AboutView />}
      </main>
      <BottomNav />
      <ScrollToTop />
      {selectedApp && (
        <AppDetail
          app={selectedApp}
          onClose={() => setSelectedApp(null)}
          onDownload={handleDownload}
          localVersion={installedVersions[selectedApp.id]}
          hasUpdate={checkHasUpdate(selectedApp)}
          supportEmail={supportEmail}
        />
      )}
      {showFAQ && <FAQModal items={faqs} onClose={() => setShowFAQ(false)} />}
      <Toast message={devToastMessage} type="info" visible={showDevToast} />
    </div>
  );
}

export default function App() {
  return (
    <AppProvider>
      <AppContent />
    </AppProvider>
  );
}
