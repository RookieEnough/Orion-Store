import { describe, expect, it } from 'vitest';
import { sanitizeDownloadUrl } from './downloadUrl';

describe('sanitizeDownloadUrl', () => {
  it('allows only web download URLs', () => {
    expect(sanitizeDownloadUrl(' https://example.com/app.apk ')).toBe('https://example.com/app.apk');
    expect(sanitizeDownloadUrl('http://example.com/app.apk')).toBe('http://example.com/app.apk');
    expect(sanitizeDownloadUrl('javascript:alert(1)')).toBe('#');
    expect(sanitizeDownloadUrl('file:///data/local/tmp/app.apk')).toBe('#');
  });
});
