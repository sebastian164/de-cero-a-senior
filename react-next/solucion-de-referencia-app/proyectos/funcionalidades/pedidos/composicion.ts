// ---- react-next/nivel-05-arquitectura/03-estructura-estado-y-arquitectura-limpia/README.md (bloque 5, desde '// pedidos/composicion.ts'), copiado tal cual entre el contexto agregado ----
// pedidos/composicion.ts — ensamblaje, solo servidor
import 'server-only';
export const cancelarPedidoCasoDeUso = crearCancelarPedido(repositorioPostgres, () => new Date());

import { crearCancelarPedido } from './aplicacion/cancelar-pedido';
import { repositorioPostgres } from './datos/repositorio-postgres';
