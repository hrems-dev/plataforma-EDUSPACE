import { Component, input, output } from '@angular/core';

/** Diálogo modal. El contenido se proyecta; los botones de acción van en el slot `footer`. */
@Component({
  selector: 'app-modal',
  templateUrl: './modal.component.html',
  styleUrl: './modal.component.css'
})
export class ModalComponent {
  readonly titulo = input.required<string>();
  readonly ancho = input(560);
  readonly cerrar = output<void>();

  onFondo(evento: MouseEvent): void {
    if (evento.target === evento.currentTarget) {
      this.cerrar.emit();
    }
  }

  onTecla(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      this.cerrar.emit();
    }
  }
}
