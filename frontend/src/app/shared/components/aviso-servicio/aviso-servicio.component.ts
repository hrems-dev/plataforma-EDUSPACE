import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { EstadoServicioService } from '../../../core/services/estado-servicio.service';

/** Franja fija arriba cuando el servidor no responde: explica qué pasa y ofrece reintentar o volver a iniciar sesión. */
@Component({
  selector: 'app-aviso-servicio',
  template: `
    @if (estado.caido()) {
      <div class="aviso-servicio" role="alert">
        <span class="texto"><strong>No pudimos conectar con el sistema.</strong> Puede que se esté reiniciando; tus datos están a salvo.</span>
        <span class="acciones">
          <button type="button" class="btn btn-sm" (click)="reintentar()">Reintentar</button>
          <button type="button" class="btn btn-sm" (click)="volverAIniciar()">Volver a iniciar sesión</button>
        </span>
      </div>
    }
  `,
  styles: `
    .aviso-servicio { position: fixed; top: 0; left: 0; right: 0; z-index: 80; display: flex; flex-wrap: wrap; gap: 10px 18px; align-items: center; justify-content: center; padding: 10px 16px; background: #8a4b08; color: #fff; box-shadow: 0 4px 14px rgb(0 0 0 / .25); }
    .texto { font-size: .92rem; }
    .acciones { display: flex; gap: 8px; }
    .acciones .btn { background: rgb(255 255 255 / .16); border-color: rgb(255 255 255 / .5); color: #fff; }
    .acciones .btn:hover { background: rgb(255 255 255 / .28); }
  `
})
export class AvisoServicioComponent {
  protected readonly estado = inject(EstadoServicioService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  /** Vuelve a cargar la pantalla actual (y sus datos); si el servicio ya volvió, el aviso desaparece solo. */
  protected reintentar(): void {
    window.location.reload();
  }

  protected volverAIniciar(): void {
    this.estado.limpiar();
    this.auth.cerrarSesionLocal('invalida');
  }
}
