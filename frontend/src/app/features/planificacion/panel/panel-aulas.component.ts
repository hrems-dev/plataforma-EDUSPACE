import { Component, OnInit, inject, signal } from '@angular/core';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Cupo } from '../../../core/models/matricula.model';
import { Anio, Docente, Nivel, Seccion } from '../../../core/models/planificacion.model';
import { AuthService } from '../../../core/services/auth.service';
import { MatriculaService } from '../../../core/services/matricula.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { AulasPanelComponent } from '../../dashboard/aulas-panel.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

/** Panel de aulas a pantalla completa: lista de secciones y, al elegir una, su tutor, alumnos, faltas, cursos y docentes. */
@Component({
  selector: 'app-panel-aulas',
  imports: [AulasPanelComponent, PageHeaderComponent],
  template: `
    <app-page-header titulo="Aulas y secciones" subtitulo="Elige una sección para ver y gestionar su tutor, alumnos, cursos y docentes en un solo lugar.">
      <select class="input" aria-label="Año escolar" (change)="cambiarAnio(+$any($event.target).value)">
        @for (a of anios(); track a.id) { <option [value]="a.id" [selected]="a.id === anio()?.id">{{ a.nombre }}</option> }
      </select>
    </app-page-header>
    @if (cargando()) {
      <p class="muted">Cargando…</p>
    } @else {
      <app-aulas-panel [secciones]="secciones()" [niveles]="niveles()" [cupos]="cupos()" [docentes]="docentes()" [esAdmin]="esAdmin()" [anio]="anio()" (cambio)="cargarAnio()" />
    }
  `
})
export class PanelAulasComponent implements OnInit {
  private readonly plan = inject(PlanificacionService);
  private readonly matriculas = inject(MatriculaService);

  protected readonly esAdmin = inject(AuthService).isAdmin;
  protected readonly anios = signal<Anio[]>([]);
  protected readonly anio = signal<Anio | null>(null);
  protected readonly secciones = signal<Seccion[]>([]);
  protected readonly niveles = signal<Nivel[]>([]);
  protected readonly cupos = signal<Cupo[] | null>(null);
  protected readonly docentes = signal<Docente[]>([]);
  protected readonly cargando = signal(true);

  ngOnInit(): void {
    forkJoin({
      anios: this.plan.anios(),
      niveles: this.plan.niveles().pipe(catchError(() => of([]))),
      docentes: this.plan.docentes('', 0, 200).pipe(catchError(() => of(null)))
    }).subscribe(r => {
      this.anios.set(r.anios);
      this.niveles.set(r.niveles);
      this.docentes.set((r.docentes?.contenido ?? []).filter(d => d.estado === 'ACTIVO'));
      this.anio.set(r.anios.find(a => a.estado === 'ACTIVO') ?? r.anios[0] ?? null);
      this.cargarAnio();
    });
  }

  protected cambiarAnio(id: number): void {
    this.anio.set(this.anios().find(a => a.id === id) ?? null);
    this.cargarAnio();
  }

  protected cargarAnio(): void {
    const a = this.anio();
    if (!a) {
      this.cargando.set(false);
      return;
    }
    forkJoin({
      secciones: this.plan.secciones(a.id).pipe(catchError(() => of([] as Seccion[]))),
      cupos: this.matriculas.cupos(a.id).pipe(catchError(() => of(null)))
    }).subscribe(r => {
      this.secciones.set(r.secciones);
      this.cupos.set(r.cupos);
      this.cargando.set(false);
    });
  }
}
