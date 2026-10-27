import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, tap } from 'rxjs';
import { EnvioManual, EnvioManualResultado, Notificacion, Plantilla, Preferencia } from '../models/notificacion.model';
import { Page } from '../models/page.model';

const API = '/api/v1/notificaciones';

@Injectable({ providedIn: 'root' })
export class NotificacionService {
  private readonly http = inject(HttpClient);

  /** Contador de no leídas que muestra el menú; se actualiza al consultarlo y al marcar como leídas. */
  private readonly noLeidasSignal = signal(0);
  readonly noLeidas = this.noLeidasSignal.asReadonly();

  mias(leida?: boolean, page = 0, size = 15): Observable<Page<Notificacion>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (leida !== undefined) params = params.set('leida', leida);
    return this.http
      .get<{ contenido: Notificacion[]; pagina: number; tamano: number; total: number; totalPaginas: number }>(`${API}/mias`, { params })
      .pipe(map(p => ({ contenido: p.contenido, pagina: p.pagina, tamano: p.tamano, totalElementos: p.total, totalPaginas: p.totalPaginas })));
  }

  actualizarConteo(): Observable<number> {
    return this.http.get<{ noLeidas: number }>(`${API}/mias/no-leidas/conteo`).pipe(
      map(r => r.noLeidas),
      tap(n => this.noLeidasSignal.set(n))
    );
  }

  marcarLeida(id: number): Observable<Notificacion> {
    return this.http.patch<Notificacion>(`${API}/${id}/leida`, {}).pipe(tap(() => this.noLeidasSignal.update(n => Math.max(0, n - 1))));
  }

  leerTodas(): Observable<void> {
    return this.http.patch<void>(`${API}/mias/leer-todas`, {}).pipe(tap(() => this.noLeidasSignal.set(0)));
  }

  preferencias(): Observable<Preferencia[]> {
    return this.http.get<Preferencia[]>(`${API}/preferencias`);
  }

  guardarPreferencias(body: Preferencia[]): Observable<Preferencia[]> {
    return this.http.put<Preferencia[]>(`${API}/preferencias`, body);
  }

  // Administración
  plantillas(): Observable<Plantilla[]> {
    return this.http.get<Plantilla[]>(`${API}/plantillas`);
  }

  actualizarPlantilla(id: number, body: { asunto?: string; cuerpo: string; activa: boolean }): Observable<Plantilla> {
    return this.http.put<Plantilla>(`${API}/plantillas/${id}`, body);
  }

  enviar(body: EnvioManual): Observable<EnvioManualResultado> {
    return this.http.post<EnvioManualResultado>(`${API}/enviar`, body);
  }
}
