import { Component, inject, signal } from '@angular/core';
import { Routes } from '@angular/router';
import { Pedido, PedidoNoEncontrado, PedidosApi, SinConexion } from './pedidos.api';

@Component({
  template: `
    <h1>Errores con significado</h1>
    @for (id of ids; track id) { <button type="button" (click)="buscar(id)">Pedido {{ id }}</button> }
    @if (pedido(); as p) { <p role="status">{{ p.cliente }}: {{ p.total }}</p> }
    @if (mensaje()) { <p role="alert">{{ mensaje() }}</p> }
  `,
})
class Demo {
  private readonly api = inject(PedidosApi);
  protected readonly ids = ['1', '404', '500', 'inestable'];
  protected readonly pedido = signal<Pedido | undefined>(undefined);
  protected readonly mensaje = signal('');
  protected buscar(id: string) {
    this.pedido.set(undefined); this.mensaje.set('');
    this.api.obtener(id).subscribe({
      next: (p) => this.pedido.set(p),
      error: (e: Error) => this.mensaje.set(e instanceof PedidoNoEncontrado ? 'Ese pedido no existe' : e instanceof SinConexion ? 'Sin conexión' : 'No pudimos cargar el pedido'),
    });
  }
}
export default [{ path: '', component: Demo }] satisfies Routes;
