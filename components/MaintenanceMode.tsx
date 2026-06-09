import React, { useState, Suspense, lazy, useRef, useEffect } from 'react';
import { useSettingsStore, Theme } from '../store/useAppStore';
import DinoCirclePreview from './DinoCirclePreview';

const DinoGameModal = lazy(() => import('./DinoGameModal'));

interface MaintenanceModeProps {
  maintenanceMessage?: string;
  socialLinks: { discord?: string; coffee?: string };
  onBypass: () => void;
  triggerHaptic: (type?: any, style?: any, notificationType?: any) => void;
  version: string;
}

const MaintenanceMode: React.FC<MaintenanceModeProps> = ({
  maintenanceMessage,
  socialLinks,
  onBypass,
  triggerHaptic,
  version
}) => {
  const [showGame, setShowGame] = useState(false);
  const clickCount = useRef(0);
  const lastClick = useRef(0);
  const { setDevUnlocked } = useSettingsStore();
  const isDevUnlocked = useSettingsStore((s) => s.isDevUnlocked);
  const theme = useSettingsStore((s) => s.theme);
  const helperTextClass = theme === 'light' ? 'fill-black/80' : 'fill-yellow-300';

  useEffect(() => {
    // Lock scroll on mount
    document.body.style.overflow = 'hidden';
    return () => {
      // Restore scroll on unmount
      document.body.style.overflow = 'unset';
    };
  }, []);

  return (
    <div className="relative flex min-h-screen w-full items-center justify-center overflow-hidden bg-surface px-6 py-10 text-theme-text selection:bg-primary/30">
      {/* Background Layer: Animated Gradient & Grid */}
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute left-1/2 top-[10%] h-[40rem] w-[40rem] -translate-x-1/2 rounded-full bg-primary/20 blur-[120px] transition-all duration-1000" />
        <div className="absolute left-1/2 top-[60%] h-[35rem] w-[35rem] -translate-x-1/2 rounded-full bg-acid/10 blur-[140px] transition-all duration-1000" />
      </div>

      <div
        className="pointer-events-none absolute inset-0 opacity-[0.05] dark:opacity-[0.08]"
        style={{
          backgroundImage: 'radial-gradient(circle at 1.5px 1.5px, currentColor 1.5px, transparent 0)',
          backgroundSize: '32px 32px',
        }}
        aria-hidden
      />

      {/* Main Content Card */}
      <div className="relative z-10 flex w-full max-w-sm flex-col items-center">
        {/* Dino Game Circle - The Interactive Centerpiece */}
        <button
          onClick={() => { triggerHaptic('selection'); setShowGame(true); }}
          className="group relative mb-10 flex h-32 w-32 items-center justify-center animate-fade-in"
        >
          {/* Orbital rings */}
          <div className="absolute inset-0 rounded-full border border-theme-border/60 transition-transform duration-500 group-hover:scale-110" />
          <div className="absolute inset-[-10%] rounded-full border border-primary/20 animate-[spin_20s_linear_infinite]" />

          <svg
            className="pointer-events-none absolute inset-[-8%] z-[1] overflow-visible"
            viewBox="0 0 140 140"
            aria-hidden
          >
            <path id="maintenance-tap-arc" d="M 25 66 A 45 45 0 0 1 115 66" fill="none" />
            <text
              className={`${helperTextClass} text-[8px] font-extrabold uppercase tracking-[0.34em]`}
              style={{ letterSpacing: '0.28em' }}
            >
              <textPath href="#maintenance-tap-arc" startOffset="50%" textAnchor="middle">
                Tap to Play
              </textPath>
            </text>
          </svg>

          {/* Neon Glow */}
          <div className="absolute inset-2 rounded-full bg-primary/10 blur-xl group-hover:bg-primary/20 transition-colors" />

          {/* Dino Live Animated Preview */}
          <div className="relative flex h-24 w-24 items-center justify-center overflow-hidden rounded-full bg-[#f7f7f7] dark:bg-[#202124] ring-1 ring-theme-border shadow-[0_8px_40px_-12px_rgba(99,102,241,0.5)] transition-all duration-300 group-hover:ring-primary/40 group-hover:shadow-primary/20">
            {/* The standalone canvas preview */}
            <DinoCirclePreview size={96} isPaused={showGame} />

            {/* Play Overlay */}
            <div className="absolute inset-0 flex items-center justify-center bg-primary/0 group-hover:bg-primary/10 transition-colors z-10">
              <i className="fas fa-play text-primary scale-0 group-hover:scale-100 transition-transform duration-300"></i>
            </div>
          </div>
        </button>

        <div className="flex flex-col items-center text-center animate-slide-up">
          <p className="mb-3 text-[0.7rem] font-black uppercase tracking-[0.32em] text-primary drop-shadow-[0_0_10px_rgba(99,102,241,0.3)]">
            Orion · Under Overhaul
          </p>
          <h1 className="mb-4 text-3xl font-black leading-tight tracking-tight text-theme-text sm:text-4xl">
            Improving Orion
          </h1>
          <p className="mb-10 max-w-[22rem] text-sm font-medium leading-relaxed text-theme-sub">
            {maintenanceMessage || "We're polishing the store with a major overhaul. Hang tight — it will be worth the wait."}
          </p>

          {/* Actions */}
          <div className="flex w-full flex-col gap-3">
            <div className="flex items-center gap-3 rounded-2xl border border-theme-border bg-theme-element p-3 shadow-sm">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary/10">
                <i className="fas fa-cat text-primary text-base opacity-90" />
              </div>
              <div className="flex-1 text-left">
                <p className="text-[10px] font-black uppercase tracking-widest text-theme-sub">Store Status</p>
                <p className="text-xs font-bold text-primary">Under Maintenance</p>
              </div>
            </div>

            {socialLinks.discord && (
              <button
                onClick={() => { triggerHaptic('selection'); window.open(socialLinks.discord, '_blank'); }}
                className="group relative w-full overflow-hidden rounded-2xl bg-primary px-5 py-4 text-sm font-black text-white shadow-lg shadow-primary/25 transition-all duration-300 hover:shadow-primary/40 hover:-translate-y-0.5 active:scale-[0.98]"
              >
                <div className="absolute inset-0 bg-gradient-to-r from-transparent via-white/10 to-transparent -translate-x-full group-hover:translate-x-full transition-transform duration-1000"></div>
                <span className="relative z-10 flex items-center justify-center gap-3 uppercase tracking-widest">
                  <i className="fab fa-discord text-base" />
                  Join Community
                </span>
              </button>
            )}

            {socialLinks.coffee && (
              <button
                onClick={() => { triggerHaptic('selection'); window.open(socialLinks.coffee, '_blank'); }}
                className="group relative w-full flex items-center justify-center gap-3 py-2.5 px-4 bg-emerald-100 dark:bg-emerald-900/60 border-2 border-emerald-400 rounded-2xl hover:scale-[1.01] transition-all cursor-pointer shadow-lg shadow-emerald-400/20"
              >
                <div className="w-8 h-8 rounded-full bg-emerald-400 text-emerald-900 flex items-center justify-center text-lg shrink-0 group-hover:rotate-12 transition-transform">
                    <i className="fas fa-coins"></i>
                </div>
                <div className="text-center">
                    <span className="font-black text-sm uppercase tracking-widest text-gray-900 dark:text-emerald-100 block leading-none">DONATE ME</span>
                    <span className="text-[10px] text-emerald-600 dark:text-emerald-200 font-bold tracking-wider mt-1 block">Support development</span>
                </div>
                {/* Visual balance spacer of same width as icon */}
                <div className="w-8 h-8 invisible shrink-0" />
              </button>
            )}

            {isDevUnlocked && (
              <button
                onClick={onBypass}
                className="mt-2 w-full rounded-xl border border-theme-border/50 bg-theme-element/30 py-2 text-[10px] font-black uppercase tracking-[0.3em] text-theme-sub transition-all hover:border-primary/30 hover:text-primary"
              >
                Developer Bypass
              </button>
            )}
          </div>

          <p
            onClick={() => {
              const now = Date.now();
              if (now - lastClick.current < 500) {
                clickCount.current++;
                if (clickCount.current === 7) {
                  setDevUnlocked(true);
                  triggerHaptic('notification', 'success');
                } else if (clickCount.current > 3) {
                  triggerHaptic('selection');
                }
              } else {
                clickCount.current = 1;
              }
              lastClick.current = now;
            }}
            className="mt-12 text-[10px] font-black uppercase tracking-[0.4em] text-theme-sub/20 active:opacity-10"
          >
            Orion Store · {version}
          </p>
        </div>
      </div>

      {/* Game Modal */}
      <Suspense fallback={null}>
        {showGame && <DinoGameModal onClose={() => setShowGame(false)} />}
      </Suspense>
    </div>
  );
};

export default MaintenanceMode;
