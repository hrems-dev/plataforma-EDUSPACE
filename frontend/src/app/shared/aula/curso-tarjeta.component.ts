import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { visualCurso } from './curso-visual';

/** Tarjeta de un curso: color propio, ícono, quién lo dicta y lo que requiere atención. Toda la tarjeta es un enlace. */
@Component({
  selector: 'app-curso-tarjeta',
  imports: [RouterLink],
  template: `
    <a class="tarjeta" [routerLink]="ruta()" [style.--c]="v().color" [style.--s]="v().suave" [attr.aria-label]="nombre() + (insignia() ? ', ' + insignia() : '')">
      <span class="icono" aria-hidden="true">{{ v().emoji }}</span>
      <span class="cuerpo">
        <strong class="nombre">{{ nombre() }}</strong>
        <span class="sub">{{ subtitulo() }}</span>
        <span class="datos">
          @for (d of datos(); track d) { <span class="dato">{{ d }}</span> }
        </span>
      </span>
      @if (insignia()) { <span class="insignia" [class.alerta]="alerta()">{{ insignia() }}</span> }
      <span class="flecha" aria-hidden="true">›</span>
    </a>
  `,
  styles: `
    :host { display: block; }
    .tarjeta { position: relative; display: flex; align-items: center; gap: 14px; padding: 16px 16px 16px 18px; border: 1px solid var(--line); border-radius: 16px; background: var(--card); text-decoration: none; color: inherit; overflow: hidden; transition: transform .15s ease, box-shadow .15s ease, border-color .15s ease; }
    .tarjeta::before { content: ''; position: absolute; inset: 0 auto 0 0; width: 5px; background: var(--c); }
    .tarjeta:hover { transform: translateY(-2px); box-shadow: 0 10px 24px -12px color-mix(in srgb, var(--c) 55%, transparent); border-color: color-mix(in srgb, var(--c) 40%, var(--line)); }
    .tarjeta:focus-visible { outline: 3px solid color-mix(in srgb, var(--c) 45%, transparent); outline-offset: 2px; }
    .icono { flex: none; width: 52px; height: 52px; display: grid; place-items: center; font-size: 26px; border-radius: 14px; background: var(--s); }
    .cuerpo { display: flex; flex-direction: column; gap: 3px; min-width: 0; flex: 1; }
    .nombre { font-size: 1.02rem; color: var(--ink); }
    .sub { font-size: .85rem; color: var(--ink-2); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .datos { display: flex; flex-wrap: wrap; gap: 4px 10px; margin-top: 2px; }
    .dato { font-size: .78rem; color: var(--ink-2); }
    .insignia { flex: none; align-self: flex-start; font-size: .75rem; font-weight: 700; padding: 3px 9px; border-radius: 999px; background: var(--s); color: var(--c); }
    .insignia.alerta { background: var(--warn-soft); color: var(--warn); }
    .flecha { flex: none; font-size: 1.6rem; color: var(--ink-2); transition: transform .15s ease; }
    .tarjeta:hover .flecha { transform: translateX(3px); color: var(--c); }
    @media (prefers-reduced-motion: reduce) { .tarjeta, .flecha { transition: none; } .tarjeta:hover { transform: none; } }
  `
})
export class CursoTarjetaComponent {
  readonly cursoId = input.required<number>();
  readonly nombre = input.required<string>();
  readonly subtitulo = input('');
  readonly datos = input<string[]>([]);
  readonly insignia = input('');
  readonly alerta = input(false);
  readonly ruta = input.required<string | unknown[]>();
  protected readonly v = computed(() => visualCurso(this.nombre(), this.cursoId()));
}
