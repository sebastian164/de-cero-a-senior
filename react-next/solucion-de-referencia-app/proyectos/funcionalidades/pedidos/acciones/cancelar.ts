// ---- react-next/nivel-05-arquitectura/03-estructura-estado-y-arquitectura-limpia/README.md (bloque 5, desde '// pedidos/acciones/cancelar.ts'), copiado tal cual entre el contexto agregado ----
// pedidos/acciones/cancelar.ts — adaptador de entrada: delgado
'use server';
export async function cancelarPedido(pedidoId: string) {
  const usuario = await exigirUsuario();
  const resultado = await cancelarPedidoCasoDeUso(pedidoId, usuario.id);
  if (resultado.ok) updateTag(`pedidos-${usuario.id}`);   // Next.js 16: ver la nota de la sesión 02
  return resultado;
}

import { updateTag } from 'next/cache';
import { exigirUsuario } from '../../../nucleo/sesion';
import { cancelarPedidoCasoDeUso } from '../composicion';
