import { uuidSinRandomUUID } from './uuid';

/**
 * Web Crypto solo existe en contextos seguros (https o localhost). Al abrir la web por http://<IP de la red>:4200 faltan
 * `crypto.randomUUID` y `crypto.subtle`, que keycloak-js usa para el state y para el desafío PKCE (S256). Aquí se completan
 * con equivalentes en JavaScript, para que el inicio de sesión de Keycloak (con su propia pantalla y PKCE) funcione también así.
 * En un contexto seguro no hace nada: se usa siempre la implementación nativa.
 */
export function completarWebCrypto(): void {
  if (typeof crypto === 'undefined') return;
  if (typeof crypto.randomUUID !== 'function') {
    const completo = (): string => uuidSinRandomUUID();
    (completo as { __completado?: boolean }).__completado = true;
    Object.defineProperty(crypto, 'randomUUID', { value: completo, configurable: true });
  }
  if (typeof crypto.subtle === 'undefined') {
    Object.defineProperty(crypto, 'subtle', {
      value: {
        digest: (algoritmo: AlgorithmIdentifier, datos: BufferSource): Promise<ArrayBuffer> => {
          const nombre = typeof algoritmo === 'string' ? algoritmo : algoritmo.name;
          if (nombre.toUpperCase() !== 'SHA-256') return Promise.reject(new Error('Solo SHA-256 en este contexto.'));
          const bytes = datos instanceof ArrayBuffer ? new Uint8Array(datos) : new Uint8Array(datos.buffer, datos.byteOffset, datos.byteLength);
          return Promise.resolve(sha256(bytes).buffer as ArrayBuffer);
        }
      },
      configurable: true
    });
  }
}

const K = new Uint32Array([
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
  0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
  0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
  0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
]);

/** SHA-256 (FIPS 180-4) en JavaScript puro. */
export function sha256(datos: Uint8Array): Uint8Array {
  const largo = datos.length;
  const relleno = (((largo + 9 + 63) >> 6) << 6);
  const m = new Uint8Array(relleno);
  m.set(datos);
  m[largo] = 0x80;
  const vista = new DataView(m.buffer);
  vista.setUint32(relleno - 8, Math.floor((largo * 8) / 0x100000000));
  vista.setUint32(relleno - 4, (largo * 8) >>> 0);
  const h = new Uint32Array([0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19]);
  const w = new Uint32Array(64);
  const rotr = (x: number, n: number): number => (x >>> n) | (x << (32 - n));
  for (let i = 0; i < relleno; i += 64) {
    for (let t = 0; t < 16; t++) w[t] = vista.getUint32(i + t * 4);
    for (let t = 16; t < 64; t++) {
      const s0 = rotr(w[t - 15], 7) ^ rotr(w[t - 15], 18) ^ (w[t - 15] >>> 3);
      const s1 = rotr(w[t - 2], 17) ^ rotr(w[t - 2], 19) ^ (w[t - 2] >>> 10);
      w[t] = (w[t - 16] + s0 + w[t - 7] + s1) >>> 0;
    }
    let [a, b, c, d, e, f, g, hh] = h;
    for (let t = 0; t < 64; t++) {
      const t1 = (hh + (rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)) + ((e & f) ^ (~e & g)) + K[t] + w[t]) >>> 0;
      const t2 = ((rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)) + ((a & b) ^ (a & c) ^ (b & c))) >>> 0;
      hh = g; g = f; f = e; e = (d + t1) >>> 0; d = c; c = b; b = a; a = (t1 + t2) >>> 0;
    }
    h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh;
  }
  const salida = new Uint8Array(32);
  const v = new DataView(salida.buffer);
  for (let i = 0; i < 8; i++) v.setUint32(i * 4, h[i]);
  return salida;
}
