import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '../../core/services/auth.service';
import { AsistenciaService } from '../../core/services/asistencia.service';
import { MatriculaService } from '../../core/services/matricula.service';
import { PagoConfigService } from '../../core/services/pago-config.service';
import { PlanificacionService } from '../../core/services/planificacion.service';
import { UsuarioService } from '../../core/services/usuario.service';
import { Anio, Docente, Nivel as NivelEscolar, Periodo, Seccion } from '../../core/models/planificacion.model';
import { Campania, Cupo, Matricula } from '../../core/models/matricula.model';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { AulasPanelComponent } from './aulas-panel.component';
import { CursosDocentesComponent } from './cursos-docentes.component';

/** Un indicador que falla no debe tumbar el resto de la pantalla: su valor queda en `null` (se muestra "—"). */
const seguro = <T>(obs: Observable<T>): Observable<T | null> => obs.pipe(catchError(() => of(null)));

type Nivel = 'danger' | 'warn' | 'info';

interface Pendiente {
  nivel: Nivel;
  titulo: string;
  detalle: string;
  ruta: string;
  accion: string;
  /** Pasos que faltan para resolver el pendiente; se muestran en el icono de la derecha. */
  falta: string[];
}

interface Paso {
  hecho: boolean;
  titulo: string;
  ayuda: string;
  ruta: string;
}

interface Acceso {
  etiqueta: string;
  ayuda: string;
  ruta: string;
  soloAdmin?: boolean;
}

interface Datos {
  anio: Anio | null;
  periodos: Periodo[] | null;
  secciones: Seccion[] | null;
  docentes: number | null;
  listaDocentes: Docente[];
  niveles: NivelEscolar[];
  prelista: Matricula[] | null;
  usuarios: number | null;
  alertas: number | null;
  morosos: number | null;
  deuda: number | null;
  campanias: Campania[] | null;
  cupos: Cupo[] | null;
  preMatriculas: number | null;
  matriculados: number | null;
  justificaciones: number | null;
  tarifas: number | null;
}

const VACIO: Datos = {
  anio: null, periodos: null, secciones: null, docentes: null, listaDocentes: [], niveles: [], prelista: null, usuarios: null, alertas: null, morosos: null, deuda: null,
  campanias: null, cupos: null, preMatriculas: null, matriculados: null, justificaciones: null, tarifas: null
};

