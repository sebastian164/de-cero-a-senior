import { Component } from '@angular/core';
import { Routes } from '@angular/router';

// Este taller se resuelve fuera de la aplicación: proyectos/nx-tienda (Nx) y proyectos/mfe (Native Federation).
// La pantalla resume qué se ejecutó y dónde.
const ARISTAS = [
  'shop -> feature-products', 'shop -> feature-product-detail',
  'feature-products -> data', 'feature-products -> shared-ui', 'feature-products -> models',
  'feature-product-detail -> data', 'feature-product-detail -> shared-ui', 'feature-product-detail -> models',
  'data -> models', 'shared-ui -> models', 'api -> products', 'api -> models', 'products -> models', 'shop-e2e -> shop',
];

@Component({
  selector: 'app-taller-n5-05',
  template: `
    <div>
      <h1>Monorepos y microfrontends</h1>
      <p>Espacio Nx: <code>proyectos/nx-tienda</code>. Anfitrión y remoto: <code>proyectos/mfe</code> (Native Federation).</p>
      <h2>Grafo de dependencias (nx graph)</h2>
      <ul>@for (a of aristas; track a) { <li>{{ a }}</li> }</ul>
    </div>
  `,
})
class Resumen { protected readonly aristas = ARISTAS; }

export default [{ path: '', component: Resumen }] satisfies Routes;
