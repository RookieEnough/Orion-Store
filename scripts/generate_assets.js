import fs from 'fs';
import { execSync } from 'child_process';
import path from 'path';
import sharp from 'sharp';

const ASSETS_DIR = 'assets';
const PUBLIC_ICON = 'public/icon.png';
const ANDROID_DIR = 'android';
const DIST_DIR = 'dist';

console.log('-----------------------------------');
console.log('🎨 SIMPLE MODE: Your PNG = The Icon');
console.log('-----------------------------------');

// Build check
if (!fs.existsSync(DIST_DIR)) {
    console.log('[+] Building...');
    execSync('npm run build', { stdio: 'inherit' });
}

// Create assets folder
if (!fs.existsSync(ASSETS_DIR)) {
    fs.mkdirSync(ASSETS_DIR);
}

// Check icon
if (!fs.existsSync(PUBLIC_ICON)) {
    console.error(`❌ '${PUBLIC_ICON}' not found!`);
    process.exit(1);
}

async function generateIcons() {
    console.log('[+] Generating icons from your PNG...');
    
    const iconBuffer = fs.readFileSync(PUBLIC_ICON);
    
    // Main icon - just resize to 1024x1024, cover the whole thing
    await sharp(iconBuffer)
        .resize(1024, 1024, {
            fit: 'cover' // COVER = fills entire space, crops if needed
        })
        .png()
        .toFile(path.join(ASSETS_DIR, 'icon.png'));
    
    console.log('    ✅ Generated icon.png');
    
    // For Android adaptive - use the same image for both layers
    // This disables the adaptive icon system essentially
    await sharp(iconBuffer)
        .resize(432, 432, { fit: 'cover' })
        .png()
        .toFile(path.join(ASSETS_DIR, 'icon-foreground.png'));
    
    await sharp(iconBuffer)
        .resize(432, 432, { fit: 'cover' })
        .png()
        .toFile(path.join(ASSETS_DIR, 'icon-background.png'));
    
    console.log('    ✅ Generated adaptive icons');
}

// Check Android
if (!fs.existsSync(ANDROID_DIR)) {
    execSync('npx cap add android', { stdio: 'inherit' });
}

(async () => {
    try {
        await generateIcons();
        
        console.log('[+] Running Capacitor...');
        execSync('npx @capacitor/assets generate --android', { stdio: 'inherit' });
        execSync('npx cap sync android', { stdio: 'inherit' });

        console.log('-----------------------------------');
        console.log('✅ DONE! Your PNG is the icon.');
        console.log('👉 npx cap open android');
        console.log('-----------------------------------');
        
    } catch (error) {
        console.error('❌', error.message);
        process.exit(1);
    }
})();