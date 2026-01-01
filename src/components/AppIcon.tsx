import { memo, useState } from 'react';
import { CATEGORY_GRADIENTS } from '@/constants';
import type { AppCategory } from '@/types';

interface AppIconProps {
  src: string;
  name: string;
  category: AppCategory;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

const sizeClasses = {
  sm: 'w-12 h-12 text-xl',
  md: 'w-16 h-16 text-2xl',
  lg: 'w-20 h-20 text-3xl',
};

export const AppIcon = memo(function AppIcon({
  src,
  name,
  category,
  size = 'md',
  className = '',
}: AppIconProps) {
  const [hasError, setHasError] = useState(false);
  const gradient = CATEGORY_GRADIENTS[category] || CATEGORY_GRADIENTS.Default;
  const sizeClass = sizeClasses[size];

  if (hasError || !src) {
    return (
      <div
        className={`${sizeClass} rounded-2xl ${gradient} flex items-center justify-center text-white font-black shadow-lg shrink-0 ${className}`}
      >
        {name.charAt(0)}
      </div>
    );
  }

  return (
    <img
      src={src}
      alt={name}
      onError={() => setHasError(true)}
      className={`${sizeClass} rounded-2xl object-cover shadow-lg bg-theme-element shrink-0 ${className}`}
    />
  );
});
