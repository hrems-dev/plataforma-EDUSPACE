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
      <div class="pub-tarjeta">
        <div class="pub-tarjeta-ico" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/></svg>
        </div>
        <h3>Misión</h3>
        <p>Brindar una educación integral y de calidad que desarrolle el pensamiento crítico, la creatividad y los valores de nuestros estudiantes.</p>
      </div>
      <div class="pub-tarjeta">
        <div class="pub-tarjeta-ico" aria-hidden="true" style="background: var(--oro-suave); color: var(--oro);">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polygon points="12 6 12 12 16 14"/></svg>
        </div>
        <h3>Visión</h3>
        <p>Ser referente en la región por formar líderes íntegros, innovadores y comprometidos con su comunidad.</p>
      </div>
    </div></section>

    <section class="pub-sec alt"><div class="pub-wrap">
      <h2>Nuestros valores</h2>
      <p class="pub-sub">Guían cada decisión y cada clase.</p>
      <div class="pub-cuadricula">
        @for (v of valores; track v.titulo) {
          <div class="pub-tarjeta">
            <div class="pub-tarjeta-ico" aria-hidden="true">
              @if (v.titulo === 'Respeto') {
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
              } @else if (v.titulo === 'Excelencia') {
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
              } @else if (v.titulo === 'Responsabilidad') {
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
              } @else {
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
              }
            </div>
            <h3>{{ v.titulo }}</h3>
            <p>{{ v.texto }}</p>
          </div>
        }
      </div>
    </div></section>

    <section class="pub-sec"><div class="pub-wrap">
      <h2>Nuestra historia</h2>
      <p class="pub-sub">De tres aulas a un campus completo.</p>
      <ol class="pub-linea">
        @for (h of hitos; track h.anio) {
          <li><b>{{ h.anio }}</b> · <strong>{{ h.titulo }}</strong><br /><span class="muted">{{ h.texto }}</span></li>
        }
      </ol>
    </div></section>

    <section class="pub-sec alt"><div class="pub-wrap">
      <h2>Instalaciones</h2>
      <p class="pub-sub">Espacios para aprender, moverse, crear y cuidarse.</p>
      <div class="pub-filtros" role="group" aria-label="Filtrar instalaciones">
        @for (t of tipos; track t) {
          <button type="button" [class.activo]="tipo() === t" (click)="tipo.set(t)">{{ t }}</button>
        }
      </div>
      <div class="pub-cuadricula">
        @for (i of visibles(); track i.nombre) {
          <div class="pub-tarjeta">
            <img [src]="imgInstalacion(i.nombre)" [alt]="i.nombre" class="pub-foto-img" />
            <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 4px;">
              <span class="pub-chip">{{ i.tipo }}</span>
            </div>
            <h3>{{ i.nombre }}</h3>
            <p>{{ i.detalle }}</p>
          </div>
        }
      </div>
    </div></section>

    <section class="pub-sec"><div class="pub-wrap">
      <h2>Equipo directivo</h2>
      <p class="pub-sub">Personas cercanas, con puertas abiertas a las familias.</p>
      <div class="pub-cuadricula">
        @for (p of equipo; track p.nombre) {
          <div class="pub-tarjeta">
            <div class="pub-avatar" aria-hidden="true">{{ p.iniciales }}</div>
            <h3>{{ p.nombre }}</h3>
            <p>{{ p.cargo }}</p>
          </div>
        }
      </div>
    </div></section>

    <section class="pub-cta"><div class="pub-wrap">
      <h2>Ven a conocernos</h2>
      <p>Recorre el campus y conversa con nuestro equipo.</p>
      <a routerLink="/contacto" class="pub-btn pub-btn-oro pub-btn-grande">Agendar visita</a>
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

  protected imgInstalacion(nombre: string): string {
    const mapa: Record<string, string> = {
      'Laboratorio de ciencias': '/img/laboratorio-secundaria.jpg',
      'Aula de robótica': '/img/taller-robotica.jpg',
      'Biblioteca': '/img/biblioteca-escolar.jpg',
      'Losa deportiva techada': '/img/deportes-campus.jpg',
      'Piscina semiolímpica': '/img/inst-piscina.svg',
      'Auditorio': '/img/inst-auditorio.svg',
      'Taller de arte': '/img/inst-arte.svg',
      'Tópico y psicología': '/img/inst-bienestar.svg'
    };
    return mapa[nombre] ?? '/img/campus-hero.jpg';
  }
}