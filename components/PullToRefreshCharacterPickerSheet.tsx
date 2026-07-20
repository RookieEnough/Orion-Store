import React from 'react';
import { PullToRefreshCharacterKey } from '../types';
import {
    getPullToRefreshCharacter,
    PixelCharacterFace,
    PULL_TO_REFRESH_CHARACTERS
} from './PullToRefreshCharacter';

interface PullToRefreshCharacterPickerSheetProps {
    selectedCharacter: PullToRefreshCharacterKey;
    onClose: () => void;
    onSelect: (character: PullToRefreshCharacterKey) => void;
}

const PullToRefreshCharacterPickerSheet: React.FC<PullToRefreshCharacterPickerSheetProps> = ({
    selectedCharacter,
    onClose,
    onSelect
}) => {
    const activeCharacter = getPullToRefreshCharacter(selectedCharacter);

    return (
        <div
            className="backdrop-scrim absolute inset-0 z-20 flex items-end justify-center bg-black/50 p-3 backdrop-blur-sm animate-fade-in sm:p-6"
            onClick={onClose}
        >
            <div
                className="w-full max-w-md overflow-hidden rounded-[2.2rem] bg-surface shadow-2xl animate-slide-up"
                onClick={(event) => event.stopPropagation()}
                role="dialog"
                aria-modal="true"
                aria-labelledby="pull-to-refresh-character-title"
            >
                <div className="border-b border-theme-border bg-surface/95 px-5 py-4 backdrop-blur-sm">
                    <div className="mx-auto mb-4 h-1.5 w-14 rounded-full bg-theme-border" />
                    <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                            <h4 id="pull-to-refresh-character-title" className="text-lg font-black tracking-tight text-theme-text">
                                Pull to Refresh Character
                            </h4>
                            <p className="mt-1 text-[10px] font-bold uppercase tracking-[0.18em] text-theme-sub">
                                Choose your style
                            </p>
                        </div>
                        <button
                            type="button"
                            aria-label="Close character picker"
                            onClick={onClose}
                            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-theme-element text-theme-text transition-colors hover:bg-theme-hover"
                        >
                            <i className="fas fa-times"></i>
                        </button>
                    </div>
                </div>

                <div className="space-y-4 p-5">
                    <div
                        className="relative overflow-hidden rounded-[1.55rem] border border-theme-border bg-card p-4"
                        style={{ backgroundImage: activeCharacter.gradient }}
                    >
                        <div className="absolute inset-0 bg-gradient-to-br from-white/10 via-transparent to-black/5 pointer-events-none" />
                        <div className="relative flex items-center gap-3">
                            <div className="rounded-[1.15rem] border border-theme-border bg-surface/85 p-2.5 shadow-sm">
                                <PixelCharacterFace character={activeCharacter} sizeClassName="h-12 w-12" />
                            </div>
                            <div className="min-w-0">
                                <div className="text-[10px] font-black uppercase tracking-[0.2em] text-primary/80">
                                    Selected
                                </div>
                                <div className="mt-1 text-lg font-black tracking-tight text-theme-text">
                                    {activeCharacter.name}
                                </div>
                                <div className="mt-1 text-xs font-bold text-theme-sub">
                                    {activeCharacter.caption}
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="max-h-[52vh] space-y-2 overflow-y-auto no-scrollbar">
                        {PULL_TO_REFRESH_CHARACTERS.map((character) => {
                            const isActive = character.id === selectedCharacter;

                            return (
                                <button
                                    key={character.id}
                                    type="button"
                                    onClick={() => onSelect(character.id)}
                                    className={`w-full rounded-[1.25rem] border px-3 py-3 text-left transition-colors active:scale-[0.99] ${isActive
                                        ? 'border-primary/35 bg-primary/10 shadow-lg shadow-primary/10'
                                        : 'border-theme-border bg-card hover:bg-theme-element/35'
                                        }`}
                                >
                                    <div className="flex items-center justify-between gap-3">
                                        <div className="flex min-w-0 items-center gap-3">
                                            <div
                                                className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl ${character.accentClass}`}
                                                style={{ backgroundImage: character.gradient }}
                                            >
                                                <PixelCharacterFace character={character} sizeClassName="h-8 w-8" />
                                            </div>
                                            <div className="min-w-0">
                                                <div className="truncate text-[14px] font-black text-theme-text">
                                                    {character.name}
                                                </div>
                                                <div className="truncate text-[11px] font-bold text-theme-sub">
                                                    {character.caption}
                                                </div>
                                            </div>
                                        </div>
                                        <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full border ${isActive
                                            ? 'border-primary/20 bg-primary text-white'
                                            : 'border-theme-border bg-theme-element text-theme-sub'
                                            }`}>
                                            <i className={`fas ${isActive ? 'fa-check' : 'fa-circle'} text-[9px]`}></i>
                                        </div>
                                    </div>
                                </button>
                            );
                        })}
                    </div>
                </div>
            </div>
        </div>
    );
};

export default PullToRefreshCharacterPickerSheet;
