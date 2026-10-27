import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Apoderado, AlumnoResumen, ESTADOS_ALUMNO, EstadoAlumno, PARENTESCOS, Parentesco } from '../../core/models/alumno.model';
import { AlumnoService } from '../../core/services/alumno.service';
import { NotificationService } from '../../core/services/notification.service';
import { mensajeError } from '../../core/utils/errores';
import { ModalComponent } from '../../shared/components/modal/modal.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

const CLASE_ESTADO: Record<EstadoAlumno, string> = {
  ACTIVO: 'badge-ok', INACTIVO: 'badge-gray', RETIRADO: 'badge-danger', TRASLADADO: 'badge-violet', EGRESADO: 'badge'
};

@Component({
  selector: 'app-alumnos',
  imports: [ReactiveFormsModule, RouterLink, ModalComponent, PageHeaderComponent],
  templateUrl: './alumnos.component.html'
})
export class AlumnosComponent implements OnInit {
  private readonly api = inject(AlumnoService);
  private readonly avisos = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly estados = ESTADOS_ALUMNO;
  protected readonly parentescos = PARENTESCOS;
  protected readonly alumnos = signal<AlumnoResumen[]>([]);
  protected readonly total = signal(0);
  protected readonly paginas = signal(1);
  protected readonly pagina = signal(0);
  protected readonly cargando = signal(false);

  protected readonly abierto = signal(false);
  protected readonly guardando = signal(false);
  protected readonly errorForm = signal<string | null>(null);

  protected readonly filtro = this.fb.nonNullable.group({ q: '', estado: '' as EstadoAlumno | '' });

  // --- alta ---
  protected readonly usarExistente = signal(false);
  protected readonly resultadosApoderado = signal<Apoderado[]>([]);
  protected readonly apoderadoElegido = signal<Apoderado | null>(null);
  protected readonly busquedaApoderado = this.fb.nonNullable.control('');

  protected readonly form = this.fb.nonNullable.group({
    tipoDocumento: ['DNI'],
    numeroDocumento: ['', [Validators.required, Validators.maxLength(20)]],
    nombres: ['', Validators.required],
    apellidoPaterno: ['', Validators.required],
    apellidoMaterno: [''],
    fechaNacimiento: ['', Validators.required],
    sexo: ['', Validators.required],
    nacionalidad: ['Peruana'],
    direccion: [''],
    distrito: [''],
    telefono: [''],
    email: ['', Validators.email],
    colegioProcedencia: [''],
    parentesco: ['MADRE' as Parentesco, Validators.required]
  });

  protected readonly formApoderado = this.fb.nonNullable.group({
    tipoDocumento: ['DNI'],
    numeroDocumento: ['', [Validators.required, Validators.maxLength(20)]],
    nombres: ['', Validators.required],
    apellidos: ['', Validators.required],
    telefono: [''],
    email: ['', Validators.email]
  });

  ngOnInit(): void {
    this.cargar(0);
    // Enlace desde el inicio: ?nuevo=1 abre el formulario de alta.
    if (this.ruta.snapshot.queryParamMap.get('nuevo')) this.nuevo();
  }

  protected clase(e: EstadoAlumno): string {
    return CLASE_ESTADO[e];
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
    const f = this.filtro.getRawValue();
    this.cargando.set(true);
    this.api.listar(f.q.trim(), f.estado, pagina).subscribe({
      next: p => {
        this.alumnos.set(p.contenido);
        this.total.set(p.totalElementos);
        this.paginas.set(Math.max(1, p.totalPaginas));
        this.pagina.set(p.pagina);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(mensajeError(e, 'No se pudo cargar la lista de alumnos.'));
      }
    });
  }

  // --- alta ---
  protected nuevo(): void {
    this.form.reset({ tipoDocumento: 'DNI', nacionalidad: 'Peruana', parentesco: 'MADRE' });
    this.formApoderado.reset({ tipoDocumento: 'DNI' });
    this.usarExistente.set(false);
    this.apoderadoElegido.set(null);
    this.resultadosApoderado.set([]);
    this.busquedaApoderado.setValue('');
    this.errorForm.set(null);
    this.abierto.set(true);
  }

  protected cerrar(): void {
    this.abierto.set(false);
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
    this.api.buscarApoderados(q).subscribe({
      next: p => this.resultadosApoderado.set(p.contenido),
      error: e => this.avisos.error(mensajeError(e))
    });
  }

  protected elegirApoderado(a: Apoderado): void {
    this.apoderadoElegido.set(a);
    this.resultadosApoderado.set([]);
  }

  protected guardar(): void {
    const existente = this.usarExistente();
    const apoderadoValido = existente ? !!this.apoderadoElegido() : this.formApoderado.valid;
    if (this.form.invalid || !apoderadoValido) {
      this.form.markAllAsTouched();
      this.formApoderado.markAllAsTouched();
      this.errorForm.set(existente && !this.apoderadoElegido() ? 'Elige un apoderado de la lista.' : 'Revisa los campos obligatorios.');
      return;
    }
    const v = this.form.getRawValue();
    const limpio = (s: string): string | undefined => s.trim() || undefined;
    const apo = this.formApoderado.getRawValue();
    const vinculo = {
      parentesco: v.parentesco,
      esPrincipal: true,
      esResponsablePago: true,
      viveConAlumno: true,
      puedeRecoger: true,
      ...(existente
        ? { apoderadoId: this.apoderadoElegido()!.id }
        : {
            apoderado: {
              tipoDocumento: apo.tipoDocumento,
              numeroDocumento: apo.numeroDocumento.trim(),
              nombres: apo.nombres.trim(),
              apellidos: apo.apellidos.trim(),
              telefono: limpio(apo.telefono),
              email: limpio(apo.email)
            }
          })
    };
    this.guardando.set(true);
    this.errorForm.set(null);
    this.api
      .crear({
        tipoDocumento: v.tipoDocumento,
        numeroDocumento: v.numeroDocumento.trim(),
        nombres: v.nombres.trim(),
        apellidoPaterno: v.apellidoPaterno.trim(),
        apellidoMaterno: limpio(v.apellidoMaterno),
        fechaNacimiento: v.fechaNacimiento,
        sexo: v.sexo,
        nacionalidad: limpio(v.nacionalidad),
        direccion: limpio(v.direccion),
        distrito: limpio(v.distrito),
        telefono: limpio(v.telefono),
        email: limpio(v.email),
        colegioProcedencia: limpio(v.colegioProcedencia),
        apoderados: [vinculo]
      })
      .subscribe({
        next: alumno => {
          this.guardando.set(false);
          this.avisos.success(`Alumno ${alumno.codigo} registrado. Sube y verifica sus documentos para poder matricularlo.`);
          this.cerrar();
          this.router.navigate(['/alumnos', alumno.id]);
        },
        error: e => {
          this.guardando.set(false);
          this.errorForm.set(mensajeError(e));
        }
      });
  }
}
