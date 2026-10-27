import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, firstValueFrom, of } from 'rxjs';
import {
  Anio,
  Asignacion,
  Aula,
  Bloque,
  ClaseHorario,
  DIAS_SEMANA,
  HorarioSeccion,
  Publicacion,
  Seccion,
  ValidacionHorario
} from '../../../core/models/planificacion.model';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

interface Celda {
  bloque: Bloque;
  dia: number;
}

const DIAS = [1, 2, 3, 4, 5];

@Component({
  selector: 'app-horarios',
  imports: [ReactiveFormsModule, DatePipe, ModalComponent, PageHeaderComponent],
  templateUrl: './horarios.component.html',
  styleUrl: './horarios.component.css'
})
export class HorariosComponent implements OnInit {
  private readonly api = inject(PlanificacionService);
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  protected readonly dias = DIAS;
  protected readonly nombreDia = (d: number) => DIAS_SEMANA[d];

  protected readonly anios = signal<Anio[]>([]);
  protected readonly secciones = signal<Seccion[]>([]);
  protected readonly aulas = signal<Aula[]>([]);
  protected readonly asignaciones = signal<Asignacion[]>([]);
  protected readonly horario = signal<HorarioSeccion | null>(null);
  protected readonly validacion = signal<ValidacionHorario | null>(null);
  protected readonly publicaciones = signal<Publicacion[]>([]);
  protected readonly cargando = signal(false);

  protected readonly anioId = this.fb.nonNullable.control(0);
  protected readonly seccionId = this.fb.nonNullable.control(0);

  // diálogo de clase
  protected readonly celda = signal<Celda | null>(null);
  protected readonly clase = signal<ClaseHorario | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    asignacionId: [0, [Validators.required, Validators.min(1)]],
    aulaId: [0]
  });

  /** Índice `bloqueId|dia` → clase, para pintar la grilla sin recorrer la lista en cada celda. */
  protected readonly porCelda = computed(() => {
    const m = new Map<string, ClaseHorario>();
    for (const c of this.horario()?.clases ?? []) m.set(`${c.bloqueId}|${c.diaSemana}`, c);
    return m;
  });

  protected readonly seccionActual = computed(() => this.secciones().find(s => s.id === this.seccionId.value) ?? null);

  ngOnInit(): void {
    this.api.aulas().subscribe(a => this.aulas.set(a.filter(x => x.activo)));
    this.api.anios().subscribe(a => {
      this.anios.set(a);
      this.anioId.setValue((a.find(x => x.estado === 'ACTIVO') ?? a[0])?.id ?? 0);
      this.cargarSecciones();
    });
  }

  protected cargarSecciones(): void {
    this.horario.set(null);
    this.validacion.set(null);
    if (!this.anioId.value) return;
    this.api.secciones(this.anioId.value).subscribe(s => {
      this.secciones.set(s);
      this.seccionId.setValue(s[0]?.id ?? 0);
      this.cargar();
    });
  }

  protected cargar(): void {
    const id = this.seccionId.value;
    this.validacion.set(null);
    if (!id) {
      this.horario.set(null);
      return;
    }
    this.cargando.set(true);
    this.api.horarioSeccion(id).subscribe({
      next: h => {
        this.horario.set({ ...h, bloques: [...h.bloques].sort((a, b) => a.orden - b.orden) });
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudo cargar el horario.'));
      }
    });
    this.api.asignaciones(id).subscribe(a => this.asignaciones.set(a));
    this.api.publicaciones(id).subscribe({ next: p => this.publicaciones.set(p), error: () => this.publicaciones.set([]) });
  }

  protected hora(h: string): string {
    return h.slice(0, 5);
  }

  protected claseEn(b: Bloque, dia: number): ClaseHorario | undefined {
    return this.porCelda().get(`${b.id}|${dia}`);
  }

  // --- editar la grilla ---
  protected abrirCelda(b: Bloque, dia: number): void {
    if (b.esReceso) return;
    const existente = this.claseEn(b, dia) ?? null;
    this.celda.set({ bloque: b, dia });
    this.clase.set(existente);
    this.form.reset({ asignacionId: existente?.asignacionId ?? 0, aulaId: existente?.aulaId ?? 0 });
    this.errorForm.set(null);
  }

  protected cerrar(): void {
    this.celda.set(null);
    this.clase.set(null);
  }

  protected guardar(): void {
    const c = this.celda();
    if (!c || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const body = { asignacionId: v.asignacionId, bloqueId: c.bloque.id, diaSemana: c.dia, aulaId: v.aulaId || null };
    const existente = this.clase();
    this.guardando.set(true);
    (existente ? this.api.actualizarClase(existente.id, body) : this.api.agregarClase(body)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(existente ? 'Clase actualizada.' : 'Clase agregada al horario.');
        this.cerrar();
        this.cargar();
      },
      error: e => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(e)); // aquí llegan los choques de docente, aula o sección
      }
    });
  }

  protected async quitar(): Promise<void> {
    const existente = this.clase();
    if (!existente) return;
    if (!(await this.confirmacion.confirmar(`¿Quitar ${existente.cursoNombre} de este bloque?`, 'Quitar', true))) return;
    this.api.quitarClase(existente.id).subscribe({
      next: () => {
        this.avisos.success('Clase quitada.');
        this.cerrar();
        this.cargar();
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }

  // --- validar y publicar ---
  protected validar(): void {
    this.api.validarHorario(this.seccionId.value).subscribe({
      next: v => this.validacion.set(v),
      error: e => this.avisos.error(mensajeError(e, 'No se pudo validar el horario.'))
    });
  }

  protected async publicar(): Promise<void> {
    const s = this.seccionActual();
    if (!s) return;
    // El servicio deja publicar un horario incompleto: se valida antes y se advierte lo que falta.
    const v = await firstValueFrom(this.api.validarHorario(s.id).pipe(catchError(() => of(null))));
    if (v) this.validacion.set(v);
    const aviso =
      v && !v.valido
        ? `El horario de ${s.gradoNombre} ${s.nombre} todavía tiene problemas (${v.choques.length} choques, ${v.cursosIncompletos.length} cursos con horas pendientes). ¿Publicarlo de todas formas?`
        : `¿Publicar el horario de ${s.gradoNombre} ${s.nombre}? Los docentes y las familias recibirán un aviso.`;
    if (!(await this.confirmacion.confirmar(aviso, 'Publicar', !!v && !v.valido))) return;
    this.api.publicarHorario(s.id).subscribe({
      next: p => {
        this.avisos.success(`Horario publicado (versión ${p.version}).`);
        this.cargar();
      },
      error: e => this.avisos.error(mensajeError(e, 'No se pudo publicar el horario.'))
    });
  }

  protected pct(h: { horasProgramadas: number; horasRequeridas: number }): number {
    return h.horasRequeridas ? Math.min(100, Math.round((h.horasProgramadas / h.horasRequeridas) * 100)) : 0;
  }
}
