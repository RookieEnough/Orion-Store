import {
    AppItem,
    AppVariant,
    VersionOption,
    UpdateStream,
    StoreCollection,
    StorefrontAnimation,
    StorefrontModuleConfig,
    BundleItem
} from '../types';
import { appMatchesCategoryFilter, getHomepageCategoryCards } from '../utils/discovery';

// --- CONSTANTS & ENUMS ---
// Re-declared for isolation
let allApps: AppItem[] = [];
let mirrorCache: Map<string, any[]> = new Map();

// --- UTILITIES ---
const determineArch = (filename: string): string => {
    const lower = filename.toLowerCase();
    if (lower.includes('arm64') || lower.includes('v8a')) return 'ARM64';
    if (lower.includes('armeabi') || lower.includes('v7a')) return 'ARMv7';
    if (lower.includes('x86_64') || lower.includes('x64')) return 'x64';
    if (lower.includes('x86')) return 'x86';
    return 'Universal';
};

const extractVersionString = (str: string): string | null => {
    if (!str) return null;
    let clean = str.toLowerCase().trim();
    // Aggressive cleaning to extract pure semantic version
    clean = clean.replace(/armeabi-v7a/g, '').replace(/arm64-v8a/g, '').replace(/x86_64/g, '').replace(/x86/g, '')
        .replace(/v7a/g, '').replace(/v8a/g, '').replace(/-all/g, '').replace(/_all/g, '')
        .replace(/-universal/g, '').replace(/_universal/g, '').replace(/universal/g, '')
        .replace(/\.apk/g, '').replace(/release/g, '').replace(/mod/g, '')
        .replace(/beta/g, '').replace(/alpha/g, '').replace(/nightly/g, '').replace(/debug/g, '');

    // Attempt 1: "1.2.3"
    const semMatch = clean.match(/v?(\d+(?:\.\d+)+)/);
    if (semMatch && semMatch[1]) return semMatch[1].replace(/-/g, '.');

    // Plain build codes (e.g. "123456") are not useful as display versions.
    // Fall through to null so the caller can use a date-based fallback instead.
    return null;
};

const formatSize = (bytes: number): string => {
    if (!bytes || bytes === 0) return 'Varies';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
};

// --- FILENAME INTELLIGENCE ENGINE ---
const detectStream = (filename: string, tagName: string, isPrerelease: boolean): UpdateStream => {
    const raw = (filename + " " + tagName).toLowerCase();

    if (raw.includes('nightly') || raw.includes('daily') || raw.includes('snapshot')) return 'Nightly';
    if (raw.includes('alpha') || raw.includes('canary') || raw.includes('debug')) return 'Alpha';
    if (raw.includes('beta') || raw.includes('preview') || raw.includes('rc') || raw.includes('pre')) return 'Beta';

    if (isPrerelease) return 'Beta'; // Fallback for generic prerelease
    return 'Stable';
};

const sanitizeUrl = (url?: string): string => {
    if (!url) return '#';
    if (url.trim().toLowerCase().startsWith('javascript:')) return '#';
    return url;
};

const parseSizeToNumber = (sizeStr: string): number => {
    if (!sizeStr || sizeStr.toLowerCase().includes('varies')) return 0;
    const clean = sizeStr.toLowerCase().replace(/[^0-9.]/g, '');
    const num = parseFloat(clean);
    if (isNaN(num)) return 0;
    if (sizeStr.toLowerCase().includes('gb')) return num * 1024;
    if (sizeStr.toLowerCase().includes('kb')) return num / 1024;
    return num;
};

