// Punto 4: se agrega 'envio' a la unión del README sin registrar su vista. Record<...> obliga a registrarla: no compila.
import type React from 'react';

type Notificacion =
  | { tipo: 'pedido'; id: string }
  | { tipo: 'pago'; id: string }
  | { tipo: 'alerta'; id: string }
  | { tipo: 'envio'; id: string; guia: string };

const NotificacionPedido = () => null;
const NotificacionPago = () => null;
const NotificacionAlerta = () => null;

export const VISTAS: Record<Notificacion['tipo'], React.ComponentType<{ datos: any }>> = {
  pedido: NotificacionPedido,
  pago: NotificacionPago,
  alerta: NotificacionAlerta,
};
