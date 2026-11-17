import { Component, inject } from '@angular/core';
import { Routes } from '@angular/router';
import { AlmacenPedidos } from './aplicacion/almacen-pedidos';
import { CancelarPedido } from './aplicacion/cancelar-pedido';
import { PedidosHttp } from './datos/pedidos-http.repositorio';
import { sePuedeCancelar } from './dominio/pedido';
import { RepositorioPedidos } from './dominio/puertos';

@Component({
  template: `
    <h1>Arquitectura limpia: pedidos</h1>
    <ul>
      @for (p of almacen.pedidos(); track p.id) {
        <li>Pedido {{ p.id }} ({{ p.estado }})
          @if (puede(p)) { <button type="button" (click)="almacen.cancelar(p)">Cancelar pedido {{ p.id }}</button> }
          @else { <span>no cancelable</span> }
        </li>
      }
    </ul>
    <p role="status">{{ almacen.mensaje() }}</p>
  `,
})
class Pagina {
  protected readonly almacen = inject(AlmacenPedidos);
  constructor() { this.almacen.cargar(); }
  protected puede(p: Parameters<typeof sePuedeCancelar>[0]) { return sePuedeCancelar(p, new Date()); }
}

export default [{
  path: '', component: Pagina,
  // pedidos.rutas.ts — ensamblaje (README)
  providers: [{ provide: RepositorioPedidos, useClass: PedidosHttp }, CancelarPedido, AlmacenPedidos],
}] satisfies Routes;
