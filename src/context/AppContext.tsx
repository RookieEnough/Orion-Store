import { createContext, useContext, useState, useCallback, useMemo, type ReactNode } from 'react';
import { useTheme, useDevMode, useApps, useLocalStorage, type Theme } from '@/hooks';
import { STORAGE_KEYS } from '@/utils/storage';
import { hasUpdate, sanitizeUrl } from '@/utils';
import { DEV_SOCIALS, DEFAULT_FAQS, DEFAULT_DEV_PROFILE, DEFAULT_SUPPORT_EMAIL, DEFAULT_EASTER_EGG } from '@/constants';
import type { AppItem, Tab, StoreConfig, FAQItem, DevProfile, SocialLinks } from '@/types';

interface AppContextValue {
  theme: Theme;
  cycleTheme: () => void;
  activeTab: Tab;
  setActiveTab: (tab: Tab) => void;
  apps: AppItem[];
  isLoading: boolean;
  isRefreshing: boolean;
  refresh: () => void;
  installedVersions: Record<string, string>;
  registerInstall: (appId: string, version: string) => void;
  selectedApp: AppItem | null;
  setSelectedApp: (app: AppItem | null) => void;
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  selectedCategory: string;
  setSelectedCategory: (category: string) => void;
  config: StoreConfig | null;
  socialLinks: SocialLinks;
  faqs: FAQItem[];
  devProfile: DevProfile;
  supportEmail: string;
  easterEggUrl: string;
  isDevUnlocked: boolean;
  handleDevTap: () => void;
  devToastMessage: string;
  showDevToast: boolean;
  useRemoteJson: boolean;
  toggleSourceMode: () => void;
  githubToken: string;
  saveGithubToken: (token: string) => void;
  handleDownload: (app: AppItem, specificUrl?: string) => void;
  checkHasUpdate: (app: AppItem) => boolean;
  showFAQ: boolean;
  setShowFAQ: (show: boolean) => void;
}

const AppContext = createContext<AppContextValue | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const { theme, cycleTheme } = useTheme();
  const devMode = useDevMode();
  const [activeTab, setActiveTab] = useState<Tab>('android');
  const [selectedApp, setSelectedApp] = useState<AppItem | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('All');
  const [showFAQ, setShowFAQ] = useState(false);
  const [useRemoteJson, setUseRemoteJson] = useLocalStorage(STORAGE_KEYS.USE_REMOTE, true);
  const [githubToken, setGithubToken] = useLocalStorage(STORAGE_KEYS.GH_TOKEN, '');

  const { apps, isLoading, isRefreshing, config, installedVersions, refresh, registerInstall } = useApps(useRemoteJson, githubToken);

  const socialLinks = config?.socials ?? DEV_SOCIALS;
  const faqs = config?.faqs ?? DEFAULT_FAQS;
  const devProfile = config?.devProfile ?? DEFAULT_DEV_PROFILE;
  const supportEmail = config?.supportEmail ?? DEFAULT_SUPPORT_EMAIL;
  const easterEggUrl = config?.easterEggUrl ?? DEFAULT_EASTER_EGG;

  const toggleSourceMode = useCallback(() => setUseRemoteJson(prev => !prev), [setUseRemoteJson]);

  const saveGithubToken = useCallback((token: string) => {
    setGithubToken(token);
    setTimeout(refresh, 500);
  }, [setGithubToken, refresh]);

  const handleDownload = useCallback((app: AppItem, specificUrl?: string) => {
    const url = sanitizeUrl(specificUrl || app.downloadUrl);
    if (url === '#') return;

    const isWebLink = !url.toLowerCase().endsWith('.apk') && !url.toLowerCase().endsWith('.exe');
    if (isWebLink) {
      window.open(url, '_blank');
      return;
    }

    registerInstall(app.id, app.latestVersion);
    app.platform === 'PC' ? window.open(url, '_blank') : (window.location.href = url);
  }, [registerInstall]);

  const checkHasUpdate = useCallback((app: AppItem) => hasUpdate(installedVersions[app.id], app.latestVersion), [installedVersions]);

  const handleTabChange = useCallback((tab: Tab) => {
    setActiveTab(tab);
    setSearchQuery('');
    setSelectedCategory('All');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, []);

  const value = useMemo<AppContextValue>(() => ({
    theme, cycleTheme, activeTab, setActiveTab: handleTabChange,
    apps, isLoading, isRefreshing, refresh, installedVersions, registerInstall,
    selectedApp, setSelectedApp, searchQuery, setSearchQuery, selectedCategory, setSelectedCategory,
    config, socialLinks, faqs, devProfile, supportEmail, easterEggUrl,
    isDevUnlocked: devMode.isUnlocked, handleDevTap: devMode.handleTap,
    devToastMessage: devMode.toastMessage, showDevToast: devMode.showToast,
    useRemoteJson, toggleSourceMode, githubToken, saveGithubToken,
    handleDownload, checkHasUpdate, showFAQ, setShowFAQ,
  }), [
    theme, cycleTheme, activeTab, handleTabChange, apps, isLoading, isRefreshing, refresh,
    installedVersions, registerInstall, selectedApp, searchQuery, selectedCategory, config,
    socialLinks, faqs, devProfile, supportEmail, easterEggUrl, devMode, useRemoteJson,
    toggleSourceMode, githubToken, saveGithubToken, handleDownload, checkHasUpdate, showFAQ,
  ]);

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useAppContext() {
  const context = useContext(AppContext);
  if (!context) throw new Error('useAppContext must be used within AppProvider');
  return context;
}
