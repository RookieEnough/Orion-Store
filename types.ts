
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
  PC = 'PC'
}

export interface AppVariant {
  arch: string;
  url: string;
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
  repoUrl?: string; 
  githubRepo?: string;
  category: AppCategory;
  platform: Platform;
  size: string;
  author: string;
  screenshots: string[];
  isInstalled?: boolean;
  releaseKeyword?: string; // Used to match assets in GitHub Releases
  packageName?: string; // Android Package Name (e.g., com.google.android.youtube)
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

export interface StoreConfig {
  appsJsonUrl: string;
  mirrorJsonUrl?: string;
  maintenanceMode: boolean;
  maintenanceMessage?: string;
  announcement?: string;
  minStoreVersion?: string;
  // Self-Update Mechanism
  latestStoreVersion?: string;
  storeDownloadUrl?: string;
  // Dynamic Content
  socials?: SocialLinks;
  devProfile?: DevProfile;
  faqs?: FAQItem[];
  // Dynamic Meta
  supportEmail?: string;
  easterEggUrl?: string;
}

export type Tab = 'android' | 'pc' | 'about';
