import { memo } from 'react';

interface ToastProps {
  message: string;
  type: 'success' | 'error' | 'info' | 'update';
  visible: boolean;
}

const styles = {
  success: { bg: 'bg-gray-900 dark:bg-white text-white dark:text-black', icon: 'fa-check-circle text-green-500' },
  error: { bg: 'bg-red-100 dark:bg-red-900/50 text-red-800 dark:text-red-200 border border-red-200 dark:border-red-800', icon: 'fa-exclamation-circle' },
  info: { bg: 'bg-card/90 backdrop-blur-md text-theme-text border border-theme-border', icon: '' },
  update: { bg: 'bg-acid text-black border border-black/10', icon: 'fa-sync-alt fa-spin' },
};

export const Toast = memo(function Toast({ message, type, visible }: ToastProps) {
  const { bg, icon } = styles[type];
  return (
    <div className={`fixed top-36 left-1/2 -translate-x-1/2 z-50 transition-all duration-500 px-6 py-3 rounded-full shadow-2xl flex items-center gap-3 font-bold ${bg} ${visible ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-10 pointer-events-none'}`}>
      {icon && <i className={`fas ${icon}`} />}
      <span>{message}</span>
    </div>
  );
});
