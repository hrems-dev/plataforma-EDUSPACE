import { Injectable, computed, signal } from '@angular/core';

/**
 * Si un servicio no responde (se reinició, está caído o el Gateway no lo encuentra) las pantallas se quedan sin datos
 * sin explicar por qué. Aquí se lleva la cuenta de qué módulos de la API están fallando (el primer tramo de la ruta:
 * `alumnos`, `pagos`…): mientras haya alguno se muestra un aviso con «Reintentar» y «Volver a iniciar sesión».
 * Una respuesta buena de un módulo solo lo quita a él, así los servicios sanos no apagan el aviso de uno caído.
 */
@Injectable({ providedIn: 'root' })
export class EstadoServicioService {
  private readonly fallando = signal<ReadonlySet<string>>(new Set());
  readonly caido = computed(() => this.fallando().size > 0);

  /** Módulo de una URL de la API: `/api/v1/alumnos/mios` → `alumnos`. */
  static modulo(url: string): string {
    const m = /\/api\/v1\/([^/?#]+)/.exec(url);
    return m ? m[1] : url;
  }

  marcarCaido(url: string): void {
    const modulo = EstadoServicioService.modulo(url);
    if (!this.fallando().has(modulo)) this.fallando.set(new Set([...this.fallando(), modulo]));
  }

  marcarOk(url: string): void {
    const modulo = EstadoServicioService.modulo(url);
    if (this.fallando().has(modulo)) this.fallando.set(new Set([...this.fallando()].filter(m => m !== modulo)));
  }

  /** Para «Volver a iniciar sesión»: se olvida lo que fallaba. */
  limpiar(): void {
    if (this.fallando().size) this.fallando.set(new Set());
  }
}
