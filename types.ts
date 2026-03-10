
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
  NEWEST = 'Recently Added',
  OLDEST = 'Oldest Added',
  NAME_ASC = 'Name (A-Z)',
  NAME_DESC = 'Name (Z-A)',
  SIZE_ASC = 'Size (Smallest)',
  SIZE_DESC = 'Size (Largest)'
}

export type UpdateStream = 'Stable' | 'Beta' | 'Alpha' | 'Nightly';

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
  platform: Platform;
  size: string;
  author: string;
  screenshots: string[];
  isInstalled?: boolean;
  officialSite?: string;
}

export interface SocialLinks {
  github: string;
  x: string;
  discord: string;
  coffee: string;
}

export interface DevProfile {
  name: string;
  image: string;
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
