import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { Caja, CierreCaja, Comprobante, Cuota, EstadoCuenta, FiltroCuotas, Pago, RegistrarPago } from '../models/cobro.model';
import { Page } from '../models/page.model';

const API = '/api/v1/pagos';

/** Cobros, cuentas por alumno, caja y comprobantes (la configuración de tarifas está en PagoConfigService). */
@Injectable({ providedIn: 'root' })
export class CobroService {
  private readonly http = inject(HttpClient);

  estadoCuenta(alumnoId: number): Observable<EstadoCuenta> {
    return this.http.get<EstadoCuenta>(`${API}/alumnos/${alumnoId}/estado-cuenta`);
  }

  cuotas(f: FiltroCuotas = {}): Observable<Page<Cuota>> {
    let params = new HttpParams().set('page', f.page ?? 0).set('size', f.size ?? 20);
    if (f.estado) params = params.set('estado', f.estado);
    if (f.alumnoId) params = params.set('alumnoId', f.alumnoId);
    if (f.seccionId) params = params.set('seccionId', f.seccionId);
    if (f.vencimiento) params = params.set('vencimiento', f.vencimiento);
    return this.http.get<Page<Cuota>>(`${API}/cuotas`, { params });
  }

  misCuotas(): Observable<Cuota[]> {
    return this.http.get<Cuota[]>(`${API}/mias/cuotas`);
  }

  registrar(body: RegistrarPago): Observable<Pago> {
    return this.http.post<Pago>(API, body);
  }

  pagarOnline(body: RegistrarPago): Observable<Pago> {
    return this.http.post<Pago>(`${API}/online`, body);
  }

  anular(id: number, motivo: string): Observable<Pago> {
    return this.http.patch<Pago>(`${API}/${id}/anular`, { motivo });
  }

  pago(id: number): Observable<Pago> {
    return this.http.get<Pago>(`${API}/${id}`);
  }

  comprobante(pagoId: number): Observable<Comprobante> {
    return this.http.get<Comprobante>(`${API}/${pagoId}/comprobante`);
  }

  reporteCobranza(desde?: string, hasta?: string, medio?: string, page = 0, size = 20): Observable<Page<Pago>> {
    let params = new HttpParams().set('page', page).set('size', size);
    // pago-ms recibe fecha y hora (LocalDateTime): el día completo va de 00:00:00 a 23:59:59.
    if (desde) params = params.set('desde', `${desde}T00:00:00`);
    if (hasta) params = params.set('hasta', `${hasta}T23:59:59`);
    if (medio) params = params.set('medio', medio);
    return this.http.get<Page<Pago>>(`${API}/reportes/cobranza`, { params });
  }

  asignarDescuento(alumnoId: number, descuentoId: number, anioId: number, motivo?: string): Observable<unknown> {
    return this.http.post(`${API}/descuentos/asignar`, { alumnoId, descuentoId, anioId, motivo });
  }

  // Caja
  cajaActual(): Observable<Caja> {
    return this.http.get<Caja>(`${API}/caja/actual`);
  }
  abrirCaja(montoApertura: number): Observable<Caja> {
    return this.http.post<Caja>(`${API}/caja/abrir`, { montoApertura });
  }
  cerrarCaja(montoCierreDeclarado: number): Observable<CierreCaja> {
    return this.http.post<CierreCaja>(`${API}/caja/cerrar`, { montoCierreDeclarado });
  }
  reporteCaja(id: number): Observable<CierreCaja> {
    // Con la caja abierta el servicio no calcula el efectivo esperado (solo al cerrar): apertura + cobros en efectivo.
    return this.http.get<CierreCaja>(`${API}/caja/${id}/reporte`).pipe(
      map(r => ({ ...r, efectivoEsperado: r.efectivoEsperado ?? r.montoApertura + r.cobrosEfectivo }))
    );
  }
}
