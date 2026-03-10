/**
 * ORION STORE - EDGE IMAGE PROXY
 * ------------------------------
 * Deploy this to Cloudflare Workers.
 * 
 * Features:
 * 1. Aggressive Caching (7 Days)
 * 2. Origin Header Stripping (Privacy)
 * 3. Fallback Handling
 */

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const targetUrl = url.searchParams.get('url');

    if (!targetUrl) {
      return new Response('Missing "url" parameter', { status: 400 });
    }

    // 1. Construct Cache Key
    // We use the full request URL (including search params) as the key
    const cacheKey = new Request(url.toString(), request);
    const cache = caches.default;

    // 2. Check Cache
    let response = await cache.match(cacheKey);

    if (!response) {
      console.log(`[Proxy] Miss: ${targetUrl}`);
      
      try {
        // 3. Fetch from Origin
        // We strip user-agent to avoid tracking/blocking by some CDNs
        const originResponse = await fetch(targetUrl, {
          headers: {
            'User-Agent': 'OrionStore-Proxy/1.0',
            'Accept': 'image/*'
          }
        });

        // Handle failed origin fetch
        if (!originResponse.ok) {
            // Return a 404 so the client knows to show a fallback
            return new Response('Image not found on origin', { status: 404 });
        }

        // 4. Reconstruct Response for Cache
        // We must create a new Response to modify headers safely
        response = new Response(originResponse.body, originResponse);

        // 5. Apply Aggressive Cache Headers
        // Cache for 7 days (604800 seconds)
        response.headers.set('Cache-Control', 'public, max-age=604800, immutable');
        
        // Security Headers
        response.headers.set('Access-Control-Allow-Origin', '*');
        response.headers.set('Content-Security-Policy', "default-src 'none'");
        response.headers.delete('Set-Cookie'); // Ensure no tracking cookies pass through

        // 6. Save to Cache
        ctx.waitUntil(cache.put(cacheKey, response.clone()));

      } catch (e) {
        return new Response('Proxy Error: ' + e.message, { status: 500 });
      }
    } else {
        console.log(`[Proxy] Hit: ${targetUrl}`);
    }

    return response;
  },
};