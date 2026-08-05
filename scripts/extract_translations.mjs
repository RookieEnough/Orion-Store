import fs from 'node:fs';
import path from 'node:path';
import ts from 'typescript';

const root = path.resolve(import.meta.dirname, '..');
const sourceFiles = [
  'App.tsx',
  'constants.ts',
  'utils/discovery.ts',
  ...fs.readdirSync(path.join(root, 'components'))
    .filter((name) => name.endsWith('.tsx'))
    .map((name) => `components/${name}`),
  ...fs.readdirSync(path.join(root, 'hooks'))
    .filter((name) => name.endsWith('.ts') || name.endsWith('.tsx'))
    .map((name) => `hooks/${name}`),
].sort();

const translatableAttributes = new Set([
  'alt', 'aria-label', 'badge', 'desc', 'eyebrow', 'label', 'meta', 'placeholder', 'title',
]);
const translatableProperties = new Set([
  'actionLabel', 'cancelText', 'confirmText', 'desc', 'description', 'emptyMessage', 'eyebrow',
  'errorMessage', 'helperText', 'label', 'message', 'placeholder', 'subtitle',
  'successMessage', 'text', 'title',
]);
const translatableCalls = /(?:alert|confirm|notify|notification|showError|showNotification|showToast|toast)$/i;
const ignoredValues = new Set(['Orion Store', 'GitHub', 'Android', 'APK', 'SHA-256', 'VirusTotal']);

function normalize(value) {
  return value.replace(/\s+/g, ' ').trim();
}

function templateText(node) {
  if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) return node.text;
  if (!ts.isTemplateExpression(node)) return null;
  let value = node.head.text;
  for (const span of node.templateSpans) {
    const expression = span.expression.getText().replace(/[^a-zA-Z0-9_.]/g, '') || 'value';
    value += `{{${expression}}}${span.literal.text}`;
  }
  return value;
}

function isUserFacing(value) {
  const text = normalize(value);
  if (!text || ignoredValues.has(text) || !/[A-Za-zÀ-ÿ]/.test(text)) return false;
  if (/^(?:https?:|[.#/@]|fa-|text-|bg-|border-|grid|flex|application\/|[a-z]+_[a-z_]+$)/.test(text)) return false;
  return true;
}

function componentName(relativeFile) {
  return path.basename(relativeFile).replace(/\.(?:ts|tsx)$/, '')
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase();
}

function slug(value) {
  return value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .replace(/\{\{([^}]+)}}/g, '$1')
    .replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '').slice(0, 64) || 'text';
}

const entries = new Map();
const usedKeys = new Map();

function add(relativeFile, value) {
  value = normalize(value);
  if (!isUserFacing(value)) return;
  const namespace = componentName(relativeFile);
  const base = `${namespace}.${slug(value)}`;
  let key = base;
  let suffix = 2;
  while (usedKeys.has(key) && usedKeys.get(key) !== value) key = `${base}_${suffix++}`;
  usedKeys.set(key, value);
  entries.set(key, value);
}

for (const relativeFile of sourceFiles) {
  const filename = path.join(root, relativeFile);
  const source = ts.createSourceFile(
    filename,
    fs.readFileSync(filename, 'utf8'),
    ts.ScriptTarget.Latest,
    true,
    relativeFile.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS,
  );

  function collectExpression(node) {
    const text = templateText(node);
    if (text !== null) {
      add(relativeFile, text);
      return;
    }
    if (ts.isConditionalExpression(node)) {
      collectExpression(node.whenTrue);
      collectExpression(node.whenFalse);
    } else if (ts.isBinaryExpression(node) && node.operatorToken.kind === ts.SyntaxKind.PlusToken) {
      collectExpression(node.left);
      collectExpression(node.right);
    } else if (ts.isParenthesizedExpression(node)) {
      collectExpression(node.expression);
    } else if (ts.isArrayLiteralExpression(node)) {
      node.elements.forEach(collectExpression);
    }
  }

  function visit(node) {
    if (ts.isJsxText(node)) add(relativeFile, node.text);

    if (ts.isJsxAttribute(node) && translatableAttributes.has(node.name.getText(source))) {
      if (node.initializer && ts.isStringLiteral(node.initializer)) add(relativeFile, node.initializer.text);
      if (node.initializer && ts.isJsxExpression(node.initializer) && node.initializer.expression) {
        collectExpression(node.initializer.expression);
      }
    }

    if (
      ts.isJsxExpression(node)
      && node.expression
      && (ts.isJsxElement(node.parent) || ts.isJsxFragment(node.parent))
    ) collectExpression(node.expression);

    if (ts.isPropertyAssignment(node)) {
      const name = node.name.getText(source).replace(/^['"]|['"]$/g, '');
      if (translatableProperties.has(name)) {
        const text = templateText(node.initializer);
        if (text !== null) add(relativeFile, text);
      }
    }

    if (ts.isCallExpression(node) && translatableCalls.test(node.expression.getText(source))) {
      for (const argument of node.arguments) {
        const text = templateText(argument);
        if (text !== null) add(relativeFile, text);
      }
    }

    ts.forEachChild(node, visit);
  }
  visit(source);
}

const sorted = Object.fromEntries([...entries].sort(([left], [right]) => left.localeCompare(right)));
const englishPath = path.join(root, 'locales/en/common.json');
const portuguesePath = path.join(root, 'locales/pt-BR/common.json');
const initialPortuguese = new Map([
  ['Welcome to Orion Store', 'Bem-vindo ao Orion Store'],
  ['Download', 'Baixar'],
  ['Install', 'Instalar'],
  ['Settings', 'Configurações'],
  ['Language', 'Idioma'],
  ['Choose the interface language', 'Escolha o idioma da interface'],
  ['Language and region', 'Idioma e região'],
  ['Interface language', 'Idioma da interface'],
  ['System default', 'Padrão do sistema'],
  ['Language changes are applied immediately and saved for the next launch.', 'As alterações de idioma são aplicadas imediatamente e salvas para a próxima inicialização.'],
]);
let existingPortuguese = {};
if (!process.argv.includes('--reset')) {
  try {
    existingPortuguese = JSON.parse(fs.readFileSync(portuguesePath, 'utf8'));
  } catch {}
}

const portuguese = Object.fromEntries(
  Object.entries(sorted).map(([key, source]) => [
    key,
    existingPortuguese[key] && existingPortuguese[key] !== source
      ? existingPortuguese[key]
      : initialPortuguese.get(source) ?? source,
  ]),
);

fs.mkdirSync(path.dirname(englishPath), { recursive: true });
fs.writeFileSync(englishPath, `${JSON.stringify(sorted, null, 2)}\n`);
fs.writeFileSync(portuguesePath, `${JSON.stringify(portuguese, null, 2)}\n`);
console.log(`Extracted ${entries.size} translatable strings from ${sourceFiles.length} source files.`);
