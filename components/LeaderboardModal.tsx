
import React, { useState, useEffect, useMemo } from 'react';
import { createPortal } from 'react-dom';
import { LeaderboardEntry } from '../types';
import { useScrollLock } from '../hooks/useScrollLock';
import { useSettingsStore } from '../store/useAppStore';

interface LeaderboardModalProps {
  onClose: () => void;
  workerUrl: string;
  onOpenSubmit: () => void;
}

const LeaderboardModal: React.FC<LeaderboardModalProps> = ({ onClose, workerUrl, onOpenSubmit }) => {
  useScrollLock(true);
  const { glassEffect, lastSubmissionTime } = useSettingsStore();
  const [entries, setEntries] = useState<LeaderboardEntry[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(false);

  // Check if user submitted in the last hour (3600000 ms)
  const isSubmissionOnCooldown = useMemo(() => {
      if (!lastSubmissionTime) return false;
      return Date.now() - lastSubmissionTime < 3600000;
  }, [lastSubmissionTime]);

  useEffect(() => {
      fetchLeaderboard();
  }, []);

  const fetchLeaderboard = async () => {
      try {
          const res = await fetch(workerUrl);
          if (!res.ok) throw new Error("Failed to load");
          const data = await res.json();
          // Sort by XP DESC
          const sorted = data.sort((a: LeaderboardEntry, b: LeaderboardEntry) => b.xp - a.xp);
          
          setEntries(sorted);
      } catch (e) {
          console.warn("Failed to fetch leaderboard", e);
          setError(true);
      } finally {
          setIsLoading(false);
      }
  };

  const getClassColor = (cls: string) => {
      if (cls === 'Warrior') return 'text-red-400 bg-red-500/10 border-red-500/30';
      if (cls === 'Scribe') return 'text-blue-400 bg-blue-500/10 border-blue-500/30';
      return 'text-purple-400 bg-purple-500/10 border-purple-500/30';
  };

  const getRankStyle = (idx: number) => {
      if (idx === 0) return 'border border-yellow-500 bg-yellow-500/10 shadow-[0_0_15px_rgba(234,179,8,0.2)] z-10 scale-[1.02]';
      // Rank 2: Bronze/Orange
      if (idx === 1) return 'border border-orange-500 bg-orange-500/10 shadow-[0_0_15px_rgba(249,115,22,0.2)]';
      // Rank 3: Silver (Using Slate/Gray for Light Mode visibility)
      if (idx === 2) return 'border border-slate-400 bg-slate-500/10 shadow-[0_0_15px_rgba(148,163,184,0.4)]';
      // Default: No border, just background
      return 'bg-card/50';
  };

  return createPortal(
    <div className="fixed inset-0 z-[10000] flex items-center justify-center touch-none">
        {/* Backdrop */}
        <div 
            className={`absolute inset-0 bg-black/90 transition-all duration-300 ${glassEffect ? 'backdrop-blur-xl' : ''}`} 
            onClick={onClose}
        ></div>
        
        {/* 
            CARD CONTAINER - Expanded Size
            w-[95vw]: Takes up 95% of width on mobile
            h-[85vh]: Takes up 85% of height to feel immersive
        */}
        <div 
            className="relative w-[95vw] max-w-lg bg-surface border border-theme-border rounded-[2.5rem] shadow-2xl flex flex-col overflow-hidden animate-slide-up transform transition-all z-50 ring-1 ring-white/10"
            style={{ height: '85vh', maxHeight: '900px' }}
        >
            
            {/* Header */}
            <div className="shrink-0 p-6 pb-4 bg-surface z-10 relative text-center border-b border-theme-border/50">
                <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-transparent via-primary to-transparent opacity-50"></div>
                
                <h2 className="text-3xl font-black text-theme-text flex items-center justify-center gap-3 drop-shadow-md">
                    <span className="text-yellow-400 text-4xl filter drop-shadow-[0_0_10px_rgba(250,204,21,0.5)]">
                        <i className="fas fa-trophy"></i>
                    </span>
                    <span>Hall of Fame</span>
                </h2>
                <p className="text-[10px] text-theme-sub font-bold uppercase tracking-widest mt-2 opacity-80">Global Top Contributors</p>
                
                <button 
                    onClick={onClose} 
                    className="absolute top-5 right-5 w-8 h-8 rounded-full bg-theme-element border border-theme-border flex items-center justify-center text-theme-text hover:bg-red-500 hover:text-white transition-all shadow-sm active:scale-90"
                >
                    <i className="fas fa-times text-xs"></i>
                </button>
            </div>

            {/* List Content */}
            <div className="flex-1 overflow-y-auto p-4 space-y-3 no-scrollbar overscroll-contain mask-image-b">
                {isLoading ? (
                    [...Array(8)].map((_, i) => (
                        <div key={i} className="h-16 bg-theme-element/50 rounded-2xl animate-pulse"></div>
                    ))
                ) : error ? (
                    <div className="flex flex-col items-center justify-center h-full text-theme-sub py-10 opacity-70">
                        <i className="fas fa-satellite-dish text-4xl mb-4 opacity-50"></i>
                        <p className="text-sm font-bold">Signal Lost</p>
                        <button onClick={fetchLeaderboard} className="mt-4 text-primary font-bold text-xs bg-theme-element px-4 py-2 rounded-full hover:bg-primary hover:text-white transition-colors">Reconnect</button>
                    </div>
                ) : entries.length === 0 ? (
                    <div className="flex flex-col items-center justify-center h-full text-theme-sub py-10 opacity-70">
                        <div className="w-20 h-20 bg-theme-element rounded-full flex items-center justify-center mb-4 animate-bounce shadow-lg shadow-black/5">
                            <i className="fas fa-ghost text-4xl opacity-50"></i>
                        </div>
                        <h3 className="text-xl font-black text-theme-text mb-1">It's quiet... too quiet.</h3>
                        <p className="text-xs font-bold text-center max-w-[220px] leading-relaxed">
                            The Hall of Fame is empty. <br/>
                            <span className="text-primary">Be the first legend to rise!</span>
                        </p>
                    </div>
                ) : (
                    entries.map((entry, idx) => (
                        <div key={idx} className={`flex items-center gap-4 p-3 pl-4 rounded-2xl ${getRankStyle(idx)} transition-transform active:scale-[0.99]`}>
                            <div className={`flex-shrink-0 w-6 text-center font-black text-lg ${idx < 3 ? 'text-theme-text scale-125' : 'text-theme-sub opacity-50'}`}>
                                {idx + 1}
                            </div>
                            <img 
                                src={entry.avatar_url} 
                                alt={entry.username} 
                                className={`w-12 h-12 rounded-full border-2 bg-theme-element object-cover ${idx === 0 ? 'border-yellow-400' : 'border-surface'}`} 
                            />
                            <div className="flex-1 min-w-0">
                                <div className="flex items-center gap-2">
                                    <h3 className="font-bold text-theme-text truncate text-sm">{entry.username}</h3>
                                    {idx < 3 && <i className="fas fa-crown text-yellow-500 text-[10px] animate-pulse"></i>}
                                </div>
                                <div className="flex items-center gap-2 mt-1">
                                    <span className={`text-[9px] font-bold px-2 py-0.5 rounded border ${getClassColor(entry.class)}`}>
                                        {entry.class}
                                    </span>
                                    <span className="text-[9px] text-theme-sub font-mono opacity-70">Lvl {entry.level}</span>
                                </div>
                            </div>
                            <div className="text-right pr-1">
                                <span className="block font-black text-theme-text text-sm tracking-tight">{entry.xp.toLocaleString()}</span>
                                <span className="text-[9px] font-bold text-theme-sub uppercase">XP</span>
                            </div>
                        </div>
                    ))
                )}
            </div>

            {/* Footer */}
            <div className="shrink-0 p-5 bg-surface z-10 border-t border-theme-border/50">
                <button 
                    onClick={onOpenSubmit}
                    disabled={isSubmissionOnCooldown}
                    className={`w-full py-4 rounded-2xl font-bold text-sm transition-all flex items-center justify-center gap-2 group mb-3 ${
                        isSubmissionOnCooldown 
                        ? 'bg-theme-element text-theme-sub border border-theme-border cursor-not-allowed' 
                        : 'bg-gradient-to-r from-indigo-600 via-purple-600 to-indigo-600 bg-[length:200%_100%] animate-shine text-white shadow-xl shadow-indigo-500/20 hover:scale-[1.02] active:scale-95'
                    }`}
                >
                    {isSubmissionOnCooldown ? (
                        <>
                            <i className="fas fa-check-circle text-green-500 text-lg"></i>
                            <span className="tracking-wide opacity-70">Rank Claimed (Wait 1h)</span>
                        </>
                    ) : (
                        <>
                            <i className="fas fa-plus-circle group-hover:rotate-90 transition-transform text-lg"></i>
                            <span className="tracking-wide">Claim Global Rank</span>
                        </>
                    )}
                </button>
                
                <div className="flex justify-center items-center gap-1 text-[10px] text-theme-sub opacity-70 bg-theme-element/50 py-2 rounded-xl">
                    <i className="fas fa-clock text-green-500 animate-pulse"></i>
                    <span className="font-medium text-center">
                        Rankings are synchronized every hour after user submission
                    </span>
                </div>
            </div>

        </div>
    </div>,
    document.body
  );
};

export default LeaderboardModal;
