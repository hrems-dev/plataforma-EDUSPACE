import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

interface Guia { roles: string[]; titulo: string; pasos: string[]; ruta?: string; enlace?: string }

const GUIAS: Guia[] = [
  { roles: ['ADMIN', 'SECRETARIA'], titulo: 'Matricular a un alumno', ruta: '/matriculas?nueva=1', enlace: 'Nueva matrícula',
    pasos: ['Registra al alumno y su apoderado en Alumnos (o impórtalos desde un CSV).', 'Sube y verifica sus documentos: DNI, partida de nacimiento y ficha SIAGIE.', 'Comprueba que haya una campaña abierta y vacantes en la sección.', 'Crea la matrícula: queda en pre-matrícula hasta que se pague.'] },
  { roles: ['ADMIN', 'SECRETARIA'], titulo: 'Armar y publicar un horario', ruta: '/planificacion/horarios', enlace: 'Ir a horarios',
    pasos: ['Define la malla del grado y asigna un docente a cada curso de la sección.', 'Crea los bloques horarios del turno.', 'En Horarios, coloca cada clase en la grilla; el sistema avisa si hay cruces.', 'Valida y publica: las familias y docentes lo ven al instante.'] },
  { roles: ['ADMIN', 'SECRETARIA'], titulo: 'Cerrar un periodo y generar boletines', ruta: '/calificaciones/cierre', enlace: 'Cierre de periodo',
    pasos: ['Verifica que los docentes cargaron todas las notas del periodo.', 'Cierra el periodo de la sección: se congelan los promedios.', 'Genera los boletines; las familias reciben un aviso.', 'Si hubo un error, solo el administrador puede reabrir con un motivo.'] },
  { roles: ['ADMIN'], titulo: 'Preparar un año nuevo', ruta: '/planificacion/anios', enlace: 'Años y periodos',
    pasos: ['Crea el año y sus periodos y actívalo.', 'Crea las secciones con su capacidad.', 'Define tarifas y abre la campaña de matrícula.', 'Usa la ratificación masiva para pasar a los alumnos del año anterior.'] },
  { roles: ['ADMIN', 'SECRETARIA'], titulo: 'Atender a un moroso', ruta: '/pagos/morosos', enlace: 'Ver morosos',
    pasos: ['Abre Morosos y alertas y filtra por sección.', 'Revisa el detalle de cuotas vencidas en Cuentas por alumno.', 'Contacta al apoderado; al pagar, la cuota se actualiza sola.'] },
  { roles: ['DOCENTE'], titulo: 'Tomar asistencia', ruta: '/docente/asistencia', enlace: 'Ir a asistencia',
    pasos: ['Elige la sección y la fecha y abre la hoja.', 'Usa «Todos presentes» y corrige a quienes faltaron o llegaron tarde.', 'Guarda: las familias ven el resultado el mismo día.'] },
  { roles: ['DOCENTE'], titulo: 'Registrar notas', ruta: '/docente/notas', enlace: 'Ir a notas',
    pasos: ['Define los criterios de evaluación del curso (los pesos suman 100 %).', 'Registra las notas por alumno; las de tareas y exámenes llegan solas.', 'Cuando termines, secretaría cierra el periodo.'] },
  { roles: ['DOCENTE'], titulo: 'Crear un examen', ruta: '/docente/examenes', enlace: 'Ir a exámenes',
    pasos: ['Carga preguntas en el banco del curso.', 'Crea el examen, elige preguntas, fecha, duración e intentos.', 'Publícalo: los alumnos lo rinden con reloj y se califica solo.'] },
  { roles: ['APODERADO', 'ALUMNO'], titulo: 'Entregar una tarea', ruta: '/familia/tareas', enlace: 'Ir a tareas',
    pasos: ['Abre la tarea y lee las indicaciones y la fecha límite.', 'Adjunta tu archivo y envía.', 'Cuando el docente la califique verás la nota.'] },
  { roles: ['APODERADO'], titulo: 'Pagar la pensión', ruta: '/familia/pagos', enlace: 'Ir a pagos',
    pasos: ['Elige a tu hijo y marca las cuotas a pagar (puedes usar «Elegir vencidas»).', 'Confirma el pago en línea.', 'Descarga el comprobante desde el historial.'] },
  { roles: ['APODERADO'], titulo: 'Justificar una inasistencia', ruta: '/familia/asistencia', enlace: 'Ir a asistencia',
    pasos: ['Entra a Asistencia y elige la falta.', 'Escribe el motivo y adjunta la constancia si la tienes.', 'Secretaría la aprueba o rechaza y te avisa.'] },
  { roles: ['CAJERO'], titulo: 'Cobrar en ventanilla', ruta: '/caja', enlace: 'Ir a caja',
    pasos: ['Abre la caja con el monto inicial.', 'Busca al alumno, marca las cuotas y elige el medio de pago.', 'Al terminar el día, cierra la caja y declara el efectivo contado.'] }
];

