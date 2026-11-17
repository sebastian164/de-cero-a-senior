import { Component, inject } from '@angular/core';
import { RouterLink, Routes } from '@angular/router';
import { Carrito, CarritoStore } from './carrito';
import { ListaPedidosPagina } from './lista-pedidos.pagina';

@Component({
  imports: [RouterLink],
  template: `
    <h1>Carrito (servicio y SignalStore)</h1>
    <button type="button" (click)="agregar()">Agregar línea</button>
    <p>Servicio: {{ servicio.cantidad() }} unidades, total {{ servicio.total() }}</p>
    <p>SignalStore: {{ store.cantidad() }} unidades, total {{ store.total() }}</p>
    <a routerLink="..">Ver pedidos</a>
  `,
})
class DemoCarrito {
  protected readonly servicio = inject(Carrito);
  protected readonly store = inject(CarritoStore);
  protected agregar() { const l = { productoId: 'a', precio: 100, cantidad: 2 }; this.servicio.agregar(l); this.store.agregar(l); }
}
// La página de pedidos es la que recibe los parámetros de consulta como entradas: tiene que ser el componente de la ruta.
export default [
  { path: '', component: ListaPedidosPagina },
  { path: 'carrito', component: DemoCarrito },
] satisfies Routes;
