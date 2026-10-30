import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Matricula, MatriculaDetalle } from '../../core/models/matricula.model';
import { Seccion } from '../../core/models/planificacion.model';
import { MatriculaService } from '../../core/services/matricula.service';
import { NotificationService } from '../../core/services/notification.service';
import { PlanificacionService } from '../../core/services/planificacion.service';
import { mensajeError } from '../../core/utils/errores';
import { ModalComponent } from '../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

/**
 * Aprobación administrativa de matrículas. Una matrícula pagada (en línea o en caja) queda POR_APROBAR; solo la administración la
 * aprueba. Al aprobar: pasa a MATRICULADO, se generan las pensiones y, si fue en línea, el apoderado recibe por correo su usuario,
 * su clave inicial, la constancia y el horario. Al rechazar: se anula, se libera el cupo y se avisa por correo con el motivo.
 */
@Component({
  selector: 'app-aprobaciones',
  imports: [ReactiveFormsModule, DatePipe, ModalComponent, PageHeaderComponent],
  template: `
    <div class="page">
      <app-page-header titulo="Matrículas por aprobar" subtitulo="Revisa cada matrícula pagada y apruébala: la familia recibe su acceso por correo" />

      <div class="aviso aviso-info" style="margin-bottom: 14px">
        <strong>Cómo funciona:</strong> el pago deja la matrícula «por aprobar» y reserva el cupo. Al aprobarla se matricula al alumno, se generan las pensiones
        y se envía al apoderado el correo con el <b>usuario, la clave inicial, la constancia y el horario</b>.
      </div>

      <div class="card">
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Código</th><th>Alumno</th><th>Sección</th><th>Origen</th><th>Pagada</th><th></th></tr></thead>
            <tbody>
              @for (m of pendientes(); track m.id) {
                <tr>
                  <td>{{ m.codigo }}</td>
                  <td><strong>{{ m.alumnoNombre }}</strong></td>
                  <td>{{ nombreSeccion(m.seccionId) }}</td>
                  <td><span class="badge badge-gray">{{ enLinea(m) ? 'En línea' : 'Presencial' }}</span></td>
                  <td class="muted">{{ (m.pagadaEn ?? m.creadoEn) | date: 'dd/MM/yyyy HH:mm' }}</td>
                  <td class="acciones"><button type="button" class="btn btn-sm btn-primary" (click)="revisar(m)">Revisar</button></td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="vacio">{{ cargando() ? 'Cargando…' : 'No hay matrículas pendientes de aprobación. 🎉' }}</td></tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    </div>

    @if (detalle(); as d) {
      <app-modal [titulo]="'Revisar ' + d.matricula.codigo" [ancho]="560" (cerrar)="cerrar()">
        <dl class="dl">
          <div><dt>Alumno</dt><dd>{{ d.matricula.alumnoNombre }}</dd></div>
          <div><dt>Sección</dt><dd>{{ nombreSeccion(d.matricula.seccionId) }}</dd></div>
          <div><dt>Origen</dt><dd>{{ enLinea(d.matricula) ? 'Matrícula en línea (la familia pagó con tarjeta)' : 'Presencial (pagada en caja)' }}</dd></div>
          <div><dt>Pago de matrícula</dt><dd>Registrado el {{ (d.matricula.pagadaEn ?? d.matricula.creadoEn) | date: 'dd/MM/yyyy HH:mm' }}</dd></div>
        </dl>

        <h3 style="margin: 16px 0 6px">Expediente</h3>
        @for (r of d.requisitos; track r.id) {
          <div class="fila-req"><span [class]="'badge ' + (r.cumplido ? 'badge-ok' : 'badge-gray')">{{ r.cumplido ? 'Cumplido' : 'Pendiente' }}</span> {{ r.tipo.replaceAll('_', ' ').toLowerCase() }}</div>
        }
        @if (pendientesExpediente(d) > 0) {
          <div class="aviso aviso-warn" style="margin-top: 10px">Hay documentos pendientes. Puedes aprobar igual (el expediente se completa en secretaría) o rechazar.</div>
        }

        @if (modo() === 'rechazar') {
          <form [formGroup]="formRechazo" (ngSubmit)="rechazar()" id="form-rechazo" novalidate style="margin-top: 14px">
            <div class="field">
              <label for="rech-motivo">Motivo del rechazo (se envía a la familia)</label>
              <textarea id="rech-motivo" rows="3" formControlName="motivo" style="padding: 10px 12px; min-height: 80px"></textarea>
              @if (formRechazo.controls.motivo.touched && formRechazo.controls.motivo.invalid) { <span class="error">El motivo es obligatorio.</span> }
            </div>
            <p class="muted" style="font-size: .85rem">Se libera el cupo. El pago de la matrícula se devuelve desde Finanzas → Cobranza.</p>
          </form>
        }
        @if (error()) { <div class="aviso aviso-danger" style="margin-top: 14px" role="alert">{{ error() }}</div> }

        <ng-container footer>
          <button type="button" class="btn" (click)="cerrar()">Cerrar</button>
          @if (modo() === 'revisar') {
            <button type="button" class="btn btn-danger" [disabled]="guardando()" (click)="modo.set('rechazar')">Rechazar…</button>
            <button type="button" class="btn btn-primary" [disabled]="guardando()" (click)="aprobar()">{{ guardando() ? 'Aprobando…' : 'Aprobar matrícula' }}</button>
          } @else {
            <button type="button" class="btn" (click)="modo.set('revisar')">Volver</button>
            <button type="submit" form="form-rechazo" class="btn btn-danger" [disabled]="guardando()">{{ guardando() ? 'Rechazando…' : 'Confirmar rechazo' }}</button>
          }
        </ng-container>
      </app-modal>
    }
  `,
  styles: `.fila-req { padding: 3px 0; text-transform: none; } .fila-req .badge { margin-right: 8px; }`
})
export class AprobacionesComponent implements OnInit {
  private readonly api = inject(MatriculaService);
  private readonly plan = inject(PlanificacionService);
  private readonly avisos = inject(NotificationService);
  private readonly fb = inject(FormBuilder);

