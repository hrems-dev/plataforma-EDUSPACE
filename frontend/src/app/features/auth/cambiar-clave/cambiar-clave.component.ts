import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { mensajeError } from '../../../core/utils/errores';

const iguales = (g: AbstractControl): ValidationErrors | null =>
  g.get('claveNueva')?.value === g.get('confirmacion')?.value ? null : { noCoinciden: true };

@Component({
  selector: 'app-cambiar-clave',
  imports: [ReactiveFormsModule],
  templateUrl: './cambiar-clave.component.html',
  styleUrl: '../login/login.component.css'
})
export class CambiarClaveComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly avisos = inject(NotificationService);

  protected readonly obligatorio = this.auth.debeCambiarClave;
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group(
    {
      claveActual: ['', [Validators.required]],
      claveNueva: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
      confirmacion: ['', [Validators.required]]
    },
    { validators: iguales }
  );

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    const { claveActual, claveNueva } = this.form.getRawValue();

    this.auth.cambiarClave({ claveActual, claveNueva }).subscribe({
      next: () => {
        this.cargando.set(false);
        this.avisos.success('Clave actualizada. Inicia sesión con la nueva clave.');
        this.router.navigate(['/login']);
      },
      error: (e: unknown) => {
        this.cargando.set(false);
        this.error.set(mensajeError(e, 'No se pudo cambiar la clave.'));
      }
    });
  }

  protected cancelar(): void {
    this.router.navigate(['/portal']);
  }
}
