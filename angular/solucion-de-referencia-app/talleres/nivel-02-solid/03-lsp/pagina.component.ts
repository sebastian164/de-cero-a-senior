import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { BotonPrimarioComponent } from './boton.component';
import { EstrellasComponent } from './estrellas.component';
import { NotificadorEnPantalla, NotificadorRemoto } from './notificador';

@Component({
  imports: [BotonPrimarioComponent, EstrellasComponent, ReactiveFormsModule],
  template: `
    <h1>LSP</h1>
    <app-estrellas [formControl]="calificacion" etiqueta="Califica el servicio" />
    <p>Valor: {{ calificacion.value }} · {{ calificacion.disabled ? 'deshabilitado' : 'habilitado' }}</p>
    <button type="button" (click)="alternar()">Alternar habilitado</button>
    <button appBotonPrimario type="button" (click)="enviar()">Enviar calificación</button>
    <button appBotonPrimario type="button" disabled>Primario deshabilitado</button>
    <ul aria-label="Avisos">@for (a of pantalla.avisos(); track $index) { <li>{{ a }}</li> }</ul>
  `,
})
export class PaginaLsp {
  protected readonly calificacion = new FormControl(3);
  protected readonly pantalla = inject(NotificadorEnPantalla);
  private readonly remoto = inject(NotificadorRemoto);
  protected alternar(): void { if (this.calificacion.disabled) this.calificacion.enable(); else this.calificacion.disable(); }
  protected async enviar(): Promise<void> {
    const mensaje = `Calificación ${this.calificacion.value}`;
    await this.remoto.notificar(mensaje);
    await this.pantalla.notificar(`${mensaje} enviada`);
  }
}
