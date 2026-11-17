import { Routes } from '@angular/router';
import { AlmacenPedidos } from './estado/almacen-pedidos';
import { DetallePedidoPagina } from './paginas/detalle-pedido.pagina';
import { ListaPedidosPagina } from './paginas/lista-pedidos.pagina';

export default [
  {
    path: '',
    providers: [AlmacenPedidos],                       // vive mientras la funcionalidad esté activa
    children: [
      { path: '', component: ListaPedidosPagina },
      { path: ':id', component: DetallePedidoPagina },
    ],
  },
] satisfies Routes;
