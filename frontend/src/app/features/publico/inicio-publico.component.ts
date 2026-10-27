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
          <span class="pub-chip">Admisión 2027 abierta</span>
          <h1>{{ colegio.lema }}</h1>
          <p>En {{ colegio.nombre }} acompañamos a cada estudiante, desde inicial hasta quinto de secundaria, con cercanía, rigor académico y mucha curiosidad.</p>
          <div class="pub-heroe-acc">
            <a routerLink="/admision" class="pub-btn pub-btn-claro pub-btn-grande">Quiero postular</a>
            <a routerLink="/nosotros" class="pub-btn pub-btn-borde pub-btn-grande">Conocer el colegio</a>
          </div>
        </div>
        <aside class="pub-heroe-panel" aria-label="Próximos eventos">
          <h3>Próximos eventos</h3>
          @for (e of proximos; track e.fecha) {
            <div class="pub-prox">
              <div class="pub-fecha"><b>{{ e.fecha | date: 'd' }}</b><span>{{ e.fecha | date: 'MMM' }}</span></div>
              <div><strong>{{ e.titulo }}</strong><br /><small>{{ e.hora }} · {{ e.lugar }}</small></div>
            </div>
          }
        </aside>
      </div>
    </section>

    <section class="pub-cifras"><div class="pub-wrap pub-cifras-in" aria-label="El colegio en cifras">
      @for (c of cifras; track c.etiqueta; let i = $index) {
        <div class="pub-cifra"><b>{{ conteo()[i] }}{{ c.sufijo }}</b><span>{{ c.etiqueta }}</span></div>
      }
    </div></section>

    <section class="pub-sec">
      <div class="pub-wrap">
        <h2>Un camino completo, de los 3 a los 16 años</h2>
        <p class="pub-sub">Tres niveles pensados para cada etapa. Elige uno para ver horarios, talleres y costos.</p>
        <div class="pub-cuadricula">
          @for (n of niveles; track n.id) {
            <a class="pub-tarjeta clic" [routerLink]="'/niveles'" [queryParams]="{ nivel: n.id }" style="text-decoration: none">
              <div class="pub-nivel-ic" [style.background]="n.color" aria-hidden="true">{{ n.nombre[0] }}</div>
              <h3>{{ n.nombre }} <small class="muted">· {{ n.edades }}</small></h3>
              <p>{{ n.resumen }}</p>
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
          <div class="pub-tarjeta"><h3>Atención personalizada</h3><p>Aulas pequeñas y tutoría constante para que ningún estudiante pase desapercibido.</p></div>
          <div class="pub-tarjeta"><h3>Inglés y tecnología</h3><p>Idiomas desde los 5 años, robótica y aulas digitales en todos los niveles.</p></div>
          <div class="pub-tarjeta"><h3>Familias informadas</h3><p>Portal web con notas, asistencia, tareas y pagos al alcance de tu celular.</p></div>
          <div class="pub-tarjeta"><h3>Bienestar emocional</h3><p>Equipo de psicología y programas de convivencia que cuidan a cada estudiante.</p></div>
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
        <div class="pub-testi" aria-live="polite" style="margin-top: 24px">
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
        <a routerLink="/admision" class="pub-btn pub-btn-claro pub-btn-grande">Ir a admisión</a>
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
}
