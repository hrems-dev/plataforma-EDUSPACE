import { Component, inject, signal } from '@angular/core';
import { AlumnoResumen } from '../../../core/models/alumno.model';
import { Libreta } from '../../../core/models/calificacion.model';
import { CalificacionService } from '../../../core/services/calificacion.service';
import { NotificationService } from '../../../core/services/notification.service';
import { mensajeError } from '../../../core/utils/errores';
import { BuscadorAlumnoComponent } from '../../../shared/components/buscador-alumno/buscador-alumno.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { LibretaVistaComponent } from '../libreta-vista/libreta-vista.component';

@Component({
  selector: 'app-boletines',
  imports: [BuscadorAlumnoComponent, LibretaVistaComponent, PageHeaderComponent],
  templateUrl: './boletines.component.html'
})
export class BoletinesComponent {
  private readonly api = inject(CalificacionService);
  private readonly avisos = inject(NotificationService);

  protected readonly libreta = signal<Libreta | null>(null);
  protected readonly cargando = signal(false);
  protected readonly sinDatos = signal(false);

  protected elegir(a: AlumnoResumen | null): void {
    this.libreta.set(null);
    this.sinDatos.set(false);
    if (!a) return;
    this.cargando.set(true);
    this.api.libreta(a.id).subscribe({
      next: l => {
        this.libreta.set(l);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.sinDatos.set(true);
        this.avisos.warning(mensajeError(e, 'Este alumno aún no tiene libreta.'));
      }
    });
  }
}
