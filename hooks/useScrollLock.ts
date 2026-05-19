
import { useEffect } from 'react';

interface ScrollLockSnapshot {
  bodyOverflow: string;
  bodyOverscrollBehavior: string;
  rootOverflowX: string;
  rootOverflowY: string;
  rootOverscrollBehavior: string;
}

let activeScrollLocks = 0;
let scrollLockSnapshot: ScrollLockSnapshot | null = null;

/**
 * Locks the body and root element scroll when the condition is true.
 * Uses a shared ref-count so nested modals cannot restore scrolling too early.
 */
export const useScrollLock = (isLocked: boolean) => {
  useEffect(() => {
    if (!isLocked || typeof document === 'undefined') return;

    const root = document.getElementById('root');
    const body = document.body;

    if (activeScrollLocks === 0) {
      scrollLockSnapshot = {
        bodyOverflow: body.style.overflow,
        bodyOverscrollBehavior: body.style.overscrollBehavior,
        rootOverflowX: root?.style.overflowX || '',
        rootOverflowY: root?.style.overflowY || '',
        rootOverscrollBehavior: root?.style.overscrollBehavior || ''
      };

      body.classList.add('orion-scroll-locked');
      body.style.overflow = 'hidden';
      body.style.overscrollBehavior = 'none';

      if (root) {
        root.style.overflowX = 'hidden';
        root.style.overflowY = 'hidden';
        root.style.overscrollBehavior = 'none';
      }
    }

    activeScrollLocks += 1;

    return () => {
      activeScrollLocks = Math.max(0, activeScrollLocks - 1);

      if (activeScrollLocks > 0 || !scrollLockSnapshot) return;

      body.classList.remove('orion-scroll-locked');
      body.style.overflow = scrollLockSnapshot.bodyOverflow;
      body.style.overscrollBehavior = scrollLockSnapshot.bodyOverscrollBehavior;

      const latestRoot = document.getElementById('root');
      if (latestRoot) {
        latestRoot.style.overflowX = scrollLockSnapshot.rootOverflowX;
        latestRoot.style.overflowY = scrollLockSnapshot.rootOverflowY;
        latestRoot.style.overscrollBehavior = scrollLockSnapshot.rootOverscrollBehavior;
      }

      scrollLockSnapshot = null;
    };
  }, [isLocked]);
};
