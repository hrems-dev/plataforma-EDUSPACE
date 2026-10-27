import { Injectable, signal } from '@angular/core';

export interface PedidoConfirmacion {
  mensaje: string;
  textoAceptar: string;
  peligroso: boolean;
}

/** Confirmación propia (en vez de `window.confirm`): la pantalla la dibuja `app-confirm`. */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private resolver: ((acepta: boolean) => void) | null = null;
  private readonly pedidoSignal = signal<PedidoConfirmacion | null>(null);
  readonly pedido = this.pedidoSignal.asReadonly();

  confirmar(mensaje: string, textoAceptar = 'Aceptar', peligroso = false): Promise<boolean> {
    this.resolver?.(false);
    this.pedidoSignal.set({ mensaje, textoAceptar, peligroso });
    return new Promise<boolean>(resolve => (this.resolver = resolve));
  }

  responder(acepta: boolean): void {
    this.pedidoSignal.set(null);
    this.resolver?.(acepta);
    this.resolver = null;
  }
}
