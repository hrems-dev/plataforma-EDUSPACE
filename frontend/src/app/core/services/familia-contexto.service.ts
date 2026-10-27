import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, forkJoin, map, shareReplay, tap } from 'rxjs';
import { Hijo } from '../models/familia.model';
import { Matricula } from '../models/matricula.model';
import { AuthService } from './auth.service';

/**
 * Datos base de las pantallas de familia: a quién representa la sesión (hijos del apoderado, o el propio alumno)
 * y cuál está seleccionado. Se carga una vez por sesión.
 */
@Injectable({ providedIn: 'root' })
export class FamiliaContextoService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  readonly hijos = signal<Hijo[]>([]);
  readonly matriculas = signal<Matricula[]>([]);
  readonly seleccionadoId = signal<number | null>(null);
  readonly cargado = signal(false);

  readonly esApoderado = computed(() => this.auth.roles().includes('APODERADO'));
  readonly seleccionado = computed(() => this.hijos().find(h => h.alumnoId === this.seleccionadoId()) ?? this.hijos()[0] ?? null);
  /** Matrícula vigente del hijo seleccionado (la que define su sección). */
  readonly matriculaActual = computed(() => {
    const h = this.seleccionado();
    if (!h) return null;
    const suyas = this.matriculas().filter(m => m.alumnoId === h.alumnoId);
    return suyas.find(m => m.estado === 'MATRICULADO') ?? suyas.find(m => m.estado === 'PRE_MATRICULA') ?? suyas[0] ?? null;
  });

  private carga$: Observable<void> | null = null;

  cargar(): Observable<void> {
    if (!this.carga$) {
      this.carga$ = forkJoin({
        hijos: this.http.get<Hijo[]>('/api/v1/alumnos/mios'),
        matriculas: this.http.get<Matricula[]>('/api/v1/matriculas/mias')
      }).pipe(
        tap(({ hijos, matriculas }) => {
          this.hijos.set(hijos);
          this.matriculas.set(matriculas);
          if (this.seleccionadoId() === null) this.seleccionadoId.set(hijos[0]?.alumnoId ?? null);
          this.cargado.set(true);
        }),
        map(() => undefined),
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this.carga$;
  }

  seleccionar(id: number): void {
    this.seleccionadoId.set(id);
  }

  invalidar(): void {
    this.carga$ = null;
  }
}
