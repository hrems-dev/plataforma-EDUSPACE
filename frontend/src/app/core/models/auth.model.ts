export type Rol = 'ADMIN' | 'SECRETARIA' | 'DOCENTE' | 'ALUMNO' | 'APODERADO' | 'CAJERO';

export const ROLES: Rol[] = ['ADMIN', 'SECRETARIA', 'DOCENTE', 'ALUMNO', 'APODERADO', 'CAJERO'];

export interface LoginRequest {
  username: string;
  password: string;
}

export interface TokenResponse {
  access_token: string;
  refresh_token: string;
  token_type: string;
  expires_in: number;
  debe_cambiar_clave: boolean;
}

export interface CambiarClaveRequest {
  claveActual: string;
  claveNueva: string;
}

export interface JwtClaims {
  sub: string;
  exp?: number;
  preferred_username?: string;
  email?: string;
  usuarioId?: number;
  realm_access?: { roles?: string[] };
}

export interface CurrentUser {
  id: number;
  username: string;
  email: string;
  roles: string[];
}
