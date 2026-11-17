// Contexto (nivel-06/02): la lectura de la lista también pasa por la capa de acceso (mismo módulo lógico que obtenerPedido).
import 'server-only';
import { obtenerUsuarioActual } from '@talleres/react-next/nivel-06-avanzado/02-seguridad-y-autenticacion/sesion';
import { bd } from './bd';
import type { PedidoVisible } from './acceso';

export async function listarPedidosVisibles(): Promise<readonly PedidoVisible[]> {
  const usuario = await obtenerUsuarioActual();
  if (!usuario) return [];
  const filas = await bd.pedido.findMany({ where: usuario.esAdministrador ? {} : { clienteId: usuario.id } });
  return filas.map((p) => ({ id: p.id, estado: p.estado, total: p.total, lineas: p.lineas }));   // proyección
}
