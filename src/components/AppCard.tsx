import { memo, useState } from 'react';
import { CATEGORY_GRADIENTS } from '@/constants';
import type { AppItem } from '@/types';

interface AppCardProps {
  app: AppItem;
  onClick: (app: AppItem) => void;
  hasUpdate: boolean;
  localVersion?: string;
}

export const AppCard = memo(function AppCard({ app, onClick, hasUpdate, localVersion }: AppCardProps) {
  const [imgError, setImgError] = useState(false);
  const gradient = CATEGORY_GRADIENTS[app.category] || CATEGORY_GRADIENTS['Default'];

  return (
    <div
      onClick={() => onClick(app)}
      className="group bg-card border border-theme-border rounded-3xl p-4 cursor-pointer hover:shadow-xl hover:shadow-primary/10 hover:border-primary/30 transition-all duration-300 hover:-translate-y-1 active:scale-[0.98] relative overflow-hidden"
    >
      {hasUpdate && (
        <div className="absolute top-3 right-3 z-10">
          <span className="bg-acid text-black text-[10px] font-black px-2 py-1 rounded-full animate-pulse">
            UPDATE
          </span>
        </div>
      )}

      <div className="flex items-start gap-4">
        <div className="relative shrink-0">
          {imgError ? (
            <div
              className={`w-16 h-16 rounded-2xl ${gradient} flex items-center justify-center text-white text-2xl font-black shadow-lg`}
            >
              {app.name.charAt(0)}
            </div>
          ) : (
            <img
              src={app.icon}
              alt={app.name}
              onError={() => setImgError(true)}
              className="w-16 h-16 rounded-2xl object-cover shadow-lg bg-theme-element"
            />
          )}
        </div>

        <div className="flex-1 min-w-0">
          <h3 className="font-bold text-theme-text text-lg truncate group-hover:text-primary transition-colors">
            {app.name}
          </h3>
          <p className="text-theme-sub text-sm truncate">{app.author}</p>
          <div className="flex items-center gap-2 mt-2">
            <span className="text-xs font-medium text-theme-sub bg-theme-element px-2 py-1 rounded-lg">
              {app.size}
            </span>
            <span className="text-xs font-medium text-primary bg-primary/10 px-2 py-1 rounded-lg">
              {localVersion || app.version}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
});
