// Punto 4 del taller: el contrato de "Listado de pedidos con filtro por estado", escrito antes de programar, con simulacros que lo cumplen.
import { z } from 'zod';

export const Estado = z.enum(['pendiente', 'pagado', 'enviado', 'cancelado']);
export const Pedido = z.object({ id: z.string().min(1), estado: Estado, total: z.number().nonnegative(), creadoEn: z.iso.datetime() });
export const RespuestaListado = z.object({ items: z.array(Pedido), siguiente: z.string().nullable() });
export const ErrorApi = z.object({ codigo: z.enum(['no_autenticado', 'parametro_invalido', 'error_interno']), mensaje: z.string() });

/** Los simulacros son parte de la entrega: el frontend se programa contra ellos y el backend debe pasar las mismas pruebas. */
export const simulacros = {
  ok: { estado: 200, cuerpo: { items: [{ id: 'p-1', estado: 'pagado', total: 120.5, creadoEn: '2026-01-05T10:00:00Z' }], siguiente: 'abc' } },
  vacio: { estado: 200, cuerpo: { items: [], siguiente: null } },
  sinSesion: { estado: 401, cuerpo: { codigo: 'no_autenticado', mensaje: 'Inicia sesión' } },
  parametroInvalido: { estado: 400, cuerpo: { codigo: 'parametro_invalido', mensaje: 'estado desconocido' } },
  falla: { estado: 500, cuerpo: { codigo: 'error_interno', mensaje: 'Intenta de nuevo' } },
} as const;

export function responderSimulacro(estado: string | null): { estado: number; cuerpo: unknown } {
  if (estado !== null && !Estado.safeParse(estado).success) return simulacros.parametroInvalido;
  const todos = simulacros.ok.cuerpo.items;
  return { estado: 200, cuerpo: { items: estado ? todos.filter((p) => p.estado === estado) : todos, siguiente: null } };
}
