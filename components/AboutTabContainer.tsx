import React, { useCallback, useState } from 'react';
import { shallow } from 'zustand/shallow';
import AboutView from './AboutView';
import { AppItem, DevProfile, DonationConfig, FAQItem, SocialLinks } from '../types';
import { useSettingsStore } from '../store/useAppStore';

interface AboutTabContainerProps {
  devProfile: DevProfile;
  socialLinks: SocialLinks;
  donation?: DonationConfig;
  faqs: FAQItem[];
  handleProfileClick: (view?: 'profile' | 'badges', badgeIndex?: number) => void;
  setShowFAQ: (show: boolean) => void;
  onOpenAdDonation: () => void;
  currentStoreVersion: string;
  onWipeCache: () => void;
  mirrorSource: string;
  availableUpdates: AppItem[];
  onTriggerUpdate: (app: AppItem) => void;
  onTriggerDebugToast: (type: 'install' | 'error' | 'cleanup') => void;
  onTriggerModernUITutorial: () => void;
  onReloadApps: () => void;
  onTriggerForcedUpdateTest: () => void;
}

const AboutTabContainer: React.FC<AboutTabContainerProps> = ({
  devProfile,
  socialLinks,
  donation,
  faqs,
  handleProfileClick,
  setShowFAQ,
  onOpenAdDonation,
  currentStoreVersion,
  onWipeCache,
  mirrorSource,
  availableUpdates,
  onTriggerUpdate,
  onTriggerDebugToast,
  onTriggerModernUITutorial,
  onReloadApps,
  onTriggerForcedUpdateTest
}) => {
  const [isEditingToken, setIsEditingToken] = useState(false);
  const {
    isLegend,
    isContributor,
    adWatchCount,
    isDevUnlocked,
    githubToken,
    hiddenTabs,
    autoUpdateEnabled,
    useRemoteJson,
    setDevUnlocked,
    toggleHiddenTab,
    toggleAutoUpdate,
    setGithubToken,
    setUseRemoteJson,
    unlockedBadges
  } = useSettingsStore((state) => ({
    isLegend: state.isLegend,
    isContributor: state.isContributor,
    adWatchCount: state.adWatchCount,
    isDevUnlocked: state.isDevUnlocked,
    githubToken: state.githubToken,
    hiddenTabs: state.hiddenTabs,
    autoUpdateEnabled: state.autoUpdateEnabled,
    useRemoteJson: state.useRemoteJson,
    setDevUnlocked: state.setDevUnlocked,
    toggleHiddenTab: state.toggleHiddenTab,
    toggleAutoUpdate: state.toggleAutoUpdate,
    setGithubToken: state.setGithubToken,
    setUseRemoteJson: state.setUseRemoteJson,
    unlockedBadges: state.unlockedBadges
  }), shallow);

  const toggleSourceMode = useCallback(() => {
    setUseRemoteJson(!useRemoteJson);
  }, [useRemoteJson, setUseRemoteJson]);

  const handleSaveGithubToken = useCallback((token: string) => {
    setGithubToken(token);
    setIsEditingToken(false);
    window.setTimeout(onReloadApps, 500);
  }, [onReloadApps, setGithubToken]);

  return (
    <AboutView
      devProfile={devProfile}
      socialLinks={socialLinks}
      donation={donation}
      faqs={faqs}
      isLegend={isLegend}
      isContributor={isContributor}
      adWatchCount={adWatchCount}
      handleProfileClick={handleProfileClick}
      setShowFAQ={setShowFAQ}
      onOpenAdDonation={onOpenAdDonation}
      isDevUnlocked={isDevUnlocked}
      githubToken={githubToken}
      isEditingToken={isEditingToken}
      setIsEditingToken={setIsEditingToken}
      saveGithubToken={handleSaveGithubToken}
      currentStoreVersion={currentStoreVersion}
      onWipeCache={onWipeCache}
      mirrorSource={mirrorSource}
      hiddenTabs={hiddenTabs}
      useRemoteJson={useRemoteJson}
      toggleSourceMode={toggleSourceMode}
      toggleHiddenTab={toggleHiddenTab}
      autoUpdateEnabled={autoUpdateEnabled}
      toggleAutoUpdate={toggleAutoUpdate}
      availableUpdates={availableUpdates}
      onTriggerUpdate={onTriggerUpdate}
      onTriggerDebugToast={onTriggerDebugToast}
      setDevUnlocked={setDevUnlocked}
      onTriggerModernUITutorial={onTriggerModernUITutorial}
      unlockedBadges={unlockedBadges}
      onTriggerForcedUpdateTest={onTriggerForcedUpdateTest}
    />
  );
};

export default React.memo(AboutTabContainer);
