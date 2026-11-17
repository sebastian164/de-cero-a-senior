// ---- react-next/nivel-03-patrones/04-capa-de-datos-y-adaptadores/README.md (bloque 1), copiado tal cual entre el contexto agregado ----
// pedidos/datos/esquema.ts — validar en la frontera
import { z } from 'zod';

const PedidoDto = z.object({
  id: z.number(),
  fecha_creacion: z.string(),
  cliente: z.object({ nombre_completo: z.string() }),
  estado: z.union([z.literal(1), z.literal(2), z.literal(3)]),
  valor_centavos: z.number(),
});

const ESTADOS = { 1: 'pendiente', 2: 'pagado', 3: 'cancelado' } as const;

// Adaptador
export const aPedido = (dto: z.infer<typeof PedidoDto>): Pedido => ({
  id: String(dto.id),
  creadoEn: new Date(dto.fecha_creacion),
  cliente: dto.cliente.nombre_completo,
  estado: ESTADOS[dto.estado],
  total: dto.valor_centavos / 100,
});

// pedidos/datos/pedidos.ts — funciones de acceso
export async function listarPedidos(filtro: FiltroPedidos, signal?: AbortSignal): Promise<Pedido[]> {
  const respuesta = await fetch(`${API}/pedidos?${new URLSearchParams(filtro)}`, { signal });
  if (!respuesta.ok) throw aErrorDeDominio(respuesta.status);
  return z.array(PedidoDto).parse(await respuesta.json()).map(aPedido);
}

// Contexto que el README da por supuesto.
import { aErrorDeDominio } from './errores';
export type FiltroPedidos = Record<string, string>;
export interface Pedido { readonly id: string; readonly creadoEn: Date; readonly cliente: string; readonly estado: 'pendiente' | 'pagado' | 'cancelado'; readonly total: number }
const API = typeof window === 'undefined' ? `http://127.0.0.1:${process.env.PORT ?? 49510}/api/n3-04` : '/api/n3-04';
export async function cancelarPedido(id: string): Promise<void> {
  const respuesta = await fetch(`${API}/pedidos/${id}`, { method: 'DELETE' });
  if (!respuesta.ok) throw aErrorDeDominio(respuesta.status);
}
export { PedidoDto };
