import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { Boletin, Promedio, ResultadoAnual } from '../../../core/models/calificacion.model';
import { Anio, Periodo, Seccion } from '../../../core/models/planificacion.model';
import { AuthService } from '../../../core/services/auth.service';
import { CalificacionService } from '../../../core/services/calificacion.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-cierre-periodo',
  imports: [ReactiveFormsModule, DatePipe, DecimalPipe, ModalComponent, PageHeaderComponent],
  templateUrl: './cierre.component.html'
})
export class CierrePeriodoComponent implements OnInit {
  private readonly api = inject(CalificacionService);
  private readonly plan = inject(PlanificacionService);
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  protected readonly esAdmin = inject(AuthService).isAdmin;

  protected readonly anios = signal<Anio[]>([]);
  protected readonly secciones = signal<Seccion[]>([]);
  protected readonly periodos = signal<Periodo[]>([]);
  protected readonly cursos = signal<Record<number, string>>({});
  protected readonly promedios = signal<Promedio[]>([]);
  protected readonly resultados = signal<ResultadoAnual[]>([]);
  protected readonly boletines = signal<Boletin[]>([]);
  protected readonly cargando = signal(false);
  protected readonly ocupado = signal(false);

  protected readonly filtro = this.fb.nonNullable.group({ anioId: 0, seccionId: 0, periodoId: 0 });

  protected readonly reabriendo = signal(false);
  protected readonly motivo = this.fb.nonNullable.control('');
  protected readonly errorForm = signal<string | null>(null);

  ngOnInit(): void {
    this.plan.anios().subscribe(a => {
      this.anios.set(a);
      this.filtro.patchValue({ anioId: (a.find(x => x.estado === 'ACTIVO') ?? a[0])?.id ?? 0 });
      this.alCambiarAnio();
    });
  }

  protected alCambiarAnio(): void {
    const anioId = this.filtro.controls.anioId.value;
    this.promedios.set([]);
    if (!anioId) return;
    // Periodos y secciones se piden juntos: los promedios necesitan ambos y si uno llega después la tabla quedaba vacía.
    forkJoin({ periodos: this.plan.periodos(anioId), secciones: this.plan.secciones(anioId) }).subscribe(({ periodos, secciones }) => {
      this.periodos.set(periodos);
      this.secciones.set(secciones);
      this.filtro.patchValue({
        periodoId: (periodos.find(x => x.estado === 'ACTIVO') ?? periodos[0])?.id ?? 0,
        seccionId: secciones[0]?.id ?? 0
      });
      this.cargar();
    });
  }

  protected cargar(): void {
    const { seccionId, periodoId } = this.filtro.getRawValue();
    this.promedios.set([]);
    this.resultados.set([]);
    if (!seccionId || !periodoId) return;
    const sec = this.secciones().find(s => s.id === seccionId);
    if (sec) {
      this.plan.malla(sec.gradoId).subscribe(m => this.cursos.set(Object.fromEntries(m.cursos.map(c => [c.cursoId, c.cursoNombre]))));
    }
    this.cargando.set(true);
    this.api.promedios(seccionId, periodoId).subscribe({
      next: p => {
        this.promedios.set(p);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar los promedios.'));
      }
    });
  }

  protected curso(id: number): string {
    return this.cursos()[id] ?? `Curso ${id}`;
  }

  protected get cerrado(): boolean {
    return this.promedios().length > 0 && this.promedios().every(p => p.estado === 'CERRADO');
  }

  protected async cerrarPeriodo(): Promise<void> {
    const { seccionId, periodoId } = this.filtro.getRawValue();
    if (!(await this.confirmacion.confirmar('¿Cerrar el periodo para esta sección? Se congelan los promedios y ya no se podrán cambiar notas sin una reapertura autorizada.', 'Cerrar periodo', true))) return;
    this.ocupado.set(true);
    this.api.cerrarPeriodo(seccionId, periodoId).subscribe({
      next: () => {
        this.ocupado.set(false);
        this.avisos.success('Periodo cerrado.');
        this.cargar();
      },
      error: e => {
        this.ocupado.set(false);
        this.avisos.error(mensajeError(e, 'No se pudo cerrar el periodo.'));
      }
    });
  }

  protected abrirReapertura(): void {
    this.motivo.setValue('');
    this.errorForm.set(null);
    this.reabriendo.set(true);
  }

  protected cerrarDialogo(): void {
    this.reabriendo.set(false);
  }

  protected reabrir(): void {
    if (!this.motivo.value.trim()) {
      this.errorForm.set('El motivo de la reapertura es obligatorio y queda auditado.');
      return;
    }
    const { seccionId, periodoId } = this.filtro.getRawValue();
    this.ocupado.set(true);
    this.api.reabrirPeriodo(seccionId, periodoId, this.motivo.value.trim()).subscribe({
      next: () => {
        this.ocupado.set(false);
        this.avisos.success('Periodo reabierto.');
        this.cerrarDialogo();
        this.cargar();
      },
      error: e => {
        this.ocupado.set(false);
        this.errorForm.set(mensajeError(e));
      }
    });
  }

  protected generarBoletines(): void {
    const { seccionId, periodoId } = this.filtro.getRawValue();
    this.ocupado.set(true);
    this.api.generarBoletines({ seccionId, periodoId }).subscribe({
      next: b => {
        this.ocupado.set(false);
        this.boletines.set(b);
        this.avisos.success(`${b.length} boletines generados. Las familias reciben un aviso.`);
      },
      error: e => {
        this.ocupado.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron generar los boletines. El periodo debe estar cerrado.'));
      }
    });
  }

  protected descargar(b: Boletin): void {
    this.api.urlBoletin(b.id).subscribe({
      next: r => {
        const url = r.urlDescarga ?? r.url;
        if (url) window.open(url, '_blank', 'noopener,noreferrer');
      },
      error: e => this.avisos.error(mensajeError(e, 'No se pudo abrir el boletín.'))
    });
  }

  protected verResultadosAnuales(): void {
    this.api.resultadosAnuales(this.filtro.controls.seccionId.value).subscribe({
      next: r => this.resultados.set(r),
      error: e => this.avisos.error(mensajeError(e, 'No se pudieron cargar los resultados anuales.'))
    });
  }

  protected claseResultado(e: string): string {
    return e === 'APROBADO' ? 'badge-ok' : e === 'RECUPERACION' ? 'badge-warn' : 'badge-danger';
  }
}
