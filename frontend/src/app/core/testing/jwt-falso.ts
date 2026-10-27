/** JWT sin firma real, solo para pruebas: el front nunca valida la firma, solo lee los claims. */
export function jwtFalso(roles: string[], venceEnSegundos = 3600, usuarioId = 7): string {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  const claims = {
    sub: String(usuarioId),
    usuarioId,
    preferred_username: 'admin',
    email: 'admin@eduspace.com',
    exp: Math.floor(Date.now() / 1000) + venceEnSegundos,
    realm_access: { roles }
  };
  return `${b64({ alg: 'RS256' })}.${b64(claims)}.firma`;
}
