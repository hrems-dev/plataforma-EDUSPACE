import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { rutaInicial } from '../../../core/utils/landing';
import { mensajeError } from '../../../core/utils/errores';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /** Por qué se llegó al login sin haberlo pedido (sesión vencida o servidor reiniciado). */
  protected readonly aviso = this.route.snapshot.queryParams['motivo'] === 'vencida'
    ? 'Tu sesión venció. Inicia sesión de nuevo para continuar.'
    : this.route.snapshot.queryParams['motivo'] === 'invalida'
      ? 'El sistema no reconoce tu sesión (puede que se haya reiniciado). Inicia sesión de nuevo y volverás a donde estabas.'
      : null;
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]]
  });

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.cargando.set(true);
    this.error.set(null);

    this.auth.login(this.form.getRawValue()).subscribe({
      next: res => {
        this.cargando.set(false);
        if (res.debe_cambiar_clave) {
          this.router.navigate(['/cambiar-clave']);
          return;
        }
        this.router.navigateByUrl(this.route.snapshot.queryParams['returnUrl'] ?? rutaInicial(this.auth.roles()));
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(
          e.status === 401 ? 'Usuario o clave incorrectos.' : mensajeError(e, 'No se pudo iniciar sesión.')
        );
      }
    });
  }
}
