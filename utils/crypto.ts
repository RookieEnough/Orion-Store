
// --- CRYPTO UTILITIES ---
// ⚠️ SECURITY NOTE: This salt is shipped in the client bundle and is NOT a secret.
// It provides integrity checking (tamper detection) but NOT authentication.
// An attacker who reads the bundle can forge valid signatures.
// For true security, signature verification should happen server-side or via a native plugin.
const SALT_KEY = "ORION_OMEGA_PROTOCOL_X9_SECURE_HASH_V1";

/**
 * Generates a SHA-256 HMAC-like signature for the data.
 * This ensures that if someone modifies the JSON payload in transit, the signature won't match.
 */
export const generateSignature = async (data: Record<string, any>): Promise<string> => {
    // Sort keys to ensure consistent stringification
    const sortedKeys = Object.keys(data).sort();
    const message = sortedKeys.map(key => `${key}:${data[key]}`).join('|') + SALT_KEY;

    const msgBuffer = new TextEncoder().encode(message);
    const hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
};
