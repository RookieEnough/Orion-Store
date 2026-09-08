import { Capacitor } from '@capacitor/core';
import AppTracker from '../plugins/AppTracker';

export type NormalizedArch = 'ARM64' | 'ARMv7' | 'x64' | 'x86' | 'Universal' | 'Unknown';

export interface DeviceArchInfo {
  rawArch: string;
  normalized: NormalizedArch;
  friendlyName: string;
  supportedAbis: string[];
}

let cachedDeviceArch: DeviceArchInfo | null = null;
let pendingFetchPromise: Promise<DeviceArchInfo> | null = null;

/**
 * Normalizes an architecture string or ABI to a canonical representation.
 */
export const normalizeArch = (arch?: string | null): NormalizedArch => {
  if (!arch) return 'Unknown';
  const lower = arch.toLowerCase().trim();

  if (lower.includes('arm64') || lower.includes('aarch64') || lower.includes('v8a')) {
    return 'ARM64';
  }
  if (lower.includes('armeabi') || lower.includes('armv7') || lower.includes('v7a') || lower === 'arm') {
    return 'ARMv7';
  }
  if (lower.includes('x86_64') || lower.includes('x64') || lower.includes('amd64')) {
    return 'x64';
  }
  if (lower.includes('x86') || lower.includes('i386') || lower.includes('i686')) {
    return 'x86';
  }
  if (lower.includes('universal') || lower.includes('all') || lower === 'any') {
    return 'Universal';
  }

  return 'Unknown';
};

/**
 * Returns a human-friendly label for a given architecture.
 */
export const getFriendlyArchLabel = (arch: string): string => {
  const norm = normalizeArch(arch);
  switch (norm) {
    case 'ARM64':
      return 'ARM64 (64-bit)';
    case 'ARMv7':
      return 'ARMv7 (32-bit)';
    case 'x64':
      return 'x86_64 (64-bit)';
    case 'x86':
      return 'x86 (32-bit)';
    case 'Universal':
      return 'Universal';
    default:
      return arch ? arch.toUpperCase() : 'Unknown';
  }
};

/**
 * Evaluates compatibility of an APK variant with the user's device architecture.
 */
export const isArchCompatible = (
  variantArch: string,
  deviceArch: string,
  supportedAbis: string[] = []
): { isCompatible: boolean; isRecommended: boolean } => {
  const normVariant = normalizeArch(variantArch);
  const normDevice = normalizeArch(deviceArch);

  // Exact canonical match is best match & recommended
  if (normVariant === normDevice && normDevice !== 'Unknown') {
    return { isCompatible: true, isRecommended: true };
  }

  // Universal variants run on all devices
  if (normVariant === 'Universal') {
    return { isCompatible: true, isRecommended: normDevice === 'Unknown' };
  }

  // Check supported ABIs if available
  const lowerVariant = variantArch.toLowerCase().trim();
  for (const abi of supportedAbis) {
    const normAbi = normalizeArch(abi);
    if (normVariant === normAbi || lowerVariant.includes(abi.toLowerCase())) {
      // If it's the primary ABI, it's recommended; otherwise compatible fallback
      const isPrimary = supportedAbis.length > 0 && abi === supportedAbis[0];
      return { isCompatible: true, isRecommended: isPrimary };
    }
  }

  // ARM64 devices typically support ARMv7 32-bit binaries via compatibility layer
  if (normDevice === 'ARM64' && normVariant === 'ARMv7') {
    return { isCompatible: true, isRecommended: false };
  }

  // x64 devices typically support x86 32-bit binaries
  if (normDevice === 'x64' && normVariant === 'x86') {
    return { isCompatible: true, isRecommended: false };
  }

  return { isCompatible: false, isRecommended: false };
};

/**
 * Detects device architecture from Web / Browser userAgent fallback.
 */
const detectWebArchitecture = (): DeviceArchInfo => {
  if (typeof navigator === 'undefined') {
    return {
      rawArch: 'unknown',
      normalized: 'Unknown',
      friendlyName: 'Unknown',
      supportedAbis: []
    };
  }

  const ua = (navigator.userAgent || '').toLowerCase();
  const platform = ((navigator as any).userAgentData?.platform || navigator.platform || '').toLowerCase();

  let detected: NormalizedArch = 'Unknown';
  let raw = 'unknown';

  if (ua.includes('arm64') || ua.includes('aarch64')) {
    detected = 'ARM64';
    raw = 'arm64-v8a';
  } else if (ua.includes('arm') || ua.includes('v7a')) {
    detected = 'ARMv7';
    raw = 'armeabi-v7a';
  } else if (ua.includes('x86_64') || ua.includes('win64') || ua.includes('x64') || platform.includes('64')) {
    detected = 'x64';
    raw = 'x86_64';
  } else if (ua.includes('x86')) {
    detected = 'x86';
    raw = 'x86';
  } else if (ua.includes('android')) {
    // Default modern Android devices to ARM64
    detected = 'ARM64';
    raw = 'arm64-v8a';
  }

  return {
    rawArch: raw,
    normalized: detected,
    friendlyName: getFriendlyArchLabel(detected),
    supportedAbis: raw !== 'unknown' ? [raw] : []
  };
};

/**
 * Fetches the device architecture via the native AppTracker plugin, with caching.
 */
export const getDeviceArchitecture = async (): Promise<DeviceArchInfo> => {
  if (cachedDeviceArch) {
    return cachedDeviceArch;
  }

  if (pendingFetchPromise) {
    return pendingFetchPromise;
  }

  pendingFetchPromise = (async () => {
    try {
      if (Capacitor.isNativePlatform()) {
        const result = await AppTracker.getDeviceArchitecture();
        if (result && result.primaryArch) {
          const norm = normalizeArch(result.primaryArch);
          cachedDeviceArch = {
            rawArch: result.primaryArch,
            normalized: norm,
            friendlyName: getFriendlyArchLabel(result.primaryArch),
            supportedAbis: result.supportedAbis || [result.primaryArch]
          };
          return cachedDeviceArch;
        }
      }
    } catch {
      // Fall through to web detection
    }

    cachedDeviceArch = detectWebArchitecture();
    return cachedDeviceArch;
  })().finally(() => {
    pendingFetchPromise = null;
  });

  return pendingFetchPromise;
};

/**
 * Clears the cached device architecture (primarily for testing).
 */
export const clearDeviceArchCache = () => {
  cachedDeviceArch = null;
  pendingFetchPromise = null;
};