const PREGUNTAS = [
  { p: '¿Cómo cambio mi clave?', r: 'En Mi perfil, botón «Cambiar mi clave».', roles: ['*'] },
  { p: '¿Por qué no veo una opción del menú?', r: 'Cada rol ve solo lo suyo. Si necesitas algo más, pídelo a secretaría o al administrador.', roles: ['*'] },
  { p: '¿Cómo busco rápido a un alumno o una pantalla?', r: 'Pulsa Ctrl + K desde cualquier página y escribe.', roles: ['ADMIN', 'SECRETARIA'] },
  { p: '¿Por qué no puedo matricular a un alumno?', r: 'Revisa tres cosas: campaña abierta, vacantes en la sección y documentos verificados.', roles: ['ADMIN', 'SECRETARIA'] },
  { p: '¿Por qué la matrícula sigue en pre-matrícula?', r: 'Pasa a «matriculado» cuando se registra el pago de la matrícula.', roles: ['ADMIN', 'SECRETARIA', 'APODERADO'] },
  { p: '¿Dónde veo las notas de mi hijo?', r: 'En Notas y boletines. Los boletines oficiales están en Boletines.', roles: ['APODERADO', 'ALUMNO'] },
  { p: '¿Puedo rendir un examen otra vez?', r: 'Solo si el docente permitió más de un intento.', roles: ['ALUMNO', 'APODERADO'] },
  { p: 'Me equivoqué en una nota ya cerrada', r: 'Pide a secretaría o al administrador que reabra el periodo con un motivo.', roles: ['DOCENTE'] },
  { p: 'Al cerrar la caja hay diferencia', r: 'Se registra y queda auditada. Revisa los comprobantes y avisa a administración.', roles: ['CAJERO'] }
];

/** Guías paso a paso y preguntas frecuentes según los roles de la sesión. */
@Component({
  selector: 'app-ayuda',
  imports: [RouterLink, PageHeaderComponent],
  template: `
    <div class="page">
      <app-page-header titulo="Ayuda" subtitulo="Guías paso a paso para lo que haces más seguido." />
      <div class="toolbar">
        <input class="input" type="search" placeholder="Buscar en la ayuda…" aria-label="Buscar en la ayuda" [value]="texto()" (input)="texto.set($any($event.target).value)" />
      </div>
      <div class="guias">
        @for (g of guias(); track g.titulo) {
          <article class="card"><div class="card-body">
            <h2 class="sec-titulo">{{ g.titulo }}</h2>
            <ol class="pasos-ayuda">@for (p of g.pasos; track p) { <li>{{ p }}</li> }</ol>
            @if (g.ruta) { <a class="btn btn-primary btn-sm" [routerLink]="camino(g.ruta)" [queryParams]="consulta(g.ruta)">{{ g.enlace }}</a> }
          </div></article>
        } @empty { <p class="muted">No encontramos guías con ese texto.</p> }
      </div>
      <h2 class="sec-titulo" style="margin-top: 28px">Preguntas frecuentes</h2>
      <div class="card"><div class="card-body">
        @for (q of preguntas(); track q.p) {
          <details class="faq"><summary>{{ q.p }}</summary><p class="muted">{{ q.r }}</p></details>
        } @empty { <p class="muted">Sin resultados.</p> }
      </div></div>
    </div>
  `,
  styles: `
    .guias { display: grid; gap: 16px; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); margin-top: 12px; }
    .pasos-ayuda { margin: 0 0 14px; padding-left: 20px; display: grid; gap: 6px; }
    .faq { border-bottom: 1px solid var(--line); padding: 10px 0; }
    .faq summary { cursor: pointer; font-weight: 600; }
    .faq p { margin: 8px 0 0; }
  `
})
export class AyudaComponent {
  private readonly auth = inject(AuthService);
  protected readonly texto = signal('');

  private coincide(t: string, ...campos: string[]): boolean {
    return !t || campos.join(' ').toLowerCase().includes(t);
  }

  protected readonly guias = computed(() => {
    const t = this.texto().trim().toLowerCase();
    return GUIAS.filter(g => this.auth.tieneAlgunRol(g.roles) && this.coincide(t, g.titulo, ...g.pasos));
  });

  protected readonly preguntas = computed(() => {
    const t = this.texto().trim().toLowerCase();
    return PREGUNTAS.filter(q => (q.roles.includes('*') || this.auth.tieneAlgunRol(q.roles)) && this.coincide(t, q.p, q.r));
  });

  protected camino(ruta: string): string { return ruta.split('?')[0]; }
  protected consulta(ruta: string): Record<string, string> { return Object.fromEntries(new URLSearchParams(ruta.split('?')[1] ?? '')); }
}
