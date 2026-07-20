import React, { memo } from 'react';
import { PullToRefreshCharacterKey } from '../types';

export interface PullToRefreshCharacter {
    id: PullToRefreshCharacterKey;
    name: string;
    caption: string;
    accentClass: string;
    gradient: string;
    grid: string[];
    gridEyesClosed: string[];
    colors: Record<string, string>;
}

const baseInk = '#111827';
const white = '#f8fafc';

const withRow = (grid: string[], rowIndex: number, row: string): string[] => {
    const copy = [...grid];
    copy[rowIndex] = row;
    return copy;
};

const catGrid = [
    '.b............b.',
    '.bb..........bb.',
    '.beb........beb.',
    '.bebb......bbeb.',
    '.bbbbbbbbbbbbbb.',
    '.bbbbabbbbabbbb.',
    '.bbbbbbbbbbbbbb.',
    '.bbedbbbbbbedbb.',
    '.bbddbbbbbbddbb.',
    '.bbbbcceeccbbbb.',
    'dbbbbcdccdcbbbbd',
    'dbbbbccddccbbbbd',
    '.bbbbbccccbbbbb.',
    '.bbbbbbbbbbbbbb.',
    '..bbbbbbbbbbbb..',
    '....bbbbbbbb....'
];

const dogGrid = [
    '.aa......aa.',
    '.aabbbbbbaa.',
    '.aabbbbbbaa.',
    '.aabbbbbbaa.',
    '.abddbbddba.',
    '.abddbbddba.',
    '.abbccccbba.',
    '.abccddccba.',
    '.abcceeccba.',
    '.abbccccbba.',
    '..abbbbbba..',
    '...aaaaaa...'
];

const pokeballRestGrid = [
    '.....aaaaaa.....',
    '...aaccccccaa...',
    '..acccccccccca..',
    '.acccccccccccca.',
    '.acccccccccccca.',
    'acccccaaaaccccca',
    'abbbbaffffabbbba',
    'aaaaaafhhfaaaaaa',
    'aaaaaafhhfaaaaaa',
    'aeeeeaffffaeeeea',
    'aeeeeeaaaaeeeeea',
    '.aeeeeeeeeeeeea.',
    '.aeeeeeeeeeeeea.',
    '..aeeeeeeeeeea..',
    '...aaeeeeeeaa...',
    '.....aaaaaa.....'
];

const pokeballSparkGrid = [
    '.g...aaaaaa...g.',
    'gggaaccccccaaggg',
    '.gaccccccccccag.',
    ...pokeballRestGrid.slice(3, 13),
    'g.aeeeeeeeeeea.g',
    '...aaeeeeeeaa...',
    '..g..aaaaaa..g..'
];

const shieldGrid = [
    '.....bbbbbb.....',
    '...bbbbbbbbbb...',
    '..bbbccccccbbb..',
    '.bbccccbbccccbb.',
    '.bbccbbbbbbccbb.',
    'bbccbbddddbbccbb',
    'bccbbddeeddbbccb',
    'bccbbeeeeeebbccb',
    'bccbbdeeeedbbccb',
    'bccbbdeddedbbccb',
    'bbccbbddddbbccbb',
    '.bbccbbbbbbccbb.',
    '.bbccccbbccccbb.',
    '..bbbccccccbbb..',
    '...bbbbbbbbbb...',
    '.....bbbbbb.....'
];

const shieldRestGrid = shieldGrid.map((row) => row.replaceAll('e', 'x'));

const batmanFaceOpenGrid = [
'................',
'..b..........b..',
'..bb........bb..',
'..bbb......bbb..',
'..bbbbbbbbbbbb..',
'..bbbbbbbbbbbb..',
'..bbcbbbbbbcbb..',
'..bbccbbbbccbb..',
'..bbcccbbcccbb..',
'..bbbbbbbbbbbb..',
'..bbbbbbbbbbbb..',
'..bddddddddddb..',
'..bdddbbbbdddb..',
'...bddddddddb...',
'....bbbbbbbb....',
'................'
];

const batmanFaceClosedGrid = [
'................',
'..b..........b..',
'..bb........bb..',
'..bbb......bbb..',
'..bbbbbbbbbbbb..',
'..bbbbbbbbbbbb..',
'..bbbbbbbbbbbb..',
'..bbbbbbbbbbbb..',
'..bbcccbbcccbb..',
'..bbbbbbbbbbbb..',
'..bbbbbbbbbbbb..',
'..bddddddddddb..',
'..bdddbbbbdddb..',
'...bddddddddb...',
'....bbbbbbbb....',
'................'
];

