'use client';
// Demostraciones de cliente de nivel-04/01: el panel necesita una función de carga, que no puede venir de un componente de servidor.
import { useCallback } from 'react';
import { PanelPedidos } from '@talleres/react-next/nivel-04-clean-code-y-testing/01-clean-code-en-react/despues/panel-pedidos';
import { PanelPedidosAntes } from '@talleres/react-next/nivel-04-clean-code-y-testing/01-clean-code-en-react/antes/panel-pedidos';
import { PanelConReloj } from '@talleres/react-next/nivel-04-clean-code-y-testing/01-clean-code-en-react/memorizacion';

const PEDIDOS = [
  { id: 'p1', cliente: 'Ana Gómez', total: 120000, estado: 'pendiente' as const },
  { id: 'p2', cliente: 'Luis Pérez', total: 45000, estado: 'pagado' as const },
  { id: 'p3', cliente: 'Ana Ríos', total: 9000, estado: 'pendiente' as const },
];

export function DemoPanel({ version }: { version: 'antes' | 'despues' }) {
  const cargar = useCallback(async () => PEDIDOS.map((p) => ({ ...p })), []);
  return version === 'antes'
    ? <PanelPedidosAntes cargar={cargar} usuario={{ rol: 'admin' }} />
    : <PanelPedidos cargar={cargar} usuario={{ rol: 'admin' }} />;
}
export function DemoReloj() {
  return <><PanelConReloj memorizar={false} /><PanelConReloj memorizar /></>;
}
