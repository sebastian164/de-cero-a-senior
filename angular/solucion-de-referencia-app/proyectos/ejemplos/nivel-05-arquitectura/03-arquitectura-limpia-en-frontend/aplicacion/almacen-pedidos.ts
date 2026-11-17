import { inject, Injectable, signal } from '@angular/core';
import { Pedido } from '../dominio/pedido';
import { RepositorioPedidos } from '../dominio/puertos';
import { CancelarPedido } from './cancelar-pedido';

@Injectable()
export class AlmacenPedidos {
  private readonly repositorio = inject(RepositorioPedidos);
  private readonly cancelarPedido = inject(CancelarPedido);
  readonly pedidos = signal<readonly Pedido[]>([]);
  readonly mensaje = signal('');
  cargar() { this.repositorio.listar().subscribe((p) => this.pedidos.set(p)); }
  cancelar(pedido: Pedido) {
    this.cancelarPedido.ejecutar(pedido).subscribe({
      next: () => { this.mensaje.set(`Pedido ${pedido.id} cancelado`); this.cargar(); },
      error: (e: Error) => this.mensaje.set(e.message),
    });
  }
}
