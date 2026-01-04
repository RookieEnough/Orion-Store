import type { Platform } from "./detectPlatform";

export function getEmbedUrl(url: string, platform: Platform): string | null {
  if (platform === "youtube") {
    const match =
      url.match(/youtu\.be\/([^?]+)/) ||
      url.match(/youtube\.com\/shorts\/([^?]+)/) ||
      url.match(/v=([^&]+)/);

    if (!match) return null;

    return `https://www.youtube.com/embed/${match[1]}`;
  }

  // future platforms
  return null;
}