export const ACCESOS: Acceso[] = [
  { etiqueta: 'Nueva matrícula', ayuda: 'Matricular a un alumno', ruta: '/matriculas?nueva=1' },
  { etiqueta: 'Nuevo alumno', ayuda: 'Registrar alumno y apoderado', ruta: '/alumnos?nuevo=1' },
  { etiqueta: 'Cuentas por alumno', ayuda: 'Estado de cuenta y cuotas', ruta: '/pagos/cuentas' },
  { etiqueta: 'Horarios', ayuda: 'Armar y publicar horarios', ruta: '/planificacion/horarios' },
  { etiqueta: 'Justificaciones', ayuda: 'Aprobar o rechazar', ruta: '/asistencia/justificaciones' },
  { etiqueta: 'Cierre de periodo', ayuda: 'Congelar promedios', ruta: '/calificaciones/cierre' },
  { etiqueta: 'Avisos', ayuda: 'Enviar comunicados', ruta: '/notificaciones/envio', soloAdmin: true }
];

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, PageHeaderComponent, AulasPanelComponent, CursosDocentesComponent],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly plan = inject(PlanificacionService);
  private readonly pagos = inject(PagoConfigService);
  private readonly usuariosApi = inject(UsuarioService);
  private readonly matriculas = inject(MatriculaService);
  private readonly asistencia = inject(AsistenciaService);

  protected readonly esAdmin = this.auth.isAdmin;
  protected readonly nombre = this.auth.currentUser;
  protected readonly cargando = signal(true);
  /** La tarjeta «Necesita tu atención» flota sobre la pantalla: empieza cerrada (la campana muestra cuántos pendientes hay) para no tapar el resto. */
  protected readonly verAtencion = signal(false);
  protected readonly datos = signal<Datos>(VACIO);

  protected readonly accesos = computed(() => ACCESOS.filter(a => !a.soloAdmin || this.esAdmin()));

  protected readonly saludo = computed(() => {
    const h = new Date().getHours();
    return h < 12 ? 'Buenos días' : h < 19 ? 'Buenas tardes' : 'Buenas noches';
  });

  protected readonly vacantes = computed(() => (this.datos().cupos ?? []).reduce((s, c) => s + c.vacantes, 0));

  /** Ocupación por nivel (Inicial, Primaria, Secundaria) y total, a partir de los cupos de cada sección. */
  protected readonly porNivel = computed(() => {
    const d = this.datos();
    return d.niveles
      .map(n => {
        const ids = new Set((d.secciones ?? []).filter(s => s.nivelId === n.id).map(s => s.id));
        const cupos = (d.cupos ?? []).filter(c => ids.has(c.seccionId));
        const capacidad = cupos.reduce((t, c) => t + c.capacidad, 0);
        const ocupados = cupos.reduce((t, c) => t + c.ocupados, 0);
        return { nivel: n, secciones: ids.size, capacidad, ocupados, porcentaje: capacidad ? Math.round((ocupados / capacidad) * 100) : 0 };
      })
      .filter(x => x.secciones > 0);
  });

  protected readonly totales = computed(() => {
    const cupos = this.datos().cupos ?? [];
    const capacidad = cupos.reduce((t, c) => t + c.capacidad, 0);
    const ocupados = cupos.reduce((t, c) => t + c.ocupados, 0);
    return { capacidad, ocupados, porcentaje: capacidad ? Math.round((ocupados / capacidad) * 100) : 0 };
  });

  /** Lo que necesita atención hoy, de lo más urgente a lo informativo. Cada ítem explica por qué importa y lleva a resolverlo. */
  protected readonly pendientes = computed<Pendiente[]>(() => {
    const d = this.datos();
    const r: Pendiente[] = [];
    if (!this.cargando() && !d.anio) {
      r.push({ nivel: 'danger', titulo: 'No hay año académico activo', detalle: 'Sin año activo no se pueden armar secciones, matrículas ni cobranza.', ruta: '/planificacion/anios', accion: 'Activar un año', falta: ['Crear o elegir el año académico', 'Marcarlo como activo'] });
      return r;
    }
    if ((d.morosos ?? 0) > 0) {
      r.push({ nivel: 'danger', titulo: `${d.morosos} alumno(s) con deuda vencida`, detalle: `${this.soles(d.deuda)} por cobrar. Revisa quién debe y registra el seguimiento.`, ruta: '/pagos/morosos', accion: 'Ver morosos', falta: ['Revisar la lista de morosos', 'Contactar a las familias', 'Registrar el seguimiento o el pago'] });
    }
    const llenas = (d.cupos ?? []).filter(c => c.vacantes === 0).length;
    const casi = (d.cupos ?? []).filter(c => c.vacantes > 0 && this.porcentaje(c) >= 90).length;
    if (llenas > 0) {
      r.push({ nivel: 'danger', titulo: `${llenas} sección(es) sin vacantes`, detalle: 'No se podrá matricular a más alumnos ahí. Sube la capacidad o abre otra sección.', ruta: '/matriculas/cupos', accion: 'Revisar cupos', falta: ['Subir la capacidad de la sección', 'O abrir otra sección'] });
    } else if (casi > 0) {
      r.push({ nivel: 'warn', titulo: `${casi} sección(es) casi llenas`, detalle: 'Tienen 90 % o más de ocupación.', ruta: '/matriculas/cupos', accion: 'Revisar cupos', falta: ['Verificar las vacantes restantes', 'Ampliar la capacidad si hace falta'] });
    }
    if (d.campanias && !d.campanias.some(c => c.estado === 'ABIERTA')) {
      r.push({ nivel: 'warn', titulo: 'No hay campaña de matrícula abierta', detalle: 'Mientras no haya una abierta, el sistema rechaza matrículas nuevas.', ruta: '/matriculas/campanias', accion: 'Ver campañas', falta: ['Crear o editar una campaña', 'Definir sus fechas', 'Abrirla'] });
    }
    if ((d.preMatriculas ?? 0) > 0) {
      r.push({ nivel: 'warn', titulo: `${d.preMatriculas} pre-matrícula(s) esperando pago`, detalle: 'Pasan a matrícula en cuanto se registra el pago de la matrícula.', ruta: '/matriculas?estado=PRE_MATRICULA', accion: 'Ver pre-matrículas', falta: ['Confirmar el pago de la matrícula', 'La pre-matrícula pasa a matrícula'] });
    }
    if ((d.justificaciones ?? 0) > 0) {
      r.push({ nivel: 'warn', titulo: `${d.justificaciones} justificación(es) por resolver`, detalle: 'Las familias esperan la respuesta a su solicitud de inasistencia.', ruta: '/asistencia/justificaciones', accion: 'Resolver', falta: ['Revisar cada solicitud', 'Aprobar o rechazar', 'Se notifica a la familia'] });
    }
    if ((d.alertas ?? 0) > 0) {
      r.push({ nivel: 'info', titulo: `${d.alertas} alerta(s) de cobranza abierta(s)`, detalle: 'Avisos del sistema de pagos que nadie ha marcado como resueltos.', ruta: '/pagos/morosos', accion: 'Revisar', falta: ['Revisar cada alerta', 'Marcarla como resuelta'] });
    }
    const sinTutor = (d.secciones ?? []).filter(s => !s.tutorDocenteId).length;
    if (sinTutor > 0) {
      r.push({ nivel: 'info', titulo: `${sinTutor} sección(es) sin tutor`, detalle: 'Asigna un docente tutor para que haya un responsable por aula.', ruta: '/planificacion/secciones', accion: 'Asignar tutor', falta: ['Elegir un docente por cada sección', 'Guardar la asignación'] });
    }
    return r;
  });

  /** Lista de puesta en marcha del año: se oculta cuando todo está listo. */
  protected readonly pasos = computed<Paso[]>(() => {
    const d = this.datos();
    const pasos: Paso[] = [
      { hecho: !!d.anio, titulo: 'Año académico activo', ayuda: 'Es la base de todo lo demás.', ruta: '/planificacion/anios' },
      { hecho: (d.periodos?.length ?? 0) > 0, titulo: 'Periodos (bimestres o trimestres)', ayuda: 'Las notas y los boletines se cierran por periodo.', ruta: '/planificacion/anios' },
      { hecho: (d.secciones?.length ?? 0) > 0, titulo: 'Secciones con su capacidad', ayuda: 'La capacidad define los cupos de matrícula.', ruta: '/planificacion/secciones' },
      { hecho: (d.docentes ?? 0) > 0, titulo: 'Docentes registrados', ayuda: 'Hacen falta para asignar cursos y armar horarios.', ruta: '/planificacion/docentes' },
      { hecho: (d.campanias ?? []).some(c => c.estado === 'ABIERTA'), titulo: 'Campaña de matrícula abierta', ayuda: 'Permite matricular dentro de sus fechas.', ruta: '/matriculas/campanias' }
    ];
    if (this.esAdmin()) {
      pasos.push({ hecho: (d.tarifas ?? 0) > 0, titulo: 'Tarifas del año', ayuda: 'Sin tarifas no se generan cuotas ni cobros.', ruta: '/pagos/tarifas' });
    }
    return pasos;
  });

  protected readonly pasosHechos = computed(() => this.pasos().filter(p => p.hecho).length);
  protected readonly configurado = computed(() => !this.cargando() && this.pasosHechos() === this.pasos().length);

  ngOnInit(): void {
    forkJoin({
      anio: seguro(this.plan.anioActivo()),
      docentes: seguro(this.plan.docentes('', 0, 200)),
      niveles: seguro(this.plan.niveles()),
      alertas: seguro(this.pagos.alertas(false)),
      morosos: seguro(this.pagos.morosos()),
      justificaciones: seguro(this.asistencia.bandeja('PENDIENTE', 0, 1)),
      // Con Keycloak los usuarios viven en su consola: auth-ms no los administra.
      usuarios: this.esAdmin() && !this.auth.esKeycloak ? seguro(this.usuariosApi.listar({ activo: true, size: 1 })) : of(null)
    }).subscribe(r => {
      this.datos.update(v => ({
        ...v,
        anio: r.anio,
        docentes: r.docentes?.totalElementos ?? null,
        listaDocentes: r.docentes?.contenido ?? [],
        niveles: r.niveles ?? [],
        alertas: r.alertas?.length ?? null,
        morosos: r.morosos?.totalAlumnosMorosos ?? null,
        deuda: r.morosos?.totalDeudaAcumulada ?? null,
        justificaciones: r.justificaciones?.totalElementos ?? null,
        usuarios: r.usuarios?.totalElementos ?? null
      }));
      const anio = r.anio;
      if (!anio) {
        this.cargando.set(false);
        return;
      }
      this.cargarAnio(anio, true);
    });
  }

  /** Secciones, cupos y matrículas del año. Se repite cuando cambian tutores o asignaciones desde las tarjetas. */
  protected cargarAnio(anio: Anio, primera = false): void {
    forkJoin({
      periodos: seguro(this.plan.periodos(anio.id)),
      secciones: seguro(this.plan.secciones(anio.id)),
      campanias: seguro(this.matriculas.campanias(anio.id)),
      cupos: seguro(this.matriculas.cupos(anio.id)),
      pre: seguro(this.matriculas.listar({ anioId: anio.id, estado: 'PRE_MATRICULA', size: 8 })),
      mat: seguro(this.matriculas.listar({ anioId: anio.id, estado: 'MATRICULADO', size: 1 })),
      tarifas: this.esAdmin() ? seguro(this.pagos.tarifas(anio.id)) : of(null)
    }).subscribe(x => {
      this.datos.update(v => ({
        ...v,
        periodos: x.periodos,
        secciones: x.secciones,
        campanias: x.campanias,
        cupos: x.cupos,
        prelista: x.pre?.contenido ?? null,
        preMatriculas: x.pre?.totalElementos ?? null,
        matriculados: x.mat?.totalElementos ?? null,
        tarifas: x.tarifas?.filter(t => t.activo !== false).length ?? null
      }));
      if (primera) this.cargando.set(false);
    });
  }

  protected refrescar(): void {
    const anio = this.datos().anio;
    if (anio) this.cargarAnio(anio);
  }

  /** Los enlaces pueden traer parámetros (`/matriculas?estado=…`): el routerLink los necesita por separado. */
  protected camino(ruta: string): string {
    return ruta.split('?')[0];
  }

  protected consulta(ruta: string): Record<string, string> {
    return Object.fromEntries(new URLSearchParams(ruta.split('?')[1] ?? ''));
  }

  protected valor(n: number | null): string {
    return n === null ? '—' : String(n);
  }

  protected soles(n: number | null): string {
    return n === null ? '—' : 'S/ ' + n.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  protected porcentaje(c: Cupo): number {
    return c.capacidad ? Math.min(100, Math.round((c.ocupados / c.capacidad) * 100)) : 0;
  }

  /** Sección y nombre de una pre-matrícula para mostrarla en la lista. */
  protected seccionDe(m: Matricula): string {
    const s = this.datos().secciones?.find(x => x.id === m.seccionId);
    return s ? `${s.gradoNombre} · ${s.nombre}` : '—';
  }

  /** Días que le quedan a una pre-matrícula antes de vencer (negativo si ya venció). */
  protected diasRestantes(m: Matricula): number | null {
    return m.venceEn ? Math.ceil((Date.parse(m.venceEn) - Date.now()) / 86_400_000) : null;
  }

  protected textoVence(m: Matricula): string {
    const d = this.diasRestantes(m);
    return d === null ? 'sin fecha' : d < 0 ? 'vencida' : d === 0 ? 'vence hoy' : d === 1 ? 'vence mañana' : `vence en ${d} días`;
  }
}
