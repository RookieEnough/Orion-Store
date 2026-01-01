import { memo, useState, useEffect } from 'react';

export const ScrollToTop = memo(function ScrollToTop() {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const onScroll = () => setVisible(window.scrollY > 300);
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  return (
    <button
      onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
      className={`fixed bottom-24 right-6 z-40 w-12 h-12 rounded-full bg-card border border-theme-border shadow-lg flex items-center justify-center text-primary transition-all duration-300 ${visible ? 'opacity-100' : 'opacity-0 translate-y-10 pointer-events-none'}`}
    >
      <i className="fas fa-arrow-up" />
    </button>
  );
});
