import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AlmacenPedidos } from '../estado/almacen-pedidos';

@Component({
  imports: [RouterLink],
  template: `<h2>Pedido {{ id() }}</h2><p>Almacén <code>{{ almacen.instancia }}</code> · {{ pedido()?.lineas?.length ?? 0 }} líneas</p><a routerLink="..">Volver</a>`,
})
export class DetallePedidoPagina {
  protected readonly almacen = inject(AlmacenPedidos);
  readonly id = input.required<string>();
  protected readonly pedido = computed(() => this.almacen.pedidos().find((p) => p.id === this.id()));
  constructor() { if (!this.almacen.pedidos().length) this.almacen.cargar(); }
}
