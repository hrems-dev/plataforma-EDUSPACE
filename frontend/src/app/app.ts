import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ConfirmComponent } from './shared/components/confirm/confirm.component';
import { ToastComponent } from './shared/components/toast/toast.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastComponent, ConfirmComponent],
  template: `
    <router-outlet />
    <app-toast />
    <app-confirm />
  `
})
export class App {}
