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
          <img [src]="fotoNivel(actual().id)" [alt]="'Nivel ' + actual().nombre" class="pub-tarjeta-img" style="height: 220px; border-radius: 14px; margin-bottom: 20px;" />
          <div style="display: flex; align-items: center; gap: 14px; margin-bottom: 12px;">
            <div class="pub-nivel-ic" [style.background]="actual().color" aria-hidden="true">{{ actual().nombre[0] }}</div>
            <div>
              <h2 style="margin: 0;">{{ actual().nombre }} · {{ actual().edades }}</h2>
              <small class="muted" style="font-weight: 600;">Propuesta formativa integral</small>
            </div>
          </div>
          <p class="pub-sub" style="margin-bottom: 24px">{{ actual().resumen }}</p>
          <h3 style="font-size: 1.15rem; color: var(--pc); margin-bottom: 12px;">Lo que destaca</h3>
          <ul class="pub-lista">@for (d of actual().destacados; track d) { <li>{{ d }}</li> }</ul>
          <h3 style="margin-top: 28px; font-size: 1.15rem; color: var(--pc); margin-bottom: 12px;">Talleres extracurriculares</h3>
          <div class="pub-etiquetas">@for (t of actual().talleres; track t) { <span class="pub-chip pub-chip-oro">{{ t }}</span> }</div>
        </div>
        <aside class="pub-tarjeta" style="border-top: 4px solid var(--pc);">
          <h3 style="margin-top: 0">Datos del nivel</h3>
          <dl class="pub-datos">
            <div><dt>Grados</dt><dd>{{ actual().grados }}</dd></div>
            <div><dt>Horario</dt><dd>{{ actual().horario }}</dd></div>
            <div><dt>Matrícula</dt><dd>S/ {{ actual().matricula }}</dd></div>
            <div><dt>Pensión mensual</dt><dd>S/ {{ actual().pension }}</dd></div>
          </dl>
          <a routerLink="/admision" [queryParams]="{ nivel: actual().id }" class="pub-btn pub-btn-primario" style="width: 100%; margin-top: 20px">Postular a {{ actual().nombre }}</a>
          <p class="muted" style="font-size: .84rem; margin: 12px 0 0; text-align: center;">Montos de ejemplo. Se pagan en 10 cuotas de marzo a diciembre.</p>
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

  protected fotoNivel(id: string): string {
    const mapa: Record<string, string> = {
      inicial: '/img/aula-inicial.jpg',
      primaria: '/img/aula-primaria.jpg',
      secundaria: '/img/laboratorio-secundaria.jpg'
    };
    return mapa[id] ?? `/img/nivel-${id}.svg`;
  }
}