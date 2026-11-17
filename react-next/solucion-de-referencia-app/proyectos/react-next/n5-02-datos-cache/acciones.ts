// ---- react-next/nivel-05-arquitectura/02-datos-cache-y-mutaciones/README.md (bloque 2), copiado tal cual entre el contexto agregado ----
'use server';

export async function cancelarPedido(pedidoId: string): Promise<Resultado> {
  const usuario = await obtenerUsuarioActual();
  if (!usuario) return { ok: false, error: 'Debes iniciar sesión' };

  const id = z.string().uuid().safeParse(pedidoId);
  if (!id.success) return { ok: false, error: 'Pedido inválido' };

  const pedido = await buscarPedido(id.data);
  if (!pedido || pedido.clienteId !== usuario.id) return { ok: false, error: 'Pedido no encontrado' };
  if (!sePuedeCancelar(pedido, new Date())) return { ok: false, error: 'El pedido ya no se puede cancelar' };

  await marcarCancelado(pedido.id);
  updateTag(`pedidos-${usuario.id}`);                   // quien muta ve su cambio en la siguiente lectura
  return { ok: true };
}

import { z } from 'zod';
import { updateTag } from 'next/cache';
import { obtenerUsuarioActual } from './usuario-actual';
import { buscarPedido, marcarCancelado, sePuedeCancelar, type Resultado } from './contexto';
