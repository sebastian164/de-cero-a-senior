import { Component, signal } from '@angular/core';
import { Routes } from '@angular/router';
import { Pedido, TablaPedidosComponent } from './tabla-pedidos.component';

@Component({
  imports: [TablaPedidosComponent],
  template: `
    <div>
      <h1>Rendimiento: tabla de pedidos</h1>
      <button type="button" (click)="agregar()">Agregar pedido</button>
      <table><app-tabla-pedidos [pedidos]="pedidos()" /></table>
    </div>
  `,
})
class Demo {
  protected readonly pedidos = signal<readonly Pedido[]>([
    { id: 1, lineas: [{ precio: 1000, cantidad: 2 }] },
    { id: 2, lineas: [{ precio: 2500, cantidad: 1 }] },
  ]);
  protected agregar(): void { this.pedidos.update((p) => [...p, { id: p.length + 1, lineas: [{ precio: 100, cantidad: 1 }] }]); }
}
export default [{ path: '', component: Demo }] satisfies Routes;
