
import { IMAGE_PROXY_CONFIG } from '../constants';

const prefetchedImageUrls = new Set<string>();

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
    // q=70:  Reduced quality for small icons to speed up loading
    // l=1:   Optimization level (compression speed vs size)
    // il=0:  Disabled interlacing for small icons as it can sometimes be slower to decode
    // maxage=31d: Force CDN to cache for 1 month (Super fast repeat loads)
    // n=-1:  No filter (Faster decoding on device)
    
    let quality = IMAGE_PROXY_CONFIG.quality;
    if (width && width <= 112) quality = 60;
    else if (width && width < 200) quality = 68;
    
    let query = `?url=${encodedUrl}&output=webp&q=${quality}&l=1&il=${width && width < 200 ? 0 : 1}&maxage=31d&n=-1`;
    
    if (width) query += `&w=${width}`;
    if (height) query += `&h=${height}`;
    
    return `https://wsrv.nl/${query}`;
};

export const prefetchImage = (url: string): void => {
    if (typeof window === 'undefined' || !url || prefetchedImageUrls.has(url)) return;

    prefetchedImageUrls.add(url);
    const image = new Image();
    image.decoding = 'async';
    image.loading = 'eager';
    image.src = url;
};
