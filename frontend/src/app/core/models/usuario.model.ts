export interface Usuario {
  id: number;
  username: string;
  email: string;
  nombres: string;
  apellidos: string;
  activo: boolean;
  bloqueado: boolean;
  debeCambiarClave: boolean;
  roles: string[];
  ultimoLogin?: string;
  creadoEn?: string;
}

export interface UsuarioCreate {
  username: string;
  email: string;
  nombres: string;
  apellidos: string;
  roles: string[];
  clave?: string;
}

export interface UsuarioUpdate {
  email: string;
  nombres: string;
  apellidos: string;
}

/** La clave temporal se muestra una sola vez: el backend no la vuelve a entregar. */
export interface UsuarioConClaveTemporal {
  usuario: Usuario;
  claveTemporal?: string;
}

export interface UsuarioFiltro {
  texto?: string;
  rol?: string;
  activo?: boolean;
  page?: number;
  size?: number;
}
