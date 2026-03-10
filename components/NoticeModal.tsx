
import React from 'react';
import { useScrollLock } from '../hooks/useScrollLock';

interface NoticeModalProps {
  title: string;
  message: string;
  onClose: () => void;
}

const NoticeModal: React.FC<NoticeModalProps> = ({ title, message, onClose }) => {
  useScrollLock(true);

  return (
    <div className="fixed inset-0 z-[200] flex items-center justify-center p-6 animate-fade-in">
      <div className="absolute inset-0 bg-black/90 backdrop-blur-md" onClick={onClose}></div>
      
      <div className="relative w-full max-w-sm bg-surface border border-theme-border rounded-[2.5rem] shadow-2xl overflow-hidden animate-slide-up flex flex-col items-center text-center p-8">
        
        {/* Decorative Background */}
        <div className="absolute top-0 left-0 right-0 h-40 bg-gradient-to-b from-primary/10 to-transparent pointer-events-none"></div>
        <div className="absolute -top-10 -right-10 w-40 h-40 bg-acid/10 rounded-full blur-3xl pointer-events-none"></div>
        
        {/* Icon */}
        <div className="relative mb-6">
            <div className="w-20 h-20 bg-theme-text text-surface rounded-3xl flex items-center justify-center text-4xl shadow-xl transform rotate-3 select-none">
                <i className="fab fa-github"></i>
            </div>
            <div className="absolute -bottom-2 -right-2 w-8 h-8 bg-primary text-white rounded-full flex items-center justify-center border-4 border-surface text-sm animate-bounce">
                <i className="fas fa-bell"></i>
            </div>
        </div>

        {/* Content */}
        <h2 className="text-2xl font-black text-theme-text mb-3 leading-tight tracking-tight relative z-10">
            {title}
        </h2>
        
        <div className="w-12 h-1 bg-gradient-to-r from-acid to-primary rounded-full mb-6"></div>

        <p className="text-theme-sub text-sm font-medium leading-relaxed mb-8 relative z-10 whitespace-pre-wrap">
            {message}
        </p>

        {/* Button */}
        <button 
            onClick={onClose}
            className="w-full py-4 rounded-2xl bg-theme-text text-surface font-bold text-sm shadow-lg hover:scale-[1.02] active:scale-95 transition-all flex items-center justify-center gap-2 relative z-10 group"
        >
            <span>Got it</span>
            <i className="fas fa-arrow-right group-hover:translate-x-1 transition-transform"></i>
        </button>

      </div>
    </div>
  );
};

export default NoticeModal;
