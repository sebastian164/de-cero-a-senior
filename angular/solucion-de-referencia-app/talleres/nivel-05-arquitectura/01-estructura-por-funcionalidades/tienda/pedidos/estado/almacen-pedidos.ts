import { inject, Injectable, signal } from '@angular/core';
import { Pedido } from '../dominio/pedido';
import { PedidosApi } from '../datos/pedidos.api';

/** Sin `providedIn: 'root'`: se provee en las rutas de la funcionalidad y vive mientras esté activa. */
@Injectable()
export class AlmacenPedidos {
  private readonly api = inject(PedidosApi);
  readonly pedidos = signal<readonly Pedido[]>([]);
  readonly instancia = Math.random().toString(36).slice(2, 8);
  cargar() { this.api.listar().subscribe((p) => this.pedidos.set(p)); }
}
