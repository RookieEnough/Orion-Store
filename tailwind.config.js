import plugin from 'tailwindcss/plugin';

/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./*.{js,ts,jsx,tsx}",           // Matches App.tsx, index.tsx, etc. in root
    "./components/**/*.{js,ts,jsx,tsx}", // Matches everything in components folder
  ],
  darkMode: 'class',
  theme: {
    extend: {
      fontFamily: {
        sans: ['var(--app-font-family)', 'sans-serif'],
      },
      colors: {
        // Vibrant Gen Z Palette
        primary: {
          DEFAULT: '#6366f1', // Indigo 500
          light: '#a5b4fc', // Indigo 200
        },
        acid: '#bef264', // Lime 400 - Pop color
        neon: '#d946ef', // Fuchsia 500 - Accent
        
        // Semantic Colors Mapped to CSS Variables
        surface: {
           DEFAULT: 'var(--bg-surface)',
           dark: '#0f0f11',
           light: '#f8fafc',
        },
        card: {
           DEFAULT: 'var(--bg-card)',
           dark: '#18181b',
           light: '#ffffff',
        },
        theme: {
          text: 'var(--text-main)',
          sub: 'var(--text-sub)',
          border: 'var(--border-color)',
          element: 'var(--element-bg)',
          hover: 'var(--element-hover)',
          input: 'var(--input-bg)'
        }
      },
      animation: {
        'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'shine': 'shine 3s linear infinite',
      },
      boxShadow: {
        'glow': '0 0 20px 2px rgba(99, 102, 241, 0.15)',
        'glow-lg': '0 0 40px 4px rgba(99, 102, 241, 0.2)',
        'glow-acid': '0 0 20px 2px rgba(190, 242, 100, 0.15)',
      },
      keyframes: {
        shine: {
          '0%': { backgroundPosition: '200% center' },
          '100%': { backgroundPosition: '-200% center' },
        }
      }
    }
  },
  plugins: [
    plugin(function ({ addVariant }) {
      addVariant('dusk', 'html.dusk &');
    }),
  ],
}
