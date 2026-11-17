import { Component, inject } from '@angular/core';
import { AlmacenPedidos } from '../estado/almacen-pedidos';
import { ListaPedidos } from '../ui/lista-pedidos';

@Component({
  imports: [ListaPedidos],
  template: `<h2>Pedidos</h2><p>Almacén <code>{{ almacen.instancia }}</code></p><app-lista-pedidos [pedidos]="almacen.pedidos()" />`,
})
export class ListaPedidosPagina { protected readonly almacen = inject(AlmacenPedidos); constructor() { this.almacen.cargar(); } }
