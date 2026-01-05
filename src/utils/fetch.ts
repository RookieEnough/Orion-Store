import { NETWORK_TIMEOUT_MS } from '@/constants';

export async function fetchWithTimeout(url: string, opts: RequestInit & { timeout?: number } = {}): Promise<Response> {
  const { timeout = NETWORK_TIMEOUT_MS, ...rest } = opts;
  const ctrl = new AbortController();
  const id = setTimeout(() => ctrl.abort(), timeout);
  try { return await fetch(url, { ...rest, signal: ctrl.signal }); }
  finally { clearTimeout(id); }
}
