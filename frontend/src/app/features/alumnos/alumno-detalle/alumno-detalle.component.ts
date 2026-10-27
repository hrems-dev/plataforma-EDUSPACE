import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Alumno,
  Apoderado,
  DOCUMENTOS_REQUERIDOS,
  Documento,
  ESTADOS_ALUMNO,
  EstadoAlumno,
  HistorialEstado,
  PARENTESCOS,
  Parentesco,
  Salud,
  TIPOS_DOCUMENTO,
  TipoDocumento,
  Vinculo
} from '../../../core/models/alumno.model';
import { AlumnoService } from '../../../core/services/alumno.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

type Dialogo = 'ninguno' | 'datos' | 'estado' | 'vinculo' | 'nuevoVinculo' | 'salud';
type EstadoDoc = 'FALTA' | 'SIN_VERIFICAR' | 'VERIFICADO';

const MAX_BYTES = 5 * 1024 * 1024;
const CLASE_ESTADO: Record<EstadoAlumno, string> = {
  ACTIVO: 'badge-ok', INACTIVO: 'badge-gray', RETIRADO: 'badge-danger', TRASLADADO: 'badge-violet', EGRESADO: 'badge'
};

@Component({
  selector: 'app-alumno-detalle',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, ModalComponent, PageHeaderComponent],
  templateUrl: './alumno-detalle.component.html'
})
export class AlumnoDetalleComponent {
  private readonly api = inject(AlumnoService);
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  /** Parámetro de ruta `:id` (withComponentInputBinding). */
  readonly id = input.required<string>();
  protected readonly alumnoId = computed(() => Number(this.id()));

  protected readonly estados = ESTADOS_ALUMNO;
  protected readonly parentescos = PARENTESCOS;
  protected readonly tiposDocumento = TIPOS_DOCUMENTO;

  protected readonly alumno = signal<Alumno | null>(null);
  protected readonly documentos = signal<Documento[]>([]);
  protected readonly salud = signal<Salud | null>(null);
  protected readonly historial = signal<HistorialEstado[]>([]);
  protected readonly cargando = signal(true);
  protected readonly noExiste = signal(false);

