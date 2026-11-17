import { Component, input } from '@angular/core';
import { Notificacion, registrarVista } from './notificaciones';

@Component({ selector: 'app-n-pedido', template: `<p class="azul">Pedido {{ datos()['numero'] }} confirmado</p>` })
export class NotificacionPedido { readonly datos = input.required<Notificacion>(); }
@Component({ selector: 'app-n-pago', template: `<p class="verde">Pago de {{ datos()['valor'] }} recibido</p>` })
export class NotificacionPago { readonly datos = input.required<Notificacion>(); }
@Component({ selector: 'app-n-alerta', template: `<p class="rojo" role="alert">{{ datos()['mensaje'] }}</p>` })
export class NotificacionAlerta { readonly datos = input.required<Notificacion>(); }

export const VISTAS_BASE = [registrarVista('pedido', NotificacionPedido), registrarVista('pago', NotificacionPago), registrarVista('alerta', NotificacionAlerta)];
