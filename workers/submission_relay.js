
/**
 * ORION GHOST RELAY (Leaderboard Worker)
 * --------------------------------------
 * Handles secure submission and caching for the Orion Leaderboard.
 * Deploy to Cloudflare Workers.
 * 
 * Env Vars required in Cloudflare:
 * - GITHUB_TOKEN: PAT with repo scope
 * - GITHUB_REPO: owner/repo
 * - SALT_KEY: Matches client key (ORION_OMEGA_PROTOCOL_X9_SECURE_HASH_V1)
 * - ORION_KV: (Optional) KV Namespace binding for rate limiting
 */

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
};

const BRANCH = "data";
const LEADERBOARD_FILE = "leaderboard.json";

export default {
  async fetch(request, env, ctx) {
    // 1. Handle Preflight
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: CORS_HEADERS });
    }

    const url = new URL(request.url);

    // --- READ (GET) ---
    // Proxies the leaderboard.json from GitHub Raw to avoid CORS issues on the app
    // EXTREME CACHING: 1 HOUR (3600s)
    if (request.method === "GET") {
        const cache = caches.default;
        const cacheKey = new Request(url.toString(), request);
        let response = await cache.match(cacheKey);

        if (!response) {
            if (!env.GITHUB_REPO) return new Response("Config Error: GITHUB_REPO missing", { status: 500 });

            const ghUrl = `https://raw.githubusercontent.com/${env.GITHUB_REPO}/${BRANCH}/${LEADERBOARD_FILE}`;
            const ghRes = await fetch(ghUrl);
            
            if (!ghRes.ok) {
                // Return empty array if file doesn't exist yet
                return new Response(JSON.stringify([]), { headers: CORS_HEADERS }); 
            }

            response = new Response(ghRes.body, ghRes);
            response.headers.set('Content-Type', 'application/json');
            response.headers.set('Cache-Control', 'public, max-age=3600'); // 1 HOUR CACHE
            response.headers.set('Access-Control-Allow-Origin', '*');
            
            ctx.waitUntil(cache.put(cacheKey, response.clone()));
        }
        return response;
    }

    // --- WRITE (POST) ---
    if (request.method === "POST") {
        try {
            const body = await request.json();
            const { data, signature } = body;

            if (!env.GITHUB_TOKEN || !env.GITHUB_REPO || !env.SALT_KEY) {
                return new Response(JSON.stringify({ error: "Worker misconfigured." }), { status: 500, headers: CORS_HEADERS });
            }

            // 1. Rate Limit (KV Check)
            // Locks IP for 24 hours to prevent spam
            if (env.ORION_KV) {
                const clientIP = request.headers.get("CF-Connecting-IP") || "unknown";
                const kvKey = `limit_${clientIP}`;
                const existing = await env.ORION_KV.get(kvKey);
                if (existing) {
                    return new Response(JSON.stringify({ error: "Rate limit: You can only submit once every 24 hours." }), { status: 429, headers: CORS_HEADERS });
                }
                // Lock IP for 24 hours
                await env.ORION_KV.put(kvKey, "1", { expirationTtl: 86400 });
            }

            // 2. Validate Cryptographic Signature (Anti-Cheat Layer 1)
            // Reconstruct the message string exactly how the client did
            const sortedKeys = Object.keys(data).sort();
            const message = sortedKeys.map(key => `${key}:${data[key]}`).join('|') + env.SALT_KEY;
            
            const msgBuffer = new TextEncoder().encode(message);
            const hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer);
            const hashArray = Array.from(new Uint8Array(hashBuffer));
            const calculatedSig = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');

            if (calculatedSig !== signature) {
                return new Response(JSON.stringify({ error: "Security signature mismatch." }), { status: 403, headers: CORS_HEADERS });
            }

            // 3. Logic Validation (Anti-Cheat Layer 2: Sanity & Rules)
            const { level, xp, adWatchCount } = data;

            // Sanity Ceiling: Prevent hacked memory values
            if (level > 150 || xp > 1000000) {
                 return new Response(JSON.stringify({ error: "Stats exceed logical limits. Submission rejected." }), { status: 400, headers: CORS_HEADERS });
            }

            // Minimum Hero Rule: Must contribute before claiming rank
            if (adWatchCount < 5) {
                 return new Response(JSON.stringify({ error: "Minimum 5 contributions required to join the leaderboard." }), { status: 400, headers: CORS_HEADERS });
            }

            // 4. Create GitHub Issue
            // We use Issues as a message queue. GitHub Actions will pick this up and process it.
            const issueTitle = `Leaderboard Submission: ${data.username}`;
            const issueBody = `
### Leaderboard Payload
\`\`\`json
${JSON.stringify(data, null, 2)}
\`\`\`
*Verified by Orion Ghost Relay*
            `.trim();

            const ghRes = await fetch(`https://api.github.com/repos/${env.GITHUB_REPO}/issues`, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${env.GITHUB_TOKEN}`,
                    'User-Agent': 'Orion-Ghost-Relay',
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ title: issueTitle, body: issueBody })
            });

            if (!ghRes.ok) {
                const errText = await ghRes.text();
                throw new Error("GitHub API Error: " + errText);
            }

            return new Response(JSON.stringify({ success: true }), { headers: CORS_HEADERS });

        } catch (e) {
            return new Response(JSON.stringify({ error: e.message }), { status: 500, headers: CORS_HEADERS });
        }
    }

    return new Response("Method Not Allowed", { status: 405, headers: CORS_HEADERS });
  },
};
