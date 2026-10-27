import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ResultadoImportacion } from '../../../core/models/alumno.model';
import { AlumnoService } from '../../../core/services/alumno.service';
import { NotificationService } from '../../../core/services/notification.service';
import { mensajeError } from '../../../core/utils/errores';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

const PLANTILLA =
  'numeroDocumento,nombres,apellidoPaterno,apellidoMaterno,fechaNacimiento,sexo,tipoDocumento,direccion,distrito,telefono,email,' +
  'apoderadoTipoDocumento,apoderadoNumeroDocumento,apoderadoNombres,apoderadoApellidos,apoderadoTelefono,apoderadoEmail,apoderadoParentesco\n' +
  '71234567,Lucía,Mendoza,Ríos,2015-04-12,F,DNI,Av. Lima 123,Miraflores,999111222,,DNI,40123456,Carmen,Ríos,999333444,carmen@ejemplo.com,MADRE\n';

@Component({
  selector: 'app-importar-alumnos',
  imports: [RouterLink, PageHeaderComponent],
  templateUrl: './importar.component.html'
})
export class ImportarAlumnosComponent {
  private readonly api = inject(AlumnoService);
  private readonly avisos = inject(NotificationService);

  protected readonly archivo = signal<File | null>(null);
  protected readonly enviando = signal(false);
  protected readonly resultado = signal<ResultadoImportacion | null>(null);
  protected readonly error = signal<string | null>(null);

  protected elegir(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    this.archivo.set(input.files?.[0] ?? null);
    this.resultado.set(null);
    this.error.set(null);
  }

  protected descargarPlantilla(): void {
    const blob = new Blob(['﻿' + PLANTILLA], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'plantilla-alumnos.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  protected importar(): void {
    const f = this.archivo();
    if (!f) return;
    this.enviando.set(true);
    this.error.set(null);
    this.api.importar(f).subscribe({
      next: r => {
        this.enviando.set(false);
        this.resultado.set(r);
        this.avisos[r.fallidos === 0 ? 'success' : 'warning'](`Importación: ${r.creados} creados, ${r.fallidos} con error.`);
      },
      error: e => {
        this.enviando.set(false);
        this.error.set(mensajeError(e, 'No se pudo importar el archivo.'));
      }
    });
  }
}
