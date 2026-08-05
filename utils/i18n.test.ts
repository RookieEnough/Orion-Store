import { beforeEach, describe, expect, it } from 'vitest';
import {
  applyLanguage,
  getLanguagePreference,
  resolveLanguage,
  setLanguagePreference,
  translate,
} from '../i18n';

describe('interface language', () => {
  beforeEach(() => {
    const values = new Map<string, string>();
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      value: {
        clear: () => values.clear(),
        getItem: (key: string) => values.get(key) ?? null,
        removeItem: (key: string) => values.delete(key),
        setItem: (key: string, value: string) => values.set(key, value),
      },
    });
    localStorage.clear();
    document.body.innerHTML = '';
  });

  it('translates catalog entries and leaves unknown content untouched', () => {
    expect(translate('Download', 'pt-BR')).toBe('Baixar');
    expect(translate('Uncatalogued application name', 'pt-BR')).toBe('Uncatalogued application name');
    expect(translate('Download', 'en')).toBe('Download');
  });

  it('persists the selected language and applies it to rendered content', () => {
    document.body.innerHTML = '<button title="Download">Download</button>';
    setLanguagePreference('pt-BR');

    expect(getLanguagePreference()).toBe('pt-BR');
    expect(resolveLanguage()).toBe('pt-BR');
    expect(document.documentElement.lang).toBe('pt-BR');
    expect(document.querySelector('button')?.textContent).toBe('Baixar');
    expect(document.querySelector('button')?.getAttribute('title')).toBe('Baixar');

    setLanguagePreference('en');
    applyLanguage();
    expect(document.querySelector('button')?.textContent).toBe('Download');
  });
});
