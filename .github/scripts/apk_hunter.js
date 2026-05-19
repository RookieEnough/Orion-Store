
/*
 * ORION DATA - DEEP DIVE APK HUNTER (V17.0 - CLEAN EDITION)
 * -------------------------------------------------------
 * Strategies for: APKDone, AN1, Direct
 */

const fs = require('fs');
const path = require('path');
const https = require('https');

// Robust Import Check
let puppeteer, StealthPlugin;
try {
    puppeteer = require('puppeteer-extra');
    StealthPlugin = require('puppeteer-extra-plugin-stealth');
    puppeteer.use(StealthPlugin());
    console.log("✅ Dependencies loaded successfully");
} catch (e) {
    console.error("❌ CRITICAL ERROR: Missing Dependencies");
    console.error("Please run: npm install puppeteer puppeteer-extra puppeteer-extra-plugin-stealth");
    console.error("Error Details:", e.message);
    process.exit(1);
}

// --- UTILS ---
const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));
const randomRange = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;

// --- FINGERPRINT INJECTION ---
async function injectTrustProfile(page) {
    await page.evaluateOnNewDocument(() => {
        const getImageData = CanvasRenderingContext2D.prototype.getImageData;
        CanvasRenderingContext2D.prototype.getImageData = function(x, y, w, h) {
            return getImageData.call(this, x, y, w, h);
        };
        Object.defineProperty(navigator, 'plugins', {
            get: () => [
                { name: 'Chrome PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
                { name: 'PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' }
            ]
        });
        Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
    });
}

// --- CLOUDFLARE SOLVER ---
const solveCloudflare = async (page) => {
    console.log('   🛡️  Checking Cloudflare status...');
    await delay(2000); 

    let attempt = 0;
    const maxAttempts = 10;

    while (attempt < maxAttempts) {
        const title = await page.title();
        const content = (await page.content()).toLowerCase();
        const isBlocked = title.includes('Just a moment') || content.includes('challenge-platform');
        
        if (!isBlocked) {
            console.log('   ✅ Cloudflare cleared!');
            return;
        }

        console.log(`   ⏳ Waiting for Cloudflare (Attempt ${attempt+1})...`);
        await page.mouse.move(randomRange(100, 700), randomRange(100, 500), { steps: 25 });
        
        // Try Shadow DOM clicks
        const shadowHandle = await page.evaluateHandle(() => {
            const targets = ['#challenge-stage', '.ctp-checkbox-label', 'input[name="cf-turnstile-response"]'];
            for (const t of targets) {
                const el = document.querySelector(t);
                if (el) return el;
            }
            return null;
        });
        if (shadowHandle.asElement()) await shadowHandle.click();

        await delay(4000);
        attempt++;
    }
};

// --- AN1 STRATEGY (THE SHORTCUT) ---
async function runAn1Strategy(page, url) {
    console.log(`🧠 Strategy: AN1.com Shortcut`);
    const idMatch = url.match(/an1\.com\/(\d+)-/);
    if (!idMatch || !idMatch[1]) throw new Error("Could not extract App ID from AN1 URL");
    const appId = idMatch[1];
    
    const downloadPageUrl = `https://an1.com/file_${appId}-dw.html`;
    console.log(`   📍 Jumping to download page: ${downloadPageUrl}`);

    await page.goto(downloadPageUrl, { waitUntil: 'domcontentloaded' });
    await solveCloudflare(page);

    console.log('   ⏳ Waiting for download button...');
    try {
        await page.waitForFunction(() => {
            const btns = Array.from(document.querySelectorAll('a'));
            return btns.some(b => b.innerText.toUpperCase().includes('DOWNLOAD') && b.offsetHeight > 0);
        }, { timeout: 15000 });
        await delay(2000);
    } catch (e) {
        console.log('   ⚠️ Wait timed out, trying to find button anyway...');
    }

    const downloadLink = await page.evaluateHandle(() => {
        const classBtn = document.querySelector('a.btn-lg.btn-green');
        if (classBtn) return classBtn;
        const allLinks = Array.from(document.querySelectorAll('a'));
        return allLinks.find(a => {
            const t = a.innerText.toUpperCase();
            return t.includes('DOWNLOAD') && !t.includes('PC') && !t.includes('TELEGRAM');
        });
    });

    if (downloadLink.asElement()) {
        const btnText = await page.evaluate(el => el.innerText, downloadLink);
        console.log(`   🚀 Found Button: "${btnText}" - CLICKING...`);
        await page.evaluate(el => el.click(), downloadLink);
        await delay(30000); 
    } else {
        throw new Error("Could not find the green download button on AN1.");
    }
}

// --- APKDONE STRATEGY (THE TOURIST) ---
async function runApkDoneStrategy(page, appSlug) {
    let searchName = appSlug.replace(/-/g, ' ').replace(/\b(mod|apk|premium|pro|unlocked)\b/gi, '').trim();
    if (searchName.length < 3) searchName = appSlug.replace(/-/g, ' ');

    console.log(`🧠 The Tourist: Searching APKDone for "${searchName}"...`);
    await page.goto('https://apkdone.com/', { waitUntil: 'domcontentloaded' });
    await solveCloudflare(page);
    await delay(randomRange(2000, 4000));

    const toggle = await page.$('.search-toggle, .fa-search');
    if (toggle) await toggle.click();
    await delay(500);
    await page.type('input[name="s"]', searchName, { delay: 100 });
    await page.keyboard.press('Enter');
    await page.waitForNavigation().catch(()=>null);
    await solveCloudflare(page);

    const appLink = await page.evaluateHandle((targetName) => {
        const links = Array.from(document.querySelectorAll('article a'));
        return links.find(l => l.innerText.toLowerCase().includes(targetName.toLowerCase())) || links[0];
    }, searchName);

    if (!appLink.asElement()) throw new Error("No results found");
    await Promise.all([page.waitForNavigation().catch(()=>null), appLink.click()]);
    
    const dlBtn = await page.$('a[href$="/download"]');
    if (dlBtn) await Promise.all([page.waitForNavigation().catch(()=>null), dlBtn.click()]);
    
    await delay(3000);
    const finalBtn = await page.evaluateHandle(() => Array.from(document.querySelectorAll('a')).find(a => a.innerText.includes('APK')));
    if (finalBtn.asElement()) await finalBtn.click();
    await delay(20000);
}

// --- MAIN ---
(async () => {
    const args = process.argv.slice(2);
    const getArg = (key) => args.indexOf('--' + key) !== -1 ? args[args.indexOf('--' + key) + 1] : null;
    const TARGET_URL = getArg('url');
    const OUTPUT_FILE = getArg('out') || 'output.apk';
    const ID = getArg('id') || 'unknown';
    const WAIT_TIME = parseInt(getArg('wait')) || 30000;

    if (!TARGET_URL) { console.error('❌ No URL'); process.exit(1); }
    
    let slug = ID;
    try {
        const urlObj = new URL(TARGET_URL);
        const pathParts = urlObj.pathname.split('/').filter(p => p && p !== 'download');
        if (pathParts.length > 0) slug = pathParts[0];
    } catch (e) {}

    const PROFILE_PATH = path.join(process.cwd(), 'chrome_profile');
    if (!fs.existsSync(PROFILE_PATH)) fs.mkdirSync(PROFILE_PATH);

    const browser = await puppeteer.launch({
        headless: "new",
        userDataDir: PROFILE_PATH,
        args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1920,1080']
    });

    let foundApkUrl = null;

    try {
        const page = await browser.newPage();
        await injectTrustProfile(page);

        await page.setRequestInterception(true);
        page.on('request', req => {
            if (['font', 'image', 'stylesheet'].includes(req.resourceType())) req.abort();
            else req.continue();
        });

        page.on('response', async (response) => {
            const url = response.url();
            if (url.endsWith('.apk') || response.headers()['content-type']?.includes('android.package-archive')) {
                console.log(`\n🎣 CAUGHT APK URL: ${url}`);
                foundApkUrl = url;
            }
        });

        // ROUTING STRATEGY
        if (TARGET_URL.includes('an1.com')) {
            await runAn1Strategy(page, TARGET_URL);
        } else if (TARGET_URL.includes('apkdone.com')) {
            await runApkDoneStrategy(page, slug);
        } else {
            console.log("🧠 Strategy: Direct / Generic");
            await page.goto(TARGET_URL);
        }

        if (foundApkUrl) {
            console.log('\n✅ Downloading to file...');
            await downloadFile(foundApkUrl, OUTPUT_FILE);
        } else {
            throw new Error("No APK URL intercepted.");
        }

    } catch (err) {
        console.error(`\n🔥 ERROR: ${err.message}`);
        process.exit(1);
    } finally {
        await browser.close();
    }
})();

function downloadFile(url, dest) {
    return new Promise((resolve, reject) => {
        const file = fs.createWriteStream(dest);
        https.get(url, (res) => {
            if (res.statusCode > 300 && res.statusCode < 400 && res.headers.location) {
                return downloadFile(res.headers.location, dest).then(resolve).catch(reject);
            }
            res.pipe(file);
            file.on('finish', () => file.close(resolve));
        }).on('error', (e) => { fs.unlink(dest, ()=>{}); reject(e); });
    });
}
