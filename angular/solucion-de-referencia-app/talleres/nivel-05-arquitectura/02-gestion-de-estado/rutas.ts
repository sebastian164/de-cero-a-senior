import { Routes } from '@angular/router';
import { CacheServidor } from './cache-servidor';
import Pagina from './pagina.component';

export default [{ path: '', component: Pagina, providers: [{ provide: CacheServidor, useFactory: () => new CacheServidor({ vigenciaMs: 30_000 }) }] }] satisfies Routes;
