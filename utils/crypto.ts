
// --- CRYPTO UTILITIES ---
// Ideally, the SALT_KEY should be an environment variable or obfuscated during build.
// For this serverless architecture, we use a shared secret between client and worker.
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
