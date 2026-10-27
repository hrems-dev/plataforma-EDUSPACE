import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Docente } from '../../../core/models/planificacion.model';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PlanificacionService } from '../../../core/services/planificacion.service';
import { mensajeError } from '../../../core/utils/errores';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-docentes',
  imports: [ReactiveFormsModule, ModalComponent, PageHeaderComponent],
  templateUrl: './docentes.component.html'
})
export class DocentesComponent implements OnInit {
  private readonly api = inject(PlanificacionService);
  private readonly avisos = inject(NotificationService);
  private readonly confirmacion = inject(ConfirmService);
  private readonly fb = inject(FormBuilder);

  protected readonly docentes = signal<Docente[]>([]);
  protected readonly total = signal(0);
  protected readonly paginas = signal(1);
  protected readonly pagina = signal(0);
  protected readonly cargando = signal(false);

  protected readonly abierto = signal(false);
  protected readonly editando = signal<Docente | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);

  protected readonly busqueda = this.fb.nonNullable.control('');

  protected readonly form = this.fb.nonNullable.group({
    tipoDocumento: ['DNI', Validators.required],
    numeroDocumento: ['', [Validators.required, Validators.maxLength(20)]],
    nombres: ['', Validators.required],
    apellidos: ['', Validators.required],
    email: ['', Validators.email],
    telefono: [''],
    especialidad: ['']
  });

  ngOnInit(): void {
    this.cargar(0);
  }

  protected buscar(): void {
    this.cargar(0);
  }

  protected irA(pagina: number): void {
    if (pagina >= 0 && pagina < this.paginas()) {
      this.cargar(pagina);
    }
  }

  private cargar(pagina: number): void {
    this.cargando.set(true);
    this.api.docentes(this.busqueda.value.trim(), pagina, 15).subscribe({
      next: p => {
        this.docentes.set(p.contenido);
        this.total.set(p.totalElementos);
        this.paginas.set(Math.max(1, p.totalPaginas));
        this.pagina.set(p.pagina);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudieron cargar los docentes.'));
      }
    });
  }

  protected nuevo(): void {
    this.form.reset({ tipoDocumento: 'DNI', numeroDocumento: '', nombres: '', apellidos: '', email: '', telefono: '', especialidad: '' });
    this.abrir(null);
  }

  protected editar(d: Docente): void {
    this.form.reset({
      tipoDocumento: d.tipoDocumento ?? 'DNI', numeroDocumento: d.numeroDocumento, nombres: d.nombres, apellidos: d.apellidos,
      email: d.email ?? '', telefono: d.telefono ?? '', especialidad: d.especialidad ?? ''
    });
    this.abrir(d);
  }

  private abrir(d: Docente | null): void {
    this.errorForm.set(null);
    this.editando.set(d);
    this.abierto.set(true);
  }

  protected cerrar(): void {
    this.abierto.set(false);
    this.editando.set(null);
  }

  protected guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const body = {
      tipoDocumento: v.tipoDocumento,
      numeroDocumento: v.numeroDocumento.trim(),
      nombres: v.nombres.trim(),
      apellidos: v.apellidos.trim(),
      email: v.email.trim() || undefined,
      telefono: v.telefono.trim() || undefined,
      especialidad: v.especialidad.trim() || undefined
    };
    const e = this.editando();
    this.guardando.set(true);
    (e ? this.api.actualizarDocente(e.id, body) : this.api.crearDocente(body)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.success(e ? 'Docente actualizado.' : 'Docente registrado.');
        this.cerrar();
        this.cargar(e ? this.pagina() : 0);
      },
      error: err => {
        this.guardando.set(false);
        this.errorForm.set(mensajeError(err));
      }
    });
  }

  protected async eliminar(d: Docente): Promise<void> {
    if (!(await this.confirmacion.confirmar(`¿Dar de baja a ${d.apellidos}, ${d.nombres}?`, 'Dar de baja', true))) return;
    this.api.eliminarDocente(d.id).subscribe({
      next: () => {
        this.avisos.success('Docente dado de baja.');
        this.cargar(this.pagina());
      },
      error: e => this.avisos.error(mensajeError(e))
    });
  }
}
