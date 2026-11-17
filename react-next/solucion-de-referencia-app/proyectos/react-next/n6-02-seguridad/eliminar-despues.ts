// ---- react-next/nivel-06-avanzado/02-seguridad-y-autenticacion/README.md (bloque 2, desde '// Después'), copiado tal cual entre el contexto agregado ----
// Después
'use server';
export async function eliminarPedido(entrada: unknown): Promise<Resultado> {
  const usuario = await obtenerUsuarioActual();
  if (!usuario) return { ok: false, error: 'No autenticado' };

  const id = z.string().uuid().safeParse(entrada);
  if (!id.success) return { ok: false, error: 'Solicitud inválida' };

  const pedido = await bd.pedido.findUnique({ where: { id: id.data } });
  if (!pedido || pedido.clienteId !== usuario.id) return { ok: false, error: 'Pedido no encontrado' };

  await bd.pedido.delete({ where: { id: pedido.id } });
  updateTag(`pedidos-${usuario.id}`);                      // Next.js 16: ver la nota de nivel-05/02
  return { ok: true };
}

import { z } from 'zod';
import { updateTag } from 'next/cache';
import { obtenerUsuarioActual } from '@talleres/react-next/nivel-06-avanzado/02-seguridad-y-autenticacion/sesion';
import { bd } from './bd';

type Resultado = { ok: true } | { ok: false; error: string };
