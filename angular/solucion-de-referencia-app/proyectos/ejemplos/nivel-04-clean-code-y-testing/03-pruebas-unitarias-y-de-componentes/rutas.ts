import { Component, inject } from '@angular/core';
import { Routes } from '@angular/router';
import { AlmacenPedidos, ListaPedidosComponent } from './pedidos';

@Component({
  imports: [ListaPedidosComponent],
  template: `
    <h1>Pruebas: lista de pedidos</h1>
    <button type="button" (click)="almacen.filtrar('pendiente')">Solo pendientes</button>
    <button type="button" (click)="almacen.filtrar('todos')">Todos</button>
    <ul><app-lista-pedidos [pedidos]="almacen.visibles()" (cancelar)="almacen.cancelar($event)" /></ul>
    @if (almacen.error()) { <p role="alert">{{ almacen.error() }}</p> }
  `,
})
class Pagina { protected readonly almacen = inject(AlmacenPedidos); constructor() { this.almacen.cargar(); } }
export default [{ path: '', component: Pagina }] satisfies Routes;
