
import React, { useState } from 'react';
import { AppItem } from '../types';
import { CATEGORY_GRADIENTS } from '../constants';
import { getOptimizedImageUrl } from '../utils/image';
import { useDataStore } from '../store/useAppStore';

interface AppCardProps {
  app: AppItem;
  onClick: (app: AppItem) => void;
  localVersion?: string;
  hasUpdateNotification?: boolean; 
  downloadProgress?: number;
  downloadStatus?: string;
  isReadyToInstall?: boolean;
  isDownloading?: boolean;
}

const AppCard: React.FC<AppCardProps> = React.memo(({ app, onClick, localVersion, hasUpdateNotification, downloadProgress, downloadStatus, isReadyToInstall, isDownloading: propsIsDownloading }) => {
  const [imgStatus, setImgStatus] = useState<'loading' | 'loaded' | 'error'>('loading');
  const { favorites } = useDataStore();

  const isInstalled = !!localVersion;
  const isUpdateAvailable = hasUpdateNotification || false;
  const isDownloading = propsIsDownloading !== undefined ? propsIsDownloading : downloadStatus === 'RUNNING';
  const bgGradient = CATEGORY_GRADIENTS[app.category] || CATEGORY_GRADIENTS['Default'];
  const isFavorite = favorites.includes(app.id);
  
  const optimizedUrl = getOptimizedImageUrl(app.icon, 128, 128);

  const handleClick = () => onClick(app);

  return (
    <div 
      onClick={handleClick}
      className="app-card-optimized app-card relative bg-card rounded-3xl p-4 flex items-center gap-4 cursor-pointer border border-theme-border active:scale-98 shadow-sm transition-transform group"
    >
      <div className="relative shrink-0 w-16 h-16">
        {imgStatus !== 'loaded' && (
            <div className={`absolute inset-0 rounded-2xl flex items-center justify-center text-white text-2xl font-bold shadow-inner ${imgStatus === 'error' ? bgGradient : 'bg-theme-element'}`}>
               {imgStatus === 'error' && app.name.charAt(0).toUpperCase()}
            </div>
        )}

        <img 
             src={optimizedUrl} 
             alt={app.name} 
             onLoad={() => setImgStatus('loaded')}
             onError={() => setImgStatus('error')}
             decoding="async"
             loading="lazy"
             className={`w-full h-full object-contain rounded-2xl transition-opacity duration-300 ${imgStatus === 'loaded' ? 'opacity-100' : 'opacity-0'}`} 
             style={{ background: "transparent" }}
        />

        {isUpdateAvailable && !isDownloading && !isReadyToInstall && (
          <span className="absolute -top-1 -right-1 flex h-4 w-4 z-10">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-acid opacity-75"></span>
            <span className="relative inline-flex rounded-full h-4 w-4 bg-acid border-2 border-surface-light dark:border-surface-dark"></span>
          </span>
        )}

        {isDownloading && (
            <div className="absolute inset-0 bg-black/40 backdrop-blur-[2px] rounded-2xl flex items-center justify-center z-10 animate-fade-in overflow-hidden">
                <svg className="w-10 h-10 transform -rotate-90">
                    <circle cx="20" cy="20" r="16" stroke="currentColor" strokeWidth="4" fill="transparent" className="text-white/20" />
                    <circle cx="20" cy="20" r="16" stroke="currentColor" strokeWidth="4" fill="transparent" 
                        strokeDasharray={100} strokeDashoffset={100 - (downloadProgress || 0)} 
                        strokeLinecap="round" className="text-acid transition-all duration-300" />
                </svg>
                <div className="absolute inset-0 flex items-center justify-center">
                    <span className="text-[10px] font-black text-white">{downloadProgress || 0}%</span>
                </div>
            </div>
        )}

        {isReadyToInstall && (
            <div className="absolute inset-0 bg-primary/20 backdrop-blur-[1px] rounded-2xl flex items-center justify-center z-10 animate-pulse border-2 border-primary/50">
                <i className="fas fa-box-open text-primary text-xl"></i>
            </div>
        )}

        {isFavorite && (
            <div className="absolute -bottom-1 -right-1 w-5 h-5 bg-card/90 backdrop-blur-sm rounded-full border border-theme-border flex items-center justify-center text-rose-500 shadow-sm z-20">
                <i className="fas fa-heart text-[10px]"></i>
            </div>
        )}

        {isInstalled && !isUpdateAvailable && !isDownloading && !isReadyToInstall && (
            <div className="absolute -bottom-1 -left-1 w-5 h-5 bg-green-500 rounded-full border-2 border-card flex items-center justify-center text-white text-[10px] z-10 shadow-sm">
                <i className="fas fa-check"></i>
            </div>
        )}
      </div>

      <div className="flex-1 min-w-0 flex flex-col gap-1">
        <div className="flex justify-between items-start">
          <h3 className="text-lg font-bold text-theme-text truncate tracking-tight leading-tight">{app.name}</h3>
        </div>
        <p className="text-xs font-medium text-theme-sub">{app.author}</p>
        <div className="flex items-center gap-2 mt-1">
          <span className={`inline-flex items-center px-2.5 py-0.5 rounded-lg text-[10px] font-bold uppercase tracking-wider ${
                app.category === 'Media' ? 'bg-purple-100 text-purple-600 dark:bg-purple-900/30 dark:text-purple-300' :
                app.category === 'Social' ? 'bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-300' :
                'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400'
             }`}>
            {app.category}
          </span>
          {isDownloading ? (
             <span className="inline-flex items-center px-2.5 py-0.5 rounded-lg text-[10px] font-bold bg-primary/20 text-primary border border-primary/30">
               DL...
             </span>
          ) : isReadyToInstall ? (
             <span className="inline-flex items-center px-2.5 py-0.5 rounded-lg text-[10px] font-bold bg-primary text-white shadow-lg shadow-primary/20">
               INSTALL
             </span>
          ) : isUpdateAvailable ? (
             <span className="inline-flex items-center px-2.5 py-0.5 rounded-lg text-[10px] font-bold bg-acid/20 text-acid-dark dark:text-acid border border-acid/30">
               UPDATE
             </span>
          ) : isInstalled ? (
             <span className="inline-flex items-center px-2.5 py-0.5 rounded-lg text-[10px] font-bold bg-green-500/10 text-green-600 dark:text-green-400 border border-green-500/20">
               v{localVersion}
             </span>
          ) : null}
        </div>
      </div>

      <div className="shrink-0 pl-2">
          <div className="w-10 h-10 rounded-full bg-theme-element flex items-center justify-center transition-colors group-hover:bg-theme-hover">
              <i className="fas fa-chevron-right text-theme-sub/60 text-xs"></i>
          </div>
      </div>
    </div>
  );
});

export default AppCard;
