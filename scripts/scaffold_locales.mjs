import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const localesRoot = path.join(root, 'locales');
const sourceRoot = path.join(localesRoot, 'en');
const catalogFiles = fs.readdirSync(sourceRoot).filter((name) => name.endsWith('.json')).sort();

export const supportedLocaleTemplates = [
  { code: 'ar', name: 'Arabic', direction: 'rtl' },
  { code: 'bn', name: 'Bengali', direction: 'ltr' },
  { code: 'cs', name: 'Czech', direction: 'ltr' },
  { code: 'da', name: 'Danish', direction: 'ltr' },
  { code: 'de', name: 'German', direction: 'ltr' },
  { code: 'el', name: 'Greek', direction: 'ltr' },
  { code: 'es', name: 'Spanish', direction: 'ltr' },
  { code: 'fil', name: 'Filipino', direction: 'ltr' },
  { code: 'fi', name: 'Finnish', direction: 'ltr' },
  { code: 'fr', name: 'French', direction: 'ltr' },
  { code: 'he', name: 'Hebrew', direction: 'rtl' },
  { code: 'hi', name: 'Hindi', direction: 'ltr' },
  { code: 'hu', name: 'Hungarian', direction: 'ltr' },
  { code: 'id', name: 'Indonesian', direction: 'ltr' },
  { code: 'it', name: 'Italian', direction: 'ltr' },
  { code: 'ja', name: 'Japanese', direction: 'ltr' },
  { code: 'ko', name: 'Korean', direction: 'ltr' },
  { code: 'ms', name: 'Malay', direction: 'ltr' },
  { code: 'nl', name: 'Dutch', direction: 'ltr' },
  { code: 'no', name: 'Norwegian', direction: 'ltr' },
  { code: 'pl', name: 'Polish', direction: 'ltr' },
  { code: 'pt-BR', name: 'Portuguese (Brazil)', direction: 'ltr' },
  { code: 'ro', name: 'Romanian', direction: 'ltr' },
  { code: 'ru', name: 'Russian', direction: 'ltr' },
  { code: 'sv', name: 'Swedish', direction: 'ltr' },
  { code: 'th', name: 'Thai', direction: 'ltr' },
  { code: 'tr', name: 'Turkish', direction: 'ltr' },
  { code: 'uk', name: 'Ukrainian', direction: 'ltr' },
  { code: 'vi', name: 'Vietnamese', direction: 'ltr' },
  { code: 'zh-CN', name: 'Chinese (Simplified)', direction: 'ltr' },
  { code: 'zh-TW', name: 'Chinese (Traditional)', direction: 'ltr' },
];

for (const locale of supportedLocaleTemplates) {
  const localeRoot = path.join(localesRoot, locale.code);
  fs.mkdirSync(localeRoot, { recursive: true });
  for (const catalogFile of catalogFiles) {
    const source = JSON.parse(fs.readFileSync(path.join(sourceRoot, catalogFile), 'utf8'));
    const destination = path.join(localeRoot, catalogFile);
    let existing = {};
    try { existing = JSON.parse(fs.readFileSync(destination, 'utf8')); } catch {}
    const merged = Object.fromEntries(
      Object.entries(source).map(([key, value]) => [key, existing[key] ?? value]),
    );
    fs.writeFileSync(destination, `${JSON.stringify(merged, null, 2)}\n`);
  }
}

const manifest = [
  { code: 'en', name: 'English', direction: 'ltr', status: 'source' },
  ...supportedLocaleTemplates.map((locale) => ({
    ...locale,
    status: locale.code === 'pt-BR' ? 'in-progress' : 'template',
  })),
];
fs.writeFileSync(path.join(localesRoot, 'locales.json'), `${JSON.stringify(manifest, null, 2)}\n`);
console.log(`Prepared ${manifest.length} locale folders with ${catalogFiles.length} catalogs each.`);
