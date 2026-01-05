import type { DevProfile, FAQItem, SocialLinks } from './types';

// Version & Cache
export const CURRENT_STORE_VERSION = '1.1.0';
export const CACHE_VERSION = 'v1_3';
export const NETWORK_TIMEOUT_MS = 8000;

// Remote URLs
export const REMOTE_CONFIG_URL = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/config.json';
export const DEFAULT_APPS_JSON = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/apps.json';
export const DEFAULT_MIRROR_JSON = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/mirror.json';

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
