import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Cuota, MedioPago, Pago } from '../../core/models/cobro.model';
import { CobroService } from '../../core/services/cobro.service';
import { NombresAlumnoService } from '../../core/services/nombres-alumno.service';
import { PagoConfigService } from '../../core/services/pago-config.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

const ETIQUETA_MEDIO: Record<string, string> = {
  EFECTIVO: 'Efectivo', ONLINE_SIMULADO: 'En línea', TRANSFERENCIA: 'Transferencia', TARJETA: 'Tarjeta', YAPE_PLIN: 'Yape o Plin'
};

/** Panorama de finanzas del mes: cuánto se cobró, por qué medio, cuánto falta y qué vence pronto. */
@Component({
  selector: 'app-finanzas',
  imports: [DatePipe, DecimalPipe, RouterLink, PageHeaderComponent],
  template: `
    <div class="page">
      <app-page-header titulo="Finanzas" subtitulo="Lo cobrado, lo que falta y lo que vence pronto.">
        <input class="input" type="month" aria-label="Mes" [value]="mes()" (change)="cambiarMes($any($event.target).value)" />
      </app-page-header>

      <section class="kpis" aria-label="Indicadores del mes">
        <div class="kpi"><div class="kpi-header"><div class="etiqueta">Cobrado en el mes</div></div>
          <div class="valor" style="color: var(--ok)">S/ {{ total() | number: '1.2-2' }}</div>
          <div class="kpi-footer"><span class="nota">{{ pagos().length }} pago(s) confirmado(s)</span></div></div>
        <div class="kpi"><div class="kpi-header"><div class="etiqueta">Ticket promedio</div></div>
          <div class="valor">S/ {{ promedio() | number: '1.2-2' }}</div>
          <div class="kpi-footer"><span class="nota">por pago</span></div></div>
        <div class="kpi"><div class="kpi-header"><div class="etiqueta">Deuda vencida</div></div>
          <div class="valor" [style.color]="deuda() > 0 ? 'var(--danger)' : 'var(--ink)'">S/ {{ deuda() | number: '1.2-2' }}</div>
          <div class="kpi-footer"><a class="nota" routerLink="/pagos/morosos">{{ morosos() }} alumno(s) →</a></div></div>
        <div class="kpi"><div class="kpi-header"><div class="etiqueta">Por vencer (15 días)</div></div>
          <div class="valor">S/ {{ porVencerTotal() | number: '1.2-2' }}</div>
          <div class="kpi-footer"><span class="nota">{{ porVencer().length }} cuota(s)</span></div></div>
      </section>

      <div class="dos-col">
        <section class="card"><div class="card-body">
          <h2 class="sec-titulo">Cobros por medio de pago</h2>
          @for (m of porMedio(); track m.medio) {
            <div class="cupo-fila" style="grid-template-columns: 110px 1fr 90px">
              <span class="cupo-nom">{{ m.etiqueta }}</span>
              <div class="barra-cupo" role="img" [attr.aria-label]="m.pct + ' por ciento del total'"><span [style.width.%]="m.pct"></span></div>
              <span class="cupo-num" style="width: auto">S/ {{ m.monto | number: '1.2-2' }}</span>
            </div>
          } @empty { <p class="muted">{{ cargando() ? 'Cargando…' : 'No hay cobros en este mes.' }}</p> }
        </div></section>

        <section class="card"><div class="card-body">
          <h2 class="sec-titulo">Cobrado por día</h2>
          @if (porDia().length) {
            <div class="barras-dia" role="img" aria-label="Cobrado por día del mes">
              @for (d of porDia(); track d.dia) {
                <div class="bd" [title]="'Día ' + d.dia + ': S/ ' + (d.monto | number: '1.2-2')"><span [style.height.%]="d.pct"></span><small>{{ d.dia }}</small></div>
              }
            </div>
          } @else { <p class="muted">{{ cargando() ? 'Cargando…' : 'Sin movimientos.' }}</p> }
        </div></section>
      </div>

      <section class="card"><div class="card-body">
        <div class="sec-cab"><h2 class="sec-titulo">Vencen en los próximos 15 días</h2><a class="btn btn-sm" routerLink="/pagos/cuentas">Ver cuentas</a></div>
        <div class="table-wrap"><table class="table">
          <thead><tr><th>Alumno</th><th>Cuota</th><th>Vence</th><th style="text-align: right">Saldo</th></tr></thead>
          <tbody>
            @for (c of porVencer().slice(0, 8); track c.id) {
              <tr><td><strong>{{ nombres.nombres()[c.alumnoId] ?? 'Alumno ' + c.alumnoId }}</strong></td><td>{{ c.descripcion }}</td>
                <td>{{ c.fechaVencimiento | date: 'd MMM' }}</td><td style="text-align: right">S/ {{ c.saldo | number: '1.2-2' }}</td></tr>
            } @empty { <tr><td colspan="4" class="vacio">{{ cargando() ? 'Cargando…' : 'Nada por vencer en ese plazo.' }}</td></tr> }
          </tbody>
        </table></div>
      </div></section>

      <section class="card"><div class="card-body">
        <div class="sec-cab"><h2 class="sec-titulo">Últimos cobros</h2><a class="btn btn-sm" routerLink="/pagos/cobranza">Ver cobranza completa</a></div>
        <div class="table-wrap"><table class="table">
          <thead><tr><th>Fecha</th><th>Alumno</th><th>Medio</th><th>Comprobante</th><th style="text-align: right">Monto</th></tr></thead>
          <tbody>
            @for (p of recientes(); track p.id) {
              <tr><td>{{ p.pagadoEn | date: 'd MMM, HH:mm' }}</td><td>{{ nombres.nombres()[p.alumnoId] ?? 'Alumno ' + p.alumnoId }}</td>
                <td>{{ medio(p.medio) }}</td><td class="muted">{{ p.comprobante?.numeroCompleto ?? '—' }}</td>
                <td style="text-align: right"><strong>S/ {{ p.montoTotal | number: '1.2-2' }}</strong></td></tr>
            } @empty { <tr><td colspan="5" class="vacio">{{ cargando() ? 'Cargando…' : 'Aún no hay cobros este mes.' }}</td></tr> }
          </tbody>
        </table></div>
      </div></section>
    </div>
  `,
  styles: `
    .barras-dia { display: flex; align-items: flex-end; gap: 4px; justify-content: flex-start; height: 150px; }
    .bd { flex: 1; max-width: 44px; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; height: 100%; min-width: 8px; }
    .bd span { width: 100%; background: var(--primary); border-radius: 4px 4px 0 0; min-height: 3px; }
    .bd:hover span { background: var(--primary-hover); }
    .bd small { font-size: .65rem; color: var(--ink-2); margin-top: 3px; }
  `
})
export class FinanzasComponent implements OnInit {
  private readonly cobros = inject(CobroService);
  private readonly config = inject(PagoConfigService);
  protected readonly nombres = inject(NombresAlumnoService);

