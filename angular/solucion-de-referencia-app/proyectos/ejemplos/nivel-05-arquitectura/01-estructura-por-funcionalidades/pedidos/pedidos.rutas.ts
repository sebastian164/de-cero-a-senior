import { Component, inject, Injectable, signal } from '@angular/core';
import { ActivatedRoute, RouterLink, Routes } from '@angular/router';

@Injectable()
export class AlmacenPedidos { readonly visitas = signal(0); readonly id = Math.random().toString(36).slice(2, 7); }

@Component({ imports: [RouterLink], template: `<h2>Lista de pedidos</h2><p>almacén {{ almacen.id }}</p><a routerLink="42">Pedido 42</a>` })
class ListaPedidosPagina { protected readonly almacen = inject(AlmacenPedidos); }
@Component({ imports: [RouterLink], template: `<h2>Pedido {{ ruta.snapshot.paramMap.get('id') }}</h2><p>almacén {{ almacen.id }}</p><a routerLink="..">Volver</a>` })
class DetallePedidoPagina { protected readonly almacen = inject(AlmacenPedidos); protected readonly ruta = inject(ActivatedRoute); }

// pedidos/pedidos.rutas.ts (README)
export default [
  {
    path: '',
    providers: [AlmacenPedidos],                       // una instancia para toda la funcionalidad
    children: [
      { path: '', component: ListaPedidosPagina },
      { path: ':id', component: DetallePedidoPagina },
    ],
  },
] satisfies Routes;
