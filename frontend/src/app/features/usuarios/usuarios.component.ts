import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ROLES } from '../../core/models/auth.model';
import { Usuario } from '../../core/models/usuario.model';
import { AuthService } from '../../core/services/auth.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NotificationService } from '../../core/services/notification.service';
import { UsuarioService } from '../../core/services/usuario.service';
import { mensajeError } from '../../core/utils/errores';
import { ModalComponent } from '../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

type Dialogo = 'ninguno' | 'crear' | 'editar' | 'roles' | 'clave';

@Component({
  selector: 'app-usuarios',
  imports: [ReactiveFormsModule, DatePipe, ModalComponent, PageHeaderComponent],
  templateUrl: './usuarios.component.html'
})
export class UsuariosComponent implements OnInit {
  private readonly api = inject(UsuarioService);
  private readonly avisos = inject(NotificationService);
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly confirmacion = inject(ConfirmService);

  protected readonly todosLosRoles = ROLES;
  protected readonly usuarios = signal<Usuario[]>([]);
  protected readonly total = signal(0);
  protected readonly paginas = signal(1);
  protected readonly pagina = signal(0);
  protected readonly cargando = signal(false);

  protected readonly dialogo = signal<Dialogo>('ninguno');
  protected readonly seleccionado = signal<Usuario | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);
  /** Clave temporal recién generada: se muestra una sola vez. */
  protected readonly claveTemporal = signal<string | null>(null);
  protected readonly rolesElegidos = signal<string[]>([]);

  protected readonly filtro = this.fb.nonNullable.group({ texto: '', rol: '', activo: '' });

  protected readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.maxLength(50)]],
    email: ['', [Validators.required, Validators.email]],
    nombres: ['', [Validators.required]],
    apellidos: ['', [Validators.required]]
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected yoMismo(u: Usuario): boolean {
    return u.id === this.auth.currentUser()?.id;
  }

  protected buscar(): void {
    this.cargar(0);
  }

  protected irA(pagina: number): void {
    if (pagina >= 0 && pagina < this.paginas()) {
      this.cargar(pagina);
    }
  }

  private cargar(pagina = this.pagina()): void {
    const f = this.filtro.getRawValue();
    this.cargando.set(true);
    this.api
      .listar({ texto: f.texto || undefined, rol: f.rol || undefined, activo: f.activo === '' ? undefined : f.activo === 'true', page: pagina, size: 15 })
      .subscribe({
        next: p => {
          this.usuarios.set(p.contenido);
          this.total.set(p.totalElementos);
          this.paginas.set(Math.max(1, p.totalPaginas));
          this.pagina.set(p.pagina);
          this.cargando.set(false);
        },
        error: e => {
          this.cargando.set(false);
          this.avisos.error(mensajeError(e, 'No se pudo cargar la lista de usuarios.'));
        }
      });
  }

  // --- Diálogos ---
  protected abrirCrear(): void {
    this.form.reset({ username: '', email: '', nombres: '', apellidos: '' });
    this.form.controls.username.enable();
    this.rolesElegidos.set([]);
    this.abrir('crear', null);
  }

  protected abrirEditar(u: Usuario): void {
    this.form.reset({ username: u.username, email: u.email, nombres: u.nombres, apellidos: u.apellidos });
    this.form.controls.username.disable(); // el nombre de usuario no cambia
    this.abrir('editar', u);
  }

  protected abrirRoles(u: Usuario): void {
    this.rolesElegidos.set([...u.roles]);
    this.abrir('roles', u);
  }

  protected cerrar(): void {
    this.dialogo.set('ninguno');
    this.seleccionado.set(null);
    this.claveTemporal.set(null);
    this.errorForm.set(null);
  }

  private abrir(d: Dialogo, u: Usuario | null): void {
    this.errorForm.set(null);
    this.claveTemporal.set(null);
    this.seleccionado.set(u);
    this.dialogo.set(d);
  }

  protected alternarRol(rol: string, marcado: boolean): void {
    this.rolesElegidos.update(r => (marcado ? [...new Set([...r, rol])] : r.filter(x => x !== rol)));
  }

  protected tieneRol(rol: string): boolean {
    return this.rolesElegidos().includes(rol);
  }

  // --- Acciones ---
  protected guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    if (this.dialogo() === 'crear') {
      if (this.rolesElegidos().length === 0) {
        this.errorForm.set('Elige al menos un rol.');
        return;
      }
      this.ejecutar(
        this.api.crear({ username: v.username.trim(), email: v.email.trim(), nombres: v.nombres.trim(), apellidos: v.apellidos.trim(), roles: this.rolesElegidos() }),
        r => {
          this.avisos.success(`Usuario ${r.usuario.username} creado.`);
          this.claveTemporal.set(r.claveTemporal ?? null);
          this.dialogo.set('clave');
          this.cargar(0);
        }
      );
    } else {
      this.ejecutar(this.api.actualizar(this.seleccionado()!.id, { email: v.email.trim(), nombres: v.nombres.trim(), apellidos: v.apellidos.trim() }), () => {
        this.avisos.success('Datos actualizados.');
        this.cerrar();
        this.cargar();
      });
    }
  }

  protected guardarRoles(): void {
    if (this.rolesElegidos().length === 0) {
      this.errorForm.set('El usuario debe tener al menos un rol.');
      return;
    }
    this.ejecutar(this.api.asignarRoles(this.seleccionado()!.id, this.rolesElegidos()), () => {
      this.avisos.success('Roles actualizados.');
      this.cerrar();
      this.cargar();
    });
  }

  protected async restablecer(u: Usuario): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Restablecer la clave de ${u.username}? Se generará una clave temporal y sus sesiones se cerrarán.`, 'Restablecer'))) return;
    this.api.restablecerClave(u.id).subscribe({
      next: r => {
        this.abrir('clave', u);
        this.claveTemporal.set(r.claveTemporal ?? null);
        this.cargar();
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }

  protected async alternarActivo(u: Usuario): Promise<void> {
    const accion = u.activo ? 'desactivar' : 'activar';
    if (u.activo && !(await this.confirmacion.confirmar(`¿Desactivar a ${u.username}? No podrá iniciar sesión.`, 'Desactivar', true))) return;
    (u.activo ? this.api.desactivar(u.id) : this.api.activar(u.id)).subscribe({
      next: () => {
        this.avisos.success(`Usuario ${accion === 'activar' ? 'activado' : 'desactivado'}.`);
        this.cargar();
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }

  protected desbloquear(u: Usuario): void {
    this.api.desbloquear(u.id).subscribe({
      next: () => {
        this.avisos.success('Usuario desbloqueado.');
        this.cargar();
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }

  private ejecutar<T>(obs: import('rxjs').Observable<T>, ok: (r: T) => void): void {
    this.guardando.set(true);
    this.errorForm.set(null);
    obs.subscribe({
      next: r => {
        this.guardando.set(false);
        ok(r);
      },
      error: e => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(e));
      }
    });
  }
}
