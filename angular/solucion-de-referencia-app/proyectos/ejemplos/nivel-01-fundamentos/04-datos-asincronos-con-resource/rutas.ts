import { Component } from '@angular/core';
import { Routes } from '@angular/router';
import { DetallePedidoComponent, DetallePedidoVariantesComponent } from './detalle-pedido.component';

@Component({
  imports: [DetallePedidoComponent, DetallePedidoVariantesComponent],
  template: `<app-detalle-pedido id="2" /><app-detalle-pedido-variantes id="3" />`,
})
class Demo {}

export default [{ path: '', component: Demo }] satisfies Routes;
