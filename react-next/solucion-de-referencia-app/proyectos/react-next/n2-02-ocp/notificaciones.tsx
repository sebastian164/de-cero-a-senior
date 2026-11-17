// ---- react-next/nivel-02-solid/02-ocp/README.md (bloque 3), copiado tal cual entre el contexto agregado ----
import type React from 'react';

// Contexto: la unión y una vista por tipo.
export type Notificacion =
  | { tipo: 'pedido'; id: string; numero: string }
  | { tipo: 'pago'; id: string; valor: number }
  | { tipo: 'alerta'; id: string; texto: string };
const NotificacionPedido = ({ datos }: { datos: Extract<Notificacion, { tipo: 'pedido' }> }) => <p>Nuevo pedido {datos.numero}</p>;
const NotificacionPago = ({ datos }: { datos: Extract<Notificacion, { tipo: 'pago' }> }) => <p>Pago recibido: {datos.valor}</p>;
const NotificacionAlerta = ({ datos }: { datos: Extract<Notificacion, { tipo: 'alerta' }> }) => <p role="status">Alerta: {datos.texto}</p>;

const VISTAS: Record<Notificacion['tipo'], React.ComponentType<{ datos: any }>> = {
  pedido: NotificacionPedido,
  pago: NotificacionPago,
  alerta: NotificacionAlerta,
};

function VistaNotificacion({ notificacion }: { notificacion: Notificacion }) {
  const Vista = VISTAS[notificacion.tipo];
  return <Vista datos={notificacion} />;
}

export { VistaNotificacion, VISTAS };
