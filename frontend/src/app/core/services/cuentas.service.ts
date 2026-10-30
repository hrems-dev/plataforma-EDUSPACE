import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, switchMap } from 'rxjs';
import { Usuario } from '../models/usuario.model';
import { UsuarioService } from './usuario.service';

export type TipoPersona = 'ALUMNO' | 'DOCENTE' | 'APODERADO';

/** Persona del colegio que puede tener una cuenta de acceso. */
export interface PersonaCuenta {
  tipo: TipoPersona;
  id: number;
  nombres: string;
  apellidos: string;
  documento: string;
  usuarioId?: number;
}

const VINCULO: Record<TipoPersona, (id: number) => string> = {
  ALUMNO: id => `/api/v1/alumnos/${id}/usuario`,
  APODERADO: id => `/api/v1/apoderados/${id}/usuario`,
  DOCENTE: id => `/api/v1/planificacion/docentes/${id}/usuario`
};

/** «Apellidos, Nombres» (como lo entrega alumnos-ms) → partes. */
export function partirNombre(completo: string): { nombres: string; apellidos: string } {
  const i = completo.indexOf(',');
  return i < 0 ? { nombres: completo.trim(), apellidos: completo.trim() } : { apellidos: completo.slice(0, i).trim(), nombres: completo.slice(i + 1).trim() };
}

/**
 * Cuentas de acceso de alumnos, docentes y apoderados: se crean con usuario `nombre.apellido` y clave inicial igual al documento
 * (el sistema obliga a cambiarla en el primer ingreso) y quedan vinculadas a la persona.
 */
@Injectable({ providedIn: 'root' })
export class CuentasService {
  private readonly http = inject(HttpClient);
  private readonly usuarios = inject(UsuarioService);

  /** Crea la cuenta y la vincula a la persona; devuelve el usuario creado. */
  crear(p: PersonaCuenta): Observable<Usuario> {
    return this.usuarios.crearCuenta({ nombres: p.nombres, apellidos: p.apellidos, documento: p.documento, rol: p.tipo }).pipe(
      switchMap(r => this.http.patch(VINCULO[p.tipo](p.id), { usuarioId: r.usuario.id }).pipe(map(() => r.usuario)))
    );
  }

  /** La clave vuelve a ser el documento y se pide cambiarla al entrar. */
  restablecer(p: PersonaCuenta): Observable<Usuario> {
    return this.usuarios.restablecerPorDocumento(p.usuarioId!, p.documento);
  }
}
