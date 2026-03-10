// 🛡️ ORION SENTINEL WORKER (INTELLIGENCE CORE)
// Handles high-performance threat detection with sharded database support.

const SHARD_URL_BASE = 'https://cdn.jsdelivr.net/gh/RookieEnough/Orion-Data@data/sentinel/shard_';
const CACHE_NAME = 'orion-sentinel-db-v1';
const BUCKETS = ['0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'];

const KNOWN_THREATS = new Map<string, string>([
    ["275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f", "EICAR Test File (Safe)"],
    ["131f95c51cc819465fa1797f6ccacf9d494aaaff46fa3eac73ae63bb9ea99a42", "EICAR Test File (Zip)"],
    ["44d88612fea8a8f36de82e1278abb02f", "EICAR Test File (MD5)"],
    ["5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8", "Orion-Malware-Sim"],
    ["d41d8cd98f00b204e9800998ecf8427e", "Null Byte Payload"],
]);

const KNOWN_BAD_PACKAGES = [
    'com.android.htmlviewer', 'com.metasploit.stage', 'com.vanced.android.youtube',
    'org.malicious.trojan', 'com.example.malware',
];

let deepScanThreats: any[] = [];

self.onmessage = async (e: MessageEvent) => {
    const { type, payload } = e.data;

    switch (type) {
        case 'CHECK_DB_STATUS':
            await checkDbStatus();
            break;
        case 'UPDATE_DB':
            await updateDatabase();
            break;
        case 'START_DEEP_SCAN':
            deepScanThreats = [];
            break;
        case 'CHECK_FILES':
            await performDeepScan(payload.files);
            break;
        case 'FINALIZE_DEEP_SCAN':
            self.postMessage({ type: 'SCAN_COMPLETE', payload: deepScanThreats });
            break;
        case 'RAPID_SCAN':
             await performRapidScan(payload.apps);
             break;
    }
};

async function countSignatures() {
    try {
        const cache = await caches.open(CACHE_NAME);
        let total = 0;
        for (const b of BUCKETS) {
            const response = await cache.match(`${SHARD_URL_BASE}${b}.json`);
            if (response) {
                const shardData: {h: string}[] = await response.json();
                total += shardData.length;
            }
        }
        total += KNOWN_THREATS.size; // Add embedded threats
        self.postMessage({ type: 'SIGNATURE_COUNT_READY', payload: { count: total } });
    } catch (e) {
        // fail silently
    }
}

async function checkDbStatus() {
    try {
        const cache = await caches.open(CACHE_NAME);
        const promises = BUCKETS.map(b => cache.match(`${SHARD_URL_BASE}${b}.json`));
        const results = await Promise.all(promises);
        const isReady = results.every(res => !!res);
        self.postMessage({ type: 'DB_STATUS', payload: { ready: isReady } });
        if (isReady) {
            await countSignatures();
        }
    } catch (e) {
        self.postMessage({ type: 'DB_STATUS', payload: { ready: true, error: true } });
    }
}

async function updateDatabase() {
    try {
        const cache = await caches.open(CACHE_NAME);
        let completed = 0;
        
        for (const b of BUCKETS) {
            const url = `${SHARD_URL_BASE}${b}.json`;
            try {
                await cache.add(new Request(url, { cache: 'reload' }));
            } catch(e) {}
            completed++;
            self.postMessage({ type: 'UPDATE_PROGRESS', payload: { progress: (completed / 16) * 100 } });
        }
        self.postMessage({ type: 'DB_UPDATE_COMPLETE' });
        await countSignatures();
    } catch (e) {
        self.postMessage({ type: 'DB_UPDATE_COMPLETE' });
    }
}

const performDeepScan = async (files: Array<{ path: string, hash: string }>) => {
    if (!files || files.length === 0) return;
    
    const cache = await caches.open(CACHE_NAME);
    const buckets: Record<string, typeof files> = {};
    
    for(const f of files) {
        if (!f.hash) continue;
        const char = f.hash.substring(0, 1).toLowerCase();
        if(!buckets[char]) buckets[char] = [];
        buckets[char].push(f);
    }

    for (const b of BUCKETS) {
        const bucketFiles = buckets[b];
        if (!bucketFiles) continue;

        for (const file of bucketFiles) {
             if (KNOWN_THREATS.has(file.hash)) {
                 deepScanThreats.push({ path: file.path, hash: file.hash, threat: KNOWN_THREATS.get(file.hash), source: 'embedded' });
             }
        }

        const response = await cache.match(`${SHARD_URL_BASE}${b}.json`);
        if (response) {
            try {
                const shardData: {h: string, n?: string}[] = await response.json(); 
                const threatMap = new Map<string, string>(shardData.map(item => [item.h, item.n || "Unknown Threat"]));

                for (const file of bucketFiles) {
                    if (threatMap.has(file.hash)) {
                        if (!deepScanThreats.some(t => t.path === file.path)) {
                            deepScanThreats.push({ path: file.path, hash: file.hash, threat: threatMap.get(file.hash), source: 'database' });
                        }
                    }
                }
            } catch(e) {}
        }
    }
    
    const lastFile = files[files.length - 1];
    if (lastFile) {
        self.postMessage({ type: 'SCAN_PROGRESS', payload: { currentFile: lastFile.path } });
    }
};

const performRapidScan = async (apps: Array<{ name: string, packageName: string }>) => {
    const threats: { name: string, packageName: string }[] = [];
    apps.forEach(app => {
        if (KNOWN_BAD_PACKAGES.includes(app.packageName.toLowerCase())) {
            threats.push({ name: app.name, packageName: app.packageName });
        }
    });
    self.postMessage({ type: 'RAPID_SCAN_COMPLETE', payload: { threats } });
};

export {};