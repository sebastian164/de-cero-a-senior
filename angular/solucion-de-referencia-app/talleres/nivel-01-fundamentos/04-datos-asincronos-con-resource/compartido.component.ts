import { Component, inject } from '@angular/core';
import { ListaDePedidos } from './pedidos.service';

/** 7. Dos componentes que leen el mismo recurso de un servicio. */
@Component({
  selector: 'app-contador-pedidos',
  template: `@if (lista.recurso.hasValue()) { <p>Total de pedidos: {{ lista.recurso.value().length }}</p> }`,
})
export class ContadorPedidosComponent { protected readonly lista = inject(ListaDePedidos); }

@Component({
  selector: 'app-nombres-pedidos',
  template: `@if (lista.recurso.hasValue()) { <p>Clientes: @for (p of lista.recurso.value(); track p.id) { {{ p.cliente }}; }</p> }`,
})
export class NombresPedidosComponent { protected readonly lista = inject(ListaDePedidos); }
