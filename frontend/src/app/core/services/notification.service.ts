import { Injectable, signal } from '@angular/core';

export type TipoAviso = 'success' | 'error' | 'warning' | 'info';

export interface Aviso {
  id: number;
  tipo: TipoAviso;
  mensaje: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private siguienteId = 1;
  private readonly avisosSignal = signal<Aviso[]>([]);
  readonly avisos = this.avisosSignal.asReadonly();

  success(mensaje: string): void {
    this.mostrar('success', mensaje);
  }

  error(mensaje: string): void {
    this.mostrar('error', mensaje, 7000);
  }

  warning(mensaje: string): void {
    this.mostrar('warning', mensaje);
  }

  info(mensaje: string): void {
    this.mostrar('info', mensaje);
  }

  cerrar(id: number): void {
    this.avisosSignal.update(lista => lista.filter(a => a.id !== id));
  }

  private mostrar(tipo: TipoAviso, mensaje: string, ms = 4500): void {
    const id = this.siguienteId++;
    this.avisosSignal.update(lista => [...lista, { id, tipo, mensaje }]);
    setTimeout(() => this.cerrar(id), ms);
  }
}
