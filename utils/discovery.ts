import { AppItem, StoreCategoryCard } from '../types';

interface DiscoveryCategoryPreset {
  id: string;
  label: string;
  icon: string;
  accent: string;
  gradient: string;
  rawCategories?: string[];
  keywords: string[];
}

const CATEGORY_PRESETS: readonly DiscoveryCategoryPreset[] = [
  {
    id: 'music',
    label: 'Music',
    icon: 'fa-headphones',
    accent: '#f472b6',
    gradient: 'linear-gradient(135deg, #ec4899 0%, #8b5cf6 100%)',
    keywords: ['music', 'audio', 'song', 'songs', 'album', 'albums', 'listen', 'radio', 'deezer', 'track', 'tracks', 'scrobble']
  },
  {
    id: 'movies',
    label: 'Movies',
    icon: 'fa-film',
    accent: '#fb923c',
    gradient: 'linear-gradient(135deg, #f97316 0%, #ef4444 100%)',
    keywords: ['movie', 'movies', 'video', 'videos', 'watch', 'stream', 'streaming', 'netflix', 'prime video', 'hulu', 'youtube']
  },
  {
    id: 'anime',
    label: 'Anime',
    icon: 'fa-dragon',
    accent: '#a78bfa',
    gradient: 'linear-gradient(135deg, #8b5cf6 0%, #ec4899 100%)',
    keywords: ['anime', 'manga', 'anilist', 'komikku']
  },
  {
    id: 'health',
    label: 'Health',
    icon: 'fa-heart-pulse',
    accent: '#34d399',
    gradient: 'linear-gradient(135deg, #10b981 0%, #14b8a6 100%)',
    keywords: ['health', 'fitness', 'workout', 'sleep', 'step', 'steps', 'habit', 'meditation', 'wellness']
  },
  {
    id: 'privacy',
    label: 'Privacy',
    icon: 'fa-user-shield',
    accent: '#2dd4bf',
    gradient: 'linear-gradient(135deg, #14b8a6 0%, #06b6d4 100%)',
    rawCategories: ['Privacy'],
    keywords: ['privacy', 'private', 'security', 'secure', 'encrypt', 'encrypted', 'authenticator', '2fa', 'totp', 'backup']
  },
  {
    id: 'social',
    label: 'Social',
    icon: 'fa-comments',
    accent: '#60a5fa',
    gradient: 'linear-gradient(135deg, #3b82f6 0%, #6366f1 100%)',
    rawCategories: ['Social'],
    keywords: ['social', 'community', 'chat', 'friends', 'messaging', 'anilist']
  },
  {
    id: 'developer',
    label: 'Developer',
    icon: 'fa-code',
    accent: '#f59e0b',
    gradient: 'linear-gradient(135deg, #f59e0b 0%, #f97316 100%)',
    rawCategories: ['Development'],
    keywords: ['developer', 'development', 'code', 'coding', 'ide', 'studio', 'terminal', 'debug']
  },
  {
    id: 'reading',
    label: 'Reading',
    icon: 'fa-book-open',
    accent: '#22c55e',
    gradient: 'linear-gradient(135deg, #22c55e 0%, #84cc16 100%)',
    keywords: ['reader', 'reading', 'read', 'book', 'books', 'comic', 'manga', 'novel']
  },
  {
    id: 'productivity',
    label: 'Productivity',
    icon: 'fa-briefcase',
    accent: '#06b6d4',
    gradient: 'linear-gradient(135deg, #06b6d4 0%, #3b82f6 100%)',
    keywords: ['productivity', 'workflow', 'organize', 'organise', 'notes', 'tasks', 'todo', 'calendar']
  },
  {
    id: 'utilities',
    label: 'Utilities',
    icon: 'fa-toolbox',
    accent: '#94a3b8',
    gradient: 'linear-gradient(135deg, #475569 0%, #0f172a 100%)',
    rawCategories: ['Utility', 'Utilities'],
    keywords: ['utility', 'tools', 'tool', 'wallpaper', 'download', 'manager', 'launcher', 'offline']
  }
];

