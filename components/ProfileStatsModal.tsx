import React, { useEffect, useMemo, useState } from 'react';
import { useSettingsStore, useDataStore } from '../store/useAppStore';
import { useScrollLock } from '../hooks/useScrollLock';

const AVATAR_CHOICES = [
    { id: 'ears1', name: 'Imp', url: 'https://api.dicebear.com/9.x/big-ears/svg?seed=Avery' },
    { id: 'pixel1', name: '8-Bit', url: 'https://api.dicebear.com/9.x/pixel-art/svg?seed=Avery' },
    { id: 'mecha', name: 'Mecha', url: 'https://api.dicebear.com/9.x/bottts/svg?seed=Mecha&backgroundColor=c0aede' },
    { id: 'pixel3', name: 'Voxel', url: 'https://api.dicebear.com/9.x/pixel-art/svg?seed=Kimberly' },
    { id: 'pixel2', name: 'Retro', url: 'https://api.dicebear.com/9.x/pixel-art/svg?seed=Wyatt' },
    { id: 'ears2', name: 'Pixie', url: 'https://api.dicebear.com/9.x/big-ears/svg?seed=Sara' },
    { id: 'smile1', name: 'Cheery', url: 'https://api.dicebear.com/9.x/big-smile/svg?seed=Jessica' },
    { id: 'unit', name: 'Unit 01', url: 'https://api.dicebear.com/9.x/bottts/svg?seed=Unit01&backgroundColor=c0aede' },
    { id: 'smile2', name: 'Glee', url: 'https://api.dicebear.com/9.x/big-smile/svg?seed=Liliana' },
    { id: 'pixel4', name: 'Block', url: 'https://api.dicebear.com/9.x/pixel-art/svg?seed=Oliver' },
];

const PROFILE_BADGES = [
  {
    id: 'Legend',
    name: 'Legend',
    icon: 'fa-crown',
    shell: 'from-yellow-500/12 to-orange-500/12 border-yellow-500/25 text-yellow-600 dark:text-yellow-400',
    ink: 'text-yellow-600 dark:text-yellow-400',
  },
  {
    id: 'Backer',
    name: 'Backer',
    icon: 'fa-gem',
    shell: 'from-cyan-500/12 to-blue-500/12 border-cyan-500/25 text-cyan-600 dark:text-cyan-400',
    ink: 'text-cyan-600 dark:text-cyan-400',
  },
  {
    id: 'Supernova',
    name: 'Supernova',
    icon: 'fa-meteor',
    shell: 'from-pink-500/12 to-rose-500/12 border-pink-500/25 text-pink-600 dark:text-pink-400',
    ink: 'text-pink-600 dark:text-pink-400',
  },
  {
    id: 'Void Walker',
    name: 'Void Walker',
    icon: 'fa-dragon',
    shell: 'from-purple-500/12 to-indigo-500/12 border-purple-500/25 text-purple-600 dark:text-purple-400',
    ink: 'text-purple-600 dark:text-purple-400',
  },
  {
    id: 'Guardian',
    name: 'Guardian',
    icon: 'fa-shield-halved',
    shell: 'from-red-500/12 to-orange-500/12 border-red-500/25 text-red-600 dark:text-red-400',
    ink: 'text-red-600 dark:text-red-400',
  },
  {
    id: 'Dev Mode',
    name: 'Dev Mode',
    icon: 'fa-terminal',
    shell: 'from-fuchsia-500/12 to-pink-500/12 border-fuchsia-500/25 text-fuchsia-600 dark:text-fuchsia-400',
    ink: 'text-fuchsia-600 dark:text-fuchsia-400',
  },
  {
    id: 'Dino Rookie',
    name: 'Dino Rookie',
    icon: 'fa-egg',
    shell: 'from-emerald-500/12 to-green-500/12 border-emerald-500/25 text-emerald-600 dark:text-emerald-400',
    ink: 'text-emerald-600 dark:text-emerald-400',
  },
  {
    id: 'Dino Master',
    name: 'Dino Master',
    icon: 'fa-meteor',
    shell: 'from-sky-500/12 to-blue-500/12 border-sky-500/25 text-sky-600 dark:text-sky-400',
    ink: 'text-sky-600 dark:text-sky-400',
  },
  {
    id: 'Dino Legend',
    name: 'Dino Legend',
    icon: 'fa-dragon',
    shell: 'from-primary/12 to-acid/12 border-primary/25 text-primary',
    ink: 'text-primary',
  },
] as const;

