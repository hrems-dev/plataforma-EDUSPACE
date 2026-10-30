import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { visualCurso } from './curso-visual';

/** Cabecera de la página de un curso: color propio, nombre, quién lo dicta y un enlace para volver a «Mis cursos». */
@Component({
  selector: 'app-curso-cabecera',
  imports: [RouterLink],
  template: `
    <header class="cab" [style.--c]="v().color" [style.--s]="v().suave">
      <a class="volver" [routerLink]="volverA()"><span aria-hidden="true">‹</span> Mis cursos</a>
      <div class="fila">
        <span class="icono" aria-hidden="true">{{ v().emoji }}</span>
        <div class="titulos">
          <h1>{{ nombre() }}</h1>
          <p>{{ subtitulo() }}</p>
        </div>
        <div class="acciones"><ng-content /></div>
      </div>
    </header>
  `,
  styles: `
    .cab { border-radius: 20px; padding: 18px 22px 20px; color: #fff; background: linear-gradient(135deg, var(--c), color-mix(in srgb, var(--c) 70%, #1b1f3b)); box-shadow: 0 14px 30px -18px var(--c); }
    .volver { display: inline-flex; gap: 6px; align-items: center; color: rgb(255 255 255 / .85); text-decoration: none; font-size: .85rem; margin-bottom: 10px; }
    .volver:hover { color: #fff; }
    .fila { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
    .icono { width: 60px; height: 60px; border-radius: 16px; display: grid; place-items: center; font-size: 30px; background: rgb(255 255 255 / .18); }
    .titulos { flex: 1 1 240px; min-width: 0; }
    h1 { margin: 0; font-size: 1.6rem; color: #fff; }
    p { margin: 2px 0 0; color: rgb(255 255 255 / .85); }
    .acciones { display: flex; flex-wrap: wrap; gap: 8px; }
    .acciones ::ng-deep .btn { background: rgb(255 255 255 / .16); border-color: rgb(255 255 255 / .35); color: #fff; }
    .acciones ::ng-deep .btn:hover { background: rgb(255 255 255 / .28); }
  `
})
export class CursoCabeceraComponent {
  readonly cursoId = input.required<number>();
  readonly nombre = input.required<string>();
  readonly subtitulo = input('');
  readonly volverA = input.required<string>();
  protected readonly v = computed(() => visualCurso(this.nombre(), this.cursoId()));
}
