import { Component, computed, effect, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NIVELES } from './datos';

@Component({
  selector: 'app-niveles-publico',
  imports: [RouterLink],
  template: `
    <section class="pub-cab-tit"><div class="pub-wrap">
      <span class="pub-chip">Niveles</span>
      <h1>Educación para cada etapa</h1>
      <p>Elige un nivel para ver su propuesta, horario, talleres y costos de referencia.</p>
    </div></section>

    <section class="pub-sec"><div class="pub-wrap">
      <div class="pub-pestanas" role="tablist" aria-label="Nivel">
        @for (n of niveles; track n.id) {
          <button type="button" role="tab" [attr.aria-selected]="n.id === actual().id" [class.activo]="n.id === actual().id" (click)="elegir(n.id)">{{ n.nombre }}</button>
        }
      </div>
      <div class="pub-detalle" role="tabpanel">
        <div>
          <div class="pub-nivel-ic" [style.background]="actual().color" aria-hidden="true">{{ actual().nombre[0] }}</div>
          <h2 style="margin-top: 12px">{{ actual().nombre }} · {{ actual().edades }}</h2>
          <p class="pub-sub" style="margin-bottom: 20px">{{ actual().resumen }}</p>
          <h3>Lo que destaca</h3>
          <ul class="pub-lista">@for (d of actual().destacados; track d) { <li>{{ d }}</li> }</ul>
          <h3 style="margin-top: 24px">Talleres</h3>
          <div class="pub-etiquetas">@for (t of actual().talleres; track t) { <span class="pub-chip">{{ t }}</span> }</div>
        </div>
        <aside class="pub-tarjeta">
          <h3 style="margin-top: 0">Datos del nivel</h3>
          <dl class="pub-datos">
            <div><dt>Grados</dt><dd>{{ actual().grados }}</dd></div>
            <div><dt>Horario</dt><dd>{{ actual().horario }}</dd></div>
            <div><dt>Matrícula</dt><dd>S/ {{ actual().matricula }}</dd></div>
            <div><dt>Pensión mensual</dt><dd>S/ {{ actual().pension }}</dd></div>
          </dl>
          <a routerLink="/admision" [queryParams]="{ nivel: actual().id }" class="pub-btn pub-btn-primario" style="width: 100%; margin-top: 18px">Postular a {{ actual().nombre }}</a>
          <p class="muted" style="font-size: .82rem; margin: 10px 0 0">Montos de ejemplo. Se pagan en 10 cuotas de marzo a diciembre.</p>
        </aside>
      </div>
    </div></section>
  `
})
export class NivelesPublicoComponent {
  /** `?nivel=primaria` llega por enlace desde el inicio (binding de parámetros de ruta). */
  readonly nivel = input<string>();
  protected readonly niveles = NIVELES;
  private readonly id = signal<string>('inicial');
  protected readonly actual = computed(() => NIVELES.find(n => n.id === this.id()) ?? NIVELES[0]);

  constructor() {
    effect(() => {
      const q = this.nivel();
      if (q) this.id.set(q);
    });
  }

  protected elegir(id: string): void {
    this.id.set(id);
  }
}
