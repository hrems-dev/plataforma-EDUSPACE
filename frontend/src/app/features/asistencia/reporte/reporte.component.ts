import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ReporteSeccion } from '../../../core/models/asistencia.model';
import { Anio, Seccion } from '../../../core/models/planificacion.model';
import { AsistenciaService } from '../../../core/services/asistencia.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { mensajeError } from '../../../core/utils/errores';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-reporte-asistencia',
  imports: [ReactiveFormsModule, RouterLink, DecimalPipe, PageHeaderComponent],
  templateUrl: './reporte.component.html'
})
export class ReporteAsistenciaComponent implements OnInit {
  private readonly api = inject(AsistenciaService);
  private readonly plan = inject(PlanificacionService);
  private readonly avisos = inject(NotificationService);
  private readonly fb = inject(FormBuilder);

  protected readonly anios = signal<Anio[]>([]);
  protected readonly secciones = signal<Seccion[]>([]);
  protected readonly reporte = signal<ReporteSeccion | null>(null);
  protected readonly cargando = signal(false);

  protected readonly filtro = this.fb.nonNullable.group({ anioId: 0, seccionId: 0, desde: '', hasta: '' });

  ngOnInit(): void {
    this.plan.anios().subscribe(a => {
      this.anios.set(a);
      this.filtro.patchValue({ anioId: (a.find(x => x.estado === 'ACTIVO') ?? a[0])?.id ?? 0 });
      this.cargarSecciones();
    });
  }

  protected cargarSecciones(): void {
    this.reporte.set(null);
    const anioId = this.filtro.controls.anioId.value;
    if (!anioId) return;
    this.plan.secciones(anioId).subscribe(s => {
      this.secciones.set(s);
      this.filtro.patchValue({ seccionId: s[0]?.id ?? 0 });
      this.generar();
    });
  }

  protected generar(): void {
    const { seccionId, desde, hasta } = this.filtro.getRawValue();
    if (!seccionId) {
      this.reporte.set(null);
      return;
    }
    this.cargando.set(true);
    this.api.reporteSeccion(seccionId, desde || undefined, hasta || undefined).subscribe({
      next: r => {
        this.reporte.set(r);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.reporte.set(null);
        this.avisos.error(mensajeError(e, 'No se pudo generar el reporte.'));
      }
    });
  }

  protected nivel(p: number): string {
    return p < 70 ? 'llena' : p < 85 ? 'alta' : '';
  }
}
