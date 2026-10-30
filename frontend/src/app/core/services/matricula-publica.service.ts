import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { InicioMatricula, OfertaMatricula, ResumenMatricula, SolicitudMatricula, Tarjeta } from '../models/matricula-publica.model';

const API = '/api/v1/publico/matricula';
const TOKEN = 'X-Matricula-Token';

/** Matrícula en línea del sitio público: no usa la sesión; cada trámite se identifica con su referencia y su token. */
@Injectable({ providedIn: 'root' })
export class MatriculaPublicaService {
  private readonly http = inject(HttpClient);

  oferta(): Observable<OfertaMatricula> {
    return this.http.get<OfertaMatricula>(`${API}/oferta`);
  }

  iniciar(s: SolicitudMatricula): Observable<InicioMatricula> {
    return this.http.post<InicioMatricula>(API, s);
  }

  /** Pasarela de pago de demostración: no se cobra nada. */
  pagar(referencia: string, token: string, tarjeta: Tarjeta): Observable<ResumenMatricula> {
    return this.http.post<ResumenMatricula>(`${API}/${referencia}/pagar`, { tarjeta }, { headers: new HttpHeaders({ [TOKEN]: token }) });
  }

  resumen(referencia: string, token: string): Observable<ResumenMatricula> {
    return this.http.get<ResumenMatricula>(`${API}/${referencia}`, { headers: new HttpHeaders({ [TOKEN]: token }) });
  }

  constancia(referencia: string, token: string): Observable<Blob> {
    return this.http.get(`${API}/${referencia}/constancia`, { headers: new HttpHeaders({ [TOKEN]: token }), responseType: 'blob' });
  }
}
