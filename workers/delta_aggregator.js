
/**
 * 📡 ORION DELTA AGGREGATOR
 * -------------------------
 * A Cloudflare Worker that calculates update deltas for Orion Store clients.
 * 
 * LOGIC:
 * 1. Client sends POST with { "installed": { "appId": "version", ... } }
 * 2. Worker fetches 'apps.json' (ID -> Repo Map) and 'mirror.json' (Repo -> Latest Version)
 * 3. Worker computes which apps are outdated.
 * 4. Worker returns ONLY the update data for those apps.
 * 
 * RESULT:
 * - 1 Request per session.
 * - Massive bandwidth saving.
 * - Zero processing load on the mobile device.
 */

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
};

// CONSTANTS - Pointing to the Ghost Branch data source
const APPS_URL = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/apps.json';
const MIRROR_URL = 'https://raw.githubusercontent.com/RookieEnough/Orion-Data/data/mirror.json';

export default {
  async fetch(request, env, ctx) {
    // Handle CORS Preflight
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: CORS_HEADERS });
    }

    if (request.method !== "POST") {
      return new Response("Method Not Allowed", { status: 405, headers: CORS_HEADERS });
    }

    try {
      // 1. Parse User Payload
      const payload = await request.json();
      const installedMap = payload.installed || {}; // { "youtube-revanced": "18.05.40", ... }
      
      if (Object.keys(installedMap).length === 0) {
        return new Response(JSON.stringify({ updates: {} }), { 
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' } 
        });
      }

      // 2. Parallel Fetch Data Sources (Cached)
      // We use the default Cloudflare cache for fetch requests
      const [appsRes, mirrorRes] = await Promise.all([
        fetch(APPS_URL, { cf: { cacheTtl: 300, cacheEverything: true } }),
        fetch(MIRROR_URL, { cf: { cacheTtl: 300, cacheEverything: true } })
      ]);

      if (!appsRes.ok || !mirrorRes.ok) {
        throw new Error("Failed to fetch upstream data sources");
      }

      const apps = await appsRes.json();     // Array of App definitions
      const mirror = await mirrorRes.json(); // Object: { "Repo/Path": ReleaseData }

      // 3. Compute Deltas
      const updates = {};

      for (const [appId, localVersion] of Object.entries(installedMap)) {
        // Find app definition
        const appDef = apps.find(a => a.id === appId);
        if (!appDef) continue; // App no longer exists in store

        // Resolve Repo Key (Logic must match mirror_generator.py)
        let repoKey = null;
        if (appDef.githubRepo) {
            repoKey = appDef.githubRepo.replace("https://github.com/", "").replace(/\/$/, "");
        } else if (appDef.repoUrl && appDef.repoUrl.includes("github.com")) {
            const parts = appDef.repoUrl.split("github.com/");
            if (parts.length > 1) {
                const sub = parts[1].split("/");
                if (sub.length >= 2) repoKey = `${sub[0]}/${sub[1]}`.replace(".git", "");
            }
        }
        // Note: GitLab support can be added here if mirror.json supports it in the same structure

        if (!repoKey) continue;

        // Lookup in Mirror
        // The mirror keys might be case-sensitive or not, usually we lowercase in generator, 
        // but here we should try exact or lower.
        const remoteData = mirror[repoKey] || mirror[repoKey.toLowerCase()];

        if (remoteData) {
            const remoteVersion = extractVersion(remoteData.tag_name);
            const cleanLocal = cleanVersion(localVersion);

            if (compareVersions(remoteVersion, cleanLocal) > 0) {
                // UPDATE AVAILABLE!
                // We return the relevant data so the client doesn't need to fetch the shard.
                updates[appId] = {
                    newVersion: remoteVersion,
                    tagName: remoteData.tag_name,
                    publishedAt: remoteData.published_at,
                    assets: remoteData.assets, // Forward assets for direct install
                    htmlUrl: remoteData.html_url
                };
            }
        }
      }

      // 4. Return Response
      return new Response(JSON.stringify({ 
          timestamp: Date.now(),
          count: Object.keys(updates).length,
          updates: updates 
      }), {
        headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' }
      });

    } catch (e) {
      return new Response(JSON.stringify({ error: e.message }), { 
          status: 500, 
          headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' } 
      });
    }
  },
};

// --- HELPERS ---

function cleanVersion(v) {
    if (!v) return "0.0.0";
    return v.toLowerCase()
        .replace("v", "")
        .replace(/-all/g, "")
        .replace(/-universal/g, "")
        .replace(/[^0-9.]/g, "")
        .trim();
}

function extractVersion(tagName) {
    if (!tagName) return "0.0.0";
    // Regex for basic SemVer (1.0.0)
    const match = tagName.match(/(\d+(?:\.\d+)+)/);
    return match ? match[1] : tagName;
}

function compareVersions(v1, v2) {
    const p1 = v1.split('.').map(Number);
    const p2 = v2.split('.').map(Number);
    const len = Math.max(p1.length, p2.length);

    for (let i = 0; i < len; i++) {
        const num1 = p1[i] || 0;
        const num2 = p2[i] || 0;
        if (num1 > num2) return 1;
        if (num1 < num2) return -1;
    }
    return 0;
}
