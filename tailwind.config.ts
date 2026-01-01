import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        primary: '#6366f1',
        acid: '#d4ff00',
        'acid-dark': '#a3c700',
        neon: '#ff00ff',
        surface: 'var(--surface)',
        card: 'var(--card)',
        'theme-text': 'var(--text)',
        'theme-sub': 'var(--text-sub)',
        'theme-border': 'var(--border)',
        'theme-element': 'var(--element)',
        'theme-hover': 'var(--hover)',
        'theme-input': 'var(--input)',
      },
      animation: {
        'fade-in': 'fadeIn 0.3s ease-out',
        'slide-up': 'slideUp 0.4s ease-out',
        'pulse-slow': 'pulse 3s infinite',
        shine: 'shine 2s infinite',
      },
      keyframes: {
        fadeIn: {
          from: { opacity: '0', transform: 'translateY(10px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        slideUp: {
          from: { opacity: '0', transform: 'translateY(20px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        shine: {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.7' },
        },
      },
    },
  },
  plugins: [],
} satisfies Config;
