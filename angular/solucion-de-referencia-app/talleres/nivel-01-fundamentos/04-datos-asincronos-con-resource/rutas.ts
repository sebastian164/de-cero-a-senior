import { Component } from '@angular/core';
import { Routes } from '@angular/router';
import { BuscadorRecursoComponent } from './buscador.component';
import { ContadorPedidosComponent, NombresPedidosComponent } from './compartido.component';
import { DetalleRecursoComponent } from './detalle.component';
import { ListaPedidosRecursoComponent } from './lista-pedidos.component';

@Component({
  imports: [ListaPedidosRecursoComponent, BuscadorRecursoComponent, ContadorPedidosComponent, NombresPedidosComponent],
  template: `<h1>Recursos</h1><app-lista-pedidos-recurso /><app-buscador-recurso /><app-contador-pedidos /><app-nombres-pedidos />`,
})
class Pagina {}

export default [
  { path: '', component: Pagina },
  { path: 'pedidos/:id', component: DetalleRecursoComponent },
] satisfies Routes;
