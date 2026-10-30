import { Component, computed, signal } from '@angular/core';
import { COLEGIO } from './datos';

@Component({
  selector: 'app-contacto',
  template: `
    <section class="pub-cab-tit"><div class="pub-wrap">
      <span class="pub-chip">Contacto</span>
      <h1>Hablemos</h1>
      <p>Escríbenos, llámanos o visítanos. Respondemos en un día hábil.</p>
    </div></section>

    <section class="pub-sec"><div class="pub-wrap pub-contacto">
      <div>
        <div class="pub-tarjeta" style="border-top: 4px solid var(--pc);">
          <h3 style="margin-top: 0; display: flex; align-items: center; gap: 8px;">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color: var(--pc);"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>
            Dónde estamos
          </h3>
          <p>{{ colegio.direccion }}<br />{{ colegio.ciudad }}</p>

          <h3 style="display: flex; align-items: center; gap: 8px;">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color: var(--pc);"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/></svg>
            Teléfonos y correo
          </h3>
          <p>Central: {{ colegio.telefono }}<br />WhatsApp: {{ colegio.whatsapp }}<br />{{ colegio.correo }}</p>

          <h3 style="display: flex; align-items: center; gap: 8px;">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color: var(--pc);"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
            Horario de atención
          </h3>
          <p>{{ colegio.horarioAtencion }}</p>
        </div>
        <div class="pub-mapa" role="img" aria-label="Mapa de ejemplo con la ubicación del colegio">
          <svg viewBox="0 0 400 220" width="100%" aria-hidden="true">
            <rect width="400" height="220" fill="#e8eeff" />
            <path d="M0 150 H400 M0 80 H400 M120 0 V220 M280 0 V220" stroke="#fff" stroke-width="14" />
            <path d="M0 150 H400 M120 0 V220" stroke="#cfd9f7" stroke-width="2" stroke-dasharray="8 8" />
            <rect x="140" y="92" width="120" height="48" rx="6" fill="#cfe9d8" />
            <text x="200" y="122" font-family="sans-serif" font-size="11" font-weight="bold" fill="#166534" text-anchor="middle">CAMPUS HORIZONTE</text>
            <path d="M200 60 c-14 0 -22 10 -22 22 c0 16 22 36 22 36 s22 -20 22 -36 c0 -12 -8 -22 -22 -22z" fill="#1e3a8a" /><circle cx="200" cy="82" r="8" fill="#fbbf24" />
          </svg>
        </div>
      </div>

      <div class="pub-tarjeta" style="border-top: 4px solid var(--oro);">
        <h2 style="font-size: 1.4rem; margin-top: 0; color: var(--pc);">Envíanos un mensaje</h2>
        @if (enviado()) {
          <div class="pub-ok">Gracias, {{ nombre() }}. Es una demostración: el mensaje no se envió a nadie.</div>
          <button type="button" class="pub-btn pub-btn-borde" style="margin-top: 14px" (click)="reiniciar()">Escribir otro</button>
        } @else {
          <form (submit)="enviar($event)" novalidate>
            <div class="pub-campo"><label for="c-nom">Nombre</label><input id="c-nom" class="pub-input" [value]="nombre()" (input)="nombre.set($any($event.target).value)" autocomplete="name" />
              @if (intento() && !nombre().trim()) { <span class="pub-error">Escribe tu nombre.</span> }</div>
            <div class="pub-campo"><label for="c-cor">Correo</label><input id="c-cor" class="pub-input" type="email" [value]="correo()" (input)="correo.set($any($event.target).value)" autocomplete="email" />
              @if (intento() && correoMal()) { <span class="pub-error">Escribe un correo válido.</span> }</div>
            <div class="pub-campo"><label for="c-asu">Asunto</label>
              <select id="c-asu" class="pub-input" [value]="asunto()" (change)="asunto.set($any($event.target).value)">
                <option>Admisión</option><option>Pagos y pensiones</option><option>Constancias y documentos</option><option>Otro</option>
              </select></div>
            <div class="pub-campo"><label for="c-msg">Mensaje <small class="muted">({{ mensaje().length }}/300)</small></label>
              <textarea id="c-msg" class="pub-input" rows="5" maxlength="300" [value]="mensaje()" (input)="mensaje.set($any($event.target).value)"></textarea>
              @if (intento() && mensaje().trim().length < 10) { <span class="pub-error">Cuéntanos un poco más (mínimo 10 caracteres).</span> }</div>
            <button type="submit" class="pub-btn pub-btn-primario" style="margin-top: 10px;">Enviar mensaje</button>
          </form>
        }
      </div>
    </div></section>
  `
})
export class ContactoComponent {
  protected readonly colegio = COLEGIO;
  protected readonly nombre = signal('');
  protected readonly correo = signal('');
  protected readonly asunto = signal('Admisión');
  protected readonly mensaje = signal('');
  protected readonly intento = signal(false);
  protected readonly enviado = signal(false);
  protected readonly correoMal = computed(() => !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.correo().trim()));

  protected enviar(e: Event): void {
    e.preventDefault();
    this.intento.set(true);
    if (this.nombre().trim() && !this.correoMal() && this.mensaje().trim().length >= 10) this.enviado.set(true);
  }

  protected reiniciar(): void {
    this.nombre.set('');
    this.correo.set('');
    this.mensaje.set('');
    this.intento.set(false);
    this.enviado.set(false);
  }
}