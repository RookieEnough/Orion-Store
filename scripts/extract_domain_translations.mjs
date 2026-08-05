import fs from 'node:fs';
import path from 'node:path';
import ts from 'typescript';

const root = path.resolve(import.meta.dirname, '..');
const reset = process.argv.includes('--reset');

const slug = (value) => value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  .replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '').slice(0, 72) || 'text';

function writeCatalog(name, source) {
  const sorted = Object.fromEntries(Object.entries(source).sort(([a], [b]) => a.localeCompare(b)));
  const englishPath = path.join(root, 'locales/en', `${name}.json`);
  const portuguesePath = path.join(root, 'locales/pt-BR', `${name}.json`);
  let current = {};
  if (!reset) {
    try { current = JSON.parse(fs.readFileSync(portuguesePath, 'utf8')); } catch {}
  }
  const translated = Object.fromEntries(
    Object.entries(sorted).map(([key, value]) => [key, current[key] ?? value]),
  );
  fs.mkdirSync(path.dirname(englishPath), { recursive: true });
  fs.mkdirSync(path.dirname(portuguesePath), { recursive: true });
  fs.writeFileSync(englishPath, `${JSON.stringify(sorted, null, 2)}\n`);
  fs.writeFileSync(portuguesePath, `${JSON.stringify(translated, null, 2)}\n`);
  return Object.keys(sorted).length;
}

function property(object, name) {
  return object.properties.find((item) => ts.isPropertyAssignment(item) && item.name.getText().replace(/["']/g, '') === name)?.initializer;
}

function stringValue(node) {
  return node && (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) ? node.text : null;
}

function extractAppContent() {
  const filename = path.join(root, 'localData.ts');
  const sourceFile = ts.createSourceFile(filename, fs.readFileSync(filename, 'utf8'), ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  let apps = null;
  sourceFile.forEachChild((node) => {
    if (!ts.isVariableStatement(node)) return;
    for (const declaration of node.declarationList.declarations) {
      if (declaration.name.getText() === 'localAppsData' && declaration.initializer && ts.isArrayLiteralExpression(declaration.initializer)) {
        apps = declaration.initializer;
      }
    }
  });
  if (!apps) throw new Error('Could not find localAppsData in localData.ts');

  const catalog = {};
  const sharedFields = new Set(['version', 'latestVersion', 'category', 'platform', 'size']);
  for (const element of apps.elements) {
    if (!ts.isObjectLiteralExpression(element)) continue;
    const id = stringValue(property(element, 'id'));
    if (!id) continue;
    const description = stringValue(property(element, 'description'));
    if (description) catalog[`apps.${id}.description`] = description;
    for (const field of sharedFields) {
      const value = stringValue(property(element, field));
      if (value) catalog[`shared.${field}.${slug(value)}`] = value;
    }
    const patches = property(element, 'patches');
    if (patches && ts.isArrayLiteralExpression(patches)) {
      for (const patch of patches.elements) {
        const value = stringValue(patch);
        if (value) catalog[`patches.${slug(value)}`] = value;
      }
    }
  }
  return catalog;
}

const dino = {
  'game.document_title': 'Dino',
  'runner.document_title': 'Chrome easter egg: T-Rex runner',
  'runner.start_instruction': 'Press Space to start',
};

const redirect = {
  'document.title': 'Open in Orion Store',
  'accessibility.skip_to_main': 'Skip to Main Content',
  'header.subtitle': 'Android Redirect',
  'opening.label': 'Opening In Orion Store',
  'opening.message': 'If Orion Store is installed, this handoff will complete automatically.',
  'opening.status': 'Waiting for Orion Store…',
  'fallback.label': 'Unable To Open In Orion Store',
  'fallback.title': 'Orion Store Is Not Available',
  'fallback.message': 'Install the latest Orion Store build on this device, then try the handoff again.',
  'fallback.retry': 'Try Again',
  'footer.deep_link': 'Deep Link',
  'app.selected': 'selected app',
};

const android = {
  'app.name': 'Orion Store',
  'download.channel_name': 'Download Progress',
  'download.background_channel': 'Background Downloads',
  'download.background_description': 'Keeps Orion Store downloads running while the app is in the background.',
  'download.single_in_progress': 'Downloading…',
  'download.multiple_in_progress': '{{count}} downloads in progress',
  'download.background_progress': '{{progress}}% — Orion Store will keep going in the background',
  'download.background_status': 'Orion Store will keep going in the background',
  'download.file': 'Downloading {{fileName}}',
};

const workers = {
  'api.worker_misconfigured': 'Worker misconfigured.',
  'api.rate_limit': 'Rate limit: You can only submit once every 24 hours.',
  'api.signature_mismatch': 'Security signature mismatch.',
  'api.stats_limit': 'Stats exceed logical limits. Submission rejected.',
  'api.minimum_contributions': 'Minimum 5 contributions required to join the leaderboard.',
  'sentinel.unknown_threat': 'Unknown Threat',
  'core.unknown_app': 'Unknown App',
  'core.unknown': 'Unknown',
  'core.latest': 'Latest',
  'core.varies': 'Varies',
  'discovery.browse_title': 'Browse by Category',
  'discovery.browse_subtitle': 'Swipe the cards, then tap one to show only matching apps',
  'discovery.spotlight_title': 'Curated Spotlight',
  'discovery.spotlight_subtitle': 'A quick 6-app mix you should not miss',
  'discovery.bundles_title': 'Recommended App Bundles',
  'discovery.bundles_subtitle': 'Install in packs for faster setup',
  'discovery.daily_driver': 'Daily Driver Kit',
  'discovery.daily_driver_description': 'Core apps for your everyday workflow',
  'discovery.media_escape': 'Media Escape',
  'discovery.media_escape_description': 'Watch, listen, and unwind quickly',
  'discovery.power_user': 'Power User Stack',
  'discovery.power_user_description': 'Advanced tools for people who build and tweak',
  'discovery.recommended_apps': 'Recommended apps',
  'discovery.hand_picked': 'Hand-picked apps that work great together',
  'discovery.featured_today': 'Featured Today',
  'discovery.recommended_for_you': 'Recommended For You',
  'discovery.new_updated': 'New & Updated',
  'badge.starter': 'Starter',
  'badge.popular': 'Popular',
  'badge.pro': 'Pro',
  'badge.hot': 'Hot',
  'badge.fresh': 'Fresh',
  'badge.classic': 'Classic',
};

const catalogs = {
  'app-content': extractAppContent(),
  android,
  dino,
  redirect,
  workers,
};

let total = 0;
for (const [name, catalog] of Object.entries(catalogs)) {
  const count = writeCatalog(name, catalog);
  total += count;
  console.log(`${name}: ${count} strings`);
}
console.log(`Extracted ${total} domain-specific strings.`);
