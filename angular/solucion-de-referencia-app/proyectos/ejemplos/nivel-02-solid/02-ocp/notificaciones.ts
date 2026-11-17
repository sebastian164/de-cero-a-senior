import { NgComponentOutlet } from '@angular/common';
import { Component, computed, inject, InjectionToken, input, Type } from '@angular/core';

// Tipos que el README usa sin declarar.
export interface NotificacionPedido { tipo: 'pedido'; numero: string; }
export interface NotificacionPago { tipo: 'pago'; valor: number; }
export type Notificacion = NotificacionPedido | NotificacionPago | { tipo: string; [clave: string]: unknown };

// El punto de extensión: un registro de qué componente pinta cada tipo
export interface VistaDeNotificacion { tipo: string; componente: Type<unknown>; }
export const VISTAS_DE_NOTIFICACION = new InjectionToken<VistaDeNotificacion[]>('vistas de notificación');

// Cada tipo es una pieza independiente
@Component({ selector: 'app-notificacion-pedido', template: `<div class="azul">Pedido {{ datos().numero }} confirmado</div>` })
export class NotificacionPedidoComponent { datos = input.required<NotificacionPedido>(); }

@Component({ selector: 'app-notificacion-pago', template: `<div class="verde">Pago de {{ datos().valor }} recibido</div>` })
export class NotificacionPagoComponent { datos = input.required<NotificacionPago>(); }

// Registro, en la configuración de la aplicación o de la funcionalidad
export const PROVEEDORES_DE_NOTIFICACIONES = [
  { provide: VISTAS_DE_NOTIFICACION, multi: true, useValue: { tipo: 'pedido', componente: NotificacionPedidoComponent } },
  { provide: VISTAS_DE_NOTIFICACION, multi: true, useValue: { tipo: 'pago', componente: NotificacionPagoComponent } },
];

// Cerrado: no cambia cuando aparece un tipo nuevo
@Component({
  selector: 'app-notificacion',
  imports: [NgComponentOutlet],
  template: `
    @if (vista(); as v) {
      <ng-container *ngComponentOutlet="v.componente; inputs: { datos: n() }" />
    }
  `,
})
export class NotificacionComponent {
  readonly n = input.required<Notificacion>();
  private readonly vistas = inject(VISTAS_DE_NOTIFICACION);
  protected readonly vista = computed(() => this.vistas.find((v) => v.tipo === this.n().tipo));
}

@Component({
  selector: 'app-tarjeta',
  template: `
    <section class="tarjeta">
      <header><ng-content select="[titulo]" /></header>
      <ng-content />
      <footer><ng-content select="[acciones]" /></footer>
    </section>
  `,
})
export class TarjetaComponent {}
