
import { useEffect } from 'react';

/**
 * Locks the body and root element scroll when the condition is true.
 * Essential for the #root scroll architecture defined in index.css.
 */
export const useScrollLock = (isLocked: boolean) => {
  useEffect(() => {
    if (!isLocked) return;

    const root = document.getElementById('root');
    const body = document.body;

    // Save original overflow styles
    const originalBodyOverflow = body.style.overflow;
    const originalRootOverflow = root ? root.style.overflow : '';

    // Apply lock
    body.style.overflow = 'hidden';
    if (root) root.style.overflow = 'hidden';

    // Cleanup
    return () => {
      body.style.overflow = originalBodyOverflow;
      if (root) root.style.overflow = originalRootOverflow;
    };
  }, [isLocked]);
};
