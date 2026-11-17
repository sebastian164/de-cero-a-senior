import { Component } from '@angular/core';
import { Routes } from '@angular/router';
import { Notificacion, NotificacionComponent, PROVEEDORES_DE_NOTIFICACIONES, TarjetaComponent } from './notificaciones';

@Component({
  imports: [NotificacionComponent, TarjetaComponent],
  template: `
    @for (n of lista; track $index) { <app-notificacion [n]="n" /> }
    <app-tarjeta><h2 titulo>Título proyectado</h2><p>Cuerpo</p><button acciones type="button">Acción</button></app-tarjeta>
  `,
})
class Demo {
  protected readonly lista: Notificacion[] = [{ tipo: 'pedido', numero: 'A-17' }, { tipo: 'pago', valor: 50000 }, { tipo: 'desconocido' }];
}
export default [{ path: '', component: Demo, providers: PROVEEDORES_DE_NOTIFICACIONES }] satisfies Routes;
