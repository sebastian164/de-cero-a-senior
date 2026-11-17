// aplicacion/cancelar-pedido.ts — caso de uso
import { inject, Injectable, InjectionToken } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { Pedido, PedidoNoCancelable, sePuedeCancelar } from '../dominio/pedido';
import { RepositorioPedidos } from '../dominio/puertos';

/** El reloj es una dependencia más: la regla de las 24 horas se prueba con la hora que se quiera. */
export const RELOJ = new InjectionToken<() => number>('RELOJ', { providedIn: 'root', factory: () => () => Date.now() });

@Injectable()
export class CancelarPedido {
  private readonly repositorio = inject(RepositorioPedidos);
  private readonly ahora = inject(RELOJ);

  ejecutar(pedido: Pedido): Observable<void> {
    if (!sePuedeCancelar(pedido, new Date(this.ahora()))) {
      return throwError(() => new PedidoNoCancelable(pedido.id));
    }
    return this.repositorio.cancelar(pedido.id);
  }
}
