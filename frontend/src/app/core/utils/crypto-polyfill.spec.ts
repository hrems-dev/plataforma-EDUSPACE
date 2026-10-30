import { sha256 } from './crypto-polyfill';

const hex = (b: Uint8Array): string => Array.from(b, x => x.toString(16).padStart(2, '0')).join('');
const texto = (s: string): Uint8Array => new TextEncoder().encode(s);

describe('SHA-256 en JavaScript (PKCE sin Web Crypto)', () => {
  it('coincide con los vectores de prueba oficiales', () => {
    expect(hex(sha256(texto('')))).toBe('e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
    expect(hex(sha256(texto('abc')))).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
    expect(hex(sha256(texto('abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq')))).toBe('248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1');
  });

  it('coincide con la implementación nativa en datos largos', async () => {
    const datos = new Uint8Array(1000).map((_, i) => (i * 31) % 256);
    const nativo = new Uint8Array(await crypto.subtle.digest('SHA-256', datos));
    expect(hex(sha256(datos))).toBe(hex(nativo));
  });
});
