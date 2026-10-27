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
        <div class="pub-tarjeta">
          <h3 style="margin-top: 0">Dónde estamos</h3>
          <p>{{ colegio.direccion }}<br />{{ colegio.ciudad }}</p>
          <h3>Teléfonos y correo</h3>
          <p>Central: {{ colegio.telefono }}<br />WhatsApp: {{ colegio.whatsapp }}<br />{{ colegio.correo }}</p>
          <h3>Horario de atención</h3>
          <p>{{ colegio.horarioAtencion }}</p>
        </div>
        <div class="pub-mapa" role="img" aria-label="Mapa de ejemplo con la ubicación del colegio">
          <svg viewBox="0 0 400 220" width="100%" aria-hidden="true">
            <rect width="400" height="220" fill="#e8eeff" />
            <path d="M0 150 H400 M0 80 H400 M120 0 V220 M280 0 V220" stroke="#fff" stroke-width="14" />
            <path d="M0 150 H400 M120 0 V220" stroke="#cfd9f7" stroke-width="2" stroke-dasharray="8 8" />
            <rect x="140" y="92" width="120" height="48" rx="6" fill="#cfe9d8" />
            <path d="M200 70 c-14 0 -22 10 -22 22 c0 16 22 36 22 36 s22 -20 22 -36 c0 -12 -8 -22 -22 -22z" fill="#2f5bea" /><circle cx="200" cy="92" r="8" fill="#fff" />
          </svg>
        </div>
      </div>

      <div class="pub-tarjeta">
        <h2 style="font-size: 1.4rem; margin-top: 0">Envíanos un mensaje</h2>
        @if (enviado()) {
          <div class="pub-ok">Gracias, {{ nombre() }}. Es una demostración: el mensaje no se envió a nadie.</div>
          <button type="button" class="pub-btn pub-btn-borde" style="margin-top: 12px" (click)="reiniciar()">Escribir otro</button>
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
            <button type="submit" class="pub-btn pub-btn-primario">Enviar mensaje</button>
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
