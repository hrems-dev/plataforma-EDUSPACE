import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Cupo } from '../../core/models/matricula.model';
import { Anio, Aula, Grado, Seccion } from '../../core/models/planificacion.model';
import { AuthService } from '../../core/services/auth.service';
import { MatriculaService } from '../../core/services/matricula.service';
import { NotificationService } from '../../core/services/notification.service';
import { PlanificacionService } from '../../core/services/planificacion.service';
import { mensajeError } from '../../core/utils/errores';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-cupos',
  imports: [ReactiveFormsModule, PageHeaderComponent],
  templateUrl: './cupos.component.html'
})
export class CuposComponent implements OnInit {
  private readonly api = inject(MatriculaService);
  private readonly plan = inject(PlanificacionService);
  private readonly avisos = inject(NotificationService);
  private readonly fb = inject(FormBuilder);
  protected readonly esAdmin = inject(AuthService).isAdmin;

  protected readonly anios = signal<Anio[]>([]);
  protected readonly grados = signal<Grado[]>([]);
  protected readonly secciones = signal<Seccion[]>([]);
  protected readonly cupos = signal<Cupo[]>([]);
  private readonly aulas = signal<Aula[]>([]);
  protected readonly cargando = signal(false);
  /** Sección cuya capacidad se está editando en la propia fila, y el valor que se escribe. */
  protected readonly editando = signal<number | null>(null);
  protected readonly nuevaCapacidad = signal(0);
  protected readonly guardando = signal(false);

  protected readonly filtro = this.fb.nonNullable.group({ anioId: 0, gradoId: 0 });

  ngOnInit(): void {
    this.plan.grados().subscribe(g => this.grados.set(g));
    this.plan.aulas().subscribe(a => this.aulas.set(a));
    this.plan.anios().subscribe(a => {
      this.anios.set(a);
      this.filtro.patchValue({ anioId: (a.find(x => x.estado === 'ACTIVO') ?? a[0])?.id ?? 0 });
      this.cargar();
    });
  }

  protected cargar(): void {
    const { anioId, gradoId } = this.filtro.getRawValue();
    if (!anioId) {
      this.cupos.set([]);
      return;
    }
    this.cargando.set(true);
    this.plan.secciones(anioId).subscribe(s => this.secciones.set(s));
    this.api.cupos(anioId, gradoId || undefined).subscribe({
      next: c => {
        this.cupos.set(c);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar los cupos.'));
      }
    });
  }

  protected nombre(c: Cupo): string {
    const s = this.secciones().find(x => x.id === c.seccionId);
    return s ? `${s.gradoNombre} · ${s.nombre}` : c.seccionNombre;
  }

  protected porcentaje(c: Cupo): number {
    return c.capacidad ? Math.min(100, Math.round((c.ocupados / c.capacidad) * 100)) : 0;
  }

  protected nivel(c: Cupo): string {
    const p = this.porcentaje(c);
    return p >= 100 ? 'llena' : p >= 90 ? 'alta' : '';
  }

  protected editar(c: Cupo): void {
    this.nuevaCapacidad.set(c.capacidad);
    this.editando.set(c.seccionId);
  }

  protected cancelar(): void {
    this.editando.set(null);
  }

  /** Motivo por el que la capacidad escrita no se puede guardar, o `null` si es válida. */
  protected problema(c: Cupo): string | null {
    const n = this.nuevaCapacidad();
    if (!Number.isInteger(n) || n < 1) return 'Debe ser un número entero mayor a 0.';
    if (n < c.ocupados) return `Hay ${c.ocupados} alumnos matriculados: la capacidad no puede ser menor.`;
    const aula = this.aulaDe(c);
    if (aula && n > aula.capacidad) return `El aula ${aula.nombre} solo admite ${aula.capacidad}. Cambia de aula en Secciones para subir la capacidad.`;
    return null;
  }

  protected aulaDe(c: Cupo): Aula | undefined {
    const aulaId = this.secciones().find(x => x.id === c.seccionId)?.aulaId;
    return aulaId ? this.aulas().find(a => a.id === aulaId) : undefined;
  }

  protected guardar(c: Cupo): void {
    const s = this.secciones().find(x => x.id === c.seccionId);
    if (!s || this.problema(c)) return;
    this.guardando.set(true);
    this.plan
      .actualizarSeccion(s.id, {
        anioId: s.anioId, gradoId: s.gradoId, aulaId: s.aulaId ?? null, nombre: s.nombre, turno: s.turno,
        capacidad: this.nuevaCapacidad(), tutorDocenteId: s.tutorDocenteId ?? null, activo: s.activo
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.editando.set(null);
          this.avisos.success(`Capacidad de ${this.nombre(c)} actualizada a ${this.nuevaCapacidad()}.`);
          this.cargar();
        },
        error: e => {
          this.guardando.set(false);
          this.avisos.error(mensajeError(e, 'No se pudo cambiar la capacidad.'));
        }
      });
  }

  protected soloNumero(valor: string): number {
    return valor === '' ? 0 : Number(valor);
  }
}
