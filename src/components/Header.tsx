import { useTranslation } from 'react-i18next';
import { useStore, useTheme, useConfig } from '@/store';
import { compareVersions } from '@/utils/sanitize';
import { CURRENT_STORE_VERSION } from '@/constants';

export function Header() {
  const { t, i18n } = useTranslation();
  const theme = useTheme();
  const config = useConfig();
  const { activeTab, setActiveTab, cycleTheme, handleDevTap } = useStore();

  const hasStoreUpdate = config?.latestStoreVersion !== undefined && compareVersions(config.latestStoreVersion, CURRENT_STORE_VERSION) > 0;
  const downloadUrl = config?.storeDownloadUrl;

  const toggleLanguage = () => {
    void i18n.changeLanguage(i18n.language === 'en' ? 'ar' : 'en');
  };

  return (
    <header className="absolute top-0 left-0 w-full z-20 px-6 py-6 flex justify-between items-center animate-fade-in entrance-header">
      <div className="flex items-center gap-3 select-none">
        <div className="w-10 h-10 bg-primary text-white rounded-xl flex items-center justify-center shadow-lg shadow-primary/30 transform rotate-3 animate-float">
          <i className="fas fa-shapes text-lg" />
        </div>
        <h1 onClick={handleDevTap} className="text-2xl font-black tracking-tighter text-theme-text cursor-pointer active:scale-95 transition-transform">
          Orion<span className="text-primary">Store</span>
        </h1>
      </div>

      <div className="flex items-center gap-2">
        {hasStoreUpdate && downloadUrl && (
          <button
            onClick={() => { window.location.href = downloadUrl; }}
            className="px-3 py-2 rounded-xl bg-acid/20 text-acid-dark dusk:text-acid border border-acid/30 font-bold text-xs flex items-center gap-2 animate-pulse"
          >
            <i className="fas fa-arrow-circle-up" />
            <span className="hidden sm:inline">{t('header.update')}</span>
          </button>
        )}
        <button
          onClick={toggleLanguage}
          className="w-10 h-10 rounded-xl bg-theme-element hover:bg-theme-hover flex items-center justify-center text-theme-sub hover:text-primary transition-all active:scale-95 text-xs font-bold"
          title={t(`language.${i18n.language === 'en' ? 'ar' : 'en'}`)}
        >
          {i18n.language === 'en' ? 'AR' : 'EN'}
        </button>
        <button
          onClick={() => setActiveTab('about')}
          className={`w-10 h-10 rounded-xl flex items-center justify-center transition-all active:scale-95 ${
            activeTab === 'about'
              ? 'bg-primary text-white shadow-lg shadow-primary/25'
              : 'bg-theme-element text-theme-sub hover:text-theme-text'
          }`}
          title={t('header.about')}
        >
          <i className="fas fa-user" />
        </button>
        <button
          onClick={cycleTheme}
          className="w-10 h-10 rounded-xl bg-theme-element hover:bg-theme-hover flex items-center justify-center text-theme-sub hover:text-primary transition-all active:scale-95"
          title={t('header.theme', { theme })}
        >
          <i className={`fas ${theme === 'light' ? 'fa-sun' : theme === 'dusk' ? 'fa-moon' : 'fa-circle-half-stroke'}`} />
        </button>
      </div>
    </header>
  );
}
