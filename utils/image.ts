
import { IMAGE_PROXY_CONFIG } from '../constants';

/**
 * Generates an optimized URL for an image using the configured proxy provider.
 * 
 * @param url The source URL of the image (GitHub, etc.)
 * @param width Target width (for resizing)
 * @param height Target height (for resizing)
 * @returns The proxied/optimized URL
 */
export const getOptimizedImageUrl = (url: string, width?: number, height?: number): string => {
    if (!url) return '';
    
    // Pass through data URIs and blobs directly
    if (url.startsWith('data:') || url.startsWith('blob:')) return url;

    // --- HD ENHANCEMENT: FIX GOOGLE PLAY URLS ---
    // Google Play images often come with =w... params that limit resolution (e.g. =w526-h296-rw).
    // We modify the URL to request a high-res master (2560x1440) before sending it to the proxy.
    let sourceUrl = url;
    if (sourceUrl.includes('googleusercontent.com') || sourceUrl.includes('ggpht.com')) {
        // Regex to match size params at end of URL (starts with = followed by s, w, or h)
        if (/=[swh]\d+/.test(sourceUrl)) {
             sourceUrl = sourceUrl.replace(/=[swh]\d+[^/]*$/, '=w2560-h1440-rw');
        } else {
             // If no params exist, append them to force high quality WebP
             sourceUrl += '=w2560-h1440-rw';
        }
    }

    const encodedUrl = encodeURIComponent(sourceUrl);

    if (IMAGE_PROXY_CONFIG.provider === 'custom') {
        // Custom Cloudflare Worker Proxy
        return `${IMAGE_PROXY_CONFIG.workerUrl}?url=${encodedUrl}`;
    }

    // Default: wsrv.nl (Global CDN + Resizing)
    // ------------------------------------------
    // output=webp: Modern format, smaller size
    // q=80:  Excellent balance of visual quality vs size
    // l=1:   Optimization level (compression speed vs size)
    // il=1:  Interlaced (Progressive) - Loads blurry first, then sharpens. FEELS faster.
    // maxage=31d: Force CDN to cache for 1 month (Super fast repeat loads)
    // n=-1:  No filter (Faster decoding on device)
    
    let query = `?url=${encodedUrl}&output=webp&q=${IMAGE_PROXY_CONFIG.quality}&l=1&il=1&maxage=31d&n=-1`;
    
    if (width) query += `&w=${width}`;
    if (height) query += `&h=${height}`;
    
    return `https://wsrv.nl/${query}`;
};
