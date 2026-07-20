const ALLOWED_DOWNLOAD_PROTOCOLS = new Set(['http:', 'https:']);

export const sanitizeDownloadUrl = (url?: string): string => {
  const candidate = url?.trim();
  if (!candidate) return '#';

  try {
    const parsed = new URL(candidate);
    return ALLOWED_DOWNLOAD_PROTOCOLS.has(parsed.protocol)
      ? candidate
      : '#';
  } catch {
    return '#';
  }
};
