import { DecimalPipe } from '@angular/common';
import { Component, computed, effect, input, signal } from '@angular/core';
import { COLEGIO, FAQ, NIVELES, PASOS_ADMISION, REQUISITOS } from './datos';

@Component({
  selector: 'app-admision',
  imports: [DecimalPipe],
  template: `
    <section class="pub-cab-tit"><div class="pub-wrap">
      <span class="pub-chip pub-chip-oro">Admisión 2027</span>
      <h1>Únete a nuestra comunidad</h1>
      <p>Un proceso claro en cinco pasos. Calcula tu pensión, revisa los requisitos y envía tu pre-inscripción.</p>
    </div></section>

    <section class="pub-sec"><div class="pub-wrap">
      <h2>El proceso, paso a paso</h2>
      <p class="pub-sub">Toca un paso para ver el detalle.</p>
      <div class="pub-pasos">
        @for (p of pasos; track p.titulo; let i = $index) {
          <button type="button" class="pub-paso" [class.activo]="paso() === i" (click)="paso.set(i)" [attr.aria-pressed]="paso() === i">
            <div class="n">{{ i + 1 }}</div><strong>{{ p.titulo }}</strong>
          </button>
        }
      </div>
      <div class="pub-paso-txt" aria-live="polite"><strong>Paso {{ paso() + 1 }}.</strong> {{ pasos[paso()].texto }}</div>
    </div></section>

    <section class="pub-sec alt"><div class="pub-wrap pub-dos" style="grid-template-columns: 1fr 1fr">
      <div class="pub-tarjeta" style="border-top: 4px solid var(--verde-esc);">
        <h3 style="margin-top: 0; display: flex; align-items: center; gap: 8px;">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color: var(--verde-esc);"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
          Lista de requisitos
        </h3>
        <p class="muted" style="margin: 0">Marca lo que ya tienes: <strong>{{ listos() }} de {{ requisitos.length }}</strong></p>
        <div class="pub-prog" role="progressbar" [attr.aria-valuenow]="porcentajeListos()" aria-valuemin="0" aria-valuemax="100"><span [style.width.%]="porcentajeListos()"></span></div>
        @for (r of requisitos; track r; let i = $index) {
          <label class="pub-check" [class.hecho]="marcados().has(i)">
            <input type="checkbox" [checked]="marcados().has(i)" (change)="marcar(i)" /><span>{{ r }}</span>
          </label>
        }
        @if (listos() === requisitos.length) { <div class="pub-ok" style="margin-top: 14px">¡Listo! Ya puedes entregar tus documentos en secretaría.</div> }
      </div>

      <div class="pub-tarjeta" style="border-top: 4px solid var(--pc);">
        <h3 style="margin-top: 0; display: flex; align-items: center; gap: 8px;">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color: var(--pc);"><rect x="4" y="2" width="16" height="20" rx="2"/><line x1="8" y1="6" x2="16" y2="6"/><line x1="16" y1="14" x2="16" y2="18"/><path d="M16 10h.01"/><path d="M12 10h.01"/><path d="M8 10h.01"/><path d="M12 14h.01"/><path d="M8 14h.01"/><path d="M12 18h.01"/><path d="M8 18h.01"/></svg>
          Calcula tu pensión
        </h3>
        <div class="pub-campo"><label id="l-niv">Nivel</label>
          <div class="pub-grupo" role="group" aria-labelledby="l-niv">
            @for (n of niveles; track n.id) { <button type="button" [class.activo]="nivelId() === n.id" (click)="nivelId.set(n.id)">{{ n.nombre }}</button> }
          </div></div>
        <div class="pub-campo"><label id="l-her">Posición del hermano en el colegio</label>
          <div class="pub-grupo" role="group" aria-labelledby="l-her">
            @for (h of hermanos; track h.valor) { <button type="button" [class.activo]="hermano() === h.valor" (click)="hermano.set(h.valor)">{{ h.etiqueta }}</button> }
          </div></div>
        <div class="pub-campo"><label id="l-pag">Forma de pago</label>
          <div class="pub-grupo" role="group" aria-labelledby="l-pag">
            <button type="button" [class.activo]="!anual()" (click)="anual.set(false)">10 cuotas</button>
            <button type="button" [class.activo]="anual()" (click)="anual.set(true)">Año adelantado (-8 %)</button>
          </div></div>
        <div class="pub-calc-res" aria-live="polite">
          <small>{{ anual() ? 'Pago único del año' : 'Cuota mensual' }}</small>
          <b>S/ {{ calculo().cuota | number: '1.2-2' }}</b>
          <small>Más matrícula de S/ {{ nivel().matricula }}. Total del año: S/ {{ calculo().total | number: '1.2-2' }}</small>
        </div>
        <p class="muted" style="font-size: .84rem; margin-top: 10px;">Cálculo referencial con montos de ejemplo.</p>
      </div>
    </div></section>

    <section class="pub-sec"><div class="pub-wrap pub-dos" style="grid-template-columns: 1fr 1fr">
      <div class="pub-faq">
        <h2>Preguntas frecuentes</h2>
        @for (f of faq; track f.p) { <details><summary>{{ f.p }}</summary><p>{{ f.r }}</p></details> }
      </div>
      <div class="pub-tarjeta" id="preinscripcion" style="border-top: 4px solid var(--oro);">
        <img src="/img/admision-visita.jpg" alt="Visita guiada de admisión en Colegio Horizonte del Valle" class="pub-tarjeta-img" style="height: 160px;" />
        <h2 style="font-size: 1.4rem; margin-top: 0; color: var(--pc);">Pre-inscripción</h2>
        @if (enviado()) {
          <div class="pub-ok">Gracias, {{ nombre() }}. Esto es una demostración: no se envió ningún dato. En el sitio real recibirías respuesta de {{ colegio.admisionCorreo }} en 24 horas hábiles.</div>
          <button type="button" class="pub-btn pub-btn-borde" style="margin-top: 14px" (click)="reiniciar()">Enviar otra</button>
        } @else {
          <form class="pub-form" (submit)="enviar($event)" novalidate>
            <div class="pub-campo"><label for="f-nom">Tu nombre</label><input id="f-nom" class="pub-input" [value]="nombre()" (input)="nombre.set($any($event.target).value)" autocomplete="name" />
              @if (intento() && !nombre().trim()) { <span class="pub-error">Escribe tu nombre.</span> }</div>
            <div class="pub-campo"><label for="f-tel">Teléfono</label><input id="f-tel" class="pub-input" inputmode="tel" [value]="telefono()" (input)="telefono.set($any($event.target).value)" autocomplete="tel" />
              @if (intento() && telefonoMal()) { <span class="pub-error">Ingresa 9 dígitos.</span> }</div>
            <div class="pub-campo"><label for="f-niv">Nivel de interés</label>
              <select id="f-niv" class="pub-input" [value]="nivelId()" (change)="nivelId.set($any($event.target).value)">
                @for (n of niveles; track n.id) { <option [value]="n.id">{{ n.nombre }} ({{ n.edades }})</option> }
              </select></div>
            <div class="pub-campo"><label for="f-edad">Edad del estudiante</label><input id="f-edad" class="pub-input" type="number" min="3" max="17" [value]="edad()" (input)="edad.set($any($event.target).value)" /></div>
            <div class="pub-campo ancho"><button type="submit" class="pub-btn pub-btn-primario" style="width: 100%; margin-top: 8px;">Enviar pre-inscripción</button></div>
          </form>
        }
      </div>
    </div></section>
  `
})
export class AdmisionComponent {
  /** `?nivel=secundaria` llega desde la página de niveles. */
  readonly nivelInicial = input<string>(undefined, { alias: 'nivel' });
  protected readonly colegio = COLEGIO;
  protected readonly pasos = PASOS_ADMISION;
  protected readonly requisitos = REQUISITOS;
  protected readonly faq = FAQ;
  protected readonly niveles = NIVELES;
  protected readonly hermanos = [
    { valor: 0, etiqueta: 'Es el primero' },
    { valor: 0.1, etiqueta: '2.° hermano (-10 %)' },
    { valor: 0.15, etiqueta: '3.° o más (-15 %)' }
  ];

