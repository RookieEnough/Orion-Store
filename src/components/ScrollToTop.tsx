import { memo, useState, useEffect } from 'react';

export const ScrollToTop = memo(function ScrollToTop() {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const handleScroll = () => setVisible(window.scrollY > 300);
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const scrollToTop = () => window.scrollTo({ top: 0, behavior: 'smooth' });

  return (
    <button
      onClick={scrollToTop}
      className={`fixed bottom-24 right-6 z-40 w-12 h-12 rounded-full bg-card border border-theme-border shadow-lg flex items-center justify-center text-primary transition-all duration-300 ${
        visible ? 'translate-y-0 opacity-100' : 'translate-y-10 opacity-0'
      }`}
    >
      <i className="fas fa-arrow-up" />
    </button>
  );
});
