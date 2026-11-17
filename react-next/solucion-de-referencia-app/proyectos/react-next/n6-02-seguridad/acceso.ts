// ---- react-next/nivel-06-avanzado/02-seguridad-y-autenticacion/README.md (bloque 1), copiado tal cual entre el contexto agregado ----
// pedidos/datos/acceso.ts
import 'server-only';

export async function obtenerPedido(id: string): Promise<PedidoVisible | null> {
  const usuario = await obtenerUsuarioActual();            // memorizado por petición
  if (!usuario) return null;

  const pedido = await bd.pedido.findUnique({ where: { id } });
  if (!pedido) return null;
  if (pedido.clienteId !== usuario.id && !usuario.esAdministrador) return null;   // autorización por recurso

  return {                                                 // proyección: solo lo que puede salir
    id: pedido.id,
    estado: pedido.estado,
    total: pedido.total,
    lineas: pedido.lineas,
  };
}

import { obtenerUsuarioActual } from '@talleres/react-next/nivel-06-avanzado/02-seguridad-y-autenticacion/sesion';
import { bd } from './bd';

// Contexto: lo que puede salir hacia la interfaz.
export interface PedidoVisible { readonly id: string; readonly estado: string; readonly total: number; readonly lineas: readonly { producto: string; cantidad: number }[] }
