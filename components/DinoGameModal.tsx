import React, { useRef, useMemo, useState, useEffect } from 'react';
import { useSettingsStore } from '../store/useAppStore';
import DinoGame, { DinoGameStatus, DinoGameHandle } from './DinoGame';
import { useScrollLock } from '../hooks/useScrollLock';
import { Capacitor } from '@capacitor/core';
import { App as CapacitorApp } from '@capacitor/app';

interface DinoGameModalProps {
  onClose: () => void;
}

const DinoGameModal: React.FC<DinoGameModalProps> = ({ onClose }) => {
  useScrollLock(true);
  const hapticEnabled = useSettingsStore((state) => state.hapticEnabled);
  const { addGameXP, unlockBadge, dinoHighScore, updateDinoHighScore } = useSettingsStore((state) => ({
    addGameXP: state.addGameXP,
    unlockBadge: state.unlockBadge,
    dinoHighScore: state.dinoHighScore,
    updateDinoHighScore: state.updateDinoHighScore,
  }));

  // Bridge haptic preference to the dino game iframe/script
  useEffect(() => {
    (window as any).__ORION_HAPTIC_DISABLED = !hapticEnabled;
    return () => { delete (window as any).__ORION_HAPTIC_DISABLED; };
  }, [hapticEnabled]);

  // Android back button handler
  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;
    const listenerPromise = CapacitorApp.addListener('backButton', () => onClose());
    return () => { listenerPromise.then(h => h.remove()); };
  }, [onClose]);

  const dinoRef = useRef<DinoGameHandle>(null);
  const [gameOverScore, setGameOverScore] = useState<number | null>(null);
  const [gameStatus, setGameStatus] = useState<DinoGameStatus>('idle');
  const [isNewRecord, setIsNewRecord] = useState(false);

  const allTimeHi = Math.max(dinoHighScore, gameOverScore ?? 0);

  const handleGameOver = (score: number) => {
    setGameOverScore(score);
    setGameStatus('crashed');

    // Track new record
    if (score > dinoHighScore) {
      setIsNewRecord(true);
    }

    // Persist all-time high score
    updateDinoHighScore(score);

    // XP Calculation: 1 XP per 100 score points (up to 50 XP per session)
    const xpEarned = Math.floor(score / 100);
    if (xpEarned > 0) {
      addGameXP(xpEarned);
    }

    // Badge Unlocking — hard thresholds based on all-time HI score
    // These thresholds use the actual in-game displayed score (same as the HI counter)
    // Rookie ≥ 200 | Master ≥ 600 | Legend ≥ 1500 — only dedicated players can reach Legend
    const newAllTimeHi = Math.max(dinoHighScore, score);
    if (newAllTimeHi >= 200) {
      unlockBadge('Dino Rookie');
    }
    if (newAllTimeHi >= 600) {
      unlockBadge('Dino Master');
    }
    if (newAllTimeHi >= 1500) {
      unlockBadge('Dino Legend');
    }
  };

  const actionLabel = useMemo(() => {
    if (gameStatus === 'playing') return 'Jump';
    if (gameStatus === 'crashed') return 'Restart';
    return 'Play';
  }, [gameStatus]);

  const handleAction = () => {
    if (gameStatus === 'playing') {
      // Direct imperative call — no React state round-trip, eliminating button lag
      dinoRef.current?.jump();
      return;
    }

    if (gameStatus === 'crashed') {
      setGameOverScore(null);
      setIsNewRecord(false);
      dinoRef.current?.restart();
      return;
    }

    setGameOverScore(null);
    setIsNewRecord(false);
    dinoRef.current?.start();
  };

  return (
    <div 
      className="fixed inset-0 z-[120] flex items-center justify-center bg-black/45 backdrop-blur-md animate-fade-in px-4"
      onClick={onClose}
    >
      <div 
        className="relative w-full max-w-sm animate-scale-up"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="relative overflow-hidden rounded-[2rem] border border-theme-border bg-surface shadow-2xl">
          <div className="absolute inset-x-0 top-0 h-24 bg-gradient-to-b from-primary/10 to-transparent pointer-events-none" />

          <div className="relative px-5 pt-5 pb-4">
            <button
              onClick={onClose}
              className="absolute right-4 top-4 flex h-9 w-9 items-center justify-center rounded-full border border-theme-border bg-card text-theme-sub transition-colors hover:text-theme-text hover:border-theme-sub/30"
              aria-label="Close game"
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>

            <div className="mb-4 flex items-start gap-3 pr-12">
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary ring-1 ring-primary/15">
                <i className="fas fa-gamepad text-base" />
              </div>
              <div className="min-w-0">
                <p className="text-[10px] font-black uppercase tracking-[0.28em] text-primary">Mini Game</p>
                <h2 className="mt-1 text-xl font-black tracking-tight text-theme-text">Orion Run</h2>
                <p className="mt-1 text-xs font-medium text-theme-sub">Play while the store is under maintenance.</p>
              </div>
            </div>

            <div className="mb-4 overflow-hidden rounded-[1.4rem] border border-theme-border bg-[#f7f7f7] dark:bg-[#202124] shadow-[inset_0_1px_0_rgba(255,255,255,0.55)]">
              <DinoGame
                ref={dinoRef}
                onGameOver={handleGameOver}
                onStatusChange={setGameStatus}
              />
            </div>

            <div className="space-y-3">
              <div className="grid grid-cols-2 gap-2.5">
                <div className="rounded-2xl border border-theme-border bg-card px-3 py-3">
                  <p className="text-[9px] font-black uppercase tracking-[0.24em] text-theme-sub">Control</p>
                  <div className="mt-2 flex items-center gap-2">
                    <svg className="h-4 w-4 text-primary/70" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z" />
                    </svg>
                    <span className="text-xs font-bold text-theme-text">Action button or tap</span>
                  </div>
                </div>

                <div className="rounded-2xl border border-theme-border bg-card px-3 py-3">
                  <p className="text-[9px] font-black uppercase tracking-[0.24em] text-theme-sub">All-Time HI</p>
                  <div className="mt-2 flex items-center gap-2">
                    <svg className="h-4 w-4 text-primary/70" fill="currentColor" viewBox="0 0 20 20">
                      <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                    </svg>
                    <span className="text-sm font-black text-theme-text">{allTimeHi > 0 ? allTimeHi.toLocaleString() : '—'}</span>
                  </div>
                </div>
              </div>

              {gameOverScore !== null && (
                <div className={`animate-fade-in rounded-2xl border px-4 py-3.5 ${isNewRecord ? 'border-yellow-400/30 bg-yellow-400/[0.07]' : 'border-primary/15 bg-primary/[0.06]'}`}>
                  <div className="flex items-end justify-between gap-3">
                    <div>
                      <div className="flex items-center gap-2">
                        <p className="text-[9px] font-black uppercase tracking-[0.24em] text-theme-sub">This Run</p>
                        {isNewRecord && (
                          <span className="inline-flex items-center gap-1 rounded-full bg-yellow-400/20 px-1.5 py-0.5 text-[8px] font-black uppercase tracking-wider text-yellow-500">
                            <i className="fas fa-trophy text-[7px]" /> New Best!
                          </span>
                        )}
                      </div>
                      <p className="mt-1 text-3xl font-black tracking-tight text-theme-text">{gameOverScore.toLocaleString()}</p>
                    </div>
                    <div className="rounded-xl bg-primary/10 px-3 py-2 text-right">
                      <p className="text-[9px] font-black uppercase tracking-[0.18em] text-primary/80">XP Earned</p>
                      <p className="mt-1 text-sm font-black text-primary">+{Math.floor(gameOverScore / 100)}</p>
                    </div>
                  </div>
                </div>
              )}

              <button 
                onClick={handleAction}
                className="w-full rounded-2xl border border-primary/20 bg-primary/[0.08] px-4 py-3.5 text-xs font-black uppercase tracking-[0.22em] text-primary transition-all hover:border-primary/35 hover:bg-primary/[0.14] active:scale-[0.98]"
              >
                {actionLabel}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DinoGameModal;
