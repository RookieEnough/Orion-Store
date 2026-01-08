
import React, { useState, useEffect, useRef } from 'react';
import { Capacitor } from '@capacitor/core';
import { UnityAds } from 'capacitor-unity-ads';
import { DEFAULT_DEV_PROFILE } from '../constants';

interface AdDonationModalProps {
  onClose: () => void;
  onSuccess: () => void;
  currentStreak: number;
}

type AdState = 'intro' | 'urgent_call' | 'loading' | 'playing' | 'success' | 'error';

// --- CONFIGURATION ---
const PLACEMENT_ID = "Rewarded_Android";

const LOADING_MESSAGES = [
  "Summoning the hero...",
  "Sharpening swords...",
  "Establishing uplink...",
  "Buffering video stream...",
  "Generating terrain...",
  "Loading boss assets...",
];

// --- CUSTOM ICONS ---
const SwordsIcon = ({ className }: { className?: string }) => (
  <svg 
    xmlns="http://www.w3.org/2000/svg" 
    viewBox="0 0 24 24" 
    fill="none" 
    stroke="currentColor" 
    strokeWidth="2" 
    strokeLinecap="round" 
    strokeLinejoin="round" 
    className={className}
  >
    <polyline points="14.5 17.5 3 6 3 3 6 3 17.5 14.5" />
    <line x1="13" x2="19" y1="19" y2="13" />
    <line x1="16" x2="20" y1="16" y2="20" />
    <line x1="19" x2="21" y1="21" y2="19" />
    <polyline points="14.5 6.5 18 3 21 3 21 6 17.5 9.5" />
    <line x1="5" x2="9" y1="14" y2="18" />
    <line x1="7" x2="4" y1="17" y2="20" />
    <line x1="3" x2="5" y1="19" y2="21" />
  </svg>
);