  protected readonly paso = signal(0);
  protected readonly marcados = signal<ReadonlySet<number>>(new Set());
  protected readonly nivelId = signal<string>('primaria');
  protected readonly hermano = signal(0);
  protected readonly anual = signal(false);

  protected readonly nivel = computed(() => NIVELES.find(n => n.id === this.nivelId()) ?? NIVELES[1]);
  protected readonly listos = computed(() => this.marcados().size);
  protected readonly porcentajeListos = computed(() => Math.round((this.listos() / REQUISITOS.length) * 100));
  protected readonly calculo = computed(() => {
    const mensual = this.nivel().pension * (1 - this.hermano());
    const total = this.anual() ? mensual * 10 * 0.92 : mensual * 10;
    return { cuota: this.anual() ? total : mensual, total: total + this.nivel().matricula };
  });

  protected readonly nombre = signal('');
  protected readonly telefono = signal('');
  protected readonly edad = signal('');
  protected readonly intento = signal(false);
  protected readonly enviado = signal(false);
  protected readonly telefonoMal = computed(() => !/^\d{9}$/.test(this.telefono().replace(/[\s-]/g, '')));

  constructor() {
    effect(() => {
      const q = this.nivelInicial();
      if (q && NIVELES.some(n => n.id === q)) this.nivelId.set(q);
    });
  }

  protected marcar(i: number): void {
    this.marcados.update(s => {
      const n = new Set(s);
      if (n.has(i)) n.delete(i);
      else n.add(i);
      return n;
    });
  }

  protected enviar(e: Event): void {
    e.preventDefault();
    this.intento.set(true);
    if (this.nombre().trim() && !this.telefonoMal()) this.enviado.set(true);
  }

  protected reiniciar(): void {
    this.nombre.set('');
    this.telefono.set('');
    this.edad.set('');
    this.intento.set(false);
    this.enviado.set(false);
  }
}