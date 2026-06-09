import React, { useCallback, useState } from 'react';
import { shallow } from 'zustand/shallow';
import AboutView from './AboutView';
import { AppItem, DevProfile, FAQItem, SocialLinks } from '../types';
import { useSettingsStore } from '../store/useAppStore';

interface AboutTabContainerProps {
  devProfile: DevProfile;
  socialLinks: SocialLinks;
  faqs: FAQItem[];
  handleProfileClick: (view?: 'profile' | 'badges', badgeIndex?: number) => void;
  setShowFAQ: (show: boolean) => void;
  onOpenAdDonation: () => void;
  currentStoreVersion: string;
  onWipeCache: () => void;
  onTestStoreUpdate: () => void;
  mirrorSource: string;
  availableUpdates: AppItem[];
  onTriggerUpdate: (app: AppItem) => void;
  onTriggerDebugToast: (type: 'install' | 'error' | 'cleanup') => void;
  onTriggerModernUITutorial: () => void;
  onReloadApps: () => void;
}

const AboutTabContainer: React.FC<AboutTabContainerProps> = ({
  devProfile,
  socialLinks,
  faqs,
  handleProfileClick,
  setShowFAQ,
  onOpenAdDonation,
  currentStoreVersion,
  onWipeCache,
  onTestStoreUpdate,
  mirrorSource,
  availableUpdates,
  onTriggerUpdate,
  onTriggerDebugToast,
  onTriggerModernUITutorial,
  onReloadApps
}) => {
  const [isEditingToken, setIsEditingToken] = useState(false);
  const {
    isLegend,
    isContributor,
    adWatchCount,
    isDevUnlocked,
    useRemoteJson,
    githubToken,
    hiddenTabs,
    autoUpdateEnabled,
    setDevUnlocked,
    toggleHiddenTab,
    toggleAutoUpdate,
    setUseRemoteJson,
    setGithubToken,
    unlockedBadges
  } = useSettingsStore((state) => ({
    isLegend: state.isLegend,
    isContributor: state.isContributor,
    adWatchCount: state.adWatchCount,
    isDevUnlocked: state.isDevUnlocked,
    useRemoteJson: state.useRemoteJson,
    githubToken: state.githubToken,
    hiddenTabs: state.hiddenTabs,
    autoUpdateEnabled: state.autoUpdateEnabled,
    setDevUnlocked: state.setDevUnlocked,
    toggleHiddenTab: state.toggleHiddenTab,
    toggleAutoUpdate: state.toggleAutoUpdate,
    setUseRemoteJson: state.setUseRemoteJson,
    setGithubToken: state.setGithubToken,
    unlockedBadges: state.unlockedBadges
  }), shallow);

  const handleToggleSourceMode = useCallback(() => {
    setUseRemoteJson(!useRemoteJson);
  }, [setUseRemoteJson, useRemoteJson]);

  const handleSaveGithubToken = useCallback((token: string) => {
    setGithubToken(token);
    setIsEditingToken(false);
    window.setTimeout(onReloadApps, 500);
  }, [onReloadApps, setGithubToken]);

  return (
    <AboutView
      devProfile={devProfile}
      socialLinks={socialLinks}
      faqs={faqs}
      isLegend={isLegend}
      isContributor={isContributor}
      adWatchCount={adWatchCount}
      handleProfileClick={handleProfileClick}
      setShowFAQ={setShowFAQ}
      onOpenAdDonation={onOpenAdDonation}
      isDevUnlocked={isDevUnlocked}
      useRemoteJson={useRemoteJson}
      toggleSourceMode={handleToggleSourceMode}
      githubToken={githubToken}
      isEditingToken={isEditingToken}
      setIsEditingToken={setIsEditingToken}
      saveGithubToken={handleSaveGithubToken}
      currentStoreVersion={currentStoreVersion}
      onWipeCache={onWipeCache}
      onTestStoreUpdate={onTestStoreUpdate}
      mirrorSource={mirrorSource}
      hiddenTabs={hiddenTabs}
      toggleHiddenTab={toggleHiddenTab}
      autoUpdateEnabled={autoUpdateEnabled}
      toggleAutoUpdate={toggleAutoUpdate}
      availableUpdates={availableUpdates}
      onTriggerUpdate={onTriggerUpdate}
      onTriggerDebugToast={onTriggerDebugToast}
      setDevUnlocked={setDevUnlocked}
      onTriggerModernUITutorial={onTriggerModernUITutorial}
      unlockedBadges={unlockedBadges}
    />
  );
};

export default React.memo(AboutTabContainer);
