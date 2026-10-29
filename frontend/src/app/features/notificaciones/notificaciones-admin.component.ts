import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  CANALES, CanalNotificacion, EstadoEnvio, FiltroAvisos, NotificacionAdmin, Plantilla, TIPOS_NOTIFICACION
} from '../../core/models/notificacion.model';
import { ConfirmService } from '../../core/services/confirm.service';
import { NotificacionService } from '../../core/services/notificacion.service';
import { NotificationService } from '../../core/services/notification.service';
import { mensajeError } from '../../core/utils/errores';
import { ModalComponent } from '../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { Destino, DestinatariosComponent } from './destinatarios.component';

type Pestana = 'enviar' | 'plantillas' | 'enviados';

const CODIGOS_SISTEMA = new Set(TIPOS_NOTIFICACION.map(t => t.codigo));

@Component({
  selector: 'app-notificaciones-admin',
  imports: [ReactiveFormsModule, DatePipe, ModalComponent, PageHeaderComponent, DestinatariosComponent],
  templateUrl: './notificaciones-admin.component.html'
})
export class NotificacionesAdminComponent implements OnInit {
  private readonly api = inject(NotificacionService);
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  protected readonly canales = CANALES;
  protected readonly tipos = [{ codigo: 'AVISO_MANUAL', etiqueta: 'Aviso manual' }, ...TIPOS_NOTIFICACION];
  protected readonly pestana = signal<Pestana>('enviar');

