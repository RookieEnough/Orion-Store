import React, { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { motion, AnimatePresence } from 'motion/react';
import { useTutorialStore } from '../store/useTutorialStore';
import { Haptics } from '@capacitor/haptics';

const SpotlightOverlay: React.FC = () => {
  const { isActive, steps, currentStepIndex, endTutorial } = useTutorialStore();
  const [targetRect, setTargetRect] = useState<DOMRect | null>(null);

  const step = steps[currentStepIndex];

  useEffect(() => {
    if (!isActive || !step) return;

    const updateRect = () => {
      if (step.targetId) {
        const el = document.getElementById(step.targetId);
        if (el) {
          setTargetRect(el.getBoundingClientRect());
        } else {
          // Element might be off DOM momentarily
          setTargetRect(null);
        }
      } else {
        setTargetRect(null);
      }
    };

    updateRect();
    window.addEventListener('resize', updateRect);
    
    // Watch for DOM changes (like tabs sliding in)
    const observer = new MutationObserver(updateRect);
    observer.observe(document.body, { childList: true, subtree: true, attributes: true });

    return () => {
      window.removeEventListener('resize', updateRect);
      observer.disconnect();
    };
  }, [isActive, step]);

  if (!isActive || !step) return null;

  const handleNext = () => {
    Haptics.selection().catch(() => {});
    if (step.onTargetClick) {
      step.onTargetClick();
    }
  };

  const isFinalStep = currentStepIndex === steps.length - 1;
  const showCutout = step.targetId && targetRect;

  return createPortal(
    <div className="fixed inset-0 z-[9999] pointer-events-none">
       {/* Global click trap to prevent clicking outside the target */}
       <div className="absolute inset-0 pointer-events-auto" onMouseDown={(e) => e.stopPropagation()} onClick={(e) => e.stopPropagation()} />

       {/* Background Mask */}
       <AnimatePresence mode="wait">
          {showCutout ? (
            <motion.div
              key={`cutout-${currentStepIndex}`}
              className="absolute pointer-events-auto transition-all duration-500 ease-out cursor-pointer"
              style={{
                top: targetRect.top - 8,
                left: targetRect.left - 8,
                width: targetRect.width + 16,
                height: targetRect.height + 16,
                borderRadius: '1.25rem', // 20px
                boxShadow: '0 0 0 9999px rgba(0,0,0,0.85)',
                border: '3px solid rgba(99,102,241,0.9)' // Primary color border
              }}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={handleNext}
            >
              {/* Optional inner pulse effect for the highlighted area */}
              <div className="absolute inset-0 rounded-[1.25rem] shadow-[0_0_20px_rgba(99,102,241,0.5)] animate-pulse" />
            </motion.div>
          ) : (
            <motion.div
              key="full-mask"
              className="absolute inset-0 bg-black/85 pointer-events-auto"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
            />
          )}
       </AnimatePresence>

       {/* Floating Tooltip Wrapper */}
       <div 
         className="absolute inset-x-0 pointer-events-none flex justify-center"
         style={{
           top: showCutout && targetRect ? (targetRect.top > window.innerHeight / 2 ? 0 : targetRect.bottom + 24) : 0,
           bottom: showCutout && targetRect ? (targetRect.top > window.innerHeight / 2 ? window.innerHeight - targetRect.top + 24 : 0) : 0,
           alignItems: showCutout && targetRect ? (targetRect.top > window.innerHeight / 2 ? 'flex-end' : 'flex-start') : 'center'
         }}
       >
         <AnimatePresence mode="wait">
           <motion.div
             key={`tooltip-${currentStepIndex}`}
             className="z-10 w-[90%] max-w-sm bg-surface border border-theme-border rounded-3xl p-6 shadow-2xl pointer-events-auto text-center flex flex-col items-center"
             initial={{ opacity: 0, scale: 0.9, y: 20 }}
             animate={{ opacity: 1, scale: 1, y: 0 }}
             exit={{ opacity: 0, scale: 0.9, y: -20 }}
           >
           <div className="w-12 h-12 rounded-full bg-primary/15 text-primary flex items-center justify-center text-xl mb-4 shadow-sm">
             <i className="fas fa-magic"></i>
           </div>
           
           <h3 className="text-xl font-black text-theme-text mb-2 tracking-tight">{step.title}</h3>
           <p className="text-sm text-theme-sub mb-6 leading-relaxed max-w-[28ch]">{step.description}</p>
           
           {/* If there is no target to click, or it's the final step without a target, show an explicit button */}
           {showCutout ? (
              <div className="flex flex-col items-center gap-1.5 animate-bounce text-primary">
                 <i className={`fas ${targetRect.top > window.innerHeight / 2 ? 'fa-arrow-down' : 'fa-arrow-up'} text-xl`}></i>
                 <span className="text-[10px] font-bold uppercase tracking-widest">Tap highlight to continue</span>
              </div>
           ) : (
             <button
               onClick={() => {
                 Haptics.selection().catch(() => {});
                 if (step.onTargetClick) step.onTargetClick();
                 else endTutorial();
               }}
               className="w-full px-5 py-3.5 bg-primary text-white font-bold rounded-2xl shadow-lg shadow-primary/25 active:scale-95 transition-all text-[0.95rem]"
             >
               {step.buttonLabel || (isFinalStep ? 'Got it!' : 'Next')}
             </button>
           )}
         </motion.div>
       </AnimatePresence>
       </div>
    </div>,
    document.body
  );
};

export default SpotlightOverlay;
