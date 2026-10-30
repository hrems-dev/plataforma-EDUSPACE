import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CIFRAS, COLEGIO, EVENTOS, NIVELES, NOTICIAS, TESTIMONIOS } from './datos';

@Component({
  selector: 'app-inicio-publico',
  imports: [RouterLink, DatePipe],
  template: `
    <section class="pub-heroe">
      <div class="pub-wrap pub-heroe-in">
        <div>
          <span class="pub-chip">✦ Admisión 2027 abierta</span>
          <h1>{{ colegio.lema }}</h1>
          <p>En {{ colegio.nombre }} acompañamos a cada estudiante, desde inicial hasta quinto de secundaria, con cercanía, rigor académico y mucha curiosidad.</p>
          <div class="pub-heroe-acc">
            <a routerLink="/admision" class="pub-btn pub-btn-oro pub-btn-grande">Quiero postular</a>
            <a routerLink="/nosotros" class="pub-btn pub-btn-borde pub-btn-grande">Conocer el colegio</a>
          </div>
        </div>
        <div class="pub-heroe-visual">
          <aside class="pub-heroe-panel" aria-label="Próximos eventos">
            <h3>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
              Próximos eventos
            </h3>
            @for (e of proximos; track e.fecha) {
              <div class="pub-prox">
                <div class="pub-fecha"><b>{{ e.fecha | date: 'd' }}</b><span>{{ e.fecha | date: 'MMM' }}</span></div>
                <div><strong>{{ e.titulo }}</strong><br /><small>{{ e.hora }} · {{ e.lugar }}</small></div>
              </div>
            }
          </aside>
        </div>
      </div>
    </section>

    <section class="pub-cifras">
      <div class="pub-wrap pub-cifras-in" aria-label="El colegio en cifras">
        @for (c of cifras; track c.etiqueta; let i = $index) {
          <div class="pub-cifra">
            <div class="pub-cifra-ico" aria-hidden="true">
              @if (i === 0) {
                <!-- Trayectoria / Medalla -->
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="6"/><path d="M15.477 12.89L17 22l-5-3-5 3 1.523-9.11"/></svg>
              } @else if (i === 1) {
                <!-- Estudiantes / Comunidad -->
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
              } @else if (i === 2) {
                <!-- Docentes / Vocación -->
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/><line x1="9" y1="7" x2="15" y2="7"/><line x1="9" y1="11" x2="13" y2="11"/></svg>
              } @else {
                <!-- Universidad / Birrete -->
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 10v6M2 10l10-5 10 5-10 5z"/><path d="M6 12v5c3 3 9 3 12 0v-5"/></svg>
              }
            </div>
            <div>
              <b>{{ conteo()[i] }}{{ c.sufijo }}</b>
              <span>{{ c.etiqueta }}</span>
            </div>
          </div>
        }
      </div>
    </section>

    <section class="pub-sec">
      <div class="pub-wrap">
        <h2>Un camino completo, de los 3 a los 16 años</h2>
        <p class="pub-sub">Tres niveles pensados para cada etapa. Elige uno para ver horarios, talleres y costos.</p>
        <div class="pub-cuadricula">
          @for (n of niveles; track n.id) {
            <a class="pub-tarjeta pub-nivel-card clic" [routerLink]="'/niveles'" [queryParams]="{ nivel: n.id }" style="text-decoration: none">
              <img [src]="imgNivel(n.id)" [alt]="'Nivel ' + n.nombre" class="pub-nivel-banner" />
              <div class="pub-nivel-body">
                <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 8px;">
                  <div class="pub-nivel-ic" [style.background]="n.color" aria-hidden="true">{{ n.nombre[0] }}</div>
                  <div>
                    <h3 style="margin: 0;">{{ n.nombre }}</h3>
                    <small class="muted" style="font-weight: 600;">{{ n.edades }}</small>
                  </div>
                </div>
                <p>{{ n.resumen }}</p>
                <div style="margin-top: 14px; font-weight: 700; color: var(--pc); font-size: 0.9rem; display: flex; align-items: center; gap: 4px;">
                  Ver detalles y costos →
                </div>
              </div>
            </a>
          }
        </div>
      </div>
    </section>

    <section class="pub-sec alt">
      <div class="pub-wrap">
        <h2>Por qué las familias nos eligen</h2>
        <p class="pub-sub">Lo que más valoran quienes ya son parte de la comunidad.</p>
        <div class="pub-cuadricula">
          <div class="pub-tarjeta">
            <div class="pub-tarjeta-ico" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
            </div>
            <h3>Atención personalizada</h3>
            <p>Aulas pequeñas y tutoría constante para que ningún estudiante pase desapercibido.</p>
          </div>
          <div class="pub-tarjeta">
            <div class="pub-tarjeta-ico" aria-hidden="true" style="background: var(--pc-suave); color: var(--pc-medio);">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1 4-10z"/></svg>
            </div>
            <h3>Inglés y tecnología</h3>
            <p>Idiomas desde los 5 años, robótica y aulas digitales en todos los niveles.</p>
          </div>
          <div class="pub-tarjeta">
            <div class="pub-tarjeta-ico" aria-hidden="true" style="background: var(--oro-suave); color: var(--oro);">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="2" width="14" height="20" rx="2" ry="2"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>
            </div>
            <h3>Familias informadas</h3>
            <p>Portal web con notas, asistencia, tareas y pagos al alcance de tu celular.</p>
          </div>
          <div class="pub-tarjeta">
            <div class="pub-tarjeta-ico" aria-hidden="true" style="background: var(--verde-suave); color: var(--verde-esc);">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            </div>
            <h3>Bienestar emocional</h3>
            <p>Equipo de psicología y programas de convivencia que cuidan a cada estudiante.</p>
          </div>
        </div>
      </div>
    </section>

    <section class="pub-sec">
      <div class="pub-wrap">
        <h2>Últimas noticias</h2>
        <p class="pub-sub">Lo que pasa hoy en el colegio.</p>
        <div class="pub-cuadricula">
          @for (n of noticias; track n.id) {
            <a class="pub-tarjeta clic" routerLink="/noticias" [queryParams]="{ ver: n.id }" style="text-decoration: none">
              <img [src]="imgNoticia(n.id)" [alt]="n.titulo" class="pub-tarjeta-img" />
              <span class="pub-chip">{{ n.categoria }}</span>
              <h3>{{ n.titulo }}</h3>
              <p>{{ n.resumen }}</p>
              <small class="pub-noticia-fecha">{{ n.fecha | date: 'd MMMM y' }}</small>
            </a>
          }
        </div>
      </div>
    </section>

    <section class="pub-sec alt">
      <div class="pub-wrap">
        <h2 style="text-align: center">Lo que dicen nuestras familias</h2>
        <div class="pub-testi" aria-live="polite" style="margin-top: 28px">
          <blockquote>«{{ testimonios[actual()].texto }}»</blockquote>
          <strong>{{ testimonios[actual()].nombre }}</strong><br /><small class="muted">{{ testimonios[actual()].rol }}</small>
          <div class="pub-puntos">
            @for (t of testimonios; track t.nombre; let i = $index) {
              <button type="button" [class.activo]="i === actual()" (click)="ir(i)" [attr.aria-label]="'Testimonio ' + (i + 1)"></button>
            }
          </div>
        </div>
      </div>
    </section>

    <section class="pub-cta">
      <div class="pub-wrap">
        <h2>¿Listo para dar el primer paso?</h2>
        <p>Agenda una visita, resuelve tus dudas y calcula la pensión de tu hijo en minutos.</p>
        <a routerLink="/admision" class="pub-btn pub-btn-oro pub-btn-grande">Ir a admisión</a>
      </div>
    </section>
  `
})
export class InicioPublicoComponent implements OnInit {
  private readonly destruir = inject(DestroyRef);
  protected readonly colegio = COLEGIO;
  protected readonly cifras = CIFRAS;
  protected readonly niveles = NIVELES;
  protected readonly noticias = NOTICIAS.slice(0, 3);
  protected readonly proximos = EVENTOS.slice(0, 4);
  protected readonly testimonios = TESTIMONIOS;
  protected readonly actual = signal(0);
  /** Las cifras suben de 0 a su valor al entrar (si la persona prefiere menos movimiento, aparecen de una vez). */
  protected readonly conteo = signal<number[]>(CIFRAS.map(c => c.valor));
  private temporizador?: ReturnType<typeof setInterval>;

