import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ConteoDePedidos } from './servicios';

// 4. Depende de un contrato de un solo método.
@Component({ selector: 'app-contador-pedidos', template: `<p>Tienes {{ total() }} pedidos</p>` })
export class ContadorPedidosComponent {
  protected readonly total = toSignal(inject(ConteoDePedidos).delUsuarioActual(), { initialValue: 0 });
}
