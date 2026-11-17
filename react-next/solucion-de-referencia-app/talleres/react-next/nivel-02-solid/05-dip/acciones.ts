'use server';
// Adaptador de entrada delgado: autentica (simulado), delega en el caso de uso ya compuesto y revalida.
import { revalidatePath } from 'next/cache';
import { cancelarPedido } from './composicion';

export async function cancelarDesdeFormulario(_previo: string, datos: FormData): Promise<string> {
  const usuarioId = 'u-1';
  const id = String(datos.get('pedidoId') ?? '');
  const r = await cancelarPedido(id, usuarioId);
  revalidatePath('/n2/05-dip');
  return r.ok ? `Pedido ${id} cancelado` : r.error;
}
