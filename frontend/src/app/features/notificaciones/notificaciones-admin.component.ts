import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CANALES, CanalNotificacion, Plantilla } from '../../core/models/notificacion.model';
import { NotificacionService } from '../../core/services/notificacion.service';
import { NotificationService } from '../../core/services/notification.service';
import { mensajeError } from '../../core/utils/errores';
import { ModalComponent } from '../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-notificaciones-admin',
  imports: [ReactiveFormsModule, ModalComponent, PageHeaderComponent],
  templateUrl: './notificaciones-admin.component.html'
})
export class NotificacionesAdminComponent implements OnInit {
  private readonly api = inject(NotificacionService);
  private readonly avisos = inject(NotificationService);
  private readonly fb = inject(FormBuilder);

  protected readonly canales = CANALES;
  protected readonly plantillas = signal<Plantilla[]>([]);
  protected readonly editando = signal<Plantilla | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);
  protected readonly enviando = signal(false);
  protected readonly resultado = signal<string | null>(null);

  protected readonly formPlantilla = this.fb.nonNullable.group({
    asunto: [''],
    cuerpo: ['', Validators.required],
    activa: [true]
  });

  protected readonly formEnvio = this.fb.nonNullable.group({
    usuarioIds: ['', Validators.required],
    titulo: ['', [Validators.required, Validators.maxLength(150)]],
    mensaje: ['', Validators.required],
    inApp: [true],
    email: [false],
    push: [false]
  });

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.api.plantillas().subscribe({
      next: p => this.plantillas.set(p),
      error: e => this.avisos.error(mensajeError(e, 'No se pudieron cargar las plantillas.'))
    });
  }

  protected canalTexto(c: CanalNotificacion): string {
    return c === 'IN_APP' ? 'En la app' : c === 'EMAIL' ? 'Correo' : 'Push';
  }

  protected editar(p: Plantilla): void {
    this.formPlantilla.reset({ asunto: p.asunto ?? '', cuerpo: p.cuerpo, activa: p.activa });
    this.errorForm.set(null);
    this.editando.set(p);
  }

  protected cerrar(): void {
    this.editando.set(null);
  }

  protected guardarPlantilla(): void {
    const p = this.editando();
    if (!p || this.formPlantilla.invalid) {
      this.formPlantilla.markAllAsTouched();
      return;
    }
    const v = this.formPlantilla.getRawValue();
    this.guardando.set(true);
    this.api.actualizarPlantilla(p.id, { asunto: v.asunto.trim() || undefined, cuerpo: v.cuerpo, activa: v.activa }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success('Plantilla actualizada.');
        this.cerrar();
        this.cargar();
      },
      error: e => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(e));
      }
    });
  }

  protected enviar(): void {
    if (this.formEnvio.invalid) {
      this.formEnvio.markAllAsTouched();
      return;
    }
    const v = this.formEnvio.getRawValue();
    const ids = v.usuarioIds.split(/[\s,;]+/).filter(Boolean).map(Number);
    if (ids.length === 0 || ids.some(n => !Number.isInteger(n) || n <= 0)) {
      this.resultado.set('Los destinatarios deben ser números de usuario separados por coma.');
      return;
    }
    const canales: CanalNotificacion[] = [];
    if (v.inApp) canales.push('IN_APP');
    if (v.email) canales.push('EMAIL');
    if (v.push) canales.push('PUSH');
    if (canales.length === 0) {
      this.resultado.set('Elige al menos un canal.');
      return;
    }
    this.enviando.set(true);
    this.resultado.set(null);
    this.api.enviar({ usuarioIds: ids, titulo: v.titulo.trim(), mensaje: v.mensaje.trim(), canales }).subscribe({
      next: r => {
        this.enviando.set(false);
        this.resultado.set(`Enviado: ${r.creadas} creadas, ${r.omitidas} omitidas (preferencias del destinatario).`);
        this.avisos.success('Aviso enviado.');
      },
      error: e => {
        this.enviando.set(false);
        this.resultado.set(mensajeError(e, 'No se pudo enviar el aviso.'));
      }
    });
  }
}