  ngOnInit(): void {
    if (!matchMedia('(prefers-reduced-motion: reduce)').matches) {
      const inicio = performance.now();
      const duracion = 1400;
      this.conteo.set(CIFRAS.map(() => 0));
      const paso = (t: number): void => {
        const p = Math.min(1, (t - inicio) / duracion);
        const suave = 1 - Math.pow(1 - p, 3);
        this.conteo.set(CIFRAS.map(c => Math.round(c.valor * suave)));
        if (p < 1) requestAnimationFrame(paso);
      };
      requestAnimationFrame(paso);
    }
    this.reiniciar();
    this.destruir.onDestroy(() => clearInterval(this.temporizador));
  }

  protected ir(i: number): void {
    this.actual.set(i);
    this.reiniciar();
  }

  private reiniciar(): void {
    clearInterval(this.temporizador);
    this.temporizador = setInterval(() => this.actual.update(i => (i + 1) % TESTIMONIOS.length), 6000);
  }

  protected imgNivel(id: string): string {
    const mapa: Record<string, string> = {
      inicial: '/img/aula-inicial.jpg',
      primaria: '/img/aula-primaria.jpg',
      secundaria: '/img/laboratorio-secundaria.jpg'
    };
    return mapa[id] ?? `/img/nivel-${id}.svg`;
  }

  protected imgNoticia(id: number): string {
    const mapa: Record<number, string> = {
      1: '/img/admision-visita.jpg',
      2: '/img/taller-robotica.jpg',
      3: '/img/deportes-campus.jpg'
    };
    return mapa[id] ?? `/img/noticia-${id}.svg`;
  }
}