import { DatePipe } from '@angular/common';
import { Component, computed, effect, input, signal } from '@angular/core';
import { CategoriaNoticia, EVENTOS, NOTICIAS, Noticia } from './datos';

@Component({
  selector: 'app-noticias',
  imports: [DatePipe],
  template: `
    <section class="pub-cab-tit"><div class="pub-wrap">
      <span class="pub-chip">Noticias y agenda</span>
      <h1>La vida en el colegio</h1>
      <p>Logros, actividades y fechas importantes. Filtra por tema o busca lo que necesitas.</p>
    </div></section>

    <section class="pub-sec"><div class="pub-wrap pub-dos">
      <div>
        <div class="pub-filtros" role="group" aria-label="Filtrar noticias">
          @for (c of categorias; track c) { <button type="button" [class.activo]="categoria() === c" (click)="categoria.set(c)">{{ c }}</button> }
          <input class="pub-input" type="search" placeholder="Buscar noticia…" aria-label="Buscar noticia" [value]="texto()" (input)="texto.set($any($event.target).value)" />
        </div>
        <div class="pub-cuadricula" style="grid-template-columns: repeat(auto-fill, minmax(260px, 1fr))">
          @for (n of visibles(); track n.id) {
            <article class="pub-tarjeta clic" tabindex="0" role="button" (click)="abrir(n)" (keydown.enter)="abrir(n)">
              <img [src]="imgNoticia(n.id)" [alt]="n.titulo" class="pub-tarjeta-img" />
              <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
                <span class="pub-chip">{{ n.categoria }}</span>
              </div>
              <h3>{{ n.titulo }}</h3>
              <p>{{ n.resumen }}</p>
              <small class="pub-noticia-fecha">{{ n.fecha | date: 'd MMMM y' }}</small>
            </article>
          } @empty {
            <p class="muted">No hay noticias para ese filtro. <button type="button" class="pub-btn pub-btn-borde" (click)="limpiar()">Quitar filtros</button></p>
          }
        </div>
      </div>
      <aside class="pub-agenda" aria-label="Agenda" style="border-top: 4px solid var(--pc);">
        <h3 style="margin-top: 0; display: flex; align-items: center; gap: 8px;">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color: var(--pc);"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
          Agenda escolar
        </h3>
        @for (e of eventos; track e.fecha) {
          <div class="pub-prox">
            <div class="pub-fecha"><b>{{ e.fecha | date: 'd' }}</b><span>{{ e.fecha | date: 'MMM' }}</span></div>
            <div><strong>{{ e.titulo }}</strong><br /><small class="muted">{{ e.hora }} · {{ e.lugar }}</small></div>
          </div>
        }
      </aside>
    </div></section>

    @if (abierta(); as n) {
      <div class="pub-velo" (click)="cerrar()" role="presentation">
        <div class="pub-modal" role="dialog" aria-modal="true" [attr.aria-label]="n.titulo" (click)="$event.stopPropagation()" (keydown.escape)="cerrar()">
          <img [src]="imgNoticia(n.id)" [alt]="n.titulo" class="pub-modal-img" />
          <span class="pub-chip">{{ n.categoria }}</span>
          <h2>{{ n.titulo }}</h2>
          <small class="pub-noticia-fecha" style="margin-bottom: 16px;">{{ n.fecha | date: 'd MMMM y' }}</small>
          @for (p of n.cuerpo; track p) { <p style="line-height: 1.7; margin-bottom: 12px; color: var(--tinta2);">{{ p }}</p> }
          <button type="button" class="pub-btn pub-btn-primario" style="margin-top: 18px;" (click)="cerrar()" autofocus>Cerrar</button>
        </div>
      </div>
    }
  `
})
export class NoticiasComponent {
  /** `?ver=2` abre esa noticia (enlaces desde el inicio). */
  readonly ver = input<string>();
  protected readonly eventos = EVENTOS;
  protected readonly categorias: ('Todas' | CategoriaNoticia)[] = ['Todas', 'Académico', 'Deportes', 'Cultura', 'Comunidad'];
  protected readonly categoria = signal<'Todas' | CategoriaNoticia>('Todas');
  protected readonly texto = signal('');
  protected readonly abierta = signal<Noticia | null>(null);

  protected readonly visibles = computed(() => {
    const t = this.texto().trim().toLowerCase();
    return NOTICIAS.filter(n => (this.categoria() === 'Todas' || n.categoria === this.categoria()) && (!t || `${n.titulo} ${n.resumen}`.toLowerCase().includes(t)));
  });

  constructor() {
    effect(() => {
      const id = Number(this.ver());
      if (id) this.abierta.set(NOTICIAS.find(n => n.id === id) ?? null);
    });
  }

  protected abrir(n: Noticia): void { this.abierta.set(n); }
  protected cerrar(): void { this.abierta.set(null); }
  protected limpiar(): void { this.categoria.set('Todas'); this.texto.set(''); }

  protected imgNoticia(id: number): string {
    const mapa: Record<number, string> = {
      1: '/img/admision-visita.jpg',
      2: '/img/taller-robotica.jpg',
      3: '/img/deportes-campus.jpg'
    };
    return mapa[id] ?? `/img/noticia-${id}.svg`;
  }
}