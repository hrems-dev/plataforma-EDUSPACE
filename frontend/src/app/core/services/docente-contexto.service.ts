import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map, shareReplay, switchMap } from 'rxjs';
import { Anio, Asignacion, DocenteMio, Periodo } from '../models/planificacion.model';
import { PlanificacionService } from './planificacion.service';

export interface ContextoDocente {
  docente: { id: number; nombre: string; estado: DocenteMio['estado'] };
  anio: Anio;
  periodos: Periodo[];
  /** Periodo activo (o, si no hay, el primero) del año activo. */
  periodoActivo: Periodo | null;
  /** Lo que dicta el docente: una fila por sección y curso. */
  asignaciones: Asignacion[];
}

/** Datos base de las pantallas del docente: quién es, en qué año está y qué cursos dicta. Se carga una vez. */
@Injectable({ providedIn: 'root' })
export class DocenteContextoService {
  private readonly plan = inject(PlanificacionService);
  private contexto$: Observable<ContextoDocente> | null = null;

  cargar(): Observable<ContextoDocente> {
    if (!this.contexto$) {
      this.contexto$ = this.plan.docenteMio().pipe(
        switchMap(mio =>
          forkJoin({
            anio: this.plan.anioActivo(),
            asignaciones: this.plan.asignaciones(undefined, mio.docenteId)
          }).pipe(
            switchMap(({ anio, asignaciones }) =>
              this.plan.periodos(anio.id).pipe(
                map(periodos => ({
                  docente: { id: mio.docenteId, nombre: mio.nombre, estado: mio.estado },
                  anio,
                  periodos,
                  periodoActivo: periodos.find(p => p.estado === 'ACTIVO') ?? periodos[0] ?? null,
                  asignaciones
                }))
              )
            )
          )
        ),
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this.contexto$;
  }

  /** Para volver a pedir los datos (p. ej. si la secretaría cambió una asignación). */
  invalidar(): void {
    this.contexto$ = null;
  }
}
