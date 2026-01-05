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
  variants?: AppVariant[] | undefined;
  repoUrl?: string | undefined;
  githubRepo?: string | undefined;
  category: AppCategory;
  platform: Platform;
  size: string;
  author: string;
  screenshots: string[];
  isInstalled?: boolean | undefined;
  releaseKeyword?: string | undefined;
  packageName?: string | undefined;
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
  mirrorJsonUrl?: string | undefined;
  maintenanceMode: boolean;
  maintenanceMessage?: string | undefined;
  announcement?: string | undefined;
  minStoreVersion?: string | undefined;
  latestStoreVersion?: string | undefined;
  storeDownloadUrl?: string | undefined;
  socials?: SocialLinks | undefined;
  devProfile?: DevProfile | undefined;
  faqs?: FAQItem[] | undefined;
  supportEmail?: string | undefined;
  easterEggUrl?: string | undefined;
}