interface ProfileStatsModalProps {
  onClose: () => void;
  initialView?: 'profile' | 'badges';
  badgeActionMode?: 'browse' | 'select';
  selectionIndex?: number | null;
}

const ProfileStatsModal: React.FC<ProfileStatsModalProps> = ({
  onClose,
  initialView = 'profile',
  badgeActionMode = 'browse',
  selectionIndex = null
}) => {
  useScrollLock(true);
  const {
    submissionCount,
    adWatchCount,
    isLegend,
    isDevUnlocked,
    unlockedBadges,
    showcasedBadges,
    userProfile,
    setUserProfile,
    toggleShowcasedBadge,
    setShowcasedBadgeAtIndex,
    clearShowcasedBadges
  } = useSettingsStore((state) => ({
    submissionCount: state.submissionCount,
    adWatchCount: state.adWatchCount,
    isLegend: state.isLegend,
    isDevUnlocked: state.isDevUnlocked,
    unlockedBadges: state.unlockedBadges,
    showcasedBadges: state.showcasedBadges,
    userProfile: state.userProfile,
    setUserProfile: state.setUserProfile,
    toggleShowcasedBadge: state.toggleShowcasedBadge,
    setShowcasedBadgeAtIndex: state.setShowcasedBadgeAtIndex,
    clearShowcasedBadges: state.clearShowcasedBadges,
  }));
  
  const favorites = useDataStore((state) => state.favorites);
  const lastRemoteVersions = useSettingsStore((state) => state.lastRemoteVersions);
  
  const [showAvatarPicker, setShowAvatarPicker] = useState(false);
  const [showBadgesView, setShowBadgesView] = useState(initialView === 'badges');
  const [flippedBadgeId, setFlippedBadgeId] = useState<string | null>(null);
  const [tempName, setTempName] = useState('');
  const [tempAvatar, setTempAvatar] = useState(AVATAR_CHOICES[0]!);
  const isSelectionMode = badgeActionMode === 'select';
  const isSwapMode = isSelectionMode && selectionIndex !== null && selectionIndex < showcasedBadges.length;
  
  const level = useMemo(() => {
    if (submissionCount === 0) return 1;
    if (submissionCount >= 50) return 5;
    if (submissionCount >= 25) return 4;
    if (submissionCount >= 10) return 3;
    if (submissionCount >= 5) return 2;
    return 1;
  }, [submissionCount]);
  
  const xp = submissionCount * 10 + adWatchCount * 5;
  const avatarInitial = userProfile?.name?.[0] || 'U';
  const avatarUrl = userProfile?.avatarUrl;
  const downloadCount = Object.keys(lastRemoteVersions).length;
  const hasBackerBadge = adWatchCount >= 3;
  const hasSupernovaBadge = adWatchCount >= 6;
  const hasVoidBadge = adWatchCount >= 12;
  const hasGuardianBadge = adWatchCount >= 25;

  const badgeItems = useMemo(() => {
    return PROFILE_BADGES.map((badge) => {
      let unlocked = false;
      switch (badge.id) {
        case 'Legend':
          unlocked = isLegend;
          break;
        case 'Backer':
          unlocked = hasBackerBadge;
          break;
        case 'Supernova':
          unlocked = hasSupernovaBadge;
          break;
        case 'Void Walker':
          unlocked = hasVoidBadge;
          break;
        case 'Guardian':
          unlocked = hasGuardianBadge;
          break;
        case 'Dev Mode':
          unlocked = isDevUnlocked;
          break;
        default:
          unlocked = unlockedBadges.includes(badge.id);
          break;
      }

      return { ...badge, unlocked, showcased: showcasedBadges.includes(badge.id) };
    });
  }, [hasBackerBadge, hasGuardianBadge, hasSupernovaBadge, hasVoidBadge, isDevUnlocked, isLegend, showcasedBadges, unlockedBadges]);

  const unlockedBadgeCount = badgeItems.filter((badge) => badge.unlocked).length;
  const showcasedBadgePreview = badgeItems.filter((badge) => badge.showcased).slice(0, 3);

  const handleSelectAvatar = (avatar: typeof AVATAR_CHOICES[0]) => {
    if (userProfile) {
      setUserProfile({
        name: userProfile.name,
        avatarId: avatar.id,
        avatarUrl: avatar.url,
      });
    } else {
      setTempAvatar(avatar);
    }
    setShowAvatarPicker(false);
  };

  const handleSaveLocalProfile = () => {
    const cleanName = tempName.trim();
    if (cleanName.length >= 3) {
      setUserProfile({
        name: cleanName,
        avatarId: tempAvatar.id,
        avatarUrl: tempAvatar.url,
      });
      setShowAvatarPicker(false);
    }
  };

  const handleBadgeFlip = (badgeId: string) => {
    setFlippedBadgeId((current) => current === badgeId ? null : badgeId);
  };

  useEffect(() => {
    if (!flippedBadgeId) return;

    const timer = window.setTimeout(() => {
      setFlippedBadgeId(null);
    }, 4000);

    return () => window.clearTimeout(timer);
  }, [flippedBadgeId]);

  const handleBadgeTap = (badgeId: string) => {
    const badge = badgeItems.find((item) => item.id === badgeId);
    if (!badge) return;

    if (isSelectionMode) {
      if (!badge.unlocked || badge.showcased) return;
      if (selectionIndex !== null) {
        setShowcasedBadgeAtIndex(selectionIndex, badgeId);
      } else if (showcasedBadges.length < 3) {
        toggleShowcasedBadge(badgeId);
      } else {
        return;
      }
      onClose();
      return;
    }

    handleBadgeFlip(badgeId);
  };

  const handleResetShowcase = () => {
    clearShowcasedBadges();
    setFlippedBadgeId(null);
  };
  
  return (
    <div 
      className="fixed inset-0 z-[100] flex items-end sm:items-center justify-center bg-black/50 backdrop-blur-sm backdrop-scrim animate-fade-in"
      onClick={onClose}
    >
      <div 
        className="relative flex max-h-[85vh] w-full max-w-sm flex-col overflow-hidden rounded-t-[2.5rem] sm:rounded-[2.5rem] bg-surface shadow-2xl animate-slide-up isolate transform-gpu"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Ambient glow */}
        <div className="absolute inset-x-0 top-0 h-40 pointer-events-none overflow-hidden rounded-t-[2.5rem]">
          <div className="absolute inset-x-8 top-4 h-24 rounded-full bg-gradient-to-r from-primary/25 to-acid/15 blur-3xl" />
        </div>

        {/* Close */}
        <div className="relative z-10 flex items-center justify-end px-5 pt-[calc(0.75rem+env(safe-area-inset-top))]">
          <button
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-full bg-theme-element/60 text-theme-sub hover:text-theme-text transition-colors"
          >
            <i className="fas fa-times text-xs"></i>
          </button>
        </div>

        {/* Content */}
        <div className="relative z-10 flex-1 overflow-y-auto no-scrollbar px-6 pb-[calc(env(safe-area-inset-bottom)+1.5rem)]">
          {/* Avatar + Name */}
          <div className="flex flex-col items-center pb-6">
            <button
              type="button"
              onClick={() => setShowAvatarPicker(!showAvatarPicker)}
              className="group relative mb-3"
              title={userProfile ? 'Change Avatar' : 'Select Avatar'}
            >
              <div className="relative h-[4.5rem] w-[4.5rem] rounded-full bg-gradient-to-br from-primary to-acid p-[2.5px] shadow-lg shadow-primary/20 group-hover:shadow-primary/35 transition-shadow">
                <div className="h-full w-full rounded-full bg-surface flex items-center justify-center overflow-hidden">
                  {avatarUrl ? (
                    <img src={avatarUrl} alt="" className="h-full w-full rounded-full object-cover bg-white" />
                  ) : (
                    <img src={tempAvatar.url} alt="" className="h-full w-full rounded-full object-cover bg-theme-element grayscale opacity-70" />
                  )}
                </div>
              </div>
              <div className="absolute -bottom-0.5 -right-0.5 h-6 w-6 rounded-full bg-primary text-white flex items-center justify-center shadow border-2 border-surface group-hover:scale-110 transition-transform">
                <i className="fas fa-camera text-[8px]"></i>
              </div>
            </button>
            
            {userProfile ? (
              <h3 className="text-lg font-black tracking-tight text-theme-text mt-1">
                {userProfile.name}
              </h3>
            ) : (
              <div className="w-full px-6 flex flex-col gap-3 mt-1">
                <input
                  type="text"
                  placeholder="Set your alien alias..."
                  maxLength={15}
                  value={tempName}
                  onChange={(e) => setTempName(e.target.value)}
                  className="w-full bg-theme-input border border-theme-border rounded-xl px-4 py-3 text-center font-bold text-sm focus:border-primary focus:outline-none placeholder-theme-sub/40 shadow-inner"
                />
                <button
                  onClick={handleSaveLocalProfile}
                  disabled={!tempName || tempName.trim().length < 3}
                  className="w-full py-3 rounded-xl font-black text-[11px] uppercase tracking-widest bg-gradient-to-r from-primary to-neon text-white shadow-lg shadow-primary/20 hover:scale-[1.02] disabled:opacity-50 disabled:scale-100 disabled:cursor-not-allowed transition-all"
                >
                  Secure Identity
                </button>
              </div>
            )}
          </div>

          {/* Avatar Picker */}
          {showAvatarPicker && (
            <div className="mb-5 animate-fade-in">
              <p className="text-[10px] font-black uppercase tracking-[0.16em] text-theme-sub text-center mb-3">Choose Avatar</p>
              <div className="grid grid-cols-5 gap-2.5">
                {AVATAR_CHOICES.map((avatar) => {
                  const isSelected = userProfile?.avatarId === avatar.id;
                  return (
                    <button
                      key={avatar.id}
                      type="button"
                      onClick={() => handleSelectAvatar(avatar)}
                      className={`aspect-square rounded-full p-[2px] transition-all duration-200 ${isSelected 
                        ? 'bg-gradient-to-br from-primary to-acid scale-110 shadow-lg shadow-primary/30' 
                        : 'bg-theme-element hover:bg-theme-hover hover:scale-105'
                      }`}
                    >
                      <img src={avatar.url} alt={avatar.name} className="w-full h-full rounded-full bg-white" loading="lazy" />
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* Stats — clean inline rows, no boxy grid */}
          <div className="space-y-0 rounded-2xl border border-theme-border bg-card overflow-hidden">
            {[
              { icon: 'fa-heart', color: 'text-rose-500', value: favorites.length, label: 'Favorites' },
              { icon: 'fa-download', color: 'text-emerald-500', value: downloadCount, label: 'Downloaded' },
              { icon: 'fa-bolt', color: 'text-amber-500', value: xp, label: 'XP Earned' },
              { icon: 'fa-crown', color: 'text-purple-500', value: `Lvl ${level}`, label: 'Rank' },
            ].map((stat, i, arr) => (
              <div
                key={stat.label}
                className={`flex items-center justify-between px-4 py-3 ${i < arr.length - 1 ? 'border-b border-theme-border' : ''}`}
              >
                <div className="flex items-center gap-3">
                  <i className={`fas ${stat.icon} ${stat.color} text-sm w-4 text-center`}></i>
                  <span className="text-sm font-bold text-theme-sub">{stat.label}</span>
                </div>
                <span className="text-sm font-black text-theme-text tabular-nums">{stat.value}</span>
              </div>
            ))}
          </div>

          <button
            type="button"
            onClick={() => {
              setFlippedBadgeId(null);
              setShowBadgesView(true);
            }}
            className="group relative mt-4 w-full overflow-hidden rounded-[1.7rem] border border-theme-border bg-card p-4 text-left shadow-sm transition-all duration-300 hover:border-primary/20 hover:shadow-lg hover:shadow-primary/10"
          >
            <div className="absolute inset-x-6 top-0 h-16 rounded-full bg-gradient-to-r from-primary/14 via-acid/10 to-primary/14 blur-2xl opacity-80" />
            <div className="relative flex items-start justify-between gap-4">
              <div className="min-w-0">
                <p className="text-[10px] font-black uppercase tracking-[0.24em] text-primary">Badges</p>
                <h4 className="mt-1 text-base font-black tracking-tight text-theme-text">Collection</h4>
                <p className="mt-1 text-xs font-medium text-theme-sub">
                  {unlockedBadgeCount} of {badgeItems.length} unlocked
                </p>
              </div>

              <div className="flex shrink-0 items-center gap-2 text-theme-sub transition-transform duration-300 group-hover:translate-x-0.5">
                <span className="rounded-full border border-theme-border bg-surface px-2.5 py-1 text-[10px] font-black uppercase tracking-[0.18em] text-theme-text">
                  View
                </span>
                <i className="fas fa-arrow-right text-[11px]" />
              </div>
            </div>

            <div className="relative mt-4 flex items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                {Array.from({ length: 3 }).map((_, index) => {
                  const badge = showcasedBadgePreview[index];
                  return (
                    <div
                      key={badge?.id || `placeholder-${index}`}
                      className={`flex h-9 w-9 items-center justify-center rounded-full border shadow-sm ${
                        badge
                          ? `bg-surface border-theme-border ${badge.ink}`
                          : 'border-theme-border bg-theme-element text-theme-sub/45 grayscale'
                      }`}
                    >
                      <i className={`fas ${badge?.icon || 'fa-lock'} text-xs`}></i>
                    </div>
                  );
                })}
              </div>

              <div className="flex items-center gap-1.5">
                {Array.from({ length: 3 }).map((_, index) => (
                  <span
                    key={index}
                    className={`h-1.5 rounded-full transition-all ${
                      showcasedBadgePreview[index] ? 'w-5 bg-primary/70' : 'w-2.5 bg-theme-border'
                    }`}
                  />
                ))}
              </div>
            </div>
          </button>
        </div>

        <div
          className={`absolute inset-0 z-20 flex flex-col bg-surface rounded-t-[2.5rem] sm:rounded-[2.5rem] transition-all duration-300 ${
            showBadgesView ? 'translate-x-0 opacity-100' : 'pointer-events-none translate-x-full opacity-0'
          }`}
        >
          <div className="absolute inset-x-0 top-0 h-28 pointer-events-none overflow-hidden">
            <div className="absolute inset-x-8 top-4 h-20 rounded-full bg-gradient-to-r from-primary/20 to-acid/12 blur-3xl" />
          </div>

          <div className="relative z-10 flex items-center justify-between gap-2 px-5 pt-[calc(0.85rem+env(safe-area-inset-top))] pb-4">
            <button
              type="button"
              onClick={() => {
                setFlippedBadgeId(null);
                if (isSelectionMode) {
                  onClose();
                  return;
                }
                setShowBadgesView(false);
              }}
              className="flex items-center gap-2 rounded-full border border-theme-border bg-card px-3 py-2 text-[11px] font-black uppercase tracking-[0.18em] text-theme-sub transition-colors hover:text-theme-text"
            >
              <i className="fas fa-arrow-left text-[10px]"></i>
              {isSelectionMode ? 'Close' : 'Back'}
            </button>

            <div className="flex items-center gap-2">
              {isSelectionMode && showcasedBadges.length > 0 && (
                <button
                  type="button"
                  onClick={handleResetShowcase}
                  className="rounded-full border border-theme-border bg-card px-3 py-2 text-[10px] font-black uppercase tracking-[0.18em] text-theme-sub transition-colors hover:text-theme-text"
                >
                  Reset
                </button>
              )}

              <div className="rounded-full border border-theme-border bg-card px-3 py-2 text-[10px] font-black uppercase tracking-[0.18em] text-theme-text">
                {unlockedBadgeCount}/{badgeItems.length}
              </div>
            </div>
          </div>

          <div className="relative z-10 flex-1 overflow-y-auto no-scrollbar px-6 pb-[calc(env(safe-area-inset-bottom)+1.5rem)]">
            <div className="mb-5">
              <p className="text-[10px] font-black uppercase tracking-[0.24em] text-primary">Badge Archive</p>
              <h3 className="mt-1 text-2xl font-black tracking-tight text-theme-text">
                {isSelectionMode ? (isSwapMode ? 'Swap Badge' : 'Pick A Badge') : 'Unlocked Badges'}
              </h3>
              <p className="mt-1 text-sm font-medium text-theme-sub">
                {isSelectionMode
                  ? (isSwapMode
                      ? 'Tap any unlocked badge to replace the selected About badge instantly.'
                      : 'Tap any unlocked badge and it will be added to your About tab instantly.')
                  : 'Your collected marks live here. Locked ones stay hidden behind the grey.'}
              </p>
            </div>

            <div className="grid grid-cols-3 gap-x-3 gap-y-5">
              <div className="col-span-3 -mb-1 text-center text-[10px] font-black uppercase tracking-[0.2em] text-theme-sub/70">
                {isSelectionMode ? 'Tap any unlocked badge to add it to About.' : 'Tap a badge to flip.'}
              </div>
              {badgeItems.map((badge) => (
                <div key={badge.id} className="flex flex-col items-center text-center">
                  <button
                    type="button"
                    onClick={() => handleBadgeTap(badge.id)}
                    className={`group ${isSelectionMode && badge.unlocked && !badge.showcased ? 'cursor-pointer' : ''}`}
                  >
                    <div className="perspective-1000 h-[4.75rem] w-[4.75rem]">
                      <div
                        className={`relative h-full w-full rounded-full transition-transform duration-500 preserve-3d ${
                          flippedBadgeId === badge.id ? 'rotate-y-180' : ''
                        }`}
                      >
                        <div
                          className={`absolute inset-0 flex h-full w-full items-center justify-center rounded-full border backface-hidden shadow-sm ${
                            badge.unlocked
                              ? 'border-[#c88a16] bg-[radial-gradient(circle_at_30%_30%,#fff1b8_0%,#f7cb55_22%,#e2a82a_55%,#b87408_100%)] text-[#7a4400] shadow-[inset_0_2px_6px_rgba(255,255,255,0.45),inset_0_-6px_12px_rgba(122,68,0,0.22),0_10px_18px_-12px_rgba(226,168,42,0.85)]'
                              : 'border-theme-border bg-card text-theme-sub/55 grayscale'
                          } ${isSelectionMode && badge.showcased ? 'ring-2 ring-primary/45 ring-offset-2 ring-offset-surface' : ''}`}
                        >
                          <i className={`fas ${badge.unlocked ? badge.icon : 'fa-lock'} ${badge.unlocked ? 'text-[1.45rem]' : 'text-[1.25rem]'}`}></i>
                        </div>

                        <div
                          className={`absolute inset-0 flex h-full w-full items-center justify-center rounded-full border rotate-y-180 backface-hidden px-3 ${
                            badge.unlocked
                              ? 'border-[#c88a16] bg-[radial-gradient(circle_at_30%_30%,#fff1b8_0%,#f7cb55_22%,#e2a82a_55%,#b87408_100%)] text-[#7a4400] shadow-[inset_0_2px_6px_rgba(255,255,255,0.45),inset_0_-6px_12px_rgba(122,68,0,0.22),0_10px_18px_-12px_rgba(226,168,42,0.85)]'
                              : 'border-theme-border bg-card text-theme-sub/55 grayscale'
                          } ${isSelectionMode && badge.showcased ? 'ring-2 ring-primary/45 ring-offset-2 ring-offset-surface' : ''}`}
                        >
                          <span className="text-[9px] font-black uppercase leading-tight tracking-[0.14em] text-center">
                            {badge.unlocked ? badge.name : 'Locked'}
                          </span>
                        </div>
                      </div>
                    </div>
                  </button>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ProfileStatsModal;
