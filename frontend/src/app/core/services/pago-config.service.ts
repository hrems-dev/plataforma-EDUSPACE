import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AlertaAdmin,
  Concepto,
  ConceptoRequest,
  Descuento,
  DescuentoRequest,
  Morosos,
  Tarifa,
  TarifaRequest
} from '../models/pago.model';

const API = '/api/v1/pagos';

/** Configuración de cobranza que administra el ADMIN: conceptos, tarifas y descuentos; más alertas y morosidad. */
@Injectable({ providedIn: 'root' })
export class PagoConfigService {
  private readonly http = inject(HttpClient);

  conceptos(): Observable<Concepto[]> {
    return this.http.get<Concepto[]>(`${API}/conceptos`);
  }
  crearConcepto(body: ConceptoRequest): Observable<Concepto> {
    return this.http.post<Concepto>(`${API}/conceptos`, body);
  }
  actualizarConcepto(id: number, body: ConceptoRequest): Observable<Concepto> {
    return this.http.put<Concepto>(`${API}/conceptos/${id}`, body);
  }

  tarifas(anioId: number): Observable<Tarifa[]> {
    return this.http.get<Tarifa[]>(`${API}/tarifas`, { params: new HttpParams().set('anioId', anioId) });
  }
  crearTarifa(body: TarifaRequest): Observable<Tarifa> {
    return this.http.post<Tarifa>(`${API}/tarifas`, body);
  }
  actualizarTarifa(id: number, body: TarifaRequest): Observable<Tarifa> {
    return this.http.put<Tarifa>(`${API}/tarifas/${id}`, body);
  }

  descuentos(): Observable<Descuento[]> {
    return this.http.get<Descuento[]>(`${API}/descuentos`);
  }
  crearDescuento(body: DescuentoRequest): Observable<Descuento> {
    return this.http.post<Descuento>(`${API}/descuentos`, body);
  }
  actualizarDescuento(id: number, body: DescuentoRequest): Observable<Descuento> {
    return this.http.put<Descuento>(`${API}/descuentos/${id}`, body);
  }

  alertas(resueltas = false): Observable<AlertaAdmin[]> {
    return this.http.get<AlertaAdmin[]>(`${API}/alertas`, { params: new HttpParams().set('resueltas', resueltas) });
  }
  resolverAlerta(id: number): Observable<AlertaAdmin> {
    return this.http.patch<AlertaAdmin>(`${API}/alertas/${id}/resolver`, {});
  }

  morosos(seccionId?: number, gradoId?: number): Observable<Morosos> {
    let params = new HttpParams();
    if (seccionId) params = params.set('seccionId', seccionId);
    if (gradoId) params = params.set('gradoId', gradoId);
    return this.http.get<Morosos>(`${API}/morosos`, { params });
  }
}
