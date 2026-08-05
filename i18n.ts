import englishCatalog from './locales/en/common.json';
import portugueseCatalog from './locales/pt-BR/common.json';
import englishAppContent from './locales/en/app-content.json';
import portugueseAppContent from './locales/pt-BR/app-content.json';
import englishAndroid from './locales/en/android.json';
import portugueseAndroid from './locales/pt-BR/android.json';
import englishDino from './locales/en/dino.json';
import portugueseDino from './locales/pt-BR/dino.json';
import englishRedirect from './locales/en/redirect.json';
import portugueseRedirect from './locales/pt-BR/redirect.json';
import englishWorkers from './locales/en/workers.json';
import portugueseWorkers from './locales/pt-BR/workers.json';

export type LanguagePreference = 'system' | 'en' | 'pt-BR';
export type ResolvedLanguage = 'en' | 'pt-BR';

const STORAGE_KEY = 'orion-language';
const translatableAttributes = ['alt', 'aria-label', 'placeholder', 'title'] as const;
const listeners = new Set<() => void>();
const textState = new WeakMap<Text, { source: string; applied: string }>();
const attributeState = new WeakMap<Element, Map<string, { source: string; applied: string }>>();
let observer: MutationObserver | null = null;
let applying = false;

const english: Record<string, string> = {
  ...englishCatalog,
  ...englishAppContent,
  ...englishAndroid,
  ...englishDino,
  ...englishRedirect,
  ...englishWorkers,
};
const portuguese: Record<string, string> = {
  ...portugueseCatalog,
  ...portugueseAppContent,
  ...portugueseAndroid,
  ...portugueseDino,
  ...portugueseRedirect,
  ...portugueseWorkers,
};
const directPortuguese = new Map<string, string>();

for (const [key, source] of Object.entries(english)) {
  const translated = portuguese[key];
  if (translated && translated !== source) directPortuguese.set(source, translated);
}

const escapeRegex = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const templates = Object.entries(english).flatMap(([key, source]) => {
  if (!source.includes('{{')) return [];
  const translated = portuguese[key];
  if (!translated || translated === source) return [];
  const names: string[] = [];
  const pattern = source.split(/(\{\{[^}]+}})/g).map((part) => {
    const match = part.match(/^\{\{([^}]+)}}$/);
    if (!match) return escapeRegex(part);
    names.push(match[1]!);
    return '(.+?)';
  }).join('');
  return [{ regex: new RegExp(`^${pattern}$`), names, translated }];
});

export function getLanguagePreference(): LanguagePreference {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    if (value === 'en' || value === 'pt-BR' || value === 'system') return value;
  } catch {}
  return 'system';
}

export function resolveLanguage(preference = getLanguagePreference()): ResolvedLanguage {
  if (preference !== 'system') return preference;
  return typeof navigator !== 'undefined' && navigator.language.toLowerCase().startsWith('pt') ? 'pt-BR' : 'en';
}

export function translate(source: string, language = resolveLanguage()): string {
  if (language === 'en') return source;
  const direct = directPortuguese.get(source);
  if (direct) return direct;
  for (const template of templates) {
    const match = source.match(template.regex);
    if (!match) continue;
    const values = new Map(template.names.map((name, index) => [name, match[index + 1] ?? '']));
    return template.translated.replace(/\{\{([^}]+)}}/g, (_, name: string) => values.get(name) ?? '');
  }
  return source;
}

function translateTextNode(node: Text, language: ResolvedLanguage) {
  const current = node.data;
  const previous = textState.get(node);
  const source = previous && current === previous.applied ? previous.source : current;
  const leading = source.match(/^\s*/)?.[0] ?? '';
  const trailing = source.match(/\s*$/)?.[0] ?? '';
  const core = source.slice(leading.length, source.length - trailing.length || undefined);
  const applied = core ? `${leading}${translate(core, language)}${trailing}` : source;
  textState.set(node, { source, applied });
  if (current !== applied) node.data = applied;
}

function translateElement(element: Element, language: ResolvedLanguage) {
  let states = attributeState.get(element);
  if (!states) {
    states = new Map();
    attributeState.set(element, states);
  }
  for (const attribute of translatableAttributes) {
    const current = element.getAttribute(attribute);
    if (current === null) continue;
    const previous = states.get(attribute);
    const source = previous && current === previous.applied ? previous.source : current;
    const applied = translate(source, language);
    states.set(attribute, { source, applied });
    if (current !== applied) element.setAttribute(attribute, applied);
  }
}

function applyTo(root: Node, language = resolveLanguage()) {
  if (root instanceof Text) {
    translateTextNode(root, language);
    return;
  }
  if (!(root instanceof Element || root instanceof Document || root instanceof DocumentFragment)) return;
  if (root instanceof Element) translateElement(root, language);
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT);
  let node = walker.nextNode();
  while (node) {
    if (node instanceof Text) translateTextNode(node, language);
    else if (node instanceof Element) translateElement(node, language);
    node = walker.nextNode();
  }
}

export function applyLanguage() {
  if (typeof document === 'undefined' || applying) return;
  applying = true;
  const language = resolveLanguage();
  document.documentElement.lang = language;
  applyTo(document.body, language);
  applying = false;
}

export function setLanguagePreference(preference: LanguagePreference) {
  try { localStorage.setItem(STORAGE_KEY, preference); } catch {}
  applyLanguage();
  listeners.forEach((listener) => listener());
}

export function subscribeLanguage(listener: () => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function installI18nObserver() {
  if (typeof document === 'undefined' || observer) return;
  observer = new MutationObserver((mutations) => {
    if (applying) return;
    applying = true;
    const language = resolveLanguage();
    for (const mutation of mutations) {
      if (mutation.type === 'characterData') applyTo(mutation.target, language);
      if (mutation.type === 'attributes') applyTo(mutation.target, language);
      mutation.addedNodes.forEach((node) => applyTo(node, language));
    }
    applying = false;
  });
  observer.observe(document.documentElement, {
    subtree: true,
    childList: true,
    characterData: true,
    attributes: true,
    attributeFilter: [...translatableAttributes],
  });
  queueMicrotask(applyLanguage);
}