const owlGrid = [
    '.b........b.',
    '.bb......bb.',
    '.bbbbbbbbbb.',
    '.bcccbbcccb.',
    '.bcdcbbcdcb.',
    '.bcdcbbcdcb.',
    '.bcccbbcccb.',
    '.bbbbeebbbb.',
    '.bbccccccbb.',
    '..bccccccb..',
    '...bbbbbb...',
    '...e....e...'
];

const robotGrid = [
    '.....cc.....',
    '.....aa.....',
    '.aaaaaaaaaa.',
    '.abbbbbbbba.',
    '.abccbbccba.',
    '.abccbbccba.',
    '.abbbbbbbba.',
    '.abeeeeeeba.',
    '.abbbbbbbba.',
    '.aaaaaaaaaa.',
    '....aaaa....',
    '............'
];

const ghostGrid = [
    '....bbbb....',
    '..bbbbbbbb..',
    '.bbbbbbbbbb.',
    '.bbccbbccbb.',
    '.bbccbbccbb.',
    '.bebbbbbbeb.',
    '.bbbbccbbbb.',
    '.bbbbbbbbbb.',
    '.bbbbbbbbbb.',
    '.bbbbbbbbbb.',
    '.bb..bb..bb.',
    '............'
];

const kittyGrid = [
    '..b......b..',
    '.bbb....bbb.',
    'ccbbbbbbbbb.',
    'cccbbbbbbbbb',
    'bbbbbbbbbbbb',
    'abbabbbbabba',
    'bbbabbbbabbb',
    'abbbbddbbbba',
    'bbbbbbbbbbbb',
    '.bbbbbbbbbb.',
    '..bbbbbbbb..',
    '............'
];

const bunnyGrid = [
    '..b......b..',
    '.bcb....bcb.',
    '.bcb....bcb.',
    '.bbb....bbb.',
    '..bbbbbbbb..',
    '.bbbbbbbbbb.',
    '.bbaabbaabb.',
    '.bbaabbaabb.',
    '.bebbddbbeb.',
    '.bbbbbbbbbb.',
    '..bbbbbbbb..',
    '...bbbbbb...'
];

