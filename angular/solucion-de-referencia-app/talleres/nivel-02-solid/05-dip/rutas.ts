import { Routes } from '@angular/router';
import { PaginaDip } from './pagina.component';
import { proveedoresEnMemoria, proveedoresNavegador, Recordatorios } from './recordatorios';

// 4. Implementación distinta para una sola ruta: /n2-05/taller/demo usa el registro en memoria.
//    Recordatorios se vuelve a proveer en cada ruta para que tome los contratos del inyector de esa ruta.
export default [
  { path: '', component: PaginaDip, providers: [...proveedoresNavegador, Recordatorios] },
  { path: 'demo', component: PaginaDip, providers: [...proveedoresEnMemoria, Recordatorios] },
] satisfies Routes;
