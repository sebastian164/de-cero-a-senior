// Versión ANTES: todo importado de forma estática; sirve solo para medir el paquete inicial.
import { Component } from '@angular/core';
import { RouterOutlet, Routes } from '@angular/router';
import adminRutas from './admin/admin.rutas';
import catalogoRutas from './catalogo/catalogo.rutas';
import { GraficaVentasConectada } from './admin/paginas/grafica-ventas-conectada';
import { Inicio } from './inicio';
import pedidosRutas from './pedidos/pedidos.rutas';

@Component({ imports: [RouterOutlet], template: `<router-outlet />` })
class Marco {}

export default [{
  path: '', component: Marco, children: [
    { path: '', component: Inicio },
    { path: 'catalogo', children: catalogoRutas },
    { path: 'pedidos', children: pedidosRutas },
    { path: 'admin', children: adminRutas },
    { path: 'grafica', component: GraficaVentasConectada },   // sin @defer: la gráfica y su serie quedan en el paquete inicial
  ],
}] satisfies Routes;
