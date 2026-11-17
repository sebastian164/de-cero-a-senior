// Punto 4: el mapa de tipo a componente del README, con un tipo nuevo ('envio') registrado.
import type React from 'react';

export type NotificacionAmpliada =
  | { tipo: 'pedido'; id: string; numero: string }
  | { tipo: 'pago'; id: string; valor: number }
  | { tipo: 'alerta'; id: string; texto: string }
  | { tipo: 'envio'; id: string; guia: string; transportadora: string };

type Vistas = { [T in NotificacionAmpliada['tipo']]: React.ComponentType<{ datos: Extract<NotificacionAmpliada, { tipo: T }> }> };

const VISTAS: Vistas = {
  pedido: ({ datos }) => <p>Nuevo pedido {datos.numero}</p>,
  pago: ({ datos }) => <p>Pago recibido: {datos.valor}</p>,
  alerta: ({ datos }) => <p>Alerta: {datos.texto}</p>,
  envio: ({ datos }) => <p>Envío {datos.guia} con {datos.transportadora}</p>,
};

export function VistaNotificacionAmpliada({ notificacion }: { notificacion: NotificacionAmpliada }) {
  const Vista = VISTAS[notificacion.tipo] as React.ComponentType<{ datos: NotificacionAmpliada }>;
  return <Vista datos={notificacion} />;
}
