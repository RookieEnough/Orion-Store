// Image-link validation for the Submission modal.
//
// An "accepted" image link is one of:
//   1. A URL whose path ends in a known image extension
//      (.jpg, .jpeg, .png, .webp, .gif, .bmp, .avif, .svg).
//   2. A Google Play Store scraped image link
//      (host ends with googleusercontent.com and the path starts
//      with `/play-lh/`).
//   3. A "raw" image host:
//        - raw.githubusercontent.com
//        - *.gitlab.* with `/-/raw/...` in the path
//        - codeberg.org/.../raw/branch/...
//        - camo.githubusercontent.com  (GitHub's image proxy)
//
// We reject any link whose host starts with `private-user` (private
// GitHub user images), because those are not publicly cacheable.

export const ACCEPTED_IMAGE_EXTENSIONS = [
    '.jpg',
    '.jpeg',
    '.png',
    '.webp',
    '.gif',
    '.bmp',
    '.avif',
    '.svg'
] as const;

function isGooglePlayScrapedImage(host: string, path: string): boolean {
    return host.endsWith('googleusercontent.com') && path.startsWith('/play-lh');
}

function isGithubRaw(host: string): boolean {
    return host === 'raw.githubusercontent.com';
}

function isCamoProxy(host: string): boolean {
    return host === 'camo.githubusercontent.com';
}

function isGitlabRaw(host: string, path: string): boolean {
    if (!host.endsWith('gitlab.com') && !host.endsWith('gitlab.io')) {
        return false;
    }
    return path.includes('/-/raw/');
}

function isCodebergRaw(host: string, path: string): boolean {
    return host === 'codeberg.org' && path.includes('/raw/branch/');
}

function endsWithImageExtension(path: string): boolean {
    const lower = (path.toLowerCase().split('?')[0] || '').split('#')[0] || '';
    return ACCEPTED_IMAGE_EXTENSIONS.some((ext) => lower.endsWith(ext));
}

function isPrivateUserHost(host: string): boolean {
    return host.startsWith('private-user');
}

/**
 * Returns true if the URL is an image link the submission form should accept.
 */
export function isAcceptedImageLink(url: string): boolean {
    if (!url) return false;
    const trimmed = url.trim();
    if (!trimmed) return false;
    if (!/^https?:\/\//i.test(trimmed)) return false;
    try {
        const u = new URL(trimmed);
        if (isPrivateUserHost(u.hostname)) return false;
        if (endsWithImageExtension(u.pathname)) return true;
        if (isGooglePlayScrapedImage(u.hostname, u.pathname)) return true;
        if (isGithubRaw(u.hostname)) return true;
        if (isCamoProxy(u.hostname)) return true;
        if (isGitlabRaw(u.hostname, u.pathname)) return true;
        if (isCodebergRaw(u.hostname, u.pathname)) return true;
        return false;
    } catch {
        return false;
    }
}

/**
 * Returns a user-friendly error message for a rejected URL, or null if
 * the URL is accepted.
 */
export function explainRejectedImageLink(url: string): string | null {
    if (!url) return null;
    const trimmed = url.trim();
    if (!trimmed) return null;
    if (!/^https?:\/\//i.test(trimmed)) {
        return 'Link must start with http:// or https://';
    }
    try {
        const u = new URL(trimmed);
        if (isPrivateUserHost(u.hostname)) {
            return 'Private-user links are not accepted. Use a public image link.';
        }
        if (endsWithImageExtension(u.pathname)) return null;
        if (isGooglePlayScrapedImage(u.hostname, u.pathname)) return null;
        if (isGithubRaw(u.hostname)) return null;
        if (isCamoProxy(u.hostname)) return null;
        if (isGitlabRaw(u.hostname, u.pathname)) return null;
        if (isCodebergRaw(u.hostname, u.pathname)) return null;
        return 'Only image links are accepted (jpg/png/webp/gif/bmp/avif/svg, Play Store scraped links, or raw GitHub/GitLab/Codeberg/Camo links).';
    } catch {
        return 'Link is not a valid URL.';
    }
}
