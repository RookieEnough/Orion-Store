import { memo, useState } from 'react';
import { useStore, useSupportEmail } from '@/store';
import { AppIcon } from './AppIcon';
import type { AppItem } from '@/types';

interface AppDetailProps {
  app: AppItem;
  onClose: () => void;
}

export const AppDetail = memo(function AppDetail({ app, onClose }: AppDetailProps) {
  const { handleDownload, checkHasUpdate, installedVersions } = useStore();
  const supportEmail = useSupportEmail();
  const [showVariants, setShowVariants] = useState(false);

  const localVersion = installedVersions[app.id];
  const hasUpdate = checkHasUpdate(app);

  const handleReport = () => {
    const subject = encodeURIComponent(`[OrionStore] Issue with ${app.name}`);
    const body = encodeURIComponent(`App: ${app.name}\nVersion: ${app.latestVersion}\nIssue: \n\n(Please describe the issue)`);
    window.open(`mailto:${supportEmail}?subject=${subject}&body=${body}`);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center animate-fade-in">
      <div className="absolute inset-0 bg-black/70 backdrop-blur-md" onClick={onClose} />

      <div className="relative bg-surface border-t sm:border border-theme-border rounded-t-[2rem] sm:rounded-3xl w-full sm:max-w-lg max-h-[90vh] overflow-y-auto animate-slide-up no-scrollbar">
        <div className="sticky top-0 bg-surface/90 backdrop-blur-xl z-10 p-4 flex justify-between items-center border-b border-theme-border">
          <button onClick={onClose} className="w-10 h-10 rounded-full bg-theme-element flex items-center justify-center text-theme-sub hover:text-theme-text transition-colors">
            <i className="fas fa-times" />
          </button>
          <button onClick={handleReport} className="w-10 h-10 rounded-full bg-red-500/10 flex items-center justify-center text-red-500 hover:bg-red-500/20 transition-colors" title="Report Issue">
            <i className="fas fa-exclamation-triangle" />
          </button>
        </div>

        <div className="p-6">
          <div className="flex items-start gap-4 mb-6">
            <AppIcon src={app.icon} name={app.name} category={app.category} size="lg" />
            <div className="flex-1 min-w-0">
              <h2 className="text-2xl font-black text-theme-text">{app.name}</h2>
              <p className="text-theme-sub">{app.author}</p>
              <div className="flex flex-wrap gap-2 mt-2">
                <span className="text-xs font-bold bg-primary/10 text-primary px-2 py-1 rounded-lg">{app.category}</span>
                <span className="text-xs font-medium bg-theme-element text-theme-sub px-2 py-1 rounded-lg">{app.size}</span>
              </div>
            </div>
          </div>

          {localVersion && (
            <div className="bg-theme-element rounded-2xl p-4 mb-6">
              <div className="flex justify-between items-center">
                <span className="text-sm text-theme-sub">Installed</span>
                <span className="font-bold text-theme-text">{localVersion}</span>
              </div>
              {hasUpdate && (
                <div className="flex justify-between items-center mt-2 pt-2 border-t border-theme-border">
                  <span className="text-sm text-acid-dark dark:text-acid">Available</span>
                  <span className="font-bold text-acid-dark dark:text-acid">{app.latestVersion}</span>
                </div>
              )}
            </div>
          )}

          <p className="text-theme-sub mb-6 leading-relaxed">{app.description}</p>

          {app.screenshots.length > 0 && (
            <div className="mb-6">
              <h3 className="font-bold text-theme-text mb-3">Screenshots</h3>
              <div className="flex gap-3 overflow-x-auto no-scrollbar pb-2">
                {app.screenshots.map((src, i) => (
                  <img key={i} src={src} alt={`Screenshot ${i + 1}`} className="h-48 rounded-xl object-cover shadow-lg" />
                ))}
              </div>
            </div>
          )}

          <div className="space-y-3">
            {app.variants && app.variants.length > 1 ? (
              <>
                <button onClick={() => setShowVariants(!showVariants)} className="w-full py-4 rounded-2xl font-bold bg-primary text-white shadow-lg shadow-primary/30 hover:bg-primary/90 transition-all active:scale-[0.98] flex items-center justify-center gap-2">
                  <i className={`fas fa-${hasUpdate ? 'sync-alt' : 'download'}`} />
                  {hasUpdate ? 'Update' : 'Download'} ({app.variants.length} variants)
                  <i className={`fas fa-chevron-${showVariants ? 'up' : 'down'} text-xs ml-2`} />
                </button>
                {showVariants && (
                  <div className="space-y-2 animate-fade-in">
                    {app.variants.map((variant, i) => (
                      <button key={i} onClick={() => handleDownload(app, variant.url)} className="w-full py-3 px-4 rounded-xl font-medium bg-theme-element text-theme-text hover:bg-theme-hover transition-all flex items-center justify-between">
                        <span>{variant.arch}</span>
                        <i className="fas fa-download text-primary" />
                      </button>
                    ))}
                  </div>
                )}
              </>
            ) : (
              <button onClick={() => handleDownload(app)} className="w-full py-4 rounded-2xl font-bold bg-primary text-white shadow-lg shadow-primary/30 hover:bg-primary/90 transition-all active:scale-[0.98] flex items-center justify-center gap-2">
                <i className={`fas fa-${hasUpdate ? 'sync-alt' : 'download'}`} />
                {hasUpdate ? 'Update' : 'Download'}
              </button>
            )}

            {app.repoUrl && (
              <a href={app.repoUrl} target="_blank" rel="noreferrer" className="w-full py-3 rounded-xl font-medium bg-theme-element text-theme-text hover:bg-theme-hover transition-all flex items-center justify-center gap-2">
                <i className="fab fa-github" />View Source
              </a>
            )}
          </div>
        </div>
      </div>
    </div>
  );
});
