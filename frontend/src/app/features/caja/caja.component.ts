import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AlumnoResumen } from '../../core/models/alumno.model';
import { Caja, CierreCaja, Cuota, EstadoCuenta, MEDIOS_PAGO, MedioPago, Pago, TipoComprobante } from '../../core/models/cobro.model';
import { AuthService } from '../../core/services/auth.service';
import { CobroService } from '../../core/services/cobro.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NotificationService } from '../../core/services/notification.service';
import { mensajeError } from '../../core/utils/errores';
import { BuscadorAlumnoComponent } from '../../shared/components/buscador-alumno/buscador-alumno.component';
import { ModalComponent } from '../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

const PAGABLES = ['PENDIENTE', 'VENCIDA', 'PARCIAL'];

@Component({
  selector: 'app-caja',
  imports: [ReactiveFormsModule, DatePipe, DecimalPipe, BuscadorAlumnoComponent, ModalComponent, PageHeaderComponent],
  templateUrl: './caja.component.html'
})
export class CajaComponent implements OnInit {
  private readonly api = inject(CobroService);
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);
  protected readonly usuario = inject(AuthService).currentUser;

  protected readonly medios = MEDIOS_PAGO;
  protected readonly caja = signal<Caja | null>(null);
  protected readonly cargandoCaja = signal(true);
  protected readonly cierre = signal<CierreCaja | null>(null);
  protected readonly parcial = signal<CierreCaja | null>(null);

  // apertura / cierre
  protected readonly montoApertura = this.fb.nonNullable.control(0, [Validators.required, Validators.min(0)]);
  protected readonly montoDeclarado = this.fb.nonNullable.control(0, [Validators.required, Validators.min(0)]);
  protected readonly cerrando = signal(false);
  protected readonly ocupado = signal(false);
  protected readonly errorCaja = signal<string | null>(null);

  // cobro
  protected readonly alumno = signal<AlumnoResumen | null>(null);
  protected readonly cuenta = signal<EstadoCuenta | null>(null);
  protected readonly montos = signal<Record<number, number>>({});
  protected readonly cobro = this.fb.nonNullable.group({
    pagadorNombre: [''],
    pagadorDocumento: [''],
    medio: ['EFECTIVO' as MedioPago, Validators.required],
    referencia: [''],
    tipoComprobante: ['RECIBO' as TipoComprobante, Validators.required]
  });
  protected readonly cobrando = signal(false);
  protected readonly errorCobro = signal<string | null>(null);
  protected readonly recibo = signal<Pago | null>(null);
  /** Una clave por intento de cobro: pulsar "Cobrar" dos veces no genera dos pagos ni dos comprobantes. */
  private claveIntento = crypto.randomUUID();

  protected readonly pagables = computed(() => (this.cuenta()?.cuotas ?? []).filter((c: Cuota) => PAGABLES.includes(c.estado)));
  protected readonly total = computed(() => Object.values(this.montos()).reduce((s, n) => s + n, 0));
  protected readonly nElegidas = computed(() => Object.keys(this.montos()).length);

  ngOnInit(): void {
    this.cargarCaja();
  }

  private cargarCaja(): void {
    this.cargandoCaja.set(true);
    this.api.cajaActual().subscribe({
      next: c => {
        this.caja.set(c);
        this.cargandoCaja.set(false);
        this.actualizarParcial();
      },
      error: (e: HttpErrorResponse) => {
        this.cargandoCaja.set(false);
        this.caja.set(null); // 404: no hay caja abierta
        if (e.status !== 404) this.avisos.error(mensajeError(e, 'No se pudo consultar la caja.'));
      }
    });
  }

  private actualizarParcial(): void {
    const c = this.caja();
    if (c) this.api.reporteCaja(c.id).subscribe({ next: r => this.parcial.set(r), error: () => undefined });
  }

  // --- abrir / cerrar ---
  protected abrirCaja(): void {
    if (this.montoApertura.invalid) return;
    this.ocupado.set(true);
    this.errorCaja.set(null);
    this.api.abrirCaja(this.montoApertura.value).subscribe({
      next: c => {
        this.ocupado.set(false);
        this.caja.set(c);
        this.cierre.set(null);
        this.avisos.success('Caja abierta.');
        this.actualizarParcial();
      },
      error: e => {
        this.ocupado.set(false);
        this.errorCaja.set(mensajeError(e));
      }
    });
  }

  protected pedirCierre(): void {
    this.montoDeclarado.setValue(this.parcial()?.efectivoEsperado ?? 0);
    this.errorCaja.set(null);
    this.cerrando.set(true);
  }

  protected cancelarCierre(): void {
    this.cerrando.set(false);
  }

  protected cerrarCaja(): void {
    if (this.montoDeclarado.invalid) return;
    this.ocupado.set(true);
    this.api.cerrarCaja(this.montoDeclarado.value).subscribe({
      next: r => {
        this.ocupado.set(false);
        this.cierre.set(r);
        this.caja.set(null);
        this.parcial.set(null);
        this.cerrando.set(false);
        this.limpiarCobro();
        this.avisos.success('Caja cerrada.');
      },
      error: e => {
        this.ocupado.set(false);
        this.errorCaja.set(mensajeError(e));
      }
    });
  }

  // --- cobrar ---
  protected elegirAlumno(a: AlumnoResumen | null): void {
    this.limpiarCobro();
    this.alumno.set(a);
    if (!a) return;
    this.api.estadoCuenta(a.id).subscribe({
      next: c => {
        this.cuenta.set(c);
        this.cobro.patchValue({ pagadorNombre: c.responsableNombre ?? '' });
      },
      error: e => this.avisos.error(mensajeError(e, 'No se pudo cargar la cuenta del alumno.'))
    });
  }

  private limpiarCobro(): void {
    this.alumno.set(null);
    this.cuenta.set(null);
    this.montos.set({});
    this.errorCobro.set(null);
    this.claveIntento = crypto.randomUUID();
  }

  protected elegida(id: number): boolean {
    return id in this.montos();
  }

  protected alternar(c: Cuota, marcada: boolean): void {
    this.montos.update(m => {
      const n = { ...m };
      if (marcada) n[c.id] = c.saldo;
      else delete n[c.id];
      return n;
    });
  }

  protected cambiarMonto(c: Cuota, texto: string): void {
    const v = Math.min(Math.max(Number(texto) || 0, 0), c.saldo);
    this.montos.update(m => ({ ...m, [c.id]: Math.round(v * 100) / 100 }));
  }

  protected cobrar(): void {
    const a = this.alumno();
    if (!a || this.nElegidas() === 0 || this.total() <= 0) return;
    const v = this.cobro.getRawValue();
    if (v.medio !== 'EFECTIVO' && !v.referencia.trim()) {
      this.errorCobro.set('Para tarjeta, Yape/Plin o transferencia, anota el número de operación.');
      return;
    }
    this.cobrando.set(true);
    this.errorCobro.set(null);
    this.api
      .registrar({
        alumnoId: a.id,
        pagadorNombre: v.pagadorNombre.trim() || undefined,
        pagadorDocumento: v.pagadorDocumento.trim() || undefined,
        montoTotal: Math.round(this.total() * 100) / 100,
        medio: v.medio,
        referencia: v.referencia.trim() || undefined,
        idempotencyKey: this.claveIntento,
        tipoComprobante: v.tipoComprobante,
        detalles: Object.entries(this.montos()).map(([id, monto]) => ({ cuotaId: Number(id), montoAplicado: monto }))
      })
      .subscribe({
        next: p => {
          this.cobrando.set(false);
          this.recibo.set(p);
          this.avisos.success('Cobro registrado.');
          this.actualizarParcial();
        },
        error: e => {
          this.cobrando.set(false);
          this.errorCobro.set(mensajeError(e, 'No se pudo registrar el cobro.'));
        }
      });
  }

  protected cerrarRecibo(): void {
    this.recibo.set(null);
    const a = this.alumno();
    this.montos.set({});
    this.claveIntento = crypto.randomUUID();
    if (a) this.elegirAlumno(a);
  }

  protected imprimir(): void {
    window.print();
  }

  protected async anularCobroVacio(): Promise<void> {
    if (this.nElegidas() === 0) return;
    if (await this.confirmacion.confirmar('¿Quitar la selección de cuotas?', 'Quitar')) this.montos.set({});
  }
}
