import { Component, inject } from '@angular/core';
import { ConfirmService } from '../../../core/services/confirm.service';
import { ModalComponent } from '../modal/modal.component';

@Component({
  selector: 'app-confirm',
  imports: [ModalComponent],
  template: `
    @if (confirm.pedido(); as p) {
      <app-modal titulo="Confirmar" [ancho]="440" (cerrar)="confirm.responder(false)">
        <p style="margin: 0">{{ p.mensaje }}</p>
        <ng-container footer>
          <button type="button" class="btn" (click)="confirm.responder(false)">Cancelar</button>
          <button type="button" class="btn" [class.btn-danger]="p.peligroso" [class.btn-primary]="!p.peligroso" (click)="confirm.responder(true)">
            {{ p.textoAceptar }}
          </button>
        </ng-container>
      </app-modal>
    }
  `
})
export class ConfirmComponent {
  protected readonly confirm = inject(ConfirmService);
}
