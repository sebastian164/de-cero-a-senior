import { Routes } from '@angular/router';
import { Cascada, DetallePedido, ListaPedidos, Marco, NuevoPedido } from './paginas';

export default [{
  path: '', component: Marco, children: [
    { path: '', component: ListaPedidos },
    { path: 'nuevo', component: NuevoPedido },
    { path: 'pedido/:id', component: DetallePedido },
    { path: 'cascada', component: Cascada },
  ],
}] satisfies Routes;
