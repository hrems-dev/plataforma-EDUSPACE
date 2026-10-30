import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EXTENSIONES_MATERIAL, Material } from '../../core/models/material.model';
import { MaterialService } from '../../core/services/material.service';
import { NotificationService } from '../../core/services/notification.service';
import { mensajeError } from '../../core/utils/errores';

/**
 * Materiales de un curso en una sección. El docente (`editable`) sube archivos o enlaces y los elimina; las familias
 * (alumno y apoderado) solo los ven, los abren y los descargan.
 */
@Component({
  selector: 'app-materiales-curso',
  imports: [ReactiveFormsModule, DatePipe],
  template: `
    @if (editable()) {
      <section class="card" style="margin-bottom: 14px"><div class="card-body">
        <h2 style="margin: 0 0 4px">Subir material</h2>
        <p class="muted" style="margin: 0 0 12px">Lo ven los alumnos y apoderados de la sección, que reciben un aviso. Archivos de hasta 10 MB (PDF, Word, PowerPoint, Excel, imágenes, ZIP o texto) o un enlace.</p>
        <div class="tipo" role="tablist" aria-label="Tipo de material">
          <button type="button" role="tab" [class.on]="tipo() === 'ARCHIVO'" [attr.aria-selected]="tipo() === 'ARCHIVO'" (click)="tipo.set('ARCHIVO')">📎 Archivo</button>
          <button type="button" role="tab" [class.on]="tipo() === 'ENLACE'" [attr.aria-selected]="tipo() === 'ENLACE'" (click)="tipo.set('ENLACE')">🔗 Enlace</button>
        </div>
        <form [formGroup]="form" (ngSubmit)="publicar()" novalidate class="form">
          <div class="field">
            <label for="mat-titulo">Título</label>
            <input id="mat-titulo" class="input" formControlName="titulo" placeholder="Ej.: Guía de fracciones" />
            @if (form.controls.titulo.touched && form.controls.titulo.invalid) { <span class="error">Escribe un título (mínimo 3 letras).</span> }
          </div>
          <div class="field">
            <label for="mat-desc">Descripción (opcional)</label>
            <textarea id="mat-desc" rows="2" formControlName="descripcion" style="padding: 10px 12px; min-height: 64px"></textarea>
          </div>
          @if (tipo() === 'ARCHIVO') {
            <div class="field">
              <label for="mat-archivo">Archivo</label>
              <input id="mat-archivo" type="file" [accept]="extensiones" (change)="elegir($event)" />
              @if (archivo(); as a) { <span class="muted">{{ a.name }} · {{ tamano(a.size) }}</span> }
            </div>
          } @else {
            <div class="field">
              <label for="mat-enlace">Enlace</label>
              <input id="mat-enlace" class="input" type="url" formControlName="enlace" placeholder="https://…" />
            </div>
          }
          @if (error()) { <div class="aviso aviso-danger" role="alert">{{ error() }}</div> }
          <div><button type="submit" class="btn btn-primary" [disabled]="subiendo()">{{ subiendo() ? 'Publicando…' : 'Publicar material' }}</button></div>
        </form>
      </div></section>
    }

    <section class="card"><div class="card-body">
      <h2 style="margin: 0 0 8px">Materiales del curso</h2>
      @for (m of materiales(); track m.id) {
        <div class="fila">
          <span class="ico" aria-hidden="true">{{ m.tipo === 'ENLACE' ? '🔗' : icono(m) }}</span>
          <div class="info">
            <strong>{{ m.titulo }}</strong>
            @if (m.descripcion) { <div>{{ m.descripcion }}</div> }
            <div class="muted">{{ m.tipo === 'ENLACE' ? enlaceCorto(m.enlace) : (m.nombreArchivo + ' · ' + tamano(m.tamanoBytes ?? 0)) }} · {{ m.creadoEn | date: 'dd/MM/yyyy' }}</div>
          </div>
          <div class="acc">
            @if (m.tipo === 'ENLACE') { <a class="btn btn-sm btn-primary" [href]="m.enlace" target="_blank" rel="noopener noreferrer">Abrir</a> }
            @else { <button type="button" class="btn btn-sm btn-primary" [disabled]="abriendo() === m.id" (click)="descargar(m)">{{ abriendo() === m.id ? '…' : 'Descargar' }}</button> }
            @if (editable()) { <button type="button" class="btn btn-sm btn-danger" (click)="eliminar(m)">Eliminar</button> }
          </div>
        </div>
      } @empty {
        <p class="muted">{{ cargando() ? 'Cargando…' : (editable() ? 'Aún no has subido materiales a este curso.' : 'El docente todavía no ha subido materiales a este curso.') }}</p>
      }
    </div></section>
  `,
  styles: `
    .tipo { display: inline-flex; gap: 4px; padding: 4px; background: var(--primary-soft); border-radius: 12px; margin-bottom: 12px; }
    .tipo button { border: 0; background: transparent; padding: 7px 16px; border-radius: 9px; font: inherit; font-weight: 600; color: var(--ink-2); cursor: pointer; }
    .tipo button.on { background: var(--card); color: var(--primary); box-shadow: 0 1px 4px rgb(20 33 61 / .12); }
    .form { display: grid; gap: 12px; max-width: 560px; }
    .fila { display: flex; align-items: center; gap: 14px; padding: 12px 0; border-top: 1px solid var(--line); }
    .fila:first-of-type { border-top: 0; }
    .ico { flex: none; width: 42px; height: 42px; border-radius: 12px; display: grid; place-items: center; background: var(--primary-soft); font-size: 1.2rem; }
    .info { flex: 1; min-width: 0; overflow-wrap: anywhere; }
    .acc { display: flex; gap: 8px; flex: none; }
    @media (max-width: 640px) { .fila { flex-wrap: wrap; } .acc { width: 100%; } }
  `
})
export class MaterialesCursoComponent implements OnInit {
  private readonly api = inject(MaterialService);
  private readonly avisos = inject(NotificationService);
  private readonly fb = inject(FormBuilder);

