import { Component, input } from '@angular/core';
import { Notificacion, registrarVista } from './notificaciones';

// 2. El cuarto tipo: un archivo nuevo y un proveedor. NotificacionComponent no se modifica.
@Component({ selector: 'app-n-envio', template: `<p class="morado">Tu pedido salió con {{ datos()['transportadora'] }} (guía {{ datos()['guia'] }})</p>` })
export class NotificacionEnvio { readonly datos = input.required<Notificacion>(); }
export const VISTA_ENVIO = registrarVista('envio', NotificacionEnvio);
