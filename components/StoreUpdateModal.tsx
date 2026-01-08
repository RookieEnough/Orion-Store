
import React from 'react';

interface StoreUpdateModalProps {
  currentVersion: string;
  newVersion: string;
  downloadUrl: string;
  onClose: () => void;
}

const StoreUpdateModal: React.FC<StoreUpdateModalProps> = ({ currentVersion, newVersion, downloadUrl, onClose }) => {
  return (
    <div className="fixed inset-0 z-[150] flex items-center justify-center p-6 animate-fade-in">
      <div className="absolute inset-0 bg-black/80 backdrop-blur-md" onClick={onClose}></div>
      
      <div className="relative bg-surface border border-theme-border w-full max-w-sm rounded-[2.5rem] shadow-2xl overflow-hidden animate-slide-up flex flex-col">
        <div className="absolute top-0 left-0 right-0 h-32 bg-gradient-to-b from-primary/20 to-transparent -z-10"></div>
        
        <div className="p-8 flex flex-col items-center text-center">
            <div className="w-20 h-20 bg-primary text-white rounded-3xl flex items-center justify-center text-4xl mb-6 shadow-2xl shadow-primary/40 transform rotate-3 animate-bounce">
                <i className="fas fa-rocket"></i>
            </div>
            
            <h3 className="text-2xl font-black text-theme-text mb-2 tracking-tight">New Update Live!</h3>
            
            <div className="flex items-center gap-3 mb-6 bg-theme-element px-4 py-2 rounded-2xl border border-theme-border">
                <span className="text-xs font-bold text-theme-sub font-mono">{currentVersion}</span>
                <i className="fas fa-arrow-right text-[10px] text-primary"></i>
                <span className="text-xs font-black text-primary font-mono">{newVersion}</span>
            </div>

            <p className="text-theme-sub text-sm leading-relaxed mb-8 font-medium">
                A new version of Orion Store is available with improvements and fresh apps. Update now to stay synced!
            </p>

            <div className="w-full space-y-3">
                <button 
                    onClick={() => window.location.href = downloadUrl}
                    className="w-full py-4 rounded-2xl bg-primary text-white font-bold text-lg shadow-xl shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all flex items-center justify-center gap-2"
                >
                    <i className="fas fa-download"></i>
                    <span>Download v{newVersion}</span>
                </button>
                
                <button 
                    onClick={onClose}
                    className="w-full py-3 rounded-2xl text-theme-sub font-bold hover:bg-theme-element transition-colors text-xs uppercase tracking-widest"
                >
                    Maybe Later
                </button>
            </div>
        </div>
        
        {/* Decorative corner */}
        <div className="absolute -bottom-6 -right-6 w-24 h-24 bg-acid/10 rounded-full blur-2xl"></div>
      </div>
    </div>
  );
};

export default StoreUpdateModal;
