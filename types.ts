
export enum AppCategory {
  UTILITY = 'Utility',
  PRIVACY = 'Privacy',
  MEDIA = 'Media',
  DEVELOPMENT = 'Development',
  SOCIAL = 'Social',
  EDUCATIONAL = 'Educational'
}

export enum Platform {
  ANDROID = 'Android',
  PC = 'PC',
  TV = 'TV'
}

export enum SortOption {
  HOME = 'Home Page',
  NEWEST = 'Recently Added',
  OLDEST = 'Oldest Added',
  NAME_ASC = 'Name (A-Z)',
  NAME_DESC = 'Name (Z-A)',
  SIZE_ASC = 'Size (Smallest)',
  SIZE_DESC = 'Size (Largest)'
}

export type UpdateStream = 'Stable' | 'Beta' | 'Alpha' | 'Nightly';
export type AppFontKey = 'spaceGrotesk' | 'inter' | 'poppins' | 'manrope' | 'outfit' | 'dmSans' | 'plusJakartaSans' | 'rubik' | 'nunitoSans' | 'publicSans' | 'systemDefault';

export interface AppVariant {
  arch: string;
  url: string;
  size?: number;
}

export interface VersionOption {
  type: UpdateStream;
  version: string;
  date: string;
  variants: AppVariant[];
}

export interface AppItem {
  id: string;
  name: string;
  description: string;
  icon: string;
  version: string;
  latestVersion: string;
  downloadUrl: string;
  variants?: AppVariant[];
  availableVersions?: VersionOption[]; // New field for version selection
  repoUrl?: string; 
  githubRepo?: string;
  gitlabRepo?: string;
  codebergRepo?: string;
  gitlabDomain?: string;
  releaseKeyword?: string;
  packageName?: string;
  category: string; // Changed from AppCategory to string to support dynamic categories
  tags?: string[]; // V1.3.0: Support for tag-driven curation
  platform: Platform;
  size: string;
  author: string;
  screenshots: string[];
  isInstalled?: boolean;
  officialSite?: string;
  patches?: string[];
}

export interface SocialLinks {
  github: string;
  x: string;
  discord: string;
  coffee: string;
}

export interface DevProfile {
  name: string;
  image?: string;
  bio: string;
}

export interface FAQItem {
  question: string;
  answer: string;
  icon: string;
}

export interface Notice {
  id: string;       // Change this ID to show the notice again (e.g. "alert-v1", "alert-v2")
  title: string;
  message: string;
  show: boolean;
}

export type StorefrontAnimation = 'snowfall' | 'confetti' | 'spark' | 'none';
export type StorefrontModuleType = 'curved_apps' | 'category_cards' | 'update_pills' | 'recommendation_bundles';
export type StorefrontModulePlatform = 'android' | 'pc' | 'tv' | 'all';

export interface StorefrontPillConfig {
  id?: string;
  label?: string;
  appId?: string;
  tone?: 'primary' | 'success' | 'warning' | 'info';
}

export interface StorefrontModuleBundleConfig {
  id: string;
  title: string;
  description?: string;
  appIds: string[];
  color?: string;
  badge?: string;
  icon?: string;
  monogram?: string;
}

export interface StorefrontModuleConfig {
  id: string;
  type: StorefrontModuleType;
  title: string;
  subtitle?: string;
  platform?: StorefrontModulePlatform;
  insertAfterCategory?: number; // Insert after N category rows (0 places it before category rows)
  animation?: StorefrontAnimation;
  appIds?: string[];
  pills?: StorefrontPillConfig[];
  bundles?: StorefrontModuleBundleConfig[];
}

export interface StoreConfig {
  appsJsonUrl: string;
  mirrorJsonUrl?: string;
  maintenanceMode: boolean;
  maintenanceMessage?: string;
  announcement?: string;
  notice?: Notice;
  minStoreVersion?: string;
  latestStoreVersion?: string;
  storeDownloadUrl?: string;
  socials?: SocialLinks;
  devProfile?: DevProfile;
  faqs?: FAQItem[];
  supportEmail?: string;
  easterEggUrl?: string;
  leaderboardUrl?: string; // New field for custom leaderboard endpoint (worker)
  storefrontModules?: StorefrontModuleConfig[]; // Optional homepage modules from config.json
}

export type Tab = 'android' | 'pc' | 'tv' | 'about' | 'updates';

export interface LeaderboardEntry {
    username: string;
    xp: number;
    level: number;
    title: string;
    class: 'Warrior' | 'Scribe' | 'Hybrid';
    avatar_url: string;
    rank?: number; // Injected by client
}

export interface BundleItem {
  id: string;
  title: string;
  description: string;
  appIds: string[];
  apps?: AppItem[];
  color?: string;
  badge?: string;
  icon?: string;
  monogram?: string;
}

export interface StorePillItem {
  id: string;
  label: string;
  tone: 'primary' | 'success' | 'warning' | 'info';
}

export interface StoreCategoryCard {
  id: string;
  label: string;
  icon: string;
  count: number;
  accent: string;
  gradient: string;
}

export interface StoreCollection {
  id: string;
  title: string;
  subtitle?: string;
  type: 'hero' | 'swimlane' | 'auto_category' | 'bundle' | 'update_pills' | 'recommendation_bundles' | 'category_cards' | 'sorted_grid';
  filter?: string; // e.g. "Games" or tag name
  animation?: StorefrontAnimation;
  appIds?: string[]; // Explicit app IDs for curated collections
  apps?: AppItem[]; // Populated by the worker
  bundles?: BundleItem[]; // Populated by the worker
  pillItems?: StorePillItem[]; // Populated by the worker
  categoryCards?: StoreCategoryCard[]; // Populated by the worker
  totalAppCount?: number;
}
