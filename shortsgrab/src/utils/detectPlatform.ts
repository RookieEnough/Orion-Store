export type Platform =
  | "youtube"
  | "facebook"
  | "twitter"
  | "unknown";

export function detectPlatform(url: string): Platform {
  if (/youtube\.com|youtu\.be/.test(url)) return "youtube";
  if (/facebook\.com|fb\.watch/.test(url)) return "facebook";
  if (/twitter\.com|x\.com/.test(url)) return "twitter";
  return "unknown";
}
