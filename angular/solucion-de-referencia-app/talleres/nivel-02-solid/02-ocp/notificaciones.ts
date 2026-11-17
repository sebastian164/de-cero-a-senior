import { NgComponentOutlet } from '@angular/common';
import { Component, computed, inject, InjectionToken, input, Provider, Type } from '@angular/core';

export interface Notificacion { readonly tipo: string; readonly [dato: string]: unknown; }
export interface VistaDeNotificacion { tipo: string; componente: Type<unknown>; }
export const VISTAS_DE_NOTIFICACION = new InjectionToken<VistaDeNotificacion[]>('vistas de notificación');

/** Cerrado: este archivo no se toca para agregar un tipo. */
@Component({
  selector: 'app-notificacion',
  imports: [NgComponentOutlet],
  template: `@if (vista(); as v) { <ng-container *ngComponentOutlet="v.componente; inputs: { datos: n() }" /> }`,
})
export class NotificacionComponent {
  readonly n = input.required<Notificacion>();
  private readonly vistas = inject(VISTAS_DE_NOTIFICACION);
  protected readonly vista = computed(() => this.vistas.find((v) => v.tipo === this.n().tipo));
}

export const registrarVista = (tipo: string, componente: Type<unknown>): Provider =>
  ({ provide: VISTAS_DE_NOTIFICACION, multi: true, useValue: { tipo, componente } });
