import { Component, inject } from '@angular/core';
import { FamiliaContextoService } from '../../../core/services/familia-contexto.service';

/** Botones para elegir de qué hijo se ven los datos. Con un solo hijo (o un alumno) no se muestra. */
@Component({
  selector: 'app-selector-hijo',
  template: `
    @if (ctx.hijos().length > 1) {
      <div class="checks" role="group" aria-label="Elegir hijo">
        @for (h of ctx.hijos(); track h.alumnoId) {
          <label [style.background]="ctx.seleccionado()?.alumnoId === h.alumnoId ? 'var(--primary-soft)' : ''">
            <input type="radio" name="hijo" [checked]="ctx.seleccionado()?.alumnoId === h.alumnoId" (change)="ctx.seleccionar(h.alumnoId)" />
            {{ h.nombreCompleto }}
          </label>
        }
      </div>
    }
  `
})
export class SelectorHijoComponent {
  protected readonly ctx = inject(FamiliaContextoService);
}
