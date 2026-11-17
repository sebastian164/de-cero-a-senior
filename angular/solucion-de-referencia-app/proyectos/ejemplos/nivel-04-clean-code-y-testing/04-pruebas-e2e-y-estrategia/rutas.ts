import { Routes } from '@angular/router';
import { Carrito, Catalogo, Marco } from './tienda';

export default [{
  path: '', component: Marco, children: [
    { path: '', pathMatch: 'full', redirectTo: 'catalogo' },
    { path: 'catalogo', component: Catalogo },
    { path: 'carrito', component: Carrito },
  ],
}] satisfies Routes;