  readonly seccionId = input.required<number>();
  readonly cursoId = input.required<number>();
  readonly editable = input(false);

  protected readonly extensiones = EXTENSIONES_MATERIAL;
  protected readonly materiales = signal<Material[]>([]);
  protected readonly cargando = signal(true);
  protected readonly tipo = signal<'ARCHIVO' | 'ENLACE'>('ARCHIVO');
  protected readonly archivo = signal<File | null>(null);
  protected readonly subiendo = signal(false);
  protected readonly abriendo = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    titulo: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(150)]],
    descripcion: ['', Validators.maxLength(1000)],
    enlace: ['']
  });

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.api.listar(this.seccionId(), this.cursoId()).subscribe({
      next: l => { this.materiales.set(l); this.cargando.set(false); },
      error: e => { this.cargando.set(false); this.avisos.error(mensajeError(e, 'No se pudieron cargar los materiales.')); }
    });
  }

  protected elegir(ev: Event): void {
    const f = (ev.target as HTMLInputElement).files?.[0] ?? null;
    if (f && f.size > 10 * 1024 * 1024) {
      this.error.set('El archivo supera los 10 MB.');
      this.archivo.set(null);
      (ev.target as HTMLInputElement).value = '';
      return;
    }
    this.error.set(null);
    this.archivo.set(f);
  }

  protected publicar(): void {
    this.error.set(null);
    if (this.form.controls.titulo.invalid) { this.form.controls.titulo.markAsTouched(); return; }
    const { titulo, descripcion, enlace } = this.form.getRawValue();
    let peticion;
    if (this.tipo() === 'ARCHIVO') {
      const a = this.archivo();
      if (!a) { this.error.set('Elige el archivo que vas a subir.'); return; }
      peticion = this.api.publicarArchivo(this.seccionId(), this.cursoId(), titulo.trim(), descripcion.trim(), a);
    } else {
      if (!/^https?:\/\/\S{4,}/i.test(enlace.trim())) { this.error.set('El enlace debe empezar con http:// o https://'); return; }
      peticion = this.api.publicarEnlace(this.seccionId(), this.cursoId(), titulo.trim(), descripcion.trim(), enlace.trim());
    }
    this.subiendo.set(true);
    peticion.subscribe({
      next: () => {
        this.subiendo.set(false);
        this.avisos.success('Material publicado. Los alumnos y apoderados recibieron un aviso.');
        this.form.reset({ titulo: '', descripcion: '', enlace: '' });
        this.archivo.set(null);
        const input = document.getElementById('mat-archivo') as HTMLInputElement | null;
        if (input) input.value = '';
        this.cargar();
      },
      error: e => { this.subiendo.set(false); this.error.set(mensajeError(e, 'No se pudo publicar el material.')); }
    });
  }

  protected descargar(m: Material): void {
    this.abriendo.set(m.id);
    this.api.urlDescarga(m.id).subscribe({
      next: d => { this.abriendo.set(null); window.open(d.urlDescarga, '_blank', 'noopener'); },
      error: e => { this.abriendo.set(null); this.avisos.error(mensajeError(e, 'No se pudo abrir el archivo.')); }
    });
  }

  protected eliminar(m: Material): void {
    if (!window.confirm(`¿Eliminar «${m.titulo}»? Los alumnos ya no lo verán.`)) return;
    this.api.eliminar(m.id).subscribe({
      next: () => { this.avisos.success('Material eliminado.'); this.cargar(); },
      error: e => this.avisos.error(mensajeError(e, 'No se pudo eliminar el material.'))
    });
  }

  protected icono(m: Material): string {
    const t = m.contentType ?? '';
    if (t.includes('pdf')) return '📕';
    if (t.startsWith('image/')) return '🖼️';
    if (t.includes('presentation')) return '📊';
    if (t.includes('spreadsheet')) return '📈';
    if (t.includes('word')) return '📝';
    if (t.includes('zip')) return '🗜️';
    return '📄';
  }

  protected enlaceCorto(url?: string): string {
    return (url ?? '').replace(/^https?:\/\//, '').slice(0, 60);
  }

  protected tamano(bytes: number): string {
    return bytes >= 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }
}