// --- CORE LOGIC ---
const sanitizeApp = (app: any): AppItem => {
    const rawCategory = String(app.category || 'Utility').trim();
    // Normalize to title-case to prevent duplicate filter tabs (e.g. "utility" → "Utility")
    const normalizedCategory = rawCategory.charAt(0).toUpperCase() + rawCategory.slice(1).toLowerCase();
    return {
        ...app,
        name: String(app.name || 'Unknown App'),
        description: String(app.description || ''),
        author: String(app.author || 'Unknown'),
        category: normalizedCategory,
        tags: Array.isArray(app.tags) ? app.tags.map((t: string) => String(t).trim()) : [],
        platform: app.platform || 'Android',
        icon: sanitizeUrl(String(app.icon || '')),
        version: String(app.version || 'Latest'),
        latestVersion: String(app.latestVersion || 'Latest'),
        downloadUrl: sanitizeUrl(String(app.downloadUrl || '#')),
        screenshots: Array.isArray(app.screenshots) ? app.screenshots.map((s: string) => sanitizeUrl(s)) : [],
        availableVersions: []
    };
};

const processItem = (app: AppItem): AppItem => {
    const isGitHub = !!(app.githubRepo || (app.repoUrl && app.repoUrl.includes('github.com')));
    const isGitLab = !!(app.gitlabRepo || (app.repoUrl && app.repoUrl.includes('gitlab.com')));
    const isCodeberg = !!(app.codebergRepo || (app.repoUrl && app.repoUrl.includes('codeberg.org')));

    if (!isGitHub && !isGitLab && !isCodeberg) return app;

    let cleanRepoPath = '';
    let uniqueKey = '';

    if (app.githubRepo) {
        cleanRepoPath = app.githubRepo;
        uniqueKey = `github::github.com::${cleanRepoPath.toLowerCase()}`;
    }
    else if (app.gitlabRepo) {
        cleanRepoPath = app.gitlabRepo;
        uniqueKey = `gitlab::${app.gitlabDomain || 'gitlab.com'}::${cleanRepoPath.toLowerCase()}`;
    }
    else if (app.codebergRepo) {
        cleanRepoPath = app.codebergRepo;
        uniqueKey = `codeberg::codeberg.org::${cleanRepoPath.toLowerCase()}`;
    }
    else if (app.repoUrl) {
        cleanRepoPath = app.repoUrl
            .replace(/^https?:\/\/(www\.)?github\.com\//i, '')
            .replace(/^https?:\/\/(www\.)?gitlab\.com\//i, '')
            .replace(/^https?:\/\/(www\.)?codeberg\.org\//i, '')
            .replace(/\.git$/i, '')
            .replace(/\/$/, '');
    }

    let releases = (uniqueKey && mirrorCache.get(uniqueKey)) || (cleanRepoPath ? mirrorCache.get(cleanRepoPath.toLowerCase()) : null);

    if (cleanRepoPath && Array.isArray(releases) && releases.length > 0) {

        const processAssets = (rel: any): any[] => {
            if (!rel || !rel.assets || !Array.isArray(rel.assets)) return [];

            const candidates = rel.assets.filter((a: any) => {
                const name = (a.name || '').toLowerCase();
                const url = (a.browser_download_url || '').toLowerCase();
                return name.endsWith('.apk') || url.includes('.apk') || name === 'apk' || name.includes('apk');
            });
            if (candidates.length === 0) return [];

            if (!app.releaseKeyword) {
                return candidates;
            }

            const kw = app.releaseKeyword.toLowerCase();

            const assetMatches = candidates.filter((a: any) => {
                const name = (a.name || '').toLowerCase();
                const url = (a.browser_download_url || '').toLowerCase();
                return name.includes(kw) || url.includes(kw);
            });
            if (assetMatches.length > 0) {
                return assetMatches;
            }

            const releaseMatches = (rel.name?.toLowerCase().includes(kw)) || (rel.tag_name?.toLowerCase().includes(kw));
            if (releaseMatches) {
                return candidates;
            }

            return [];
        };

        // Correctly typed buckets to avoid implicit any/undefined errors
        const streamBuckets: Record<UpdateStream, VersionOption[]> = {
            'Stable': [], 'Beta': [], 'Alpha': [], 'Nightly': []
        };

        // Scan ALL releases
        releases.forEach((rel: any) => {
            const assets = processAssets(rel);
            if (assets.length === 0) return;

            const tagName = rel.tag_name || '';
            const fileName = assets[0].name;
            const releaseName = rel.name || '';

            // 1. Detect Stream
            const stream = detectStream(fileName, tagName, rel.prerelease);

            // 2. Extract Version
            let finalVersion = "Unknown";
            const tagVer = extractVersionString(tagName);
            const fileVer = extractVersionString(fileName);
            const relNameVer = extractVersionString(releaseName);

            if (fileVer) finalVersion = fileVer;
            else if (tagVer && !['latest', 'all'].includes(tagName.toLowerCase())) finalVersion = tagVer;
            else if (relNameVer) finalVersion = relNameVer;
            else {
                try {
                    const d = new Date(rel.published_at || rel.created_at || Date.now());
                    finalVersion = d.toISOString().split('T')[0] || "Unknown";
                } catch (e) { finalVersion = "Unknown"; }
            }

            // 3. Build Variants
            const variants: AppVariant[] = assets.map((a: any) => ({
                arch: determineArch(a.name),
                url: a.browser_download_url,
                size: a.size // Store size in variant
            }));

            variants.sort((a, b) => {
                const priority = (name: string) => name === 'Universal' ? 1 : name === 'ARM64' ? 2 : name === 'ARMv7' ? 3 : 4;
                return priority(a.arch) - priority(b.arch);
            });

            // Push to bucket (guaranteed to exist because streamBuckets is exhaustive)
            streamBuckets[stream].push({
                type: stream,
                version: finalVersion,
                date: (rel.published_at || "").split('T')[0],
                variants
            });
        });

        const availableVersions: VersionOption[] = [];
        const streams: UpdateStream[] = ['Stable', 'Beta', 'Alpha', 'Nightly'];

        // Pick the latest 1 from each stream
        streams.forEach(s => {
            const bucket = streamBuckets[s];
            if (bucket && bucket.length > 0) {
                // Since mirror.json releases are usually sorted by date desc, [0] is latest.
                // We use ! because we checked length > 0.
                availableVersions.push(bucket[0]!);
            }
        });

        // Determine Default (Preferred)
        let defaultVer = availableVersions.find(v => v.type === 'Stable') || availableVersions[0];

        if (defaultVer) {
            // Update app size based on the primary variant of the default version
            const primaryVariant = defaultVer.variants[0];
            const displaySize = primaryVariant && primaryVariant.size ? formatSize(primaryVariant.size) : app.size;

            return {
                ...app,
                version: defaultVer.version,
                latestVersion: defaultVer.version,
                downloadUrl: defaultVer.variants[0]?.url || '#',
                variants: defaultVer.variants,
                availableVersions,
                size: displaySize
            };
        }
    }
    return app;
};

const performSearch = (query: string, category: string, sort: string, apps: AppItem[]) => {
    const q = query.toLowerCase().trim();
    let results = apps;

    if (q) {
        // Scoring Algorithm: Name Match (10) > Author (5) > Description (1)
        results = results.map(app => {
            let score = 0;
            if (app.name.toLowerCase().includes(q)) score += 10;
            if (app.name.toLowerCase().startsWith(q)) score += 5; // Bonus for prefix
            if (app.author.toLowerCase().includes(q)) score += 5;
            if (app.description.toLowerCase().includes(q)) score += 1;
            if (app.id.includes(q)) score += 2;

            return { app, score };
        })
            .filter(item => item.score > 0)
            .sort((a, b) => b.score - a.score)
            .map(item => item.app);
    }

    if (category && category !== 'All') {
        results = results.filter((app) => appMatchesCategoryFilter(app, category));
    }

    results.sort((a, b) => {
        switch (sort) {
            case 'Oldest Added': return 0;
            case 'Recently Added': return 0;
            case 'Name (A-Z)': return a.name.localeCompare(b.name);
            case 'Name (Z-A)': return b.name.localeCompare(a.name);
            case 'Size (Smallest)': return parseSizeToNumber(a.size) - parseSizeToNumber(b.size);
            case 'Size (Largest)': return parseSizeToNumber(b.size) - parseSizeToNumber(a.size);
            default: return 0;
        }
    });

    if (sort === 'Recently Added' && !q) {
        return [...results].reverse();
    }

    return results;
};

// --- STOREFRONT GENERATOR ---
const mulberry32 = (a: number) => {
    return function() {
      var t = a += 0x6D2B79F5;
      t = Math.imul(t ^ t >>> 15, t | 1);
      t ^= t + Math.imul(t ^ t >>> 7, t | 61);
      return ((t ^ t >>> 14) >>> 0) / 4294967296;
    }
}

const getDailySeed = () => {
    const d = new Date();
    return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate();
};

const shuffleArray = <T>(array: T[], seed: number): T[] => {
    const rng = mulberry32(seed);
    const result = [...array];
    for (let i = result.length - 1; i > 0; i--) {
        const j = Math.floor(rng() * (i + 1));
        const temp = result[i];
        result[i] = result[j] as T;
        result[j] = temp as T;
    }
    return result;
};

const normalizeModuleAnimation = (animation?: string): StorefrontAnimation => {
    if (animation === 'snowfall' || animation === 'confetti' || animation === 'spark' || animation === 'none') {
        return animation;
    }
    return 'snowfall';
};

const buildBundleMonogram = (title: string) => {
    const words = title
        .split(/\s+/)
        .map((word) => word.replace(/[^a-z0-9]/gi, '').trim())
        .filter(Boolean);

    if (words.length === 0) return 'B';
    if (words.length === 1) return words[0]!.slice(0, 1).toUpperCase();
    return `${words[0]!.slice(0, 1)}${words[1]!.slice(0, 1)}`.toUpperCase();
};

const resolveAppsByIds = (ids: string[] | undefined, appMap: Map<string, AppItem>): AppItem[] => {
    if (!Array.isArray(ids) || ids.length === 0) return [];
    const seen = new Set<string>();
    const resolved: AppItem[] = [];
    ids.forEach((id) => {
        const app = appMap.get(id);
        if (app && !seen.has(app.id)) {
            seen.add(app.id);
            resolved.push(app);
        }
    });
    return resolved;
};

const isModuleForPlatform = (module: StorefrontModuleConfig, platform: string): boolean => {
    const target = (module.platform || 'all').toLowerCase();
    return target === 'all' || target === platform.toLowerCase();
};

const buildDefaultModules = (platformApps: AppItem[], seed: number): StorefrontModuleConfig[] => {
    const shuffled = shuffleArray(platformApps, seed + 17);

    return [
        {
            id: 'default_category_cards',
            type: 'category_cards',
            title: 'Browse by Category',
            subtitle: 'Swipe the cards, then tap one to show only matching apps',
            insertAfterCategory: 0,
            animation: 'confetti'
        },
        {
            id: 'default_curved_showcase',
            type: 'curved_apps',
            title: 'Curated Spotlight',
            subtitle: 'A quick 6-app mix you should not miss',
            insertAfterCategory: 2,
            animation: 'snowfall',
            appIds: shuffled.slice(0, 6).map((app) => app.id)
        },
        {
            id: 'default_recommended_bundles',
            type: 'recommendation_bundles',
            title: 'Recommended App Bundles',
            subtitle: 'Install in packs for faster setup',
            insertAfterCategory: 4,
            animation: 'spark',
            bundles: [
                {
                    id: 'default_bundle_daily',
                    title: 'Daily Driver Kit',
                    description: 'Core apps for your everyday workflow',
                    appIds: shuffled.slice(0, 3).map((app) => app.id),
                    badge: 'Starter',
                    icon: 'fa-bolt'
                },
                {
                    id: 'default_bundle_media',
                    title: 'Media Escape',
                    description: 'Watch, listen, and unwind quickly',
                    appIds: shuffled.slice(3, 6).map((app) => app.id),
                    badge: 'Popular',
                    icon: 'fa-photo-film'
                },
                {
                    id: 'default_bundle_power',
                    title: 'Power User Stack',
                    description: 'Advanced tools for people who build and tweak',
                    appIds: shuffled.slice(6, 9).map((app) => app.id),
                    badge: 'Pro',
                    icon: 'fa-terminal'
                }
            ]
        }
    ];
};

interface InterludeCollection {
    insertAfterCategory: number;
    collection: StoreCollection;
}

const buildInterludeCollection = (
    module: StorefrontModuleConfig,
    index: number,
    platformApps: AppItem[],
    appMap: Map<string, AppItem>,
    seed: number
): InterludeCollection | null => {
    const fallbackInsert = 2 + (index * 2);
    const insertAfterCategory = Math.max(0, Math.floor(module.insertAfterCategory ?? fallbackInsert));
    const animation = normalizeModuleAnimation(module.animation);

    if (module.type === 'category_cards') {
        const sourceApps = resolveAppsByIds(module.appIds, appMap);
        const cards = getHomepageCategoryCards(sourceApps.length > 0 ? sourceApps : platformApps);
        if (cards.length === 0) return null;
        return {
            insertAfterCategory,
            collection: {
                id: module.id,
                title: module.title,
                subtitle: module.subtitle,
                type: 'category_cards',
                animation,
                categoryCards: cards
            }
        };
    }

    if (module.type === 'curved_apps') {
        const selected = resolveAppsByIds(module.appIds, appMap);
        const taken = new Set(selected.map((app) => app.id));
        const fallback = shuffleArray(platformApps, seed + index + 41);

        for (const app of fallback) {
            if (selected.length >= 6) break;
            if (!taken.has(app.id)) {
                selected.push(app);
                taken.add(app.id);
            }
        }

        if (selected.length === 0) return null;
        return {
            insertAfterCategory,
            collection: {
                id: module.id,
                title: module.title,
                subtitle: module.subtitle,
                type: 'bundle',
                animation,
                apps: selected.slice(0, 6)
            }
        };
    }

    if (module.type === 'update_pills') {
        return null;
    }

    if (module.type === 'recommendation_bundles') {
        const bundles: BundleItem[] = [];

        if (Array.isArray(module.bundles) && module.bundles.length > 0) {
            module.bundles.forEach((bundle, bundleIndex) => {
                const apps = resolveAppsByIds(bundle.appIds, appMap);
                if (apps.length === 0) return;
                bundles.push({
                    id: bundle.id || `${module.id}-bundle-${bundleIndex}`,
                    title: bundle.title || `Bundle ${bundleIndex + 1}`,
                    description: bundle.description || 'Recommended apps',
                    appIds: bundle.appIds,
                    apps,
                    color: bundle.color,
                    badge: bundle.badge,
                    icon: bundle.icon,
                    monogram: bundle.monogram || buildBundleMonogram(bundle.title || `Bundle ${bundleIndex + 1}`)
                });
            });
        }

        if (bundles.length === 0) {
            const sourceApps = resolveAppsByIds(module.appIds, appMap);
            const fallbackApps = sourceApps.length > 0 ? sourceApps : shuffleArray(platformApps, seed + index + 67).slice(0, 9);
            for (let bundleIndex = 0; bundleIndex < 3; bundleIndex++) {
                const start = bundleIndex * 3;
                const chunk = fallbackApps.slice(start, start + 3);
                if (chunk.length < 2) continue;
                bundles.push({
                    id: `${module.id}-bundle-${bundleIndex}`,
                    title: `Recommended Pack ${bundleIndex + 1}`,
                    description: 'Hand-picked apps that work great together',
                    appIds: chunk.map((app) => app.id),
                    apps: chunk,
                    badge: bundleIndex === 0 ? 'Hot' : bundleIndex === 1 ? 'Fresh' : 'Classic',
                    icon: bundleIndex === 0 ? 'fa-wand-magic-sparkles' : bundleIndex === 1 ? 'fa-headphones' : 'fa-rocket',
                    monogram: buildBundleMonogram(`Recommended Pack ${bundleIndex + 1}`)
                });
            }
        }

        if (bundles.length === 0) return null;
        return {
            insertAfterCategory,
            collection: {
                id: module.id,
                title: module.title,
                subtitle: module.subtitle,
                type: 'recommendation_bundles',
                animation,
                bundles
            }
        };
    }

    return null;
};

const generateStorefront = (
    apps: AppItem[],
    platform: string,
    storefrontModules: StorefrontModuleConfig[] = []
): StoreCollection[] => {
    const platformApps = apps.filter(a => a.platform.toLowerCase() === platform.toLowerCase());
    if (platformApps.length === 0) return [];

    const collections: StoreCollection[] = [];
    const seed = getDailySeed();
    const appMap = new Map<string, AppItem>(platformApps.map((app) => [app.id, app]));

    // 1. Hero Carousel (Featured Today)
    const shuffledForHero = shuffleArray(platformApps, seed);
    collections.push({
        id: 'hero_daily',
        title: 'Featured Today',
        type: 'hero',
        apps: shuffledForHero.slice(0, 5)
    });

    // 2. Recommended For You (Daily Rotation)
    const shuffledForRecommended = shuffleArray(platformApps, seed + 1);
    collections.push({
        id: 'recommended_daily',
        title: 'Recommended For You',
        type: 'swimlane',
        apps: shuffledForRecommended.slice(0, 10)
    });

    // 3. New & Updated
    collections.push({
        id: 'new_updated',
        title: 'New & Updated',
        type: 'swimlane',
        apps: [...platformApps].reverse().slice(0, 12),
        totalAppCount: Math.min(platformApps.length, 18)
    });

    // 4. Dynamic Categories with interludes inserted between rows
    const categories = Array.from(new Set(platformApps.map(a => a.category))).filter(c => c !== 'All');
    const categoryRows: StoreCollection[] = [];
    categories.forEach((cat) => {
        const catApps = platformApps.filter(a => a.category === cat);
        if (catApps.length >= 3) {
            categoryRows.push({
                id: `cat_${cat.toLowerCase()}`,
                title: `Top in ${cat}`,
                type: 'swimlane',
                filter: cat,
                apps: catApps.slice(0, 12)
            });
        }
    });

    const configuredModules = storefrontModules
        .filter((module) => isModuleForPlatform(module, platform))
        .filter((module) => module.type !== 'update_pills');
    let activeModules = configuredModules.length > 0 ? configuredModules : buildDefaultModules(platformApps, seed);
    if (!activeModules.some((module) => module.type === 'category_cards')) {
        activeModules = [
            {
                id: `default_category_cards_${platform.toLowerCase()}`,
                type: 'category_cards',
                title: 'Browse by Category',
                subtitle: 'Swipe the cards, then tap one to show only matching apps',
                insertAfterCategory: 0,
                animation: 'confetti'
            },
            ...activeModules
        ];
    }
    const interludeCollections = activeModules
        .filter((module) => isModuleForPlatform(module, platform))
        .map((module, index) => buildInterludeCollection(module, index, platformApps, appMap, seed))
        .filter((entry): entry is InterludeCollection => entry !== null)
        .sort((a, b) => a.insertAfterCategory - b.insertAfterCategory);

    const interleavedRows: StoreCollection[] = [];
    let categoryCount = 0;
    let interludeIndex = 0;

    while (interludeIndex < interludeCollections.length) {
        const nextInterlude = interludeCollections[interludeIndex];
        if (!nextInterlude || nextInterlude.insertAfterCategory > 0) break;
        interleavedRows.push(nextInterlude.collection);
        interludeIndex += 1;
    }

    categoryRows.forEach((row) => {
        interleavedRows.push(row);
        categoryCount += 1;

        while (interludeIndex < interludeCollections.length) {
            const nextInterlude = interludeCollections[interludeIndex];
            if (!nextInterlude || categoryCount < nextInterlude.insertAfterCategory) break;
            interleavedRows.push(nextInterlude.collection);
            interludeIndex += 1;
        }
    });

    while (interludeIndex < interludeCollections.length) {
        const nextInterlude = interludeCollections[interludeIndex];
        if (!nextInterlude) break;
        interleavedRows.push(nextInterlude.collection);
        interludeIndex += 1;
    }

    collections.push(...interleavedRows);

    // 5. Tag-based collections
    const allTags = new Set<string>();
    platformApps.forEach(a => a.tags?.forEach(t => allTags.add(t)));
    
    if (allTags.size > 0) {
        const shuffledTags = shuffleArray(Array.from(allTags), seed + 2);
        const tagsToFeature = shuffledTags.slice(0, 2);
        
        tagsToFeature.forEach(tag => {
            const tagApps = platformApps.filter(a => a.tags?.includes(tag));
            if (tagApps.length >= 3) {
                collections.push({
                    id: `tag_${tag.toLowerCase()}`,
                    title: `Curated: ${tag}`,
                    type: 'swimlane',
                    filter: tag,
                    apps: tagApps.slice(0, 12)
                });
            }
        });
    }

    return collections;
};

// --- SHARED MESSAGE HANDLER ---
// This allows both the Real Worker and the Mock Worker to use the same logic.
const handleMessage = (data: any, postMessage: (msg: any) => void) => {
    const { type, payload } = data;

    switch (type) {
        case 'INIT_DATA':
            const { rawApps, mirrorData, importedApps } = payload;

            mirrorCache.clear();
            if (mirrorData) {
                Object.keys(mirrorData).forEach(key => {
                    const d = mirrorData![key];
                    mirrorCache.set(key.toLowerCase(), Array.isArray(d) ? d : [d]);
                });
            }

            const processedApps = rawApps.map(sanitizeApp).map(processItem);
            const processedImported = importedApps.map(sanitizeApp).map(processItem);

            allApps = [...processedApps, ...processedImported];

            postMessage({
                type: 'DATA_PROCESSED',
                payload: { apps: processedApps, imported: processedImported }
            });
            break;

        case 'FILTER':
            let filteredApps = allApps;
            if (payload.platform) {
                filteredApps = allApps.filter(a => a.platform === payload.platform);
            }
            const results = performSearch(payload.query, payload.category, payload.sort, filteredApps);
            
            let collections: StoreCollection[] = [];
            if (!payload.query && payload.platform) {
                const storefrontModules: StorefrontModuleConfig[] = Array.isArray(payload.storefrontModules) ? payload.storefrontModules : [];
                collections = generateStorefront(allApps, payload.platform, storefrontModules);
            }
            
            postMessage({ type: 'FILTER_RESULTS', payload: { results, collections } });
            break;

        case 'HASH':
            crypto.subtle.digest('SHA-256', new TextEncoder().encode(payload.message)).then(hash => {
                const hex = Array.from(new Uint8Array(hash)).map(b => b.toString(16).padStart(2, '0')).join('');
                postMessage({ type: 'HASH_RESULT', payload: { id: payload.id, hash: hex } });
            });
            break;
    }
};

// --- REAL WORKER ENTRY POINT ---
// This block only runs if we are in a true Worker environment.
const isWorkerEnv = typeof self !== 'undefined' && typeof window === 'undefined' && typeof self.document === 'undefined';

if (isWorkerEnv) {
    self.onmessage = (e: MessageEvent) => {
        handleMessage(e.data, (msg) => self.postMessage(msg));
    };
}

// --- MOCK WORKER (PREVIEW FALLBACK) ---
// This export satisfies the 'default' import requirement when the file is loaded as a module in web previews.
// It acts as a bridge to run the logic on the Main Thread if Worker instantiation fails.
export default class MockWorker {
    onmessage: ((e: MessageEvent) => void) | null = null;

    constructor() {
        console.warn("⚠️ Orion Store: Running in Main Thread Fallback Mode (Web Preview)");
    }

    postMessage(data: any) {
        // Simulate async behavior to match Worker API
        setTimeout(() => {
            handleMessage(data, (payload) => {
                if (this.onmessage) {
                    this.onmessage({ data: payload } as MessageEvent);
                }
            });
        }, 0);
    }

    terminate() {
        // No-op for main thread fallback
    }
}
