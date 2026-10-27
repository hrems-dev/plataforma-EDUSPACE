import { Component, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EQUIPO, HITOS, INSTALACIONES, Instalacion, VALORES } from './datos';

@Component({
  selector: 'app-nosotros',
  imports: [RouterLink],
  template: `
    <section class="pub-cab-tit"><div class="pub-wrap">
      <span class="pub-chip">Nosotros</span>
      <h1>Más de 25 años formando personas</h1>
      <p>Somos una comunidad educativa que cree que aprender es descubrir, convivir y servir.</p>
    </div></section>

    <section class="pub-sec"><div class="pub-wrap pub-cuadricula">
      <div class="pub-tarjeta"><h3>Misión</h3><p>Brindar una educación integral y de calidad que desarrolle el pensamiento crítico, la creatividad y los valores de nuestros estudiantes.</p></div>
      <div class="pub-tarjeta"><h3>Visión</h3><p>Ser referente en la región por formar líderes íntegros, innovadores y comprometidos con su comunidad.</p></div>
    </div></section>

    <section class="pub-sec alt"><div class="pub-wrap">
      <h2>Nuestros valores</h2>
      <p class="pub-sub">Guían cada decisión y cada clase.</p>
      <div class="pub-cuadricula">
        @for (v of valores; track v.titulo) { <div class="pub-tarjeta"><h3>{{ v.titulo }}</h3><p>{{ v.texto }}</p></div> }
      </div>
    </div></section>

    <section class="pub-sec"><div class="pub-wrap">
      <h2>Nuestra historia</h2>
      <p class="pub-sub">De tres aulas a un campus completo.</p>
      <ol class="pub-linea">
        @for (h of hitos; track h.anio) { <li><b>{{ h.anio }}</b> · <strong>{{ h.titulo }}</strong><br /><span class="muted">{{ h.texto }}</span></li> }
      </ol>
    </div></section>

    <section class="pub-sec alt"><div class="pub-wrap">
      <h2>Instalaciones</h2>
      <p class="pub-sub">Espacios para aprender, moverse, crear y cuidarse.</p>
      <div class="pub-filtros" role="group" aria-label="Filtrar instalaciones">
        @for (t of tipos; track t) { <button type="button" [class.activo]="tipo() === t" (click)="tipo.set(t)">{{ t }}</button> }
      </div>
      <div class="pub-cuadricula">
        @for (i of visibles(); track i.nombre) {
          <div class="pub-tarjeta"><div class="pub-foto" [style.background-color]="i.color">{{ i.tipo }}</div><h3>{{ i.nombre }}</h3><p>{{ i.detalle }}</p></div>
        }
      </div>
    </div></section>

    <section class="pub-sec"><div class="pub-wrap">
      <h2>Equipo directivo</h2>
      <p class="pub-sub">Personas cercanas, con puertas abiertas a las familias.</p>
      <div class="pub-cuadricula">
        @for (p of equipo; track p.nombre) { <div class="pub-tarjeta"><div class="pub-avatar" aria-hidden="true">{{ p.iniciales }}</div><h3>{{ p.nombre }}</h3><p>{{ p.cargo }}</p></div> }
      </div>
    </div></section>

    <section class="pub-cta"><div class="pub-wrap">
      <h2>Ven a conocernos</h2><p>Recorre el campus y conversa con nuestro equipo.</p>
      <a routerLink="/contacto" class="pub-btn pub-btn-claro pub-btn-grande">Agendar visita</a>
    </div></section>
  `
})
export class NosotrosComponent {
  protected readonly valores = VALORES;
  protected readonly hitos = HITOS;
  protected readonly equipo = EQUIPO;
  protected readonly tipos: ('Todas' | Instalacion['tipo'])[] = ['Todas', 'Aprendizaje', 'Deporte', 'Arte', 'Bienestar'];
  protected readonly tipo = signal<'Todas' | Instalacion['tipo']>('Todas');
  protected readonly visibles = computed(() => INSTALACIONES.filter(i => this.tipo() === 'Todas' || i.tipo === this.tipo()));
}