  protected readonly dialogo = signal<Dialogo>('ninguno');
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);
  protected readonly vinculoSel = signal<Vinculo | null>(null);

  // Subida de documento
  protected readonly tipoSubida = this.fb.nonNullable.control<TipoDocumento>('DNI');
  protected readonly archivo = signal<File | null>(null);
  protected readonly subiendo = signal(false);

  // Estado de cada documento requerido (falta, subido sin verificar o verificado)
  protected readonly checklist = computed(() =>
    DOCUMENTOS_REQUERIDOS.map(tipo => {
      const docs = this.documentos().filter(d => d.tipo === tipo);
      const estado: EstadoDoc = docs.some(d => d.verificado) ? 'VERIFICADO' : docs.length ? 'SIN_VERIFICAR' : 'FALTA';
      return { tipo, estado };
    })
  );
  protected readonly expedienteCompleto = computed(() => this.checklist().every(c => c.estado === 'VERIFICADO'));

  protected readonly formDatos = this.fb.nonNullable.group({
    tipoDocumento: ['DNI'],
    numeroDocumento: ['', [Validators.required, Validators.maxLength(20)]],
    nombres: ['', Validators.required],
    apellidoPaterno: ['', Validators.required],
    apellidoMaterno: [''],
    fechaNacimiento: ['', Validators.required],
    sexo: ['', Validators.required],
    nacionalidad: [''],
    direccion: [''],
    distrito: [''],
    telefono: [''],
    email: ['', Validators.email],
    colegioProcedencia: ['']
  });

  protected readonly formEstado = this.fb.nonNullable.group({
    estado: ['ACTIVO' as EstadoAlumno, Validators.required],
    motivo: ['', [Validators.required, Validators.maxLength(255)]]
  });

  protected readonly formVinculo = this.fb.nonNullable.group({
    parentesco: ['MADRE' as Parentesco, Validators.required],
    esPrincipal: [false],
    esResponsablePago: [false],
    viveConAlumno: [false],
    puedeRecoger: [true]
  });

  protected readonly formApoderado = this.fb.nonNullable.group({
    tipoDocumento: ['DNI'],
    numeroDocumento: ['', [Validators.required, Validators.maxLength(20)]],
    nombres: ['', Validators.required],
    apellidos: ['', Validators.required],
    telefono: [''],
    email: ['', Validators.email]
  });
  protected readonly usarExistente = signal(false);
  protected readonly busquedaApoderado = this.fb.nonNullable.control('');
  protected readonly resultadosApoderado = signal<Apoderado[]>([]);
  protected readonly apoderadoElegido = signal<Apoderado | null>(null);

  protected readonly formSalud = this.fb.nonNullable.group({
    grupoSanguineo: [''],
    alergias: [''],
    observaciones: [''],
    seguroMedico: [''],
    contactoEmergenciaNombre: [''],
    contactoEmergenciaTelefono: ['']
  });

  constructor() {
    toObservable(this.alumnoId).subscribe(id => this.cargarTodo(id));
  }

  protected clase(e: EstadoAlumno): string {
    return CLASE_ESTADO[e];
  }

  protected claseDoc(e: EstadoDoc): string {
    return e === 'VERIFICADO' ? 'badge-ok' : e === 'SIN_VERIFICAR' ? 'badge-warn' : 'badge-danger';
  }

  protected textoDoc(e: EstadoDoc): string {
    return e === 'VERIFICADO' ? 'Verificado' : e === 'SIN_VERIFICAR' ? 'Subido, sin verificar' : 'Falta';
  }

  protected kb(bytes: number): string {
    return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }

  // --- carga ---
  private cargarTodo(id: number): void {
    this.cargando.set(true);
    this.noExiste.set(false);
    this.api.obtener(id).subscribe({
      next: a => {
        this.alumno.set(a);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.noExiste.set(e.status === 404);
        this.avisos.error(mensajeError(e, 'No se pudo cargar el alumno.'));
      }
    });
    this.recargarDocumentos(id);
    this.recargarHistorial(id);
    this.api.salud(id).subscribe({ next: s => this.salud.set(s), error: () => this.salud.set(null) }); // 404: aún sin ficha
  }

  private recargarAlumno(): void {
    this.api.obtener(this.alumnoId()).subscribe(a => this.alumno.set(a));
  }

  private recargarDocumentos(id = this.alumnoId()): void {
    this.api.documentos(id).subscribe({ next: d => this.documentos.set(d), error: e => this.avisos.error(mensajeError(e)) });
  }

  private recargarHistorial(id = this.alumnoId()): void {
    this.api.historialEstado(id).subscribe({ next: h => this.historial.set(h), error: () => this.historial.set([]) });
  }

  // --- diálogos ---
  protected cerrar(): void {
    this.dialogo.set('ninguno');
    this.errorForm.set(null);
  }

  private abrir(d: Dialogo): void {
    this.errorForm.set(null);
    this.dialogo.set(d);
  }

  private ejecutar<T>(obs: Observable<T>, ok: (r: T) => void): void {
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

  // --- datos ---
  protected editarDatos(): void {
    const a = this.alumno()!;
    this.formDatos.reset({
      tipoDocumento: a.tipoDocumento ?? 'DNI', numeroDocumento: a.numeroDocumento, nombres: a.nombres, apellidoPaterno: a.apellidoPaterno,
      apellidoMaterno: a.apellidoMaterno ?? '', fechaNacimiento: a.fechaNacimiento, sexo: a.sexo, nacionalidad: a.nacionalidad ?? '',
      direccion: a.direccion ?? '', distrito: a.distrito ?? '', telefono: a.telefono ?? '', email: a.email ?? '', colegioProcedencia: a.colegioProcedencia ?? ''
    });
    this.abrir('datos');
  }

  protected guardarDatos(): void {
    if (this.formDatos.invalid) {
      this.formDatos.markAllAsTouched();
      return;
    }
    const v = this.formDatos.getRawValue();
    const limpio = (s: string): string | undefined => s.trim() || undefined;
    this.ejecutar(
      this.api.actualizar(this.alumnoId(), {
        tipoDocumento: v.tipoDocumento, numeroDocumento: v.numeroDocumento.trim(), nombres: v.nombres.trim(),
        apellidoPaterno: v.apellidoPaterno.trim(), apellidoMaterno: limpio(v.apellidoMaterno), fechaNacimiento: v.fechaNacimiento,
        sexo: v.sexo, nacionalidad: limpio(v.nacionalidad), direccion: limpio(v.direccion), distrito: limpio(v.distrito),
        telefono: limpio(v.telefono), email: limpio(v.email), colegioProcedencia: limpio(v.colegioProcedencia)
      }),
      a => {
        this.alumno.set(a);
        this.avisos.success('Datos actualizados.');
        this.cerrar();
      }
    );
  }

  // --- estado ---
  protected cambiarEstado(): void {
    this.formEstado.reset({ estado: this.alumno()!.estado, motivo: '' });
    this.abrir('estado');
  }

  protected guardarEstado(): void {
    if (this.formEstado.invalid) {
      this.formEstado.markAllAsTouched();
      return;
    }
    const { estado, motivo } = this.formEstado.getRawValue();
    this.ejecutar(this.api.cambiarEstado(this.alumnoId(), estado, motivo.trim()), () => {
      this.avisos.success('Estado actualizado.');
      this.cerrar();
      this.recargarAlumno();
      this.recargarHistorial();
    });
  }

  // --- documentos ---
  protected elegirArchivo(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const f = input.files?.[0] ?? null;
    if (f && f.size > MAX_BYTES) {
      this.avisos.error('El archivo supera los 5 MB.');
      input.value = '';
      this.archivo.set(null);
      return;
    }
    this.archivo.set(f);
  }

  protected subir(entrada: HTMLInputElement): void {
    const f = this.archivo();
    if (!f) {
      this.avisos.warning('Elige un archivo primero.');
      return;
    }
    this.subiendo.set(true);
    this.api.subirDocumento(this.alumnoId(), this.tipoSubida.value, f).subscribe({
      next: () => {
        this.subiendo.set(false);
        this.avisos.success('Documento subido. Falta verificarlo para que cuente en el expediente.');
        this.archivo.set(null);
        entrada.value = '';
        this.recargarDocumentos();
      },
      error: e => {
        this.subiendo.set(false);
        this.avisos.error(mensajeError(e, 'No se pudo subir el documento.'));
      }
    });
  }

  protected alternarVerificado(d: Documento): void {
    this.api.verificarDocumento(this.alumnoId(), d.id, !d.verificado).subscribe({
      next: () => {
        this.avisos.success(d.verificado ? 'Verificación quitada.' : 'Documento verificado.');
        this.recargarDocumentos();
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }

  protected descargar(d: Documento): void {
    this.api.descargarDocumento(this.alumnoId(), d.id).subscribe({
      next: r => window.open(r.url, '_blank', 'noopener,noreferrer'),
      error: e => this.avisos.error(mensajeError(e, 'No se pudo generar el enlace de descarga.'))
    });
  }

  protected async eliminarDocumento(d: Documento): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Eliminar el documento ${d.nombreArchivo}?`, 'Eliminar', true))) return;
    this.api.eliminarDocumento(this.alumnoId(), d.id).subscribe({
      next: () => {
        this.avisos.success('Documento eliminado.');
        this.recargarDocumentos();
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }

  // --- apoderados ---
  protected editarVinculo(v: Vinculo): void {
    this.vinculoSel.set(v);
    this.formVinculo.reset({
      parentesco: v.parentesco, esPrincipal: v.esPrincipal, esResponsablePago: v.esResponsablePago,
      viveConAlumno: v.viveConAlumno, puedeRecoger: v.puedeRecoger
    });
    this.abrir('vinculo');
  }

  protected guardarVinculo(): void {
    const v = this.vinculoSel();
    if (!v) return;
    this.ejecutar(this.api.actualizarVinculo(this.alumnoId(), v.id, this.formVinculo.getRawValue()), () => {
      this.avisos.success('Vínculo actualizado.');
      this.cerrar();
      this.recargarAlumno();
    });
  }

  protected async quitarVinculo(v: Vinculo): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Quitar a ${v.apoderado.apellidos}, ${v.apoderado.nombres} como apoderado de este alumno?`, 'Quitar', true))) return;
    this.api.quitarVinculo(this.alumnoId(), v.id).subscribe({
      next: () => {
        this.avisos.success('Apoderado quitado.');
        this.recargarAlumno();
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }

  protected agregarApoderado(): void {
    this.formVinculo.reset({ parentesco: 'PADRE', esPrincipal: false, esResponsablePago: false, viveConAlumno: false, puedeRecoger: true });
    this.formApoderado.reset({ tipoDocumento: 'DNI' });
    this.usarExistente.set(false);
    this.apoderadoElegido.set(null);
    this.resultadosApoderado.set([]);
    this.busquedaApoderado.setValue('');
    this.abrir('nuevoVinculo');
  }

  protected modoApoderado(existente: boolean): void {
    this.usarExistente.set(existente);
    this.apoderadoElegido.set(null);
  }

  protected buscarApoderado(): void {
    const q = this.busquedaApoderado.value.trim();
    if (q.length < 2) {
      this.resultadosApoderado.set([]);
      return;
    }
    this.api.buscarApoderados(q).subscribe({ next: p => this.resultadosApoderado.set(p.contenido), error: e => this.avisos.error(mensajeError(e)) });
  }

  protected elegirApoderado(a: Apoderado): void {
    this.apoderadoElegido.set(a);
    this.resultadosApoderado.set([]);
  }

  protected guardarNuevoVinculo(): void {
    const existente = this.usarExistente();
    if (existente ? !this.apoderadoElegido() : this.formApoderado.invalid) {
      this.formApoderado.markAllAsTouched();
      this.errorForm.set(existente ? 'Elige un apoderado de la lista.' : 'Revisa los campos obligatorios del apoderado.');
      return;
    }
    const limpio = (s: string): string | undefined => s.trim() || undefined;
    const apo = this.formApoderado.getRawValue();
    this.ejecutar(
      this.api.agregarVinculo(this.alumnoId(), {
        ...this.formVinculo.getRawValue(),
        ...(existente
          ? { apoderadoId: this.apoderadoElegido()!.id }
          : {
              apoderado: {
                tipoDocumento: apo.tipoDocumento, numeroDocumento: apo.numeroDocumento.trim(), nombres: apo.nombres.trim(),
                apellidos: apo.apellidos.trim(), telefono: limpio(apo.telefono), email: limpio(apo.email)
              }
            })
      }),
      () => {
        this.avisos.success('Apoderado agregado.');
        this.cerrar();
        this.recargarAlumno();
      }
    );
  }

  // --- salud ---
  protected editarSalud(): void {
    const s = this.salud();
    this.formSalud.reset({
      grupoSanguineo: s?.grupoSanguineo ?? '', alergias: s?.alergias ?? '', observaciones: s?.observaciones ?? '',
      seguroMedico: s?.seguroMedico ?? '', contactoEmergenciaNombre: s?.contactoEmergenciaNombre ?? '',
      contactoEmergenciaTelefono: s?.contactoEmergenciaTelefono ?? ''
    });
    this.abrir('salud');
  }

  protected guardarSalud(): void {
    const v = this.formSalud.getRawValue();
    const limpio = (s: string): string | undefined => s.trim() || undefined;
    this.ejecutar(
      this.api.guardarSalud(this.alumnoId(), {
        grupoSanguineo: limpio(v.grupoSanguineo), alergias: limpio(v.alergias), observaciones: limpio(v.observaciones),
        seguroMedico: limpio(v.seguroMedico), contactoEmergenciaNombre: limpio(v.contactoEmergenciaNombre),
        contactoEmergenciaTelefono: limpio(v.contactoEmergenciaTelefono)
      }),
      s => {
        this.salud.set(s);
        this.avisos.success('Ficha de salud guardada.');
        this.cerrar();
      }
    );
  }
}
