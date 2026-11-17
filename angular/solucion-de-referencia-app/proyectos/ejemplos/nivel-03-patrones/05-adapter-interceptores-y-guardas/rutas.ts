import { provideHttpClient, withInterceptors, withRequestsMadeViaParent } from '@angular/common/http';
import { Routes } from '@angular/router';
import { pedidoDeLaRuta, requiereRol, requiereSesion } from './guardas';
import { interceptorDeAutenticacion, interceptorDeErrores } from './interceptores';
import { ListaPedidos } from './pantallas';

// provideHttpClient(withInterceptors([...])) del README, aquí en el inyector de la ruta.
// withRequestsMadeViaParent() encadena con el HttpClient de la raíz (que antepone /api/n3-05).
export default [
  {
    path: '',
    providers: [provideHttpClient(withInterceptors([interceptorDeAutenticacion, interceptorDeErrores]), withRequestsMadeViaParent())],
    children: [
      { path: '', component: ListaPedidos },
      { path: 'admin', canActivate: [requiereSesion, requiereRol('admin')], loadChildren: () => import('./admin-rutas') },
      { path: 'pedidos/:id', resolve: { pedido: pedidoDeLaRuta }, loadComponent: () => import('./pantallas').then((m) => m.DetallePedido) },
    ],
  },
] satisfies Routes;
