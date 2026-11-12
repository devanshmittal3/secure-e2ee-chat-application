import * as SecureStore from 'expo-secure-store';
import * as ExpoCrypto from 'expo-crypto';
import { x25519 } from '@noble/curves/ed25519.js';
import { xchacha20poly1305 } from '@noble/ciphers/chacha.js';
import { hkdf } from '@noble/hashes/hkdf.js';
import { sha256 } from '@noble/hashes/sha2.js';
import { utf8ToBytes } from '@noble/hashes/utils.js';


function decodeUtf8(bytes: Uint8Array): string {
  let encoded = '';
  for (const byte of bytes) {
    encoded += `%${byte.toString(16).padStart(2, '0')}`;
  }
  return decodeURIComponent(encoded);
}

const API_BASE_URL = 'https://secure-e2ee-chat-application.onrender.com';
const ALGORITHM = 'X25519-XCHACHA20-POLY1305-V1';
const ENVELOPE_PREFIX = 'SC_E2EE_V1:';

type DevicePublicKey = {
  sessionId: string;
  userId: number;
  publicKey: string;
  algorithm: string;
  fingerprint?: string;
};

type EncryptedRecipientEnvelope = {
  sessionId: string;
  ephemeralPublicKey: string;
  nonce: string;
  ciphertext: string;
};

type EncryptedEnvelope = {
  version: 1;
  algorithm: typeof ALGORITHM;
  conversationId: number;
  recipients: EncryptedRecipientEnvelope[];
};

type ApiMessage = {
  id: number;
  senderId: number;
  senderUsername: string;
  content: string;
  createdAt: string;
  encryptionStatus?: 'encrypted' | 'legacy' | 'unavailable';
};

const privateKeyStorageKey = (sessionId: string) =>
  `secureconnect.e2ee.x25519.private.${sessionId}`;

function toBase64(bytes: Uint8Array): string {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  let output = '';
  for (let i = 0; i < bytes.length; i += 3) {
    const a = bytes[i];
    const hasB = i + 1 < bytes.length;
    const hasC = i + 2 < bytes.length;
    const b = hasB ? bytes[i + 1] : 0;
    const c = hasC ? bytes[i + 2] : 0;
    output += alphabet[a >> 2];
    output += alphabet[((a & 3) << 4) | (b >> 4)];
    output += hasB ? alphabet[((b & 15) << 2) | (c >> 6)] : '=';
    output += hasC ? alphabet[c & 63] : '=';
  }
  return output;
}

function fromBase64(value: string): Uint8Array {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  const clean = value.replace(/[^A-Za-z0-9+/=]/g, '');
  if (!clean || clean.length % 4 !== 0) throw new Error('Invalid Base64 data in encrypted message.');
  const output: number[] = [];
  for (let i = 0; i < clean.length; i += 4) {
    const a = alphabet.indexOf(clean[i]);
    const b = alphabet.indexOf(clean[i + 1]);
    const c = clean[i + 2] === '=' ? 0 : alphabet.indexOf(clean[i + 2]);
    const d = clean[i + 3] === '=' ? 0 : alphabet.indexOf(clean[i + 3]);
    if (a < 0 || b < 0 || c < 0 || d < 0) throw new Error('Invalid Base64 data in encrypted message.');
    const n = (a << 18) | (b << 12) | (c << 6) | d;
    output.push((n >> 16) & 255);
    if (clean[i + 2] !== '=') output.push((n >> 8) & 255);
    if (clean[i + 3] !== '=') output.push(n & 255);
  }
  return new Uint8Array(output);
}

async function readApiResponse<T>(response: Response): Promise<T> {
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data?.message || data?.detail || `E2EE server error ${response.status}.`);
  }
  return data as T;
}

async function getPrivateKey(sessionId: string): Promise<Uint8Array> {
  const encoded = await SecureStore.getItemAsync(privateKeyStorageKey(sessionId));
  if (!encoded) {
    throw new Error('This device encryption key is missing. Sign out and link this device again.');
  }
  return fromBase64(encoded);
}

/**
 * Creates a per-device X25519 identity and registers only its public key.
 * The private key remains in Expo SecureStore on this device.
 */
export async function ensureLocalCryptoIdentity(
  token: string,
  sessionId: string,
): Promise<void> {
  if (!token || !sessionId) throw new Error('A signed-in device session is required for E2EE.');

  const storageKey = privateKeyStorageKey(sessionId);
  let encodedPrivateKey = await SecureStore.getItemAsync(storageKey);
  let privateKey: Uint8Array;

  if (encodedPrivateKey) {
    privateKey = fromBase64(encodedPrivateKey);
    if (privateKey.length !== 32) {
      throw new Error('The stored device encryption key is invalid. Do not delete app data; contact support.');
    }
  } else {
    privateKey = await ExpoCrypto.getRandomBytesAsync(32);
    encodedPrivateKey = toBase64(privateKey);
    await SecureStore.setItemAsync(storageKey, encodedPrivateKey);
  }

  const publicKey = toBase64(x25519.getPublicKey(privateKey));
  const response = await fetch(`${API_BASE_URL}/api/e2ee/device-key`, {
    method: 'PUT',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ publicKey, algorithm: ALGORITHM }),
  });
  await readApiResponse<DevicePublicKey>(response);
}

