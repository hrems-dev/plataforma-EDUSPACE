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
        <div class="pub-cuadricula" style="grid-template-columns: repeat(auto-fill, minmax(240px, 1fr))">
          @for (n of visibles(); track n.id) {
            <article class="pub-tarjeta clic" tabindex="0" role="button" (click)="abrir(n)" (keydown.enter)="abrir(n)">
              <span class="pub-chip">{{ n.categoria }}</span>
              <h3>{{ n.titulo }}</h3>
              <p>{{ n.resumen }}</p>
              <small class="pub-noticia-fecha">{{ n.fecha | date: 'd MMMM y' }}</small>
            </article>
          } @empty {
            <p class="muted">No hay noticias para ese filtro. <button type="button" class="pub-btn pub-btn-borde" (click)="limpiar()">Quitar filtros</button></p>
          }
        </div>
      </div>
      <aside class="pub-agenda" aria-label="Agenda">
        <h3 style="margin-top: 0">Agenda</h3>
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
          <span class="pub-chip">{{ n.categoria }}</span>
          <h2>{{ n.titulo }}</h2>
          <small class="pub-noticia-fecha">{{ n.fecha | date: 'd MMMM y' }}</small>
          @for (p of n.cuerpo; track p) { <p>{{ p }}</p> }
          <button type="button" class="pub-btn pub-btn-primario" (click)="cerrar()" autofocus>Cerrar</button>
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
}
