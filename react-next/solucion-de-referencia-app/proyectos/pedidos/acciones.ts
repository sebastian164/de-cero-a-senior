// ---- react-next/nivel-01-fundamentos/03-fundamentos-de-next/README.md (bloque 4), copiado tal cual entre el contexto agregado ----
// pedidos/acciones.ts
'use server';

import { revalidatePath } from 'next/cache';

export async function cancelarPedido(pedidoId: string) {
  const sesion = await exigirSesion();                 // una acción es un endpoint público: siempre autorizar
  await repositorio.cancelar(pedidoId, sesion.usuarioId);
  revalidatePath('/pedidos');
}

// Contexto (no exportado: un módulo 'use server' solo exporta acciones).
import { exigirSesion, repositorio } from './servidor';
