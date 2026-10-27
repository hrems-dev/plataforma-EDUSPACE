import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from '../models/page.model';
import { Usuario, UsuarioConClaveTemporal, UsuarioCreate, UsuarioFiltro, UsuarioUpdate } from '../models/usuario.model';

const API = '/api/v1/auth/usuarios';

@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly http = inject(HttpClient);

  listar(filtro: UsuarioFiltro = {}): Observable<Page<Usuario>> {
    let params = new HttpParams().set('page', filtro.page ?? 0).set('size', filtro.size ?? 20);
    if (filtro.texto) params = params.set('texto', filtro.texto);
    if (filtro.rol) params = params.set('rol', filtro.rol);
    if (filtro.activo !== undefined) params = params.set('activo', filtro.activo);
    return this.http.get<Page<Usuario>>(API, { params });
  }

  crear(body: UsuarioCreate): Observable<UsuarioConClaveTemporal> {
    return this.http.post<UsuarioConClaveTemporal>(API, body);
  }

  actualizar(id: number, body: UsuarioUpdate): Observable<Usuario> {
    return this.http.put<Usuario>(`${API}/${id}`, body);
  }

  asignarRoles(id: number, roles: string[]): Observable<Usuario> {
    return this.http.put<Usuario>(`${API}/${id}/roles`, { roles });
  }

  activar(id: number): Observable<Usuario> {
    return this.http.patch<Usuario>(`${API}/${id}/activar`, {});
  }

  desactivar(id: number): Observable<Usuario> {
    return this.http.patch<Usuario>(`${API}/${id}/desactivar`, {});
  }

  desbloquear(id: number): Observable<Usuario> {
    return this.http.patch<Usuario>(`${API}/${id}/desbloquear`, {});
  }

  restablecerClave(id: number): Observable<UsuarioConClaveTemporal> {
    return this.http.post<UsuarioConClaveTemporal>(`${API}/${id}/restablecer-clave`, {});
  }
}
