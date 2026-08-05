import { spawnSync } from 'node:child_process';
import path from 'node:path';

const scripts = ['extract_translations.mjs', 'extract_domain_translations.mjs', 'scaffold_locales.mjs'];
const forwardedArguments = process.argv.slice(2);

for (const script of scripts) {
  const result = spawnSync(process.execPath, [path.join(import.meta.dirname, script), ...forwardedArguments], {
    stdio: 'inherit',
  });
  if (result.status !== 0) process.exit(result.status ?? 1);
}
