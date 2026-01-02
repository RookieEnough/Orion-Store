import type { AppCategory, DevProfile, FAQItem, SocialLinks } from './types';

// Version & Cache
export const CURRENT_STORE_VERSION = '1.1.0';
export const CACHE_VERSION = 'v1_3';
export const NETWORK_TIMEOUT_MS = 8000;

// Timing Constants
export const CACHE_TTL_WITH_TOKEN = 10 * 60 * 1000; // 10 minutes
export const CACHE_TTL_DEFAULT = 60 * 60 * 1000; // 1 hour
export const DEBOUNCE_MS = 200;
export const TOAST_DURATION_MS = 2000;
export const TOKEN_RELOAD_DELAY_MS = 500;
export const SCROLL_THRESHOLD = 300;
export const API_BATCH_SIZE = 5;

// Easter Egg / Dev Mode
export const DEV_TAP_TARGET = 9;
export const DEV_TAP_HINT_THRESHOLD = 5;
export const EASTER_EGG_TAP_TARGET = 8;

// Remote URLs
export const REMOTE_CONFIG_URL = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/config.json';
export const DEFAULT_APPS_JSON = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/apps.json';
export const DEFAULT_MIRROR_JSON = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/mirror.json';

// Category Gradients
export const CATEGORY_GRADIENTS: Record<AppCategory | 'Default', string> = {
  Utility: 'bg-gradient-to-br from-blue-500 to-cyan-400',
  Privacy: 'bg-gradient-to-br from-emerald-500 to-teal-400',
  Media: 'bg-gradient-to-br from-fuchsia-500 to-pink-400',
  Development: 'bg-gradient-to-br from-orange-500 to-amber-400',
  Social: 'bg-gradient-to-br from-indigo-500 to-violet-400',
  Educational: 'bg-gradient-to-br from-green-500 to-lime-400',
  Default: 'bg-gradient-to-br from-gray-500 to-slate-400',
};

// Default Social Links
export const DEV_SOCIALS: SocialLinks = {
  github: 'https://github.com/RookieEnough',
  x: 'https://x.com/_Rookie_Z',
  discord: 'https://discord.com/invite/CrM6y4ujnq',
  coffee: 'https://ko-fi.com/rookie_z',
};

// Default Dev Profile
export const DEFAULT_DEV_PROFILE: DevProfile = {
  name: 'RookieZ',
  bio: 'Building the open web, one commit at a time. No ads, no tracking, just code.',
  image: 'https://i.pinimg.com/originals/12/79/48/127948a3253396796874286570740594.jpg',
};

// Default Support
export const DEFAULT_SUPPORT_EMAIL = 'orionstoredev@gmail.com';
export const DEFAULT_EASTER_EGG = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ';

// Default FAQs
export const DEFAULT_FAQS: FAQItem[] = [
  {
    question: 'Is Orion Store safe?',
    answer:
      'Absolutely. Orion Store is completely open-source. This means our code is public on GitHub for anyone to audit. We believe in transparency—no hidden trackers, no data mining, just a clean gateway to apps.',
    icon: 'fa-shield-cat',
  },
  {
    question: 'Are apps on Orion safe?',
    answer:
      'Yes. I personally review and mod them using tools available on their official repositories to ensure they are safe, functional, and privacy-respecting before they land here.',
    icon: 'fa-check-double',
  },
  {
    question: 'Download not working?',
    answer:
      "Don't panic! Just head to the app's detail page and click the 'Report' icon (⚠️) in the top right corner. It will pre-fill an email so I can fix it ASAP.",
    icon: 'fa-bug',
  },
  {
    question: 'Will there be more apps?',
    answer:
      'Yes, if there will be more interesting apps to add on. As long as I find open-source or useful tools that deserve a spotlight, the library will keep growing.',
    icon: 'fa-layer-group',
  },
  {
    question: 'How can I support?',
    answer:
      'By donation through ko-fi. Code fuels the store, but coffee fuels the dev! You can find the link in the socials section.',
    icon: 'fa-heart',
  },
  {
    question: 'Is there any hidden easter egg?',
    answer:
      'Where the Architect stares, the secret sleeps.\n\nCount the legs of a spider. Count the vertices of a cube.\n\nStrike the Visage that many times.\n\nThe Golden Truth awaits those who know the rules... and so do I.',
    icon: 'fa-user-secret',
  },
];
