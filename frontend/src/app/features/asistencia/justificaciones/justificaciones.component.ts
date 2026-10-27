import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EstadoJustificacion, Justificacion } from '../../../core/models/asistencia.model';
import { AlumnoService } from '../../../core/services/alumno.service';
import { AsistenciaService } from '../../../core/services/asistencia.service';
import { NotificationService } from '../../../core/services/notification.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

const CLASE: Record<EstadoJustificacion, string> = { PENDIENTE: 'badge-warn', APROBADA: 'badge-ok', RECHAZADA: 'badge-danger' };

@Component({
  selector: 'app-justificaciones',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, ModalComponent, PageHeaderComponent],
  templateUrl: './justificaciones.component.html'
})
export class JustificacionesComponent implements OnInit {
  private readonly api = inject(AsistenciaService);
  private readonly alumnosApi = inject(AlumnoService);
  private readonly avisos = inject(NotificationService);
  private readonly fb = inject(FormBuilder);

  protected readonly lista = signal<Justificacion[]>([]);
  protected readonly nombres = signal<Record<number, string>>({});
  protected readonly total = signal(0);
  protected readonly paginas = signal(1);
  protected readonly pagina = signal(0);
  protected readonly cargando = signal(false);
  protected readonly estado = this.fb.nonNullable.control<EstadoJustificacion | ''>('PENDIENTE');

  protected readonly resolviendo = signal<{ j: Justificacion; resolucion: 'APROBADA' | 'RECHAZADA' } | null>(null);
  protected readonly comentario = this.fb.nonNullable.control('');
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);

  ngOnInit(): void {
    this.cargar(0);
  }

  protected clase(e: EstadoJustificacion): string {
    return CLASE[e];
  }

  protected nombre(id: number): string {
    return this.nombres()[id] ?? `Alumno ${id}`;
  }

  protected irA(p: number): void {
    if (p >= 0 && p < this.paginas()) this.cargar(p);
  }

  protected cargar(pagina = 0): void {
    this.cargando.set(true);
    this.api.bandeja(this.estado.value || undefined, pagina).subscribe({
      next: p => {
        this.lista.set(p.contenido);
        this.total.set(p.totalElementos);
        this.paginas.set(Math.max(1, p.totalPaginas));
        this.pagina.set(p.pagina);
        this.cargando.set(false);
        this.cargarNombres(p.contenido);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudo cargar la bandeja.'));
      }
    });
  }

  /** La justificación trae solo el id del alumno: se busca el nombre una vez por alumno distinto. */
  private cargarNombres(lista: Justificacion[]): void {
    const faltan = [...new Set(lista.map(j => j.alumnoId))].filter(id => !(id in this.nombres()));
    for (const id of faltan) {
      this.alumnosApi.obtener(id).subscribe({
        next: a => this.nombres.update(m => ({ ...m, [id]: a.nombreCompleto })),
        error: () => undefined
      });
    }
  }

  protected verSustento(j: Justificacion): void {
    this.api.archivoSustento(j.id).subscribe({
      next: r => {
        const url = r.url ?? r.urlDescarga;
        if (url) window.open(url, '_blank', 'noopener,noreferrer');
        else this.avisos.warning('Esta justificación no tiene archivo de sustento.');
      },
      error: e => this.avisos.error(mensajeError(e, 'No se pudo abrir el sustento.'))
    });
  }

  protected resolver(j: Justificacion, resolucion: 'APROBADA' | 'RECHAZADA'): void {
    this.comentario.setValue('');
    this.errorForm.set(null);
    this.resolviendo.set({ j, resolucion });
  }

  protected cerrar(): void {
    this.resolviendo.set(null);
  }

  protected confirmar(): void {
    const r = this.resolviendo();
    if (!r) return;
    if (r.resolucion === 'RECHAZADA' && !this.comentario.value.trim()) {
      this.errorForm.set('Para rechazar, explica el motivo al apoderado.');
      return;
    }
    this.guardando.set(true);
    this.api.resolver(r.j.id, r.resolucion, this.comentario.value.trim() || undefined).subscribe({
      next: res => {
        this.guardando.set(false);
        this.avisos.success(r.resolucion === 'APROBADA' ? `Justificación aprobada (${res.faltasJustificadas ?? 0} faltas justificadas).` : 'Justificación rechazada.');
        this.cerrar();
        this.cargar(this.pagina());
      },
      error: e => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(e));
      }
    });
  }
}