export const PULL_TO_REFRESH_CHARACTERS: PullToRefreshCharacter[] = [
    {
        id: 'cat',
        name: 'Pixel Cat',
        caption: 'Classic Orion blink',
        accentClass: 'text-orange-400 bg-orange-500/10',
        gradient: 'linear-gradient(135deg, rgba(251,146,60,0.24), rgba(236,72,153,0.14))',
        colors: { a: '#c2410c', b: '#fb923c', c: white, d: '#1f2937', e: '#f9a8d4' },
        grid: catGrid,
        gridEyesClosed: withRow(catGrid, 5, '.bbbbbbbbbbbbbb.')
    },
    {
        id: 'dog',
        name: 'Pixel Dog',
        caption: 'Happy helper',
        accentClass: 'text-amber-500 bg-amber-500/10',
        gradient: 'linear-gradient(135deg, rgba(245,158,11,0.24), rgba(34,197,94,0.13))',
        colors: { a: '#7c2d12', b: '#d97706', c: '#fef3c7', d: baseInk, e: '#ef4444' },
        grid: dogGrid,
        gridEyesClosed: withRow(dogGrid, 4, '.abbbbbbbba.')
    },
    {
        id: 'pokeball',
        name: 'Poke Ball',
        caption: 'Ready to catch refresh',
        accentClass: 'text-rose-400 bg-rose-500/10',
        gradient: 'linear-gradient(135deg, rgba(248,113,113,0.24), rgba(255,255,255,0.16))',
        colors: {
            a: '#111827',
            b: '#b91c1c',
            c: '#ef4444',
            d: '#111827',
            e: '#f8fafc',
            f: '#e5e7eb',
            g: '#fde68a',
            h: '#ffffff'
        },
        grid: pokeballRestGrid,
        gridEyesClosed: pokeballRestGrid
    },
    {
        id: 'shield',
        name: 'Cap Shield',
        caption: 'Throw to refresh',
        accentClass: 'text-blue-500 bg-blue-500/10',
        gradient: 'linear-gradient(135deg, rgba(220,38,38,0.24), rgba(29,78,216,0.18))',
        colors: {
            b: '#dc2626',
            c: '#f1f5f9',
            d: '#1d4ed8',
            e: '#ffffff',
            x: '#94a3b8'
        },
        grid: shieldGrid,
        gridEyesClosed: shieldRestGrid
    },
    {
        id: 'batman',
        name: 'Batman',
        caption: 'Face alert refresh',
        accentClass: 'text-yellow-300 bg-yellow-500/10',
        gradient: 'linear-gradient(135deg, rgba(250,204,21,0.24), rgba(17,24,39,0.26))',
        colors: {
            b: '#05070b',
            c: '#f8fafc',
            d: '#f1e4c9',
            e: '#111827'
        },
        grid: batmanFaceOpenGrid,
        gridEyesClosed: batmanFaceClosedGrid
    },
    {
        id: 'owl',
        name: 'Pixel Owl',
        caption: 'Night refresh',
        accentClass: 'text-sky-400 bg-sky-500/10',
        gradient: 'linear-gradient(135deg, rgba(14,165,233,0.24), rgba(168,85,247,0.14))',
        colors: { b: '#0ea5e9', c: '#fef3c7', d: baseInk, e: '#f59e0b' },
        grid: owlGrid,
        gridEyesClosed: withRow(owlGrid, 4, '.bcccbbcccb.')
    },
    {
        id: 'robot',
        name: 'Pixel Bot',
        caption: 'Mecha refresh',
        accentClass: 'text-cyan-400 bg-cyan-500/10',
        gradient: 'linear-gradient(135deg, rgba(6,182,212,0.23), rgba(99,102,241,0.16))',
        colors: { a: '#334155', b: '#94a3b8', c: '#22d3ee', e: '#f59e0b' },
        grid: robotGrid,
        gridEyesClosed: withRow(robotGrid, 4, '.abbbbbbbba.')
    },
    {
        id: 'ghost',
        name: 'Pixel Ghost',
        caption: 'Boo but useful',
        accentClass: 'text-violet-300 bg-violet-500/10',
        gradient: 'linear-gradient(135deg, rgba(139,92,246,0.22), rgba(244,114,182,0.14))',
        colors: { b: white, c: baseInk, e: '#f472b6' },
        grid: ghostGrid,
        gridEyesClosed: withRow(ghostGrid, 3, '.bbbbbbbbbb.')
    },
    {
        id: 'kitty',
        name: 'Pixel Kitty',
        caption: 'Bow and whiskers',
        accentClass: 'text-rose-400 bg-rose-500/10',
        gradient: 'linear-gradient(135deg, rgba(244,63,94,0.22), rgba(251,207,232,0.18))',
        colors: { a: baseInk, b: white, c: '#ef4444', d: '#facc15' },
        grid: kittyGrid,
        gridEyesClosed: withRow(kittyGrid, 5, 'abbbbbbbbbba')
    },
    {
        id: 'bunny',
        name: 'Pixel Bunny',
        caption: 'Hop to refresh',
        accentClass: 'text-pink-400 bg-pink-500/10',
        gradient: 'linear-gradient(135deg, rgba(236,72,153,0.20), rgba(167,139,250,0.14))',
        colors: { a: baseInk, b: white, c: '#f9a8d4', d: '#ec4899', e: '#fda4af' },
        grid: bunnyGrid,
        gridEyesClosed: withRow(bunnyGrid, 6, '.bbbbbbbbbb.')
    }
];

export const DEFAULT_PULL_TO_REFRESH_CHARACTER: PullToRefreshCharacterKey = 'cat';

export const getPullToRefreshCharacter = (id?: string) => {
    const normalizedId = id === 'pikachu'
        ? 'pokeball'
        : id === 'fox'
            ? 'batman'
            : id === 'panda'
                ? 'cat'
                : id;
    return PULL_TO_REFRESH_CHARACTERS.find((character) => character.id === normalizedId) || PULL_TO_REFRESH_CHARACTERS[0]!;
};

interface PixelCharacterFaceProps {
    character: PullToRefreshCharacter;
    eyesClosed?: boolean;
    sizeClassName?: string;
    className?: string;
}

export const PixelCharacterFace = memo(({ character, eyesClosed = false, sizeClassName = 'h-16 w-16', className = '' }: PixelCharacterFaceProps) => {
    const grid = eyesClosed ? character.gridEyesClosed : character.grid;
    return (
        <div
            className={`grid shrink-0 ${sizeClassName} ${className}`}
            style={{
                gridTemplateColumns: `repeat(${grid[0]?.length || 12}, minmax(0, 1fr))`,
                imageRendering: 'pixelated'
            }}
            aria-hidden="true"
        >
            {grid.join('').split('').map((cell, index) => (
                <span
                    key={`${character.id}-${eyesClosed ? 'closed' : 'open'}-${index}`}
                    className="block"
                    style={{ backgroundColor: cell === '.' ? 'transparent' : character.colors[cell] || 'transparent' }}
                />
            ))}
        </div>
    );
});
