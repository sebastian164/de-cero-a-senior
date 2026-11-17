import { inject, InjectionToken, Provider } from '@angular/core';
import { RepositorioSolicitudes } from '../dominio/puertos';
import { SolicitudesEnMemoria, SolicitudesHttp } from './solicitudes-http';

export type ModoDatos = 'http' | 'memoria';
/** Configuración: se lee de `?datos=memoria` en el navegador; por defecto, el servidor. */
export const MODO_DATOS = new InjectionToken<ModoDatos>('MODO_DATOS', {
  providedIn: 'root',
  factory: () => (typeof location !== 'undefined' && new URLSearchParams(location.search).get('datos') === 'memoria' ? 'memoria' : 'http'),
});

export const provideSolicitudes = (): Provider[] => [
  SolicitudesHttp, SolicitudesEnMemoria,
  { provide: RepositorioSolicitudes, useFactory: () => (inject(MODO_DATOS) === 'memoria' ? inject(SolicitudesEnMemoria) : inject(SolicitudesHttp)) },
];
