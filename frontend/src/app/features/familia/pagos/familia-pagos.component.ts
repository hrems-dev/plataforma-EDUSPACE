import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, effect, inject, signal, untracked } from '@angular/core';
import { EstadoCuenta, EstadoCuota, Pago } from '../../../core/models/cobro.model';
import { CobroService } from '../../../core/services/cobro.service';
import { FamiliaContextoService } from '../../../core/services/familia-contexto.service';
import { NotificationService } from '../../../core/services/notification.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { SelectorHijoComponent } from '../../../shared/components/selector-hijo/selector-hijo.component';

const CLASE: Record<EstadoCuota, string> = { PENDIENTE: 'badge-warn', PARCIAL: 'badge-violet', PAGADA: 'badge-ok', VENCIDA: 'badge-danger', ANULADA: 'badge-gray' };
const PAGABLES: EstadoCuota[] = ['PENDIENTE', 'VENCIDA', 'PARCIAL'];

@Component({
  selector: 'app-familia-pagos',
  imports: [DatePipe, DecimalPipe, ModalComponent, PageHeaderComponent, SelectorHijoComponent],
  templateUrl: './familia-pagos.component.html'
})
export class FamiliaPagosComponent implements OnInit {
  protected readonly ctx = inject(FamiliaContextoService);
  private readonly api = inject(CobroService);
  private readonly avisos = inject(NotificationService);

  protected readonly cuenta = signal<EstadoCuenta | null>(null);
  protected readonly cargando = signal(true);
  protected readonly elegidas = signal<Set<number>>(new Set());

  protected readonly pagables = computed(() => (this.cuenta()?.cuotas ?? []).filter(c => PAGABLES.includes(c.estado)));
  protected readonly totalElegido = computed(() => this.pagables().filter(c => this.elegidas().has(c.id)).reduce((s, c) => s + c.saldo, 0));

  protected readonly confirmando = signal(false);
  protected readonly pagando = signal(false);
  protected readonly errorPago = signal<string | null>(null);
  protected readonly recibo = signal<Pago | null>(null);
  /** Clave de idempotencia del intento: si se pulsa "Pagar" dos veces, el servidor cobra una sola vez. */
  private claveIntento = '';

  constructor() {
    effect(() => {
      const h = this.ctx.seleccionado();
      untracked(() => this.cargar(h?.alumnoId));
    });
  }

  ngOnInit(): void {
    this.ctx.cargar().subscribe({ error: e => this.avisos.error(mensajeError(e)) });
  }

  protected clase(e: EstadoCuota): string {
    return CLASE[e];
  }

  protected pagable(e: EstadoCuota): boolean {
    return PAGABLES.includes(e);
  }

  private cargar(alumnoId?: number): void {
    this.elegidas.set(new Set());
    this.cuenta.set(null);
    if (!alumnoId) {
      this.cargando.set(false);
      return;
    }
    this.cargando.set(true);
    this.api.estadoCuenta(alumnoId).subscribe({
      next: c => {
        this.cuenta.set(c);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudo cargar la cuenta.'));
      }
    });
  }

  protected alternar(id: number, marcada: boolean): void {
    this.elegidas.update(s => {
      const n = new Set(s);
      if (marcada) n.add(id);
      else n.delete(id);
      return n;
    });
  }

  protected elegirVencidas(): void {
    this.elegidas.set(new Set(this.pagables().filter(c => c.estado === 'VENCIDA' || c.diasAtraso > 0).map(c => c.id)));
  }

  // --- pagar ---
  protected abrirPago(): void {
    if (this.elegidas().size === 0) return;
    this.claveIntento = crypto.randomUUID();
    this.errorPago.set(null);
    this.recibo.set(null);
    this.confirmando.set(true);
  }

  protected cerrar(): void {
    this.confirmando.set(false);
    if (this.recibo()) this.cargar(this.ctx.seleccionado()?.alumnoId);
  }

  protected pagar(): void {
    const h = this.ctx.seleccionado();
    if (!h) return;
    const detalles = this.pagables().filter(c => this.elegidas().has(c.id)).map(c => ({ cuotaId: c.id, montoAplicado: c.saldo }));
    this.pagando.set(true);
    this.errorPago.set(null);
    this.api
      .pagarOnline({ alumnoId: h.alumnoId, montoTotal: detalles.reduce((s, d) => s + d.montoAplicado, 0), medio: 'ONLINE_SIMULADO', idempotencyKey: this.claveIntento, detalles })
      .subscribe({
        next: p => {
          this.pagando.set(false);
          this.recibo.set(p);
          this.avisos.success('Pago registrado. Te enviamos el comprobante.');
        },
        error: e => {
          this.pagando.set(false);
          this.errorPago.set(mensajeError(e, 'No se pudo procesar el pago.'));
        }
      });
  }
}
