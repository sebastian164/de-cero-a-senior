// Taller nivel-05/02, punto 6: una fuga entre usuarios por una clave de caché mal elegida, y su corrección.
import 'server-only';
import { cacheLife, cacheTag } from 'next/cache';
import { fijarUsuarioOculto, usuarioOculto } from '@app/origen-cache';

const PEDIDOS: Record<string, string[]> = { 'u-1': ['Café de Huila', 'Chocolate'], 'u-2': ['Prensa francesa'] };

/** MAL: el usuario entra por una vía que no es argumento, así que la clave de caché es la misma para todos. */
export async function pedidosMal() {
  'use cache';
  cacheLife('max');
  return PEDIDOS[usuarioOculto() ?? ''] ?? [];
}
/** BIEN: el usuario es argumento (forma parte de la clave) y la etiqueta lo incluye. */
export async function pedidosBien(usuarioId: string) {
  'use cache';
  cacheTag(`pedidos-${usuarioId}`);
  cacheLife('max');
  return PEDIDOS[usuarioId] ?? [];
}
export { fijarUsuarioOculto };
