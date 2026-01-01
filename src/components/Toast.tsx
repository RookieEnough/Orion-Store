import { memo } from 'react';

interface ToastProps {
  message: string;
  type: 'success' | 'error' | 'info' | 'update' | 'theme';
  visible: boolean;
  icon?: string;
}

export const Toast = memo(function Toast({ message, type, visible, icon }: ToastProps) {
  const baseClasses =
    'fixed top-36 left-1/2 -translate-x-1/2 z-50 transition-all duration-500 px-6 py-3 rounded-full shadow-2xl flex items-center gap-3 font-bold';

  const typeClasses = {
    success: 'bg-gray-900 dark:bg-white text-white dark:text-black',
    error: 'bg-red-100 dark:bg-red-900/50 text-red-800 dark:text-red-200 border border-red-200 dark:border-red-800',
    info: 'bg-card/90 backdrop-blur-md text-theme-text border border-theme-border',
    update: 'bg-acid text-black border border-black/10',
    theme: 'bg-surface/80 backdrop-blur-xl border border-theme-border text-theme-text',
  };

  const defaultIcons = {
    success: 'fa-check-circle text-green-500',
    error: 'fa-exclamation-circle',
    info: '',
    update: 'fa-sync-alt fa-spin',
    theme: '',
  };

  return (
    <div
      className={`${baseClasses} ${typeClasses[type]} ${
        visible ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-10 pointer-events-none'
      }`}
    >
      {(icon || defaultIcons[type]) && <i className={`fas ${icon || defaultIcons[type]}`} />}
      <span>{message}</span>
    </div>
  );
});
