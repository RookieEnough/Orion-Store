import { NETWORK_TIMEOUT_MS } from '@/constants';

export async function fetchWithTimeout(url: string, opts: RequestInit & { timeout?: number } = {}): Promise<Response> {
  const { timeout = NETWORK_TIMEOUT_MS, ...rest } = opts;
  const ctrl = new AbortController();
  const id = setTimeout(() => ctrl.abort(), timeout);
  try { return await fetch(url, { ...rest, signal: ctrl.signal }); }
  finally { clearTimeout(id); }
}

export function shuffleArray<T>(arr: T[]): T[] {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}