const normalizeText = (value: string) =>
  value
    .toLowerCase()
    .replace(/[^a-z0-9\s]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();

const buildSearchText = (app: AppItem) =>
  normalizeText([
    app.name,
    app.description,
    app.author,
    app.category,
    ...(app.tags || [])
  ].join(' '));

const findPreset = (label: string) => {
  const normalizedLabel = normalizeText(label);
  return CATEGORY_PRESETS.find((preset) =>
    normalizeText(preset.label) === normalizedLabel || normalizeText(preset.id) === normalizedLabel
  );
};

const matchesPreset = (app: AppItem, preset: DiscoveryCategoryPreset) => {
  const normalizedCategory = normalizeText(app.category);
  if (preset.rawCategories?.some((raw) => normalizeText(raw) === normalizedCategory)) {
    return true;
  }

  const normalizedTags = (app.tags || []).map(normalizeText);
  if (normalizedTags.some((tag) => normalizeText(preset.label) === tag || normalizeText(preset.id) === tag)) {
    return true;
  }

  const haystack = buildSearchText(app);
  return preset.keywords.some((keyword) => haystack.includes(normalizeText(keyword)));
};

const buildFallbackCard = (category: string, count: number): StoreCategoryCard => ({
  id: `raw-${normalizeText(category).replace(/\s+/g, '-')}`,
  label: category,
  icon: normalizeText(category).includes('social') ? 'fa-comments' : normalizeText(category).includes('media') ? 'fa-photo-film' : 'fa-layer-group',
  count,
  accent: '#64748b',
  gradient: 'linear-gradient(135deg, #334155 0%, #0f172a 100%)'
});

export const appMatchesCategoryFilter = (app: AppItem, category: string) => {
  if (!category || category === 'All') return true;

  const normalizedCategory = normalizeText(category);
  if (normalizeText(app.category) === normalizedCategory) return true;
  if ((app.tags || []).some((tag) => normalizeText(tag) === normalizedCategory)) return true;

  const preset = findPreset(category);
  if (!preset) return false;
  return matchesPreset(app, preset);
};

export const getHomepageCategoryCards = (apps: AppItem[], limit = 10): StoreCategoryCard[] => {
  const presetCards = CATEGORY_PRESETS
    .map((preset) => ({
      id: preset.id,
      label: preset.label,
      icon: preset.icon,
      count: apps.filter((app) => matchesPreset(app, preset)).length,
      accent: preset.accent,
      gradient: preset.gradient
    }))
    .filter((card) => card.count > 0)
    .sort((a, b) => b.count - a.count || a.label.localeCompare(b.label));

  const existingLabels = new Set(presetCards.map((card) => normalizeText(card.label)));
  const rawCards = Array.from(new Set(apps.map((app) => app.category)))
    .map((category) => ({
      category,
      count: apps.filter((app) => normalizeText(app.category) === normalizeText(category)).length
    }))
    .filter(({ category, count }) => count > 0 && !existingLabels.has(normalizeText(category)))
    .sort((a, b) => b.count - a.count || a.category.localeCompare(b.category))
    .map(({ category, count }) => buildFallbackCard(category, count));

  return [...presetCards, ...rawCards].slice(0, limit);
};

export const getAvailableFilterCategories = (apps: AppItem[]) => {
  const categoryLabels = getHomepageCategoryCards(apps, 12).map((card) => card.label);
  const rawCategories = Array.from(new Set(apps.map((app) => app.category))).sort((a, b) => a.localeCompare(b));
  const unique = ['All', ...categoryLabels, ...rawCategories].filter((label, index, arr) => (
    arr.findIndex((item) => normalizeText(item) === normalizeText(label)) === index
  ));
  return unique;
};
