import { Injectable, inject, signal } from '@angular/core';
import { AlumnoService } from './alumno.service';

/** Resuelve nombres de alumnos a partir de sus ids (varias APIs devuelven solo el id) y los recuerda mientras dura la sesión. */
@Injectable({ providedIn: 'root' })
export class NombresAlumnoService {
  private readonly api = inject(AlumnoService);
  private readonly pedidos = new Set<number>();
  private readonly mapaSignal = signal<Record<number, string>>({});
  readonly nombres = this.mapaSignal.asReadonly();

  nombre(id: number): string {
    return this.mapaSignal()[id] ?? `Alumno ${id}`;
  }

  cargar(ids: number[]): void {
    for (const id of new Set(ids)) {
      if (this.pedidos.has(id)) continue;
      this.pedidos.add(id);
      this.api.obtener(id).subscribe({
        next: a => this.mapaSignal.update(m => ({ ...m, [id]: a.nombreCompleto })),
        error: () => this.pedidos.delete(id)
      });
    }
  }
}
