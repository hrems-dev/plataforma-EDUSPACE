import { Component, input } from '@angular/core';

/** Título de página con subtítulo y un slot a la derecha para las acciones (botón "Nuevo", filtros…). */
@Component({
  selector: 'app-page-header',
  template: `
    <header class="enc">
      <div class="txt">
        <h1>{{ titulo() }}</h1>
        @if (subtitulo()) {
          <div class="muted">{{ subtitulo() }}</div>
        }
      </div>
      <div class="acc"><ng-content /></div>
    </header>
  `,
  styles: `
    .enc { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
    .txt { flex: 1 1 260px; }
    .acc { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
  `
})
export class PageHeaderComponent {
  readonly titulo = input.required<string>();
  readonly subtitulo = input<string>('');
}
