import { Routes } from '@angular/router';
import { Antes, Despues, Marco } from './paginas';

export default [{
  path: '', component: Marco, children: [
    { path: 'antes', component: Antes },
    { path: 'despues', component: Despues },
  ],
}] satisfies Routes;