  protected readonly mes = signal(new Date().toISOString().slice(0, 7));
  protected readonly pagos = signal<Pago[]>([]);
  protected readonly deuda = signal(0);
  protected readonly morosos = signal(0);
  protected readonly porVencer = signal<Cuota[]>([]);
  protected readonly cargando = signal(true);

  protected readonly total = computed(() => this.pagos().reduce((s, p) => s + p.montoTotal, 0));
  protected readonly promedio = computed(() => (this.pagos().length ? this.total() / this.pagos().length : 0));
  protected readonly porVencerTotal = computed(() => this.porVencer().reduce((s, c) => s + c.saldo, 0));
  protected readonly recientes = computed(() => [...this.pagos()].sort((a, b) => b.pagadoEn.localeCompare(a.pagadoEn)).slice(0, 8));

  protected readonly porMedio = computed(() => {
    const t = this.total() || 1;
    const acc = new Map<string, number>();
    for (const p of this.pagos()) acc.set(p.medio, (acc.get(p.medio) ?? 0) + p.montoTotal);
    return [...acc.entries()].sort((a, b) => b[1] - a[1]).map(([medio, monto]) => ({ medio, etiqueta: this.medio(medio), monto, pct: Math.round((monto / t) * 100) }));
  });

  protected readonly porDia = computed(() => {
    const acc = new Map<number, number>();
    for (const p of this.pagos()) {
      const d = new Date(p.pagadoEn).getDate();
      acc.set(d, (acc.get(d) ?? 0) + p.montoTotal);
    }
    const max = Math.max(1, ...acc.values());
    return [...acc.entries()].sort((a, b) => a[0] - b[0]).map(([dia, monto]) => ({ dia, monto, pct: Math.max(4, Math.round((monto / max) * 100)) }));
  });

  ngOnInit(): void {
    this.cargarMes();
    forkJoin({
      mor: this.config.morosos().pipe(catchError(() => of(null))),
      cuotas: this.cobros.cuotas({ estado: 'PENDIENTE', size: 100 }).pipe(catchError(() => of(null)))
    }).subscribe(({ mor, cuotas }) => {
      this.deuda.set(mor?.totalDeudaAcumulada ?? 0);
      this.morosos.set(mor?.totalAlumnosMorosos ?? 0);
      const limite = new Date();
      limite.setDate(limite.getDate() + 15);
      const hoy = new Date().toISOString().slice(0, 10);
      const prox = (cuotas?.contenido ?? [])
        .filter(c => c.fechaVencimiento >= hoy && c.fechaVencimiento <= limite.toISOString().slice(0, 10))
        .sort((a, b) => a.fechaVencimiento.localeCompare(b.fechaVencimiento));
      this.porVencer.set(prox);
      this.nombres.cargar(prox.slice(0, 8).map(c => c.alumnoId));
    });
  }

  protected cambiarMes(valor: string): void {
    if (!valor) return;
    this.mes.set(valor);
    this.cargarMes();
  }

  protected medio(m: MedioPago | string): string {
    return ETIQUETA_MEDIO[m] ?? m;
  }

  private cargarMes(): void {
    const [a, m] = this.mes().split('-').map(Number);
    const desde = `${this.mes()}-01`;
    const hasta = `${this.mes()}-${String(new Date(a, m, 0).getDate()).padStart(2, '0')}`;
    this.cargando.set(true);
    this.cobros.reporteCobranza(desde, hasta, undefined, 0, 200).pipe(catchError(() => of(null))).subscribe(r => {
      const ok = (r?.contenido ?? []).filter(p => p.estado === 'CONFIRMADO');
      this.pagos.set(ok);
      this.nombres.cargar(ok.slice(0, 50).map(p => p.alumnoId));
      this.cargando.set(false);
    });
  }
}