  protected readonly pendientes = signal<Matricula[]>([]);
  protected readonly secciones = signal<Seccion[]>([]);
  protected readonly cargando = signal(true);
  protected readonly detalle = signal<MatriculaDetalle | null>(null);
  protected readonly modo = signal<'revisar' | 'rechazar'>('revisar');
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly formRechazo = this.fb.nonNullable.group({ motivo: ['', [Validators.required, Validators.maxLength(255)]] });

  ngOnInit(): void {
    this.plan.secciones().subscribe(s => this.secciones.set(s));
    this.cargar();
  }

  private cargar(): void {
    this.cargando.set(true);
    this.api.listar({ estado: 'POR_APROBAR', size: 100 }).subscribe({
      next: p => { this.pendientes.set(p.contenido); this.cargando.set(false); },
      error: e => { this.cargando.set(false); this.avisos.error(mensajeError(e, 'No se pudieron cargar las matrículas por aprobar.')); }
    });
  }

  protected nombreSeccion(id: number): string {
    const s = this.secciones().find(x => x.id === id);
    return s ? `${s.gradoNombre} · ${s.nombre}` : `Sección ${id}`;
  }

  protected enLinea(m: Matricula): boolean {
    return (m.observaciones ?? '').toLowerCase().includes('en línea');
  }

  protected pendientesExpediente(d: MatriculaDetalle): number {
    return d.requisitos.filter(r => !r.cumplido).length;
  }

  protected revisar(m: Matricula): void {
    this.error.set(null);
    this.modo.set('revisar');
    this.formRechazo.reset({ motivo: '' });
    this.api.detalle(m.id).subscribe({
      next: d => this.detalle.set(d),
      error: e => this.avisos.error(mensajeError(e, 'No se pudo abrir la matrícula.'))
    });
  }

  protected cerrar(): void {
    this.detalle.set(null);
  }

  protected aprobar(): void {
    const d = this.detalle();
    if (!d) return;
    this.guardando.set(true);
    this.error.set(null);
    this.api.aprobar(d.matricula.id).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(this.enLinea(d.matricula)
          ? 'Matrícula aprobada. Enviamos al apoderado el correo con su usuario, clave inicial, constancia y horario.'
          : 'Matrícula aprobada. Se generaron las pensiones; las cuentas de acceso se crean en «Cuentas de acceso».');
        this.cerrar();
        this.cargar();
      },
      error: e => { this.guardando.set(false); this.error.set(mensajeError(e, 'No se pudo aprobar la matrícula.')); }
    });
  }

  protected rechazar(): void {
    const d = this.detalle();
    if (!d) return;
    if (this.formRechazo.invalid) { this.formRechazo.markAllAsTouched(); return; }
    this.guardando.set(true);
    this.error.set(null);
    this.api.rechazar(d.matricula.id, this.formRechazo.getRawValue().motivo.trim()).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success('Matrícula rechazada y cupo liberado.');
        this.cerrar();
        this.cargar();
      },
      error: e => { this.guardando.set(false); this.error.set(mensajeError(e, 'No se pudo rechazar la matrícula.')); }
    });
  }
}
