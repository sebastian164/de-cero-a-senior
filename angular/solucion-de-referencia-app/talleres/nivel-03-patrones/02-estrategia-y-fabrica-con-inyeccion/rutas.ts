import { Routes } from '@angular/router';
import { provideDocumentos } from './documentos';
import { PaginaDocumentos } from './pagina.component';
import { VALIDADOR_CHILE } from './pais-nuevo';

export default [
  { path: '', component: PaginaDocumentos, providers: [provideDocumentos({ paises: ['CO', 'AR'], extra: [VALIDADOR_CHILE] })] },
] satisfies Routes;
