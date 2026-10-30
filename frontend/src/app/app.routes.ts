import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards/auth.guard';
import { modoGuard } from './core/guards/modo.guard';
import { roleGuard } from './core/guards/role.guard';
import { ShellComponent } from './layout/shell/shell.component';
import { AuthService } from './core/services/auth.service';
import { rutaInicial } from './core/utils/landing';
import { inject } from '@angular/core';

const PERSONAL = ['ADMIN', 'SECRETARIA'];
const DOCENTE = ['DOCENTE'];
const FAMILIA = ['APODERADO', 'ALUMNO'];
const SOLO_ADMIN = ['ADMIN'];

export const routes: Routes = [
  // Sitio público del colegio: no exige sesión.
  {
    path: '',
    loadComponent: () => import('./features/publico/publico-layout.component').then(m => m.PublicoLayoutComponent),
    children: [
      { path: '', pathMatch: 'full', title: 'Colegio Horizonte del Valle', loadComponent: () => import('./features/publico/inicio-publico.component').then(m => m.InicioPublicoComponent) },
      { path: 'nosotros', title: 'Nosotros · Horizonte del Valle', loadComponent: () => import('./features/publico/nosotros.component').then(m => m.NosotrosComponent) },
      { path: 'niveles', title: 'Niveles · Horizonte del Valle', loadComponent: () => import('./features/publico/niveles-publico.component').then(m => m.NivelesPublicoComponent) },
      { path: 'admision', title: 'Admisión · Horizonte del Valle', loadComponent: () => import('./features/publico/admision.component').then(m => m.AdmisionComponent) },
      { path: 'matricula-en-linea', title: 'Matrícula en línea · Horizonte del Valle', loadComponent: () => import('./features/publico/matricula-en-linea.component').then(m => m.MatriculaEnLineaComponent) },
      { path: 'noticias', title: 'Noticias · Horizonte del Valle', loadComponent: () => import('./features/publico/noticias.component').then(m => m.NoticiasComponent) },
      { path: 'contacto', title: 'Contacto · Horizonte del Valle', loadComponent: () => import('./features/publico/contacto.component').then(m => m.ContactoComponent) }
    ]
  },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'cambiar-clave',
    canActivate: [authGuard],
    loadComponent: () => import('./features/auth/cambiar-clave/cambiar-clave.component').then(m => m.CambiarClaveComponent)
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      // Cada rol arranca en su propia pantalla de inicio.
      { path: 'portal', pathMatch: 'full', redirectTo: () => rutaInicial(inject(AuthService).roles()) },
      {
        path: 'calendario',
        loadComponent: () => import('./features/calendario/calendario.component').then(m => m.CalendarioComponent)
      },
      {
        path: 'ayuda',
        loadComponent: () => import('./features/ayuda/ayuda.component').then(m => m.AyudaComponent)
      },
      {
        path: 'mi-perfil',
        loadComponent: () => import('./features/perfil/perfil.component').then(m => m.PerfilComponent)
      },
      {
        path: 'finanzas',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/finanzas/finanzas.component').then(m => m.FinanzasComponent)
      },
      {
        path: 'docente/alumnos',
        canActivate: [roleGuard],
        data: { roles: DOCENTE },
        loadComponent: () => import('./features/docente/alumnos/docente-alumnos.component').then(m => m.DocenteAlumnosComponent)
      },
      {
        path: 'familia/boletines',
        canActivate: [roleGuard],
        data: { roles: FAMILIA },
        loadComponent: () => import('./features/familia/boletines/familia-boletines.component').then(m => m.FamiliaBoletinesComponent)
      },
      {
        path: 'notificaciones',
        loadComponent: () => import('./features/notificaciones/notificaciones.component').then(m => m.NotificacionesComponent)
      },
      {
        path: 'notificaciones/envio',
        canActivate: [roleGuard],
        data: { roles: SOLO_ADMIN },
        loadComponent: () => import('./features/notificaciones/notificaciones-admin.component').then(m => m.NotificacionesAdminComponent)
      },
      {
        path: 'inicio',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'usuarios',
        canActivate: [roleGuard, modoGuard],
        data: { roles: SOLO_ADMIN, modo: 'eduspace' },
        loadComponent: () => import('./features/usuarios/usuarios.component').then(m => m.UsuariosComponent)
      },
      {
        path: 'cuentas',
        canActivate: [roleGuard, modoGuard],
        data: { roles: SOLO_ADMIN, modo: 'eduspace' },
        loadComponent: () => import('./features/cuentas/cuentas.component').then(m => m.CuentasComponent)
      },
      {
        path: 'alumnos',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/alumnos/alumnos.component').then(m => m.AlumnosComponent)
      },
      {
        path: 'alumnos/importar',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/alumnos/importar/importar.component').then(m => m.ImportarAlumnosComponent)
      },
      {
        path: 'asistencia/justificaciones',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/asistencia/justificaciones/justificaciones.component').then(m => m.JustificacionesComponent)
      },
      {
        path: 'asistencia/reporte',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/asistencia/reporte/reporte.component').then(m => m.ReporteAsistenciaComponent)
      },
      {
        path: 'calificaciones/cierre',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/calificaciones/cierre/cierre.component').then(m => m.CierrePeriodoComponent)
      },
      {
        path: 'calificaciones/boletines',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/calificaciones/boletines/boletines.component').then(m => m.BoletinesComponent)
      },
      {
        path: 'pagos/cuentas',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/pagos/cuentas/cuentas.component').then(m => m.CuentasComponent)
      },
      {
        path: 'pagos/cobranza',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/pagos/cobranza/cobranza.component').then(m => m.CobranzaComponent)
      },
      {
        path: 'alumnos/:id',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/alumnos/alumno-detalle/alumno-detalle.component').then(m => m.AlumnoDetalleComponent)
      },
      {
        path: 'matriculas',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/matriculas/matriculas.component').then(m => m.MatriculasComponent)
      },
      {
        path: 'matriculas/aprobaciones',
        canActivate: [roleGuard],
        data: { roles: SOLO_ADMIN },
        loadComponent: () => import('./features/matriculas/aprobaciones.component').then(m => m.AprobacionesComponent)
      },
      {
        path: 'matriculas/campanias',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/matriculas/campanias.component').then(m => m.CampaniasComponent)
      },
      {
        path: 'matriculas/cupos',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/matriculas/cupos.component').then(m => m.CuposComponent)
      },
      {
        path: 'docente/inicio',
        canActivate: [roleGuard],
        data: { roles: DOCENTE },
        loadComponent: () => import('./features/docente/inicio/docente-inicio.component').then(m => m.DocenteInicioComponent)
      },
      {
        path: 'docente/curso/:seccionId/:cursoId',
        canActivate: [roleGuard],
        data: { roles: DOCENTE },
        loadComponent: () => import('./features/docente/curso/docente-curso.component').then(m => m.DocenteCursoComponent)
      },
      {
        path: 'docente/horario',
        canActivate: [roleGuard],
        data: { roles: DOCENTE },
        loadComponent: () => import('./features/docente/horario/docente-horario.component').then(m => m.DocenteHorarioComponent)
      },
      {
        path: 'docente/asistencia',
        canActivate: [roleGuard],
        data: { roles: DOCENTE },
        loadComponent: () => import('./features/docente/asistencia/docente-asistencia.component').then(m => m.DocenteAsistenciaComponent)
      },
      {
        path: 'docente/tareas',
        canActivate: [roleGuard],
        data: { roles: DOCENTE },
        loadComponent: () => import('./features/docente/tareas/docente-tareas.component').then(m => m.DocenteTareasComponent)
      },
      {
        path: 'docente/tareas/:id',
        canActivate: [roleGuard],
        data: { roles: DOCENTE },
        loadComponent: () => import('./features/docente/tareas/tarea-entregas.component').then(m => m.TareaEntregasComponent)
      },
      {
        path: 'docente/examenes',
        canActivate: [roleGuard],
        data: { roles: DOCENTE },
        loadComponent: () => import('./features/docente/examenes/docente-examenes.component').then(m => m.DocenteExamenesComponent)
      },
      {
        path: 'docente/banco',
        canActivate: [roleGuard],
        data: { roles: DOCENTE },
        loadComponent: () => import('./features/docente/examenes/docente-banco.component').then(m => m.DocenteBancoComponent)
      },
      {
        path: 'docente/notas',
        canActivate: [roleGuard],
        data: { roles: DOCENTE },
        loadComponent: () => import('./features/docente/notas/docente-notas.component').then(m => m.DocenteNotasComponent)
      },
      {
        path: 'familia/inicio',
        canActivate: [roleGuard],
        data: { roles: FAMILIA },
        loadComponent: () => import('./features/familia/cursos/familia-cursos.component').then(m => m.FamiliaCursosComponent)
      },
      {
        path: 'familia/curso/:cursoId',
        canActivate: [roleGuard],
        data: { roles: FAMILIA },
        loadComponent: () => import('./features/familia/cursos/familia-curso.component').then(m => m.FamiliaCursoComponent)
      },
      {
        path: 'familia/asistencia',
        canActivate: [roleGuard],
        data: { roles: FAMILIA },
        loadComponent: () => import('./features/familia/asistencia/familia-asistencia.component').then(m => m.FamiliaAsistenciaComponent)
      },
      {
        path: 'familia/notas',
        canActivate: [roleGuard],
        data: { roles: FAMILIA },
        loadComponent: () => import('./features/familia/notas/familia-notas.component').then(m => m.FamiliaNotasComponent)
      },
      {
        path: 'familia/horario',
        canActivate: [roleGuard],
        data: { roles: FAMILIA },
        loadComponent: () => import('./features/familia/horario/familia-horario.component').then(m => m.FamiliaHorarioComponent)
      },
      {
        path: 'familia/tareas',
        canActivate: [roleGuard],
        data: { roles: FAMILIA },
        loadComponent: () => import('./features/familia/tareas/familia-tareas.component').then(m => m.FamiliaTareasComponent)
      },
      {
        path: 'familia/examenes',
        canActivate: [roleGuard],
        data: { roles: FAMILIA },
        loadComponent: () => import('./features/familia/examenes/familia-examenes.component').then(m => m.FamiliaExamenesComponent)
      },
      {
        path: 'familia/examenes/:id/rendir',
        canActivate: [roleGuard],
        data: { roles: FAMILIA },
        loadComponent: () => import('./features/familia/examenes/rendir-examen.component').then(m => m.RendirExamenComponent)
      },
      {
        path: 'familia/pagos',
        canActivate: [roleGuard],
        data: { roles: ['APODERADO'] },
        loadComponent: () => import('./features/familia/pagos/familia-pagos.component').then(m => m.FamiliaPagosComponent)
      },
      {
        path: 'caja',
        canActivate: [roleGuard],
        data: { roles: ['CAJERO'] },
        loadComponent: () => import('./features/caja/caja.component').then(m => m.CajaComponent)
      },
      {
        path: 'planificacion/panel',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/planificacion/panel/panel-aulas.component').then(m => m.PanelAulasComponent)
      },
      {
        path: 'planificacion/cursos-docentes',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/planificacion/panel/panel-cursos.component').then(m => m.PanelCursosComponent)
      },
      {
        path: 'planificacion/aulas-disponibles',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/planificacion/aulas-disponibles/aulas-disponibles.component').then(m => m.AulasDisponiblesComponent)
      },
      {
        path: 'planificacion/malla',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/planificacion/malla/malla.component').then(m => m.MallaComponent)
      },
      {
        path: 'planificacion/asignaciones',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/planificacion/asignaciones/asignaciones.component').then(m => m.AsignacionesComponent)
      },
      {
        path: 'planificacion/bloques',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/planificacion/bloques/bloques.component').then(m => m.BloquesComponent)
      },
      {
        path: 'planificacion/horarios-auto',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/planificacion/horarios/horario-auto.component').then(m => m.HorarioAutoComponent)
      },
      {
        path: 'planificacion/horarios',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/planificacion/horarios/horarios.component').then(m => m.HorariosComponent)
      },
      {
        path: 'planificacion/feriados',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/planificacion/feriados/feriados.component').then(m => m.FeriadosComponent)
      },
      {
        path: 'planificacion/anios',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/planificacion/anios/anios.component').then(m => m.AniosComponent)
      },
      {
        path: 'planificacion/secciones',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/planificacion/secciones/secciones.component').then(m => m.SeccionesComponent)
      },
      {
        path: 'planificacion/cursos',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/planificacion/cursos/cursos.component').then(m => m.CursosComponent)
      },
      {
        path: 'planificacion/docentes',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/planificacion/docentes/docentes.component').then(m => m.DocentesComponent)
      },
      {
        path: 'planificacion/aulas',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/planificacion/aulas/aulas.component').then(m => m.AulasComponent)
      },
      {
        path: 'pagos/conceptos',
        canActivate: [roleGuard],
        data: { roles: SOLO_ADMIN },
        loadComponent: () => import('./features/pagos/conceptos/conceptos.component').then(m => m.ConceptosComponent)
      },
      {
        path: 'pagos/tarifas',
        canActivate: [roleGuard],
        data: { roles: SOLO_ADMIN },
        loadComponent: () => import('./features/pagos/tarifas/tarifas.component').then(m => m.TarifasComponent)
      },
      {
        path: 'pagos/descuentos',
        canActivate: [roleGuard],
        data: { roles: SOLO_ADMIN },
        loadComponent: () => import('./features/pagos/descuentos/descuentos.component').then(m => m.DescuentosComponent)
      },
      {
        path: 'pagos/morosos',
        canActivate: [roleGuard],
        data: { roles: PERSONAL },
        loadComponent: () => import('./features/pagos/morosos/morosos.component').then(m => m.MorososComponent)
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
