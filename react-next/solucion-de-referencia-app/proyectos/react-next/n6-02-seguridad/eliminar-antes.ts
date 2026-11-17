// ---- react-next/nivel-06-avanzado/02-seguridad-y-autenticacion/README.md (bloque 2, desde '// Antes'), copiado tal cual entre el contexto agregado ----
// Antes: confía en que la interfaz solo ofrece los pedidos propios
'use server';
export async function eliminarPedido(id: string) {
  await bd.pedido.delete({ where: { id } });               // cualquiera puede borrar cualquier pedido
}

import { bd } from './bd';
