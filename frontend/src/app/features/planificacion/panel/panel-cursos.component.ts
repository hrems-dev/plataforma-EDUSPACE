import { Component, OnInit, inject, signal } from '@angular/core';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Anio, Docente, Seccion } from '../../../core/models/planificacion.model';
import { AuthService } from '../../../core/services/auth.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { CursosDocentesComponent } from '../../dashboard/cursos-docentes.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

/** Cursos y docentes juntos: quién dicta qué, en qué secciones, con altas rápidas de curso y de asignación. */
@Component({
  selector: 'app-panel-cursos',
  imports: [CursosDocentesComponent, PageHeaderComponent],
  template: `
    <app-page-header titulo="Cursos y docentes" subtitulo="Crea cursos, asígnalos a un grado y elige qué docente los dicta en cada sección." />
    @if (cargando()) {
      <p class="muted">Cargando…</p>
    } @else {
      <app-cursos-docentes [secciones]="secciones()" [docentes]="docentes()" [esAdmin]="esAdmin()" />
    }
  `
})
export class PanelCursosComponent implements OnInit {
  private readonly plan = inject(PlanificacionService);
  protected readonly esAdmin = inject(AuthService).isAdmin;
  protected readonly secciones = signal<Seccion[]>([]);
  protected readonly docentes = signal<Docente[]>([]);
  protected readonly cargando = signal(true);

  ngOnInit(): void {
    this.plan.anios().subscribe(anios => {
      const anio: Anio | undefined = anios.find(a => a.estado === 'ACTIVO') ?? anios[0];
      forkJoin({
        secciones: anio ? this.plan.secciones(anio.id).pipe(catchError(() => of([] as Seccion[]))) : of([] as Seccion[]),
        docentes: this.plan.docentes('', 0, 200).pipe(catchError(() => of(null)))
      }).subscribe(r => {
        this.secciones.set(r.secciones);
        this.docentes.set((r.docentes?.contenido ?? []).filter(d => d.estado === 'ACTIVO'));
        this.cargando.set(false);
      });
    });
  }
}
