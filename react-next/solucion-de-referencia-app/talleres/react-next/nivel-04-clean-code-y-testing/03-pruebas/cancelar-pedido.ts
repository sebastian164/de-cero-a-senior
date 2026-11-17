// Acción de servidor del taller (punto 4): los seis pasos del README de nivel-05/02, con dependencias inyectables para probarla.
import { z } from 'zod';

export interface PedidoRegistro { readonly id: string; readonly clienteId: string; readonly estado: 'pendiente' | 'pagado' | 'cancelado'; readonly creadoEn: Date }
export type Resultado = { ok: true } | { ok: false; error: string };
export interface Dependencias {
  usuarioActual(): Promise<{ id: string } | null>;
  buscar(id: string): Promise<PedidoRegistro | null>;
  marcarCancelado(id: string): Promise<void>;
  invalidar(etiqueta: string): void;
  ahora(): Date;
}

export const sePuedeCancelar = (p: PedidoRegistro, ahora: Date) =>
  p.estado !== 'cancelado' && ahora.getTime() - p.creadoEn.getTime() <= 24 * 3_600_000;

export const crearCancelarPedido = (d: Dependencias) => async (pedidoId: unknown): Promise<Resultado> => {
  const usuario = await d.usuarioActual();                                       // 1. autenticar
  if (!usuario) return { ok: false, error: 'Debes iniciar sesión' };
  const id = z.string().uuid().safeParse(pedidoId);                              // 2. validar
  if (!id.success) return { ok: false, error: 'Pedido inválido' };
  const pedido = await d.buscar(id.data);                                        // 3. autorizar
  if (!pedido || pedido.clienteId !== usuario.id) return { ok: false, error: 'Pedido no encontrado' };
  if (!sePuedeCancelar(pedido, d.ahora())) return { ok: false, error: 'El pedido ya no se puede cancelar' };
  await d.marcarCancelado(pedido.id);                                            // 4. ejecutar
  d.invalidar(`pedidos-${usuario.id}`);                                          // 5. invalidar
  return { ok: true };                                                           // 6. responder
};
