import { Routes } from '@angular/router';
import { Cuadre, Descuadre, Detalle, Inicio, NoEncontrada, Privado, Visitas } from './paginas';

export const routes: Routes = [
  { path: '', component: Inicio },
  { path: 'producto/:id', component: Detalle },
  { path: 'producto-csr/:id', component: Detalle },     // misma pantalla, renderizada solo en el cliente (para medir LCP)
  { path: 'privado', component: Privado },
  { path: 'descuadre', component: Descuadre },
  { path: 'cuadre', component: Cuadre },
  { path: 'visitas', component: Visitas },
  { path: '**', component: NoEncontrada },
];
