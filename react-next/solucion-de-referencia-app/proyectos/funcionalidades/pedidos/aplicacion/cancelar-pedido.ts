// ---- react-next/nivel-05-arquitectura/03-estructura-estado-y-arquitectura-limpia/README.md (bloque 5, desde '// pedidos/aplicacion/cancelar-pedido.ts'), copiado tal cual entre el contexto agregado ----
import { sePuedeCancelar, type Resultado } from '../dominio/pedido';
import type { RepositorioPedidos } from '../dominio/puertos';

// pedidos/aplicacion/cancelar-pedido.ts — caso de uso: recibe sus dependencias
export const crearCancelarPedido = (repositorio: RepositorioPedidos, reloj: () => Date) =>
  async (pedidoId: string, usuarioId: string): Promise<Resultado> => {
    const pedido = await repositorio.buscar(pedidoId);
    if (!pedido || pedido.clienteId !== usuarioId) return { ok: false, error: 'Pedido no encontrado' };
    if (!sePuedeCancelar(pedido, reloj())) return { ok: false, error: 'El pedido ya no se puede cancelar' };
    await repositorio.guardar({ ...pedido, estado: 'cancelado' });
    return { ok: true };
  };
