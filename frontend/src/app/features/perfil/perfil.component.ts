import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { rutaInicial } from '../../core/utils/landing';

const NOMBRE_ROL: Record<string, string> = {
  ADMIN: 'Administrador', SECRETARIA: 'Secretaría', DOCENTE: 'Docente', APODERADO: 'Apoderado', ALUMNO: 'Alumno', CAJERO: 'Caja / finanzas'
};
const QUE_PUEDES: Record<string, string[]> = {
  ADMIN: ['Configurar año, secciones, cursos y tarifas', 'Administrar usuarios, campañas y descuentos', 'Cerrar y reabrir periodos', 'Ver finanzas y morosidad'],
  SECRETARIA: ['Matricular y administrar alumnos', 'Armar y publicar horarios', 'Resolver justificaciones', 'Cerrar periodos y generar boletines'],
  DOCENTE: ['Tomar asistencia y registrar notas', 'Publicar tareas y exámenes', 'Ver tu horario y tus alumnos'],
  APODERADO: ['Ver notas, asistencia, tareas y horario de tus hijos', 'Justificar inasistencias', 'Pagar pensiones en línea y descargar boletines'],
  ALUMNO: ['Ver tus notas, horario y asistencia', 'Entregar tareas y rendir exámenes'],
  CAJERO: ['Abrir y cerrar caja', 'Cobrar cuotas en ventanilla', 'Hacer el arqueo del día']
};

/** Quién eres en el sistema, qué puedes hacer y cómo cuidar tu cuenta. */
@Component({
  selector: 'app-perfil',
  imports: [RouterLink, PageHeaderComponent],
  template: `
    <div class="page">
      <app-page-header titulo="Mi perfil" subtitulo="Tu cuenta y lo que puedes hacer en el sistema." />
      <div class="dos-col">
        <section class="card"><div class="card-body">
          <div class="perfil-cab">
            <div class="avatar" aria-hidden="true">{{ iniciales() }}</div>
            <div><strong style="font-size: 1.15rem">{{ usuario()?.username }}</strong><br />
              @for (r of roles(); track r) { <span class="badge badge-violet" style="margin-right: 6px">{{ nombreRol(r) }}</span> }</div>
          </div>
          <dl class="datos" style="margin-top: 18px">
            <div><dt>Usuario</dt><dd>{{ usuario()?.username }}</dd></div>
            <div><dt>Inicio de sesión</dt><dd>{{ esKeycloak ? 'Keycloak (cuenta única)' : 'Cuenta del colegio' }}</dd></div>
          </dl>
          <div style="display: flex; gap: 10px; flex-wrap: wrap; margin-top: 18px">
            <button type="button" class="btn btn-primary" (click)="cambiarClave()">Cambiar mi clave</button>
            <a class="btn" [routerLink]="inicio()">Ir a mi inicio</a>
            <a class="btn" routerLink="/ayuda">Ver la ayuda</a>
            <button type="button" class="btn btn-danger" (click)="salir()">Cerrar sesión</button>
          </div>
        </div></section>

        <section class="card"><div class="card-body">
          <h2 class="sec-titulo">Con tu rol puedes</h2>
          @for (r of roles(); track r) {
            <p style="margin: 8px 0 4px"><strong>{{ nombreRol(r) }}</strong></p>
            <ul class="lista-simple">@for (q of puede(r); track q) { <li>{{ q }}</li> }</ul>
          }
          <h2 class="sec-titulo" style="margin-top: 22px">Cuida tu cuenta</h2>
          <ul class="lista-simple">
            <li>No compartas tu clave, ni siquiera con el colegio: nadie debe pedírtela.</li>
            <li>Al terminar en un equipo compartido, cierra la sesión.</li>
            <li>Si notas actividad extraña, cambia tu clave y avisa a secretaría.</li>
          </ul>
        </div></section>
      </div>
    </div>
  `,
  styles: `
    .perfil-cab { display: flex; gap: 16px; align-items: center; }
    .avatar { width: 64px; height: 64px; border-radius: 50%; background: linear-gradient(135deg, var(--primary), #7c3aed); color: #fff; font-weight: 800; font-size: 1.4rem; display: grid; place-items: center; }
    .lista-simple { margin: 0 0 8px; padding-left: 20px; display: grid; gap: 4px; color: var(--ink-2); }
  `
})
export class PerfilComponent {
  private readonly auth = inject(AuthService);
  protected readonly usuario = this.auth.currentUser;
  protected readonly roles = this.auth.roles;
  protected readonly esKeycloak = this.auth.esKeycloak;
  protected readonly iniciales = computed(() => (this.usuario()?.username ?? '?').slice(0, 2).toUpperCase());
  protected readonly inicio = computed(() => rutaInicial(this.roles()));

  protected nombreRol(r: string): string { return NOMBRE_ROL[r] ?? r; }
  protected puede(r: string): string[] { return QUE_PUEDES[r] ?? []; }
  protected cambiarClave(): void { this.auth.esKeycloak ? this.auth.abrirCuenta() : void location.assign('/cambiar-clave'); }
  protected salir(): void { this.auth.logout(); }
}
