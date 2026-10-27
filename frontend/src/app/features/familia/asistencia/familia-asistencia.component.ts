import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AsistenciaHijo, EstadoAsistencia, Justificacion, TIPOS_JUSTIFICACION, TipoJustificacion } from '../../../core/models/asistencia.model';
import { AsistenciaService } from '../../../core/services/asistencia.service';
import { FamiliaContextoService } from '../../../core/services/familia-contexto.service';
import { NotificationService } from '../../../core/services/notification.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { SelectorHijoComponent } from '../../../shared/components/selector-hijo/selector-hijo.component';

const CLASE: Record<EstadoAsistencia, string> = { PRESENTE: 'badge-ok', TARDANZA: 'badge-warn', FALTA: 'badge-danger', JUSTIFICADO: 'badge-violet' };
const MAX_BYTES = 5 * 1024 * 1024;

@Component({
  selector: 'app-familia-asistencia',
  imports: [ReactiveFormsModule, DatePipe, DecimalPipe, ModalComponent, PageHeaderComponent, SelectorHijoComponent],
  templateUrl: './familia-asistencia.component.html'
})
export class FamiliaAsistenciaComponent implements OnInit {
  protected readonly ctx = inject(FamiliaContextoService);
  private readonly api = inject(AsistenciaService);
  private readonly http = inject(HttpClient);
  private readonly avisos = inject(NotificationService);
  private readonly fb = inject(FormBuilder);

  protected readonly tipos = TIPOS_JUSTIFICACION;
  protected readonly datos = signal<AsistenciaHijo[]>([]);
  protected readonly justificaciones = signal<Justificacion[]>([]);
  protected readonly cargando = signal(true);

  protected readonly hijo = computed(() => this.datos().find(a => a.alumnoId === this.ctx.seleccionado()?.alumnoId) ?? null);
  protected readonly justificacionesHijo = computed(() => this.justificaciones().filter(j => j.alumnoId === this.ctx.seleccionado()?.alumnoId));

  protected readonly abierto = signal(false);
  protected readonly archivo = signal<File | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    fechaDesde: ['', Validators.required],
    fechaHasta: ['', Validators.required],
    tipo: ['MEDICA' as TipoJustificacion, Validators.required],
    motivo: ['', [Validators.required, Validators.maxLength(500)]]
  });

  ngOnInit(): void {
    this.ctx.cargar().subscribe({ error: e => this.avisos.error(mensajeError(e)) });
    this.recargar();
  }

  protected clase(e: EstadoAsistencia): string {
    return CLASE[e];
  }

  protected claseJ(e: string): string {
    return e === 'APROBADA' ? 'badge-ok' : e === 'RECHAZADA' ? 'badge-danger' : 'badge-warn';
  }

  protected nivel(p: number): string {
    return p < 70 ? 'llena' : p < 85 ? 'alta' : '';
  }

  private recargar(): void {
    this.cargando.set(true);
    this.api.mias().subscribe({
      next: a => {
        this.datos.set(a);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudo cargar la asistencia.'));
      }
    });
    if (this.ctx.esApoderado()) this.api.misJustificaciones().subscribe({ next: j => this.justificaciones.set(j), error: () => undefined });
  }

  // --- justificar ---
  protected nueva(): void {
    const hoy = new Date().toISOString().slice(0, 10);
    this.form.reset({ fechaDesde: hoy, fechaHasta: hoy, tipo: 'MEDICA', motivo: '' });
    this.archivo.set(null);
    this.errorForm.set(null);
    this.abierto.set(true);
  }

  protected cerrar(): void {
    this.abierto.set(false);
  }

  protected elegirArchivo(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const f = input.files?.[0] ?? null;
    if (f && f.size > MAX_BYTES) {
      this.avisos.error('El archivo supera los 5 MB.');
      input.value = '';
      this.archivo.set(null);
      return;
    }
    this.archivo.set(f);
  }

  protected enviar(): void {
    const h = this.ctx.seleccionado();
    if (!h || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    if (v.fechaHasta < v.fechaDesde) {
      this.errorForm.set('La fecha final no puede ser anterior a la inicial.');
      return;
    }
    this.guardando.set(true);
    this.errorForm.set(null);
    this.api.solicitarJustificacion({ alumnoId: h.alumnoId, fechaDesde: v.fechaDesde, fechaHasta: v.fechaHasta, tipo: v.tipo, motivo: v.motivo.trim() }).subscribe({
      next: j => {
        const f = this.archivo();
        if (!f) return this.terminar();
        // El sustento se sube directo al almacenamiento con una URL firmada: no pasa por el servidor.
        const tipo = f.type || 'application/octet-stream';
        this.api.urlSubidaSustento(j.id, f.name, tipo).subscribe({
          next: u =>
            this.http.put(u.uploadUrl, f, { headers: { 'Content-Type': tipo }, responseType: 'text' }).subscribe({
              next: () => this.terminar(),
              error: () => this.terminar('La solicitud se envió, pero no se pudo subir el archivo de sustento.')
            }),
          error: () => this.terminar('La solicitud se envió, pero no se pudo preparar la subida del archivo.')
        });
      },
      error: e => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(e));
      }
    });
  }

  private terminar(advertencia?: string): void {
    this.guardando.set(false);
    if (advertencia) this.avisos.warning(advertencia);
    else this.avisos.success('Justificación enviada. Secretaría la revisará y te avisaremos.');
    this.cerrar();
    this.recargar();
  }
}
