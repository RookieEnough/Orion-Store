export const BADGE_BASE_URL = 'https://rookieenough.github.io/Orion-Store';

export interface DeepLinkShareArtifacts {
  deepLink: string;
  webLink: string;
  badgeImgUrl: string;
  mdBadge: string;
  htmlBadge: string;
}

export function buildDeepLinkShareArtifacts(appId: string, appIconUrl?: string): DeepLinkShareArtifacts {
  const encodedId = encodeURIComponent(appId);
  const encodedIcon = appIconUrl?.trim() ? `&icon=${encodeURIComponent(appIconUrl.trim())}` : '';
  const deepLink = `orionstore://app/${encodedId}`;
  const webLink = `${BADGE_BASE_URL}/redirect.html?id=${encodedId}${encodedIcon}`;
  const badgeImgUrl = `https://raw.githubusercontent.com/RookieEnough/Orion-Store/refs/heads/main/assets/orion-badge.png`;

  return {
    deepLink,
    webLink,
    badgeImgUrl,
    mdBadge: `[![Get it on Orion Store](${badgeImgUrl})](${webLink})`,
    htmlBadge: `<a href="${webLink}">\n  <img src="${badgeImgUrl}" alt="Get it on Orion Store" height="60">\n</a>`
  };
}
