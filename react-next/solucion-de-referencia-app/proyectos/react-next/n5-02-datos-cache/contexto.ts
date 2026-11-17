// Contexto que los ejemplos de nivel-05/02 dan por supuesto: sesión de demostración, usuarios y pedidos en memoria.
import 'server-only';
import { cookies } from 'next/headers';
import { cacheLife, cacheTag } from 'next/cache';

export interface Usuario { readonly id: string; readonly nombre: string; readonly consulta: number }
export interface Pedido {
  readonly id: string; readonly clienteId: string; readonly descripcion: string;
  readonly estado: 'pendiente' | 'pagado' | 'cancelado'; readonly creadoEn: Date; readonly total: number;
}
export type Resultado = { ok: true } | { ok: false; error: string };

/** Cuántas veces se consultó de verdad el usuario: lo muestra la página para demostrar `cache` (taller, punto 5). */
let consultasDeUsuario = 0;
const USUARIOS: Record<string, string> = { 'u-1': 'Ana', 'u-2': 'Luis' };

export async function leerSesion(): Promise<{ usuarioId: string } | null> {
  const id = (await cookies()).get('usuario-demo')?.value;
  return id && USUARIOS[id] ? { usuarioId: id } : null;
}
export async function buscarUsuario(id: string): Promise<Usuario> {
  await new Promise((r) => setTimeout(r, 20));
  return { id, nombre: USUARIOS[id]!, consulta: ++consultasDeUsuario };
}

const hace = (horas: number) => new Date(Date.now() - horas * 3_600_000);
const pedidos = new Map<string, Pedido>([
  ['6f1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d', { id: '6f1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d', clienteId: 'u-1', descripcion: 'Café de Huila', estado: 'pendiente', creadoEn: hace(2), total: 32000 }],
  ['7a2c3d4e-5f6a-4b7c-9d8e-0f1a2b3c4d5e', { id: '7a2c3d4e-5f6a-4b7c-9d8e-0f1a2b3c4d5e', clienteId: 'u-1', descripcion: 'Chocolate de mesa', estado: 'pagado', creadoEn: hace(5), total: 9000 }],
  ['8b3d4e5f-6a7b-4c8d-8e9f-1a2b3c4d5e6f', { id: '8b3d4e5f-6a7b-4c8d-8e9f-1a2b3c4d5e6f', clienteId: 'u-1', descripcion: 'Molino manual (hace 3 días)', estado: 'pagado', creadoEn: hace(72), total: 85000 }],
  ['9c4e5f6a-7b8c-4d9e-9f0a-2b3c4d5e6f7a', { id: '9c4e5f6a-7b8c-4d9e-9f0a-2b3c4d5e6f7a', clienteId: 'u-2', descripcion: 'Prensa francesa', estado: 'pendiente', creadoEn: hace(1), total: 64000 }],
]);

export async function buscarPedido(id: string): Promise<Pedido | null> {
  return pedidos.get(id) ?? null;
}
export async function marcarCancelado(id: string): Promise<void> {
  const p = pedidos.get(id);
  if (p) pedidos.set(id, { ...p, estado: 'cancelado' });
}
export const sePuedeCancelar = (pedido: Pedido, ahora: Date): boolean =>
  (pedido.estado === 'pendiente' || pedido.estado === 'pagado') && (ahora.getTime() - pedido.creadoEn.getTime()) / 3_600_000 <= 24;

/** Lectura en caché por usuario: el usuario va en los argumentos, así que forma parte de la clave; la etiqueta permite invalidar. */
export async function listarPedidosDe(usuarioId: string): Promise<readonly Pedido[]> {
  'use cache';
  cacheTag(`pedidos-${usuarioId}`);
  cacheLife('max');
  await new Promise((r) => setTimeout(r, 50));
  return [...pedidos.values()].filter((p) => p.clienteId === usuarioId && p.estado !== 'cancelado');
}
/** Para el taller: lo mismo sin caché, para comparar. */
export async function todosLosPedidos(): Promise<readonly Pedido[]> {
  return [...pedidos.values()];
}
