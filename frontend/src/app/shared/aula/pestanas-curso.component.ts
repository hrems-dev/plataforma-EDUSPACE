import { Component, input, model } from '@angular/core';

export interface PestanaCurso {
  id: string;
  etiqueta: string;
  /** Número que se muestra junto al nombre (p. ej. tareas pendientes). */
  cuenta?: number | null;
}

/** Pestañas internas de la página de un curso (Tareas, Notas, …): una sola fila, con el número de pendientes. */
@Component({
  selector: 'app-pestanas-curso',
  template: `
    <div class="tabs" role="tablist" aria-label="Secciones del curso">
      @for (p of pestanas(); track p.id) {
        <button type="button" role="tab" class="tab" [class.on]="activa() === p.id" [attr.aria-selected]="activa() === p.id" (click)="activa.set(p.id)">
          {{ p.etiqueta }}@if (p.cuenta) { <span class="n">{{ p.cuenta }}</span> }
        </button>
      }
    </div>
  `,
  styles: `
    .tabs { display: flex; gap: 6px; padding: 4px; margin: 18px 0; background: var(--primary-soft); border-radius: 14px; overflow-x: auto; width: fit-content; max-width: 100%; }
    .tab { border: 0; background: transparent; padding: 9px 18px; border-radius: 10px; font: inherit; font-weight: 600; color: var(--ink-2); cursor: pointer; white-space: nowrap; }
    .tab:hover { color: var(--ink); }
    .tab.on { background: var(--card); color: var(--primary); box-shadow: 0 1px 4px rgb(20 33 61 / .12); }
    .tab:focus-visible { outline: 2px solid var(--primary); outline-offset: 1px; }
    .n { margin-left: 8px; background: var(--warn-soft); color: var(--warn); border-radius: 999px; padding: 1px 8px; font-size: .75rem; }
  `
})
export class PestanasCursoComponent {
  readonly pestanas = input.required<PestanaCurso[]>();
  readonly activa = model.required<string>();
}
