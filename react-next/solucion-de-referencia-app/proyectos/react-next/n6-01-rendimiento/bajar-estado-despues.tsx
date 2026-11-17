// ---- react-next/nivel-06-avanzado/01-rendimiento/README.md (bloque 1, desde '// Después'), copiado tal cual entre el contexto agregado ----
// Después: el estado vive donde se usa; la tabla no se entera
function Pagina() {
  return (<><Buscador /><TablaPesada /></>);
}

import { Buscador, TablaPesada } from './contexto';
export { Pagina };
