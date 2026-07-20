import { describe, expect, it } from 'vitest';
import MockWorker from '../workers/core.worker';

const initializeWorker = (payload: unknown) => new Promise<any>((resolve) => {
  const worker = new MockWorker();
  worker.onmessage = (event) => resolve(event.data);
  worker.postMessage({ type: 'INIT_DATA', payload });
});

describe('core worker input handling', () => {
  it('keeps valid apps available when remote data includes malformed entries', async () => {
    const result = await initializeWorker({
      rawApps: [
        null,
        'not-an-app',
        {
          id: 123,
          name: 'Safe App',
          category: 'utility',
          platform: 'android',
          tags: [' tools ', 7],
          screenshots: [null],
          githubRepo: 99
        }
      ],
      importedApps: null,
      mirrorData: 'invalid'
    });

    expect(result.type).toBe('DATA_PROCESSED');
    expect(result.payload.imported).toEqual([]);
    expect(result.payload.apps).toHaveLength(1);
    expect(result.payload.apps[0]).toMatchObject({
      id: '123',
      category: 'Utility',
      platform: 'Android',
      tags: ['tools', '7'],
      githubRepo: undefined
    });
  });
});
