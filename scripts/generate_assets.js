import fs from 'fs';
import { execSync } from 'child_process';
import path from 'path';

const ASSETS_DIR = 'assets';
const PUBLIC_ICON = 'public/icon.png';
const ASSETS_ICON = 'assets/icon.png';
const ANDROID_DIR = 'android';
const DIST_DIR = 'dist';

console.log('-----------------------------------');
console.log('🎨 Orion Store Asset Generator');
console.log('-----------------------------------');

// 1. Ensure dist folder exists (Required for cap add android)
if (!fs.existsSync(DIST_DIR)) {
    console.log('[+] "dist" folder missing. Running build...');
    try {
        execSync('npm run build', { stdio: 'inherit' });
    } catch (e) {
        console.error('❌ Build failed. Cannot proceed.');
        process.exit(1);
    }
}

// 2. Create assets folder if missing
if (!fs.existsSync(ASSETS_DIR)) {
    console.log(`[+] Creating '${ASSETS_DIR}' directory...`);
    fs.mkdirSync(ASSETS_DIR);
}

// 3. Force Copy icon from public (Overwrite existing)
if (fs.existsSync(PUBLIC_ICON)) {
    console.log(`[+] Copying icon from '${PUBLIC_ICON}' to '${ASSETS_ICON}'...`);
    fs.copyFileSync(PUBLIC_ICON, ASSETS_ICON);
} else {
    console.error(`[!] ERROR: Could not find '${PUBLIC_ICON}'. Please place your 1024x1024 icon there.`);
    process.exit(1);
}

// 4. Check for Android Platform and Add if missing
if (!fs.existsSync(ANDROID_DIR)) {
    console.log('[!] Android platform folder not found. Adding it now...');
    try {
        execSync('npx cap add android', { stdio: 'inherit' });
    } catch (e) {
        console.error('❌ Failed to add Android platform. Ensure you have @capacitor/android installed.');
        console.error(e.message);
        process.exit(1);
    }
}

// 5. Run Capacitor Assets & Sync
console.log('[+] Running @capacitor/assets generate...');
try {
    // Generate icons/splash
    // CHANGED: Using #ffffff (White) instead of black to prevent ugly borders.
    execSync('npx @capacitor/assets generate --android --iconBackgroundColor "#ffffff"', { stdio: 'inherit' });
    
    console.log('[+] Syncing changes to Android project...');
    execSync('npx cap sync android', { stdio: 'inherit' });

    // --- PATCH 1: Fix "flatDir" issues in Android Studio ---
    console.log('[+] Patching Android build files for plugin compatibility...');
    const buildGradlePath = path.join('android', 'build.gradle');
    if (fs.existsSync(buildGradlePath)) {
        let content = fs.readFileSync(buildGradlePath, 'utf8');
        // Check if flatDir is already present in allprojects/repositories
        if (!content.includes('flatDir {')) {
            // Find the allprojects { repositories { ... } } block and inject flatDir
            // We look for 'mavenCentral()' as an anchor point common in Capacitor templates
            if (content.includes('mavenCentral()')) {
                const patch = `
        mavenCentral()
        flatDir {
            dirs 'libs'
        }
`;
                content = content.replace('mavenCentral()', patch.trim());
                fs.writeFileSync(buildGradlePath, content);
                console.log('    ✅ Injected flatDir support into android/build.gradle');
            } else {
                console.warn('    ⚠️ Could not find injection point in build.gradle. Skipping patch.');
            }
        }
    }

    // --- PATCH 2: Force Fix App Name in strings.xml ---
    console.log('[+] Forcing App Name update in strings.xml...');
    const stringsXmlPath = path.join('android', 'app', 'src', 'main', 'res', 'values', 'strings.xml');
    if (fs.existsSync(stringsXmlPath)) {
        let stringsContent = fs.readFileSync(stringsXmlPath, 'utf8');
        const desiredName = "Orion Store";
        
        // Regex to replace <string name="app_name">Whatever</string>
        const appNameRegex = /<string name="app_name">.*?<\/string>/g;
        const titleRegex = /<string name="title_activity_main">.*?<\/string>/g;
        
        let updatedContent = stringsContent;
        updatedContent = updatedContent.replace(appNameRegex, `<string name="app_name">${desiredName}</string>`);
        updatedContent = updatedContent.replace(titleRegex, `<string name="title_activity_main">${desiredName}</string>`);
        
        if (updatedContent !== stringsContent) {
            fs.writeFileSync(stringsXmlPath, updatedContent);
            console.log(`    ✅ Updated Android App Name to "${desiredName}"`);
        } else {
            console.log('    ℹ️  App Name is already correct.');
        }
    } else {
        console.warn('    ⚠️ Could not find strings.xml to patch name.');
    }

    console.log('-----------------------------------');
    console.log('✅ Assets generated, Synced & Patched!');
    console.log('👉 Now open Android Studio: npx cap open android');
    console.log('👉 In Android Studio: Build > Clean Project, then Run.');
    console.log('-----------------------------------');
} catch (error) {
    console.error('❌ Error generating assets.');
    console.error(error.message);
    process.exit(1);
}
