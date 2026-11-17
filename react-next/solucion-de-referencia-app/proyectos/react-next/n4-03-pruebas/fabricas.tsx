// Contexto: fábricas de datos y renderConProveedores que usan las pruebas del README.
import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { Linea, Producto } from '../n3-03-carrito/reductor';
import type { PedidoLista } from './lista-pedidos';

export const unProducto = (p: Partial<Producto> = {}): Producto => ({ id: 'p1', nombre: 'Café', precio: 18000, ...p });
export const unaLinea = (l: Partial<Linea> = {}): Linea => ({ productoId: 'p1', nombre: 'Café', precio: 18000, cantidad: 1, ...l });
export const unPedido = (p: Partial<PedidoLista> = {}): PedidoLista => ({ id: 'p1', cliente: 'Ana', total: 18000, ...p });

export const aFormData = (datos: Record<string, string>) => {
  const f = new FormData();
  for (const [k, v] of Object.entries(datos)) f.set(k, v);
  return f;
};

export function renderConProveedores(ui: React.ReactElement) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={cliente}>{ui}</QueryClientProvider>);
}
