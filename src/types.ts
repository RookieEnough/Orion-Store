export type AppCategory = 'Utility' | 'Privacy' | 'Media' | 'Development' | 'Social' | 'Educational';

export type Platform = 'Android' | 'PC';

export type Tab = 'android' | 'pc' | 'about';

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
  releaseKeyword?: string;
  packageName?: string;
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
  latestStoreVersion?: string;
  storeDownloadUrl?: string;
  socials?: SocialLinks;
  devProfile?: DevProfile;
  faqs?: FAQItem[];
  supportEmail?: string;
  easterEggUrl?: string;
}