async function getConversationKeys(
  token: string,
  conversationId: number,
): Promise<DevicePublicKey[]> {
  const response = await fetch(
    `${API_BASE_URL}/api/e2ee/conversation/${conversationId}/keys`,
    {
      method: 'GET',
      headers: { Accept: 'application/json', Authorization: `Bearer ${token}` },
    },
  );
  const keys = await readApiResponse<DevicePublicKey[]>(response);
  return Array.isArray(keys) ? keys.filter((key) => key.algorithm === ALGORITHM) : [];
}

function deriveMessageKey(sharedSecret: Uint8Array, conversationId: number, recipientSessionId: string) {
  const info = utf8ToBytes(`SecureConnect/E2EE/v1/conversation/${conversationId}/device/${recipientSessionId}`);
  return hkdf(sha256, sharedSecret, undefined, info, 32);
}

/**
 * Encrypts independently for every currently active device in the conversation.
 * Fails closed if the other participant has not registered an encryption key.
 */
export async function encryptChatMessage(
  plaintext: string,
  conversationId: number,
  token: string,
  currentSessionId: string,
  currentUserId: number,
): Promise<string> {
  await ensureLocalCryptoIdentity(token, currentSessionId);
  const deviceKeys = await getConversationKeys(token, conversationId);
  if (!deviceKeys.some((key) => key.sessionId === currentSessionId)) {
    throw new Error('This device public key is not registered with the server yet. Try again in a moment.');
  }
  const recipientKeys = deviceKeys.filter((key) => key.userId !== currentUserId);
  if (recipientKeys.length === 0) {
    throw new Error('The other participant has not opened SecureConnect on a device with E2EE enabled yet. Ask them to sign in before sending encrypted messages.');
  }

  const recipients: EncryptedRecipientEnvelope[] = [];
  for (const recipient of deviceKeys) {
    const recipientPublicKey = fromBase64(recipient.publicKey);
    if (recipientPublicKey.length !== 32) {
      throw new Error(`The public key for device ${recipient.sessionId} is invalid.`);
    }

    const ephemeralPrivateKey = await ExpoCrypto.getRandomBytesAsync(32);
    const ephemeralPublicKey = x25519.getPublicKey(ephemeralPrivateKey);
    const sharedSecret = x25519.getSharedSecret(ephemeralPrivateKey, recipientPublicKey);
    const messageKey = deriveMessageKey(sharedSecret, conversationId, recipient.sessionId);
    const nonce = await ExpoCrypto.getRandomBytesAsync(24);
    const ciphertext = xchacha20poly1305(messageKey, nonce).encrypt(utf8ToBytes(plaintext));

    recipients.push({
      sessionId: recipient.sessionId,
      ephemeralPublicKey: toBase64(ephemeralPublicKey),
      nonce: toBase64(nonce),
      ciphertext: toBase64(ciphertext),
    });
  }

  const envelope: EncryptedEnvelope = {
    version: 1,
    algorithm: ALGORITHM,
    conversationId,
    recipients,
  };
  return ENVELOPE_PREFIX + toBase64(utf8ToBytes(JSON.stringify(envelope)));
}

/**
 * Decrypts envelopes addressed to this exact device. Legacy plaintext is retained
 * visibly as legacy and is never misrepresented as end-to-end encrypted.
 */
export async function decryptChatMessages(
  messages: ApiMessage[],
  currentSessionId: string,
): Promise<ApiMessage[]> {
  const encodedPrivateKey = await SecureStore.getItemAsync(privateKeyStorageKey(currentSessionId));
  const privateKey = encodedPrivateKey ? fromBase64(encodedPrivateKey) : null;

  return Promise.all(messages.map(async (message) => {
    if (!message.content?.startsWith(ENVELOPE_PREFIX)) {
      return { ...message, encryptionStatus: 'legacy' as const };
    }

    try {
      if (!privateKey) return { ...message, content: '[Encrypted message: this device has no local key]', encryptionStatus: 'unavailable' as const };
      const rawEnvelope = decodeUtf8(fromBase64(message.content.slice(ENVELOPE_PREFIX.length)));
      const envelope = JSON.parse(rawEnvelope) as EncryptedEnvelope;
      if (envelope.version !== 1 || envelope.algorithm !== ALGORITHM) {
        throw new Error('Unsupported encrypted message version.');
      }
      const recipient = envelope.recipients.find((entry) => entry.sessionId === currentSessionId);
      if (!recipient) {
        return { ...message, content: '[Encrypted message: this device was not authorized when it was sent]', encryptionStatus: 'unavailable' as const };
      }
      const sharedSecret = x25519.getSharedSecret(privateKey, fromBase64(recipient.ephemeralPublicKey));
      const messageKey = deriveMessageKey(sharedSecret, envelope.conversationId, currentSessionId);
      const plaintext = xchacha20poly1305(messageKey, fromBase64(recipient.nonce))
        .decrypt(fromBase64(recipient.ciphertext));
      return { ...message, content: decodeUtf8(plaintext), encryptionStatus: 'encrypted' as const };
    } catch {
      return { ...message, content: '[Unable to decrypt this message on this device]', encryptionStatus: 'unavailable' as const };
    }
  }));
}