// --- RPG DATA ---
const STORY_CARDS = [
    { lvl: 1, title: "Hero Awakens", msg: "You open your eyes. The digital world needs saving. You pick up a rusty keyboard.", icon: "fa-user-shield", color: "text-blue-400", bg: "bg-blue-500/10" },
    { lvl: 2, title: "Slime Defeated", msg: "A wild Glitch Slime appeared! You smashed it with a console log. +10 XP.", icon: "fa-ghost", color: "text-green-400", bg: "bg-green-500/10" },
    { lvl: 3, title: "Golem Defeated!", msg: "BOSS BATTLE: The Code Golem crumbled! Backer Badge Unlocked!", icon: "fa-gem", color: "text-cyan-400", bg: "bg-cyan-500/20" },
    { lvl: 4, title: "Loot Found", msg: "You found the 'Shield of Syntax'. Compilation resilience +20%.", icon: "fa-shield-halved", color: "text-yellow-500", bg: "bg-yellow-500/10" },
    { lvl: 5, title: "Troll Banished", msg: "A Forum Troll blocked the bridge. You banned him permanently. The path is clear.", icon: "fa-gavel", color: "text-red-400", bg: "bg-red-500/10" },
    { lvl: 6, title: "Swarm Cleared!", msg: "BOSS BATTLE: You exterminated the Bug Swarm Queen! Supernova Badge Unlocked!", icon: "fa-meteor", color: "text-rose-500", bg: "bg-rose-500/20" },
    { lvl: 7, title: "Level Up", msg: "You learned 'Dark Mode Vision'. You can now see clearly in the deep code.", icon: "fa-adjust", color: "text-gray-200", bg: "bg-gray-700/50" },
    { lvl: 8, title: "Dungeon Run", msg: "Entered the Server Room Dungeon. It's cold and loud.", icon: "fa-server", color: "text-indigo-400", bg: "bg-indigo-500/10" },
    { lvl: 9, title: "Firewall Built", msg: "You reinforced the defenses. Intruders will find no quarter here.", icon: "fa-shield-virus", color: "text-orange-500", bg: "bg-orange-500/10" },
    { lvl: 10, title: "Rare Artifact", msg: "Obtained the 'Crystal of Uptime'. Stability +500%.", icon: "fa-cube", color: "text-purple-400", bg: "bg-purple-500/10" },
    { lvl: 11, title: "Approaching Boss", msg: "The ground shakes. Something massive approaches from the Void...", icon: "fa-exclamation-triangle", color: "text-red-500", bg: "bg-red-500/10" },
    { lvl: 12, title: "Void Dragon Slayed!", msg: "BOSS BATTLE: You compiled the Dragon out of existence! Void Walker Badge Unlocked!", icon: "fa-dragon", color: "text-purple-600", bg: "bg-purple-500/20" },
    { lvl: 13, title: "Respite", msg: "You rest by the digital bonfire. Your health is restored.", icon: "fa-fire", color: "text-orange-400", bg: "bg-orange-500/10" },
    { lvl: 14, title: "Village Saved", msg: "The Open Source Village is safe thanks to you.", icon: "fa-home", color: "text-emerald-400", bg: "bg-emerald-500/10" },
    { lvl: 15, title: "New Quest", msg: "The Kernel King has requested your aid.", icon: "fa-scroll", color: "text-yellow-200", bg: "bg-yellow-500/10" },
    { lvl: 16, title: "Memory Leak", msg: "You patched a massive leak in the dam. The valley is saved.", icon: "fa-fill-drip", color: "text-blue-500", bg: "bg-blue-500/10" },
    { lvl: 17, title: "Cloud City", msg: "You ascended to the Cloud Layer. Data flows freely here.", icon: "fa-cloud", color: "text-sky-400", bg: "bg-sky-500/10" },
    { lvl: 18, title: "Lightning Bolt", msg: "You harnessed the power of raw electricity.", icon: "fa-bolt", color: "text-yellow-400", bg: "bg-yellow-500/10" },
    { lvl: 19, title: "Time Travel", msg: "You reverted a bad commit. History is corrected.", icon: "fa-history", color: "text-pink-400", bg: "bg-pink-500/10" },
    { lvl: 20, title: "Max Level", msg: "You reached level 99. But the final challenge awaits.", icon: "fa-arrow-up", color: "text-green-500", bg: "bg-green-500/10" },
    { lvl: 21, title: "The Gate", msg: "Standing before the Gateway of Entropy.", icon: "fa-dungeon", color: "text-gray-400", bg: "bg-gray-700/50" },
    { lvl: 22, title: "First Seal", msg: "You broke the Seal of Neglect. Updates flow again.", icon: "fa-unlink", color: "text-red-400", bg: "bg-red-500/10" },
    { lvl: 23, title: "Second Seal", msg: "You broke the Seal of Greed. Everything remains free.", icon: "fa-coins", color: "text-yellow-600", bg: "bg-yellow-500/10" },
    { lvl: 24, title: "Final Prep", msg: "Potions drunk. Armor polished. This is it.", icon: "fa-flask", color: "text-purple-400", bg: "bg-purple-500/10" },
    { lvl: 25, title: "ENTROPY DEFEATED!", msg: "FINAL BOSS: You saved the entire digital universe! Cosmic Guardian Badge Unlocked!", icon: "fa-user-astronaut", color: "text-fuchsia-400", bg: "bg-fuchsia-500/20" },
];

const END_GAME_CARD = { lvl: 999, title: "Guardian Duty", msg: "You continue to protect the realm. The dev salutes you.", icon: "fa-star", color: "text-white", bg: "bg-gradient-to-r from-indigo-500 to-purple-500" };

