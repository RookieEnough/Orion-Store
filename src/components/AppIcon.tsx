import { memo, useState } from 'react';
import { CATEGORY_GRADIENTS } from '@/constants';
import type { AppCategory } from '@/types';

const sizes = { sm: 'w-12 h-12 text-xl', md: 'w-16 h-16 text-2xl', lg: 'w-20 h-20 text-3xl' };

export const AppIcon = memo(function AppIcon({ src, name, category, size = 'md' }: { src: string; name: string; category: AppCategory; size?: 'sm' | 'md' | 'lg' }) {
  const [err, setErr] = useState(false);
  const s = sizes[size];
  const gradient = CATEGORY_GRADIENTS[category] || CATEGORY_GRADIENTS.Default;

  return err || !src ? (
    <div className={`${s} rounded-2xl ${gradient} flex items-center justify-center text-white font-black shadow-lg shrink-0`}>{name.charAt(0)}</div>
  ) : (
    <img src={src} alt={name} onError={() => setErr(true)} className={`${s} rounded-2xl object-cover shadow-lg bg-theme-element shrink-0`} />
  );
});
