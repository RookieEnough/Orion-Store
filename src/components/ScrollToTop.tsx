import { memo, useState, useEffect } from 'react';
import { SCROLL_THRESHOLD } from '@/constants';

export const ScrollToTop = memo(function ScrollToTop() {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const onScroll = () => setVisible(window.scrollY > SCROLL_THRESHOLD);
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  if (!visible) return null;

  return (
    <button
      onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
      className="fixed bottom-24 right-6 z-40 w-11 h-11 rounded-xl bg-card border border-theme-border shadow-lg flex items-center justify-center text-theme-sub hover:text-primary hover:border-primary/50 transition-all active:scale-95 animate-fade-in"
    >
      <i className="fas fa-arrow-up" />
    </button>
  );
});
