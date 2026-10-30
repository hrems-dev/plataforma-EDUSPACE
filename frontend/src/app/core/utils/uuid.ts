/**
 * UUID v4. `crypto.randomUUID()` solo existe en contextos seguros (https o localhost); al abrir la web por la IP de la red
 * (http://192.168.x.x:4200) no está, y todo lo que lo usara fallaba. `getRandomValues` sí funciona en cualquier contexto.
 */
export function uuid(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function' && !(crypto.randomUUID as { __completado?: boolean }).__completado) return crypto.randomUUID();
  return uuidSinRandomUUID();
}

/** UUID v4 con `getRandomValues` (existe en cualquier contexto). No llama a `crypto.randomUUID`, así que el polyfill no se recursiona. */
export function uuidSinRandomUUID(): string {
  const b = new Uint8Array(16);
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') crypto.getRandomValues(b);
  else for (let i = 0; i < 16; i++) b[i] = Math.floor(Math.random() * 256);
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = Array.from(b, x => x.toString(16).padStart(2, '0'));
  return `${h.slice(0, 4).join('')}-${h.slice(4, 6).join('')}-${h.slice(6, 8).join('')}-${h.slice(8, 10).join('')}-${h.slice(10).join('')}`;
}