  // --- plantillas
  protected readonly plantillas = signal<Plantilla[]>([]);
  protected readonly plantillaAbierta = signal(false);
  protected readonly editando = signal<Plantilla | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);

  protected readonly formPlantilla = this.fb.nonNullable.group({
    codigo: ['', [Validators.required, Validators.pattern(/^[A-Z][A-Z0-9_]{2,59}$/)]],
    canal: ['IN_APP' as CanalNotificacion],
    asunto: [''],
    cuerpo: ['', Validators.required],
    activa: [true]
  });

  // --- enviar
  protected readonly destino = signal<Destino | null>(null);
  protected readonly enviando = signal(false);
  protected readonly resultado = signal<{ ok: boolean; texto: string } | null>(null);
  protected readonly formEnvio = this.fb.nonNullable.group({
    titulo: ['', [Validators.required, Validators.maxLength(200)]],
    mensaje: ['', Validators.required],
    inApp: [true],
    push: [false]
  });

  // --- enviados
  protected readonly lista = signal<NotificacionAdmin[]>([]);
  protected readonly total = signal(0);
  protected readonly pagina = signal(0);
  protected readonly totalPaginas = signal(0);
  protected readonly cargandoLista = signal(false);
  protected readonly filtro = signal<FiltroAvisos>({});
  protected readonly avisoEditando = signal<NotificacionAdmin | null>(null);
  protected readonly formAviso = this.fb.nonNullable.group({
    titulo: ['', [Validators.required, Validators.maxLength(200)]],
    mensaje: ['', Validators.required]
  });

  ngOnInit(): void {
    this.cargarPlantillas();
  }

  protected ir(p: Pestana): void {
    this.pestana.set(p);
    if (p === 'enviados' && this.lista().length === 0) this.cargarLista(0);
  }

  protected canalTexto(c: CanalNotificacion): string {
    return c === 'IN_APP' ? 'En la app' : c === 'EMAIL' ? 'Correo' : 'Push';
  }

  protected tipoTexto(codigo: string): string {
    return this.tipos.find(t => t.codigo === codigo)?.etiqueta ?? codigo;
  }

  protected esDelSistema(p: Plantilla): boolean {
    return CODIGOS_SISTEMA.has(p.codigo);
  }

  protected claseEstado(e: EstadoEnvio): string {
    return e === 'ENVIADA' ? 'badge-ok' : e === 'FALLIDA' ? 'badge-danger' : e === 'PENDIENTE' ? 'badge-warn' : 'badge-gray';
  }

  // ---------------------------------------------------------------- plantillas
  private cargarPlantillas(): void {
    this.api.plantillas().subscribe({
      next: p => this.plantillas.set(p),
      error: e => this.avisos.error(mensajeError(e, 'No se pudieron cargar las plantillas.'))
    });
  }

  protected nuevaPlantilla(): void {
    this.formPlantilla.reset({ codigo: '', canal: 'IN_APP', asunto: '', cuerpo: '', activa: true });
    this.formPlantilla.controls.codigo.enable();
    this.formPlantilla.controls.canal.enable();
    this.editando.set(null);
    this.errorForm.set(null);
    this.plantillaAbierta.set(true);
  }

  protected editar(p: Plantilla): void {
    this.formPlantilla.reset({ codigo: p.codigo, canal: p.canal, asunto: p.asunto ?? '', cuerpo: p.cuerpo, activa: p.activa });
    this.formPlantilla.controls.codigo.disable(); // el código y el canal identifican la plantilla: no se cambian
    this.formPlantilla.controls.canal.disable();
    this.editando.set(p);
    this.errorForm.set(null);
    this.plantillaAbierta.set(true);
  }

  protected cerrarPlantilla(): void {
    this.plantillaAbierta.set(false);
    this.editando.set(null);
  }

  protected guardarPlantilla(): void {
    if (this.formPlantilla.invalid) {
      this.formPlantilla.markAllAsTouched();
      return;
    }
    const v = this.formPlantilla.getRawValue();
    const asunto = v.asunto.trim() || undefined;
    const p = this.editando();
    this.guardando.set(true);
    (p
      ? this.api.actualizarPlantilla(p.id, { asunto, cuerpo: v.cuerpo, activa: v.activa })
      : this.api.crearPlantilla({ codigo: v.codigo.trim(), canal: v.canal, asunto, cuerpo: v.cuerpo, activa: v.activa })
    ).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(p ? 'Plantilla actualizada.' : 'Plantilla creada.');
        this.cerrarPlantilla();
        this.cargarPlantillas();
      },
      error: e => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(e));
      }
    });
  }

  protected async eliminarPlantilla(p: Plantilla): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Eliminar la plantilla ${p.codigo} (${this.canalTexto(p.canal)})?`, 'Eliminar', true))) return;
    this.api.eliminarPlantilla(p.id).subscribe({
      next: () => {
        this.avisos.success('Plantilla eliminada.');
        this.cargarPlantillas();
      },
      error: e => this.avisos.error(mensajeError(e, 'No se pudo eliminar la plantilla.'))
    });
  }

  // ---------------------------------------------------------------- enviar
  protected alCambiarDestino(d: Destino): void {
    this.destino.set(d);
  }

  protected enviar(): void {
    const d = this.destino();
    if (this.formEnvio.invalid) {
      this.formEnvio.markAllAsTouched();
      return;
    }
    if (!d || d.usuarioIds.length === 0) {
      this.resultado.set({ ok: false, texto: 'Elige a quién enviar el aviso.' });
      return;
    }
    const v = this.formEnvio.getRawValue();
    const canales: CanalNotificacion[] = [];
    if (v.inApp) canales.push('IN_APP');
    if (v.push) canales.push('PUSH');
    if (canales.length === 0) {
      this.resultado.set({ ok: false, texto: 'Elige al menos un canal.' });
      return;
    }
    this.enviando.set(true);
    this.resultado.set(null);
    this.api.enviar({ usuarioIds: d.usuarioIds, titulo: v.titulo.trim(), mensaje: v.mensaje.trim(), canales }).subscribe({
      next: r => {
        this.enviando.set(false);
        this.resultado.set({ ok: true, texto: `Enviado a ${d.usuarioIds.length} usuario(s): ${r.creadas} aviso(s) creados, ${r.omitidas} omitidos (preferencias o sin dispositivo).` });
        this.avisos.success('Aviso enviado.');
        this.formEnvio.reset({ titulo: '', mensaje: '', inApp: true, push: false });
        this.lista.set([]); // el historial se vuelve a leer al abrirlo
      },
      error: e => {
        this.enviando.set(false);
        this.resultado.set({ ok: false, texto: mensajeError(e, 'No se pudo enviar el aviso.') });
      }
    });
  }

  // ---------------------------------------------------------------- enviados
  protected filtrar(campo: keyof FiltroAvisos, valor: string): void {
    this.filtro.update(f => ({ ...f, [campo]: campo === 'usuarioId' ? (Number(valor) || undefined) : valor || undefined }));
    this.cargarLista(0);
  }

  protected cargarLista(pagina: number): void {
    this.cargandoLista.set(true);
    this.api.avisos(this.filtro(), pagina, 12).subscribe({
      next: p => {
        this.lista.set(p.contenido);
        this.total.set(p.totalElementos);
        this.pagina.set(p.pagina);
        this.totalPaginas.set(p.totalPaginas);
        this.cargandoLista.set(false);
      },
      error: e => {
        this.cargandoLista.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar los avisos.'));
      }
    });
  }

  protected editarAviso(n: NotificacionAdmin): void {
    this.formAviso.reset({ titulo: n.titulo, mensaje: n.mensaje });
    this.errorForm.set(null);
    this.avisoEditando.set(n);
  }

  protected guardarAviso(): void {
    const n = this.avisoEditando();
    if (!n || this.formAviso.invalid) {
      this.formAviso.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    this.api.editarAviso(n.id, this.formAviso.getRawValue()).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success('Aviso actualizado.');
        this.avisoEditando.set(null);
        this.cargarLista(this.pagina());
      },
      error: e => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(e));
      }
    });
  }

  protected async eliminarAviso(n: NotificacionAdmin): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Eliminar el aviso «${n.titulo}»? Desaparece de la bandeja del usuario ${n.usuarioId}.`, 'Eliminar', true))) return;
    this.api.eliminarAviso(n.id).subscribe({
      next: () => {
        this.avisos.success('Aviso eliminado.');
        this.cargarLista(this.pagina());
      },
      error: e => this.avisos.error(mensajeError(e, 'No se pudo eliminar el aviso.'))
    });
  }

  protected valor(e: Event): string {
    return (e.target as HTMLInputElement | HTMLSelectElement).value;
  }
}
