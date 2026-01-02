import { memo, useState, useRef } from 'react';
import { useStore, useSocialLinks, useDevProfile, useEasterEggUrl } from '@/store';
import { storage } from '@/utils/storage';
import { CURRENT_STORE_VERSION, EASTER_EGG_TAP_TARGET } from '@/constants';
import { SectionDivider } from './SectionDivider';

export const AboutView = memo(function AboutView() {
  const { isDevUnlocked, useRemoteJson, toggleSourceMode, githubToken, setGithubToken, setShowFAQ, isLegend, setIsLegend } = useStore();
  const socialLinks = useSocialLinks();
  const devProfile = useDevProfile();
  const easterEggUrl = useEasterEggUrl();

  const [profileImgError, setProfileImgError] = useState(false);
  const [isEditingToken, setIsEditingToken] = useState(false);
  const [easterEggCount, setEasterEggCount] = useState(0);
  const tokenInputRef = useRef<HTMLInputElement>(null);

  const handleProfileClick = () => {
    const newCount = easterEggCount + 1;
    setEasterEggCount(newCount);
    if (newCount >= EASTER_EGG_TAP_TARGET) {
      window.open(easterEggUrl, '_blank');
      setEasterEggCount(0);
      setIsLegend(true);
    }
  };

  const handleTokenSave = (token: string) => {
    setGithubToken(token);
    setIsEditingToken(false);
  };

  return (
    <div className="p-6 pt-24 pb-28 flex flex-col items-center text-center">
      <div onClick={handleProfileClick} className="w-32 h-32 rounded-full p-1 mb-6 bg-gradient-to-br from-acid to-primary animate-pulse-slow cursor-pointer transition-transform active:scale-90 select-none relative z-30">
        {isLegend && (
          <div className="absolute -top-4 -right-10 z-50">
            <div className="bg-gradient-to-br from-yellow-300 via-yellow-500 to-yellow-600 text-yellow-900 px-3 py-1.5 rounded-2xl shadow-[0_0_15px_rgba(250,204,21,0.6)] animate-shine border border-yellow-200 transform rotate-6 flex items-center gap-1 min-w-[70px] justify-center">
              <i className="fas fa-crown text-[8px] animate-bounce" />
              <span className="text-[9px] font-black tracking-wider uppercase">Legend</span>
            </div>
          </div>
        )}
        {profileImgError ? (
          <div className="w-full h-full rounded-full bg-card border-4 border-card flex items-center justify-center relative overflow-hidden">
            <div className="absolute inset-0 bg-gradient-to-br from-gray-900 to-black opacity-90" />
            <span className="relative text-7xl font-black text-transparent bg-clip-text bg-gradient-to-r from-acid via-primary to-neon">{devProfile.name.charAt(0)}</span>
          </div>
        ) : (
          <img src={devProfile.image} alt={devProfile.name} onError={() => setProfileImgError(true)} className="w-full h-full rounded-full object-cover border-4 border-card bg-theme-element" />
        )}
      </div>

      <div className="relative z-0 flex flex-col items-center animate-fade-in w-full">
        <h2 className="text-3xl font-black text-theme-text mb-2">{devProfile.name}</h2>
        <p className="text-theme-sub max-w-md mb-8 text-lg">{devProfile.bio}</p>

        <div className="w-full max-w-md space-y-6">
          <div className="space-y-3">
            <SectionDivider label="Connect" />
            <div className="grid grid-cols-2 gap-3">
              <a href={socialLinks.github} target="_blank" rel="noreferrer" className="flex items-center justify-center gap-3 p-4 bg-card border border-theme-border rounded-2xl hover:scale-[1.02] transition-all">
                <i className="fab fa-github text-2xl text-theme-text" />
                <span className="font-bold text-theme-text">GitHub</span>
              </a>
              <a href={socialLinks.x} target="_blank" rel="noreferrer" className="flex items-center justify-center gap-3 p-4 bg-black dusk:bg-white rounded-2xl hover:scale-[1.02] transition-all shadow-lg">
                <svg viewBox="0 0 24 24" className="w-6 h-6 fill-white dusk:fill-black">
                  <path d="M18.901 1.153h3.68l-8.04 9.19L24 22.846h-7.406l-5.8-7.584-6.638 7.584H.474l8.6-9.83L0 1.154h7.594l5.243 6.932ZM17.61 20.644h2.039L6.486 3.24H4.298Z" />
                </svg>
              </a>
              <a href={socialLinks.discord} target="_blank" rel="noreferrer" className="col-span-2 flex items-center justify-between p-4 bg-[#5865F2]/10 rounded-2xl hover:scale-[1.01] transition-all border border-[#5865F2]/20">
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-full bg-[#5865F2] text-white flex items-center justify-center"><i className="fab fa-discord" /></div>
                  <span className="font-bold text-[#5865F2]">Join Discord Community</span>
                </div>
                <i className="fas fa-arrow-right text-[#5865F2] text-sm opacity-50" />
              </a>
            </div>
          </div>

          <div className="space-y-3">
            <SectionDivider label="Resources" />
            <a href={socialLinks.coffee} target="_blank" rel="noreferrer" className="flex items-center justify-between p-4 bg-yellow-50 dusk:bg-yellow-900/20 border-2 border-yellow-400 rounded-2xl hover:scale-[1.01] transition-all shadow-lg shadow-yellow-400/20">
              <div className="flex items-center gap-4">
                <div className="w-10 h-10 rounded-full bg-yellow-400 text-yellow-900 flex items-center justify-center text-xl"><i className="fas fa-coffee" /></div>
                <div className="text-left">
                  <span className="font-bold text-gray-900 dusk:text-yellow-100 text-lg block">Buy me a coffee</span>
                  <span className="text-xs text-yellow-600 dusk:text-yellow-200 font-semibold">Support development</span>
                </div>
              </div>
              <i className="fas fa-heart text-red-500 animate-bounce" />
            </a>
            <button onClick={() => setShowFAQ(true)} className="flex items-center justify-between p-4 bg-purple-50 dusk:bg-purple-900/20 border-2 border-dashed border-purple-300 dusk:border-purple-700 rounded-2xl hover:scale-[1.01] transition-all w-full text-left">
              <div className="flex items-center gap-4">
                <div className="w-10 h-10 rounded-full bg-purple-400 text-white flex items-center justify-center text-xl"><i className="fas fa-question" /></div>
                <div>
                  <span className="font-bold text-gray-900 dusk:text-purple-100 text-lg block">FAQs</span>
                  <span className="text-xs text-purple-600 dusk:text-purple-300 font-semibold">Secrets & Safety</span>
                </div>
              </div>
              <i className="fas fa-chevron-right text-purple-400" />
            </button>
          </div>

          {isDevUnlocked && (
            <div className="flex flex-col items-center gap-3 mt-8 w-full animate-fade-in">
              <SectionDivider label="Developer Options" />
              <div className="flex flex-col gap-3 w-full p-4 bg-card border border-theme-border rounded-2xl shadow-lg shadow-primary/5">
                <div className="flex items-center justify-between">
                  <div className="text-left">
                    <span className="font-bold text-theme-text block">Data Source</span>
                    <span className="text-xs text-theme-sub">{useRemoteJson ? 'Remote (Config)' : 'Local Bundle'}</span>
                  </div>
                  <button onClick={toggleSourceMode} className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${useRemoteJson ? 'bg-primary text-white' : 'bg-theme-element text-theme-sub'}`}>
                    {useRemoteJson ? 'Remote' : 'Local'}
                  </button>
                </div>
                <div className="h-px bg-theme-border w-full" />
                <div className="flex flex-col gap-2 text-left">
                  <div className="flex justify-between items-center">
                    <span className="font-bold text-theme-text text-sm">GitHub Token (PAT)</span>
                    <button onClick={() => setIsEditingToken(!isEditingToken)} className="text-xs text-primary font-bold">{isEditingToken ? 'Cancel' : 'Edit'}</button>
                  </div>
                  <p className="text-[10px] text-theme-sub leading-tight">Bypass rate limits (5000 req/hr vs 60 req/hr).</p>
                  {isEditingToken ? (
                    <div className="flex gap-2 mt-1">
                      <input ref={tokenInputRef} type="password" placeholder="ghp_xxxxxxxxxxxx" className="flex-1 bg-theme-input border border-theme-border rounded-lg px-3 py-2 text-sm outline-none focus:border-primary" onKeyDown={e => { if (e.key === 'Enter') handleTokenSave(tokenInputRef.current?.value ?? ''); }} />
                      <button className="bg-primary text-white px-3 rounded-lg text-xs font-bold" onClick={() => handleTokenSave(tokenInputRef.current?.value ?? '')}>Save</button>
                    </div>
                  ) : (
                    <div className="flex items-center gap-2">
                      <div className="bg-theme-element px-3 py-2 rounded-lg flex-1 flex items-center justify-between">
                        <span className="text-xs font-mono text-theme-sub">{githubToken ? '••••••••••••••••••••' : 'No token set'}</span>
                        {githubToken && <i className="fas fa-check-circle text-green-500 text-xs" />}
                      </div>
                      {githubToken && <button onClick={() => setGithubToken('')} className="w-8 h-8 flex items-center justify-center bg-red-500/10 text-red-500 rounded-lg"><i className="fas fa-trash text-xs" /></button>}
                    </div>
                  )}
                </div>
              </div>
              <button onClick={() => { storage.clear(); window.location.reload(); }} className="px-4 py-2 rounded-xl bg-red-500/10 text-red-500 text-xs font-bold hover:bg-red-500/20 transition-colors flex items-center gap-2">
                <i className="fas fa-trash-alt" />Wipe Cache & Reset
              </button>
            </div>
          )}

          <div className="mt-12 mb-2 flex flex-col items-center gap-4">
            <div className="flex items-center gap-3 text-sm font-medium text-theme-sub">
              <span className="opacity-60 font-mono">v{CURRENT_STORE_VERSION}</span>
              <span className="w-1 h-1 rounded-full bg-theme-border" />
              <div className={`px-3 py-1 rounded-full border text-xs font-bold flex items-center gap-2 shadow-xs ${useRemoteJson ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-600 dusk:text-emerald-400' : 'bg-amber-500/10 border-amber-500/20 text-amber-600 dusk:text-amber-400'}`}>
                <span className="uppercase tracking-wider opacity-80">Source:</span>
                <span>{useRemoteJson ? 'Remote' : 'Local'}</span>
              </div>
            </div>
            <span className="text-xs font-mono text-theme-sub opacity-40">Made with 💜 for Geeks</span>
          </div>
        </div>
      </div>
    </div>
  );
});
