import React, { useState } from 'react';
import { useSettingsStore } from '../store/useAppStore';

interface ModernUITutorialProps {
  onOpenSettings: () => void;
  onClose: () => void;
}

const steps = [
  {
    icon: 'fa-cog',
    title: 'Open Settings',
    desc: 'Tap the gear icon in the top right corner',
  },
  {
    icon: 'fa-palette',
    title: 'Go to Interface',
    desc: 'Find the Interface section in settings',
  },
  {
    icon: 'fa-toggle-on',
    title: 'Enable Modern UI',
    desc: 'Toggle on "Modern UI" to unlock the new layout',
  },
] as const;

const ModernUITutorial: React.FC<ModernUITutorialProps> = ({ onOpenSettings, onClose }) => {
  const [step, setStep] = useState(-1);
  const setHasSeen = useSettingsStore((s) => s.setHasSeenModernUITutorial);
  const setStoreLayout = useSettingsStore((s) => s.setStoreLayout);

  const handleNext = () => {
    if (step < steps.length - 1) {
      setStep(step + 1);
    }
  };

  const handleEnableNow = () => {
    setStoreLayout('modern');
    setHasSeen(true);
    onClose();
  };

  const handleDismiss = () => {
    setHasSeen(true);
    onClose();
  };

  const handleOpenSettings = () => {
    setHasSeen(true);
    onOpenSettings();
    onClose();
  };

  const isIntro = step === -1;
  const current = !isIntro ? steps[step % steps.length]! : null;

  return (
    <div className="fixed inset-0 z-[160] flex items-center justify-center p-6 animate-fade-in">
      <div className="backdrop-scrim absolute inset-0 bg-black/60 backdrop-blur-md"></div>

      <div className="relative bg-surface w-full max-w-xs rounded-[2.5rem] shadow-2xl overflow-hidden animate-slide-up flex flex-col">
        <div className="absolute top-0 left-0 right-0 h-24 bg-gradient-to-b from-primary/15 to-transparent -z-10"></div>

        <div className="p-6 flex flex-col items-center text-center">
          {isIntro ? (
            <>
              <div className="w-16 h-16 bg-primary/10 text-primary rounded-2xl flex items-center justify-center text-2xl mb-4 relative">
                <i className="fas fa-star"></i>
                <i className="fas fa-sparkles text-[8px] absolute top-2 right-2 text-primary/60"></i>
                <i className="fas fa-sparkles text-[6px] absolute bottom-3 left-2 text-primary/40"></i>
                <i className="fas fa-sparkles text-[7px] absolute top-4 left-1 text-primary/50"></i>
              </div>
              <h3 className="text-xl font-black text-theme-text mb-1 tracking-tight">Try New UI</h3>
              <p className="text-theme-sub text-xs font-medium leading-relaxed mb-5">Orion has a fresh Modern UI with curated collections, hero carousels, and a beautiful new layout.</p>
              <div className="w-full space-y-2">
                <button
                  onClick={() => setStep(0)}
                  className="w-full py-3 rounded-xl bg-primary text-white font-bold text-sm shadow-lg shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all flex items-center justify-center gap-2"
                >
                  How to Enable
                  <i className="fas fa-arrow-right text-[10px]"></i>
                </button>
                <button
                  onClick={handleEnableNow}
                  className="w-full py-2.5 rounded-xl bg-theme-element text-theme-text font-bold text-xs transition-all hover:bg-theme-hover flex items-center justify-center gap-2"
                >
                  <i className="fas fa-bolt text-[10px]"></i>
                  Enable Now
                </button>
                <button
                  onClick={handleDismiss}
                  className="w-full py-2 rounded-xl text-theme-sub font-bold text-[10px] uppercase tracking-widest hover:bg-theme-element/50 transition-colors"
                >
                  Skip
                </button>
              </div>
            </>
          ) : (
            <>
              <div className="flex gap-1.5 mb-5">
                {steps.map((_, i) => (
                  <div
                    key={i}
                    className={`h-1.5 rounded-full transition-all duration-300 ${i === step ? 'w-6 bg-primary' : i < step ? 'w-1.5 bg-primary/40' : 'w-1.5 bg-theme-element'}`}
                  />
                ))}
              </div>

              <div className="w-14 h-14 bg-primary/10 text-primary rounded-2xl flex items-center justify-center text-xl mb-4">
                <i className={`fas ${current!.icon}`}></i>
              </div>

              <h3 className="text-lg font-black text-theme-text mb-1 tracking-tight">{current!.title}</h3>
              <p className="text-theme-sub text-xs font-medium leading-relaxed mb-5">{current!.desc}</p>

              <div className="w-full space-y-2">
                {step < steps.length - 1 ? (
                  <button
                    onClick={handleNext}
                    className="w-full py-3 rounded-xl bg-primary text-white font-bold text-sm shadow-lg shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all flex items-center justify-center gap-2"
                  >
                    Next
                    <i className="fas fa-arrow-right text-[10px]"></i>
                  </button>
                ) : (
                  <>
                    <button
                      onClick={handleEnableNow}
                      className="w-full py-3 rounded-xl bg-primary text-white font-bold text-sm shadow-lg shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all flex items-center justify-center gap-2"
                    >
                      <i className="fas fa-bolt text-[10px]"></i>
                      Enable Now
                    </button>
                    <button
                      onClick={handleOpenSettings}
                      className="w-full py-2.5 rounded-xl bg-theme-element text-theme-text font-bold text-xs transition-all hover:bg-theme-hover flex items-center justify-center gap-2"
                    >
                      <i className="fas fa-cog text-[10px]"></i>
                      Open Settings
                    </button>
                  </>
                )}

                <button
                  onClick={handleDismiss}
                  className="w-full py-2 rounded-xl text-theme-sub font-bold text-[10px] uppercase tracking-widest hover:bg-theme-element/50 transition-colors"
                >
                  Skip
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default ModernUITutorial;
