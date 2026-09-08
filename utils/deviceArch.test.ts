import { describe, expect, it, beforeEach } from 'vitest';
import {
  normalizeArch,
  getFriendlyArchLabel,
  isArchCompatible,
  clearDeviceArchCache,
  getDeviceArchitecture
} from './deviceArch';

describe('deviceArch utilities', () => {
  beforeEach(() => {
    clearDeviceArchCache();
  });

  describe('normalizeArch', () => {
    it('normalizes 64-bit ARM variants', () => {
      expect(normalizeArch('arm64-v8a')).toBe('ARM64');
      expect(normalizeArch('ARM64')).toBe('ARM64');
      expect(normalizeArch('aarch64')).toBe('ARM64');
      expect(normalizeArch('v8a')).toBe('ARM64');
    });

    it('normalizes 32-bit ARM variants', () => {
      expect(normalizeArch('armeabi-v7a')).toBe('ARMv7');
      expect(normalizeArch('ARMv7')).toBe('ARMv7');
      expect(normalizeArch('armeabi')).toBe('ARMv7');
      expect(normalizeArch('v7a')).toBe('ARMv7');
      expect(normalizeArch('arm')).toBe('ARMv7');
    });

    it('normalizes x86_64 variants', () => {
      expect(normalizeArch('x86_64')).toBe('x64');
      expect(normalizeArch('x64')).toBe('x64');
      expect(normalizeArch('amd64')).toBe('x64');
    });

    it('normalizes x86 32-bit variants', () => {
      expect(normalizeArch('x86')).toBe('x86');
      expect(normalizeArch('i386')).toBe('x86');
      expect(normalizeArch('i686')).toBe('x86');
    });

    it('normalizes Universal / all variants', () => {
      expect(normalizeArch('Universal')).toBe('Universal');
      expect(normalizeArch('all')).toBe('Universal');
      expect(normalizeArch('any')).toBe('Universal');
    });

    it('handles undefined, null, or unknown arch', () => {
      expect(normalizeArch(undefined)).toBe('Unknown');
      expect(normalizeArch(null)).toBe('Unknown');
      expect(normalizeArch('mips')).toBe('Unknown');
    });
  });

  describe('getFriendlyArchLabel', () => {
    it('returns human-readable labels', () => {
      expect(getFriendlyArchLabel('arm64-v8a')).toBe('ARM64 (64-bit)');
      expect(getFriendlyArchLabel('armeabi-v7a')).toBe('ARMv7 (32-bit)');
      expect(getFriendlyArchLabel('x86_64')).toBe('x86_64 (64-bit)');
      expect(getFriendlyArchLabel('x86')).toBe('x86 (32-bit)');
      expect(getFriendlyArchLabel('Universal')).toBe('Universal');
    });
  });

  describe('isArchCompatible', () => {
    it('identifies exact matching architecture as recommended', () => {
      const match = isArchCompatible('ARM64', 'arm64-v8a');
      expect(match.isCompatible).toBe(true);
      expect(match.isRecommended).toBe(true);
    });

    it('marks Universal as compatible for any device', () => {
      const match = isArchCompatible('Universal', 'arm64-v8a');
      expect(match.isCompatible).toBe(true);
      expect(match.isRecommended).toBe(false);
    });

    it('recognizes 32-bit fallback for 64-bit ARM as compatible but not recommended', () => {
      const match = isArchCompatible('ARMv7', 'arm64-v8a', ['arm64-v8a', 'armeabi-v7a']);
      expect(match.isCompatible).toBe(true);
      expect(match.isRecommended).toBe(false);
    });

    it('marks mismatched architectures as incompatible', () => {
      const match = isArchCompatible('x86', 'arm64-v8a', ['arm64-v8a']);
      expect(match.isCompatible).toBe(false);
      expect(match.isRecommended).toBe(false);
    });
  });

  describe('getDeviceArchitecture', () => {
    it('retrieves architecture and caches the result', async () => {
      const arch1 = await getDeviceArchitecture();
      expect(arch1).toBeDefined();
      expect(arch1.normalized).toBeDefined();

      const arch2 = await getDeviceArchitecture();
      expect(arch2).toBe(arch1); // Same reference = cached
    });
  });
});
