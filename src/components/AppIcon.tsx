import { useState } from 'react';
import type { AppCategory } from '@/types';

const GRADIENTS: Record<AppCategory | 'Default', string> = {
  Utility: 'bg-gradient-to-br from-blue-500 to-cyan-400',
  Privacy: 'bg-gradient-to-br from-emerald-500 to-teal-400',
  Media: 'bg-gradient-to-br from-fuchsia-500 to-pink-400',
  Development: 'bg-gradient-to-br from-orange-500 to-amber-400',
  Social: 'bg-gradient-to-br from-indigo-500 to-violet-400',
  Educational: 'bg-gradient-to-br from-green-500 to-lime-400',
  Default: 'bg-gradient-to-br from-gray-500 to-slate-400',
};

const sizes = { sm: 'w-12 h-12 text-xl', md: 'w-16 h-16 text-2xl', lg: 'w-20 h-20 text-3xl' };

export function AppIcon({ src, name, category, size = 'md' }: { src: string; name: string; category: AppCategory; size?: 'sm' | 'md' | 'lg' }) {
  const [err, setErr] = useState(false);
  const s = sizes[size];
  const gradient = GRADIENTS[category] || GRADIENTS.Default;

  return err || !src ? (
    <div className={`${s} rounded-2xl ${gradient} flex items-center justify-center text-white font-black shadow-lg shrink-0`}>{name.charAt(0)}</div>
  ) : (
    <img src={src} alt={name} onError={() => setErr(true)} className={`${s} rounded-2xl object-cover shadow-lg bg-theme-element shrink-0`} />
  );
}
