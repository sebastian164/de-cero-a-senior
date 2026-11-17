'use client';
// Proveedores de cliente envueltos en un componente propio (README nivel-05/01): el diseño los usa y pasa la
// aplicación como children, así el contenido sigue pudiendo ser de servidor.
import { useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ProveedorCarritoProducto } from '@/react-next/n5-01-servidor-cliente/acciones-carrito';

export function Proveedores({ children }: { children: React.ReactNode }) {
  const [cliente] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: 1 } } }));
  return <QueryClientProvider client={cliente}><ProveedorCarritoProducto>{children}</ProveedorCarritoProducto></QueryClientProvider>;
}