const AdDonationModal: React.FC<AdDonationModalProps> = ({ onClose, onSuccess, currentStreak }) => {
  const [viewState, setViewState] = useState<AdState>('intro');
  const [timeLeft, setTimeLeft] = useState(5);
  const [progress, setProgress] = useState(0);
  const [errorMessage, setErrorMessage] = useState<string>('');
  const [loadingMsgIndex, setLoadingMsgIndex] = useState(0);
  const [imgError, setImgError] = useState(false);
  
  const isMounted = useRef(true);

  // Determine the resulting state (Card to show)
  const resultingStreak = currentStreak > 0 ? currentStreak : 1;
  const cardData = STORY_CARDS.find(c => c.lvl === resultingStreak) || END_GAME_CARD;
  const prevCard = STORY_CARDS.find(c => c.lvl === currentStreak);
  const isBossVictory = [3, 6, 12, 25].includes(resultingStreak);

  const log = (msg: string) => {
      console.log(`[UnityAds] ${msg}`);
  };

  // --- INITIALIZATION ---
  useEffect(() => {
    isMounted.current = true;
    
    // ATTEMPT 1: BACKGROUND LOAD ON MOUNT
    // This allows the ad to buffer while the user is reading the text.
    if (Capacitor.isNativePlatform()) {
        log("Mounting modal. Triggering background load...");
        (UnityAds as any).loadRewardedVideo({ placementId: PLACEMENT_ID }).catch((e: any) => {
            log("Background load warning (safe to ignore): " + e);
        });
    }

    return () => { isMounted.current = false; };
  }, []);

  // Cycle Loading Messages
  useEffect(() => {
    let interval: any;
    if (viewState === 'loading') {
        setLoadingMsgIndex(0);
        interval = setInterval(() => {
            setLoadingMsgIndex(prev => (prev + 1) % LOADING_MESSAGES.length);
        }, 1200);
    }
    return () => clearInterval(interval);
  }, [viewState]);

  // --- MAIN LOGIC ---
  const startAdSequence = async () => {
    if (!Capacitor.isNativePlatform()) {
      startMockAd("Web Browser Mode");
      return;
    }

    try {
        log("Attempting immediate show...");
        // ATTEMPT 2: IMMEDIATE SHOW
        // If the background load (on mount) succeeded, this will work instantly.
        await (UnityAds as any).showRewardedVideo({ placementId: PLACEMENT_ID });
        handleAdComplete();
    
    } catch (immediateError: any) {
        // If immediate show fails, it means video is "not loaded" or "not ready".
        // We now enter the visual loading state and force a fresh load.
        log("Immediate show failed. Entering buffer mode. Error: " + immediateError);
        setViewState('loading');

        try {
            // ATTEMPT 3: FORCE LOAD & WAIT
            log("Requesting fresh load...");
            await (UnityAds as any).loadRewardedVideo({ placementId: PLACEMENT_ID });
            
            // Wait 6 seconds for buffering
            await new Promise(resolve => setTimeout(resolve, 6000));

            log("Attempting delayed show...");
            await (UnityAds as any).showRewardedVideo({ placementId: PLACEMENT_ID });
            handleAdComplete();

        } catch (delayedError: any) {
            log("Delayed show failed. Error: " + delayedError);
            const errStr = (delayedError?.message || JSON.stringify(delayedError)).toLowerCase();
            
            // Fallback to simulation so user isn't stuck
            startMockAd(errStr.includes("loaded") ? "Network Busy" : "Connection Failed");
        }
    }
  };

  // --- MOCK AD (WEB / FALLBACK) ---
  const startMockAd = (reason: string) => {
    if (!isMounted.current) return;
    setErrorMessage(reason); 
    setViewState('playing');
    setTimeLeft(5);
    setProgress(0);

    const step = 100;
    const totalTime = 5000;
    let elapsed = 0;

    const interval = setInterval(() => {
      if (!isMounted.current) { clearInterval(interval); return; }
      elapsed += step;
      const percent = Math.min((elapsed / totalTime) * 100, 100);
      setProgress(percent);
      setTimeLeft(Math.ceil((totalTime - elapsed) / 1000));

      if (elapsed >= totalTime) {
        clearInterval(interval);
        handleAdComplete();
      }
    }, step);
  };

  const handleAdComplete = () => {
    if (!isMounted.current) return;
    onSuccess();
    setViewState('success');
  };

  const handleClose = () => {
    if (viewState === 'playing') return;
    onClose();
  };

  // --- RENDER HELPERS ---
  const renderUrgentCall = () => (
    <div className="p-8 flex flex-col items-center text-center h-full justify-center bg-gray-950 text-white relative overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,_var(--tw-gradient-stops))] from-red-900/30 via-gray-950 to-black animate-pulse"></div>
        <div className="absolute inset-0 z-0 opacity-10 bg-[url('https://www.transparenttextures.com/patterns/carbon-fibre.png')]"></div>
        
        <div className="relative z-10 w-28 h-28 rounded-full border-4 border-red-500 shadow-[0_0_40px_rgba(239,68,68,0.5)] mb-6 overflow-hidden bg-black">
             {imgError ? (
                <div className="w-full h-full flex items-center justify-center relative overflow-hidden">
                    <div className="absolute inset-0 bg-gradient-to-br from-gray-900 to-black"></div>
                    <div className="absolute w-full h-full bg-gradient-to-tr from-red-500/20 to-orange-500/20 animate-pulse"></div>
                    <span className="relative text-5xl font-black text-transparent bg-clip-text bg-gradient-to-r from-red-500 via-orange-500 to-yellow-500 tracking-tighter filter drop-shadow-lg">R</span>
                </div>
             ) : (
                 <img src={DEFAULT_DEV_PROFILE.image} className="w-full h-full object-cover animate-pulse-slow" alt="RookieZ" onError={() => setImgError(true)} />
             )}
             <div className="absolute inset-0 bg-red-500/10 mix-blend-color-dodge bg-[linear-gradient(transparent_50%,_rgba(0,0,0,0.5)_50%)] bg-[length:10px_4px] pointer-events-none"></div>
             <div className="absolute -top-1 -right-1 w-6 h-6 bg-red-500 rounded-full animate-ping"></div>
        </div>

        <h2 className="relative z-10 text-3xl font-black text-white italic tracking-tighter mb-4 drop-shadow-[0_0_15px_rgba(220,38,38,0.8)] animate-pulse px-2">ORION IS IN DANGER!</h2>
        
        <div className="relative z-10 bg-red-600/10 border border-red-500/30 p-4 rounded-xl mb-8 backdrop-blur-md shadow-lg shadow-red-900/20 transform rotate-1">
            <p className="text-red-100 font-bold text-sm leading-relaxed text-justify tracking-tight">
                “Listen to me! The Entropy Legion has breached the firewall. They're corrupting the source code! I can't hold them back alone. I need you to channel the energy <span className="text-red-400 font-black">RIGHT NOW!</span>”
            </p>
            <div className="flex justify-end mt-2 items-center gap-2">
                <div className="h-px w-8 bg-red-500/50"></div>
                <p className="text-[10px] text-red-400 uppercase tracking-widest font-black">RookieZ, Lead Dev</p>
            </div>
        </div>

        <div className="relative z-10 w-full space-y-4">
            <button 
                onClick={startAdSequence}
                className="w-full py-4 rounded-xl bg-gradient-to-r from-red-600 to-orange-600 text-white font-black text-lg shadow-[0_0_20px_rgba(220,38,38,0.4)] hover:scale-[1.02] hover:shadow-[0_0_30px_rgba(220,38,38,0.7)] active:scale-95 transition-all flex items-center justify-center gap-3 group border border-red-400/30 relative overflow-hidden"
            >
                <div className="absolute inset-0 bg-white/10 translate-y-full group-hover:translate-y-0 transition-transform duration-300"></div>
                <i className="fas fa-meteor text-xl group-hover:animate-spin"></i>
                <span className="tracking-wide">ACCEPT THE QUEST</span>
            </button>
            <button onClick={onClose} className="w-full py-3 rounded-xl bg-gray-800/40 border border-gray-700/50 text-gray-400 font-bold hover:bg-gray-800 hover:text-white transition-colors text-xs uppercase tracking-widest">I need time...</button>
        </div>
    </div>
  );

  const renderIntro = () => {
      if (currentStreak === 0) {
          return (
            <div className="p-8 flex flex-col items-center text-center h-full justify-center">
                <div className="w-20 h-20 bg-gradient-to-br from-indigo-500 to-purple-600 rounded-full flex items-center justify-center shadow-lg shadow-indigo-500/30 mb-6 animate-pulse-slow relative">
                    <i className="fas fa-heart text-3xl text-white ml-1"></i>
                </div>
                <h2 className="text-2xl font-black text-theme-text mb-2">The Developer's Quest</h2>
                <div className="bg-theme-element px-3 py-1 rounded-full mb-4"><span className="text-xs font-bold text-theme-sub uppercase tracking-wider">Chapter 1</span></div>
                <p className="text-theme-sub text-sm leading-relaxed mb-8">You are the reason this project exists. By watching ads, you fuel the code, keep servers running, and support open source. You are incredible.</p>
                <div className="w-full space-y-3">
                  <button onClick={() => setViewState('urgent_call')} className="w-full py-4 rounded-2xl bg-primary text-white font-bold text-lg shadow-xl shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all flex items-center justify-center gap-2">
                    <SwordsIcon className="w-6 h-6" /><span>I'm a Hero</span>
                  </button>
                  <button onClick={onClose} className="w-full py-3 rounded-2xl text-theme-sub font-bold hover:bg-theme-element border border-transparent hover:border-theme-border transition-colors">Cancel</button>
                </div>
            </div>
          );
      }
      return (
        <div className="p-8 flex flex-col items-center text-center h-full justify-center">
            <div className="w-20 h-20 bg-gradient-to-br from-orange-500 to-red-500 rounded-full flex items-center justify-center shadow-lg shadow-orange-500/30 mb-6 relative"><i className="fas fa-fire text-3xl text-white animate-pulse"></i></div>
            <h2 className="text-2xl font-black text-theme-text mb-2">Resting at Camp</h2>
            <div className="bg-theme-element px-3 py-1 rounded-full mb-4"><span className="text-xs font-bold text-theme-sub uppercase tracking-wider">Level {currentStreak}</span></div>
            {prevCard && (
                <div className="bg-card border border-theme-border p-3 rounded-xl mb-6 w-full shadow-sm flex items-center gap-3">
                    <div className={`w-8 h-8 rounded-full flex items-center justify-center ${prevCard.bg}`}><i className={`fas ${prevCard.icon} text-xs ${prevCard.color}`}></i></div>
                    <div className="text-left"><p className="text-[10px] uppercase font-bold text-theme-sub">Previously</p><p className="text-xs font-bold text-theme-text truncate">{prevCard.title}</p></div>
                </div>
            )}
            <p className="text-theme-sub text-sm leading-relaxed mb-8">You've restored your health by the fire. The journey ahead is dangerous, but the rewards are legendary. Ready to proceed?</p>
            <div className="w-full space-y-3">
              <button onClick={startAdSequence} className="w-full py-4 rounded-2xl bg-primary text-white font-bold text-lg shadow-xl shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all flex items-center justify-center gap-2">
                <SwordsIcon className="w-6 h-6" /><span>Continue Journey</span>
              </button>
              <button onClick={onClose} className="w-full py-3 rounded-2xl text-theme-sub font-bold hover:bg-theme-element border border-transparent hover:border-theme-border transition-colors">Stay at Camp (Close)</button>
            </div>
        </div>
      );
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 animate-fade-in">
      <div className="absolute inset-0 bg-black/80 backdrop-blur-md transition-opacity" onClick={handleClose}></div>
      <div className="relative bg-surface border border-theme-border w-full max-w-sm rounded-3xl shadow-2xl overflow-hidden animate-slide-up flex flex-col min-h-[450px]">
        {viewState === 'intro' && renderIntro()}
        {viewState === 'urgent_call' && renderUrgentCall()}
        {viewState === 'loading' && (
           <div className="p-8 flex flex-col items-center text-center h-full justify-center space-y-8 animate-fade-in">
              <div className="relative">
                  <div className="w-24 h-24 rounded-full border-4 border-theme-element border-t-primary animate-spin"></div>
                  <div className="absolute inset-0 flex items-center justify-center"><i className="fas fa-dragon text-primary text-3xl animate-pulse"></i></div>
              </div>
              <div className="space-y-2 max-w-[80%] mx-auto">
                  <h3 className="text-xl font-bold text-theme-text">Engaging Enemy</h3>
                  <p className="text-theme-sub text-sm font-medium min-h-[3rem] flex items-center justify-center transition-all duration-300">{LOADING_MESSAGES[loadingMsgIndex]}</p>
                  <p className="text-[10px] text-theme-sub/50 animate-pulse">(Connecting to Unity Network...)</p>
              </div>
           </div>
        )}
        {viewState === 'playing' && (
          <div className="relative h-full flex flex-col bg-black">
            <div className="flex-1 flex flex-col items-center justify-center p-6 text-center space-y-4">
                <div className="w-24 h-24 rounded-2xl bg-gradient-to-tr from-acid via-primary to-neon animate-spin-slow flex items-center justify-center shadow-2xl shadow-primary/20"><i className="fas fa-robot text-4xl text-white"></i></div>
                <div className="z-10 w-full">
                    <h3 className="text-white font-black text-2xl tracking-tight">Ad Simulation</h3>
                    <p className="text-white/60 text-sm font-medium mb-4">Real ad failed. Running Sim.</p>
                    <div className="bg-red-900/30 border border-red-500/30 rounded-lg p-2 mx-auto max-w-[200px]"><p className="text-red-200 text-[10px] font-mono leading-tight break-words">{errorMessage || "Unknown Error"}</p></div>
                </div>
            </div>
            <div className="absolute top-4 right-4 bg-black/50 backdrop-blur-md px-4 py-2 rounded-full text-white text-xs font-bold border border-white/10 tabular-nums">{timeLeft}s</div>
            <div className="absolute bottom-0 left-0 right-0 h-1.5 bg-gray-800"><div className="h-full bg-gradient-to-r from-acid to-primary transition-all duration-100 ease-linear" style={{ width: `${progress}%` }}></div></div>
          </div>
        )}
        {viewState === 'success' && (
          <div className={`p-8 flex flex-col items-center text-center h-full justify-center bg-gradient-to-b from-surface to-gray-500/5`}>
            <div className={`w-28 h-28 ${cardData.bg} rounded-full flex items-center justify-center mb-6 relative animate-bounce`}>
               <i className={`fas ${cardData.icon} text-5xl ${cardData.color} ${isBossVictory ? 'animate-spin-slow' : ''}`}></i>
               {isBossVictory && <div className="absolute -top-2 -right-2 bg-yellow-400 text-black text-xs font-bold px-2 py-1 rounded-full animate-pulse">VICTORY</div>}
            </div>
            <h2 className="text-2xl font-black text-theme-text mb-2 uppercase tracking-tight">{cardData.title}</h2>
            <div className="w-12 h-1 bg-theme-border rounded-full mb-4"></div>
            <p className="text-theme-sub text-sm leading-relaxed mb-8 px-2 font-medium">{cardData.msg}</p>
            <div className="w-full space-y-3">
              <button onClick={startAdSequence} className="w-full py-4 rounded-2xl bg-theme-element border border-theme-border text-theme-text font-bold text-lg hover:bg-theme-hover active:scale-95 transition-all flex items-center justify-center gap-2">
                <i className="fas fa-forward"></i><span>Continue Quest</span>
              </button>
              <button onClick={onClose} className="w-full py-3 rounded-2xl bg-transparent text-theme-sub font-bold hover:text-primary border border-transparent hover:border-theme-border transition-colors">Rest at Camp (Close)</button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default AdDonationModal;
