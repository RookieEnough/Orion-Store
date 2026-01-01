import { memo } from 'react';

interface SectionDividerProps {
  label: string;
}

export const SectionDivider = memo(function SectionDivider({ label }: SectionDividerProps) {
  return (
    <div className="flex items-center gap-2 px-2">
      <div className="h-px bg-theme-border flex-1" />
      <span className="text-xs font-bold text-theme-sub uppercase tracking-widest">{label}</span>
      <div className="h-px bg-theme-border flex-1" />
    </div>
  );
});
