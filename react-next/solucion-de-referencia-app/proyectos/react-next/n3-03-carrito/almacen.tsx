'use client';
// ---- react-next/nivel-03-patrones/03-contexto-reductores-y-estado/README.md (bloque 3), copiado tal cual entre el contexto agregado ----
import { create } from 'zustand';
import { reductorCarrito, type EstadoCarrito, type Producto } from './reductor';

interface Acciones { agregar(producto: Producto): void; quitar(productoId: string): void; vaciar(): void }

const useAlmacenCarrito = create<EstadoCarrito & Acciones>()((set) => ({
  lineas: [],
  agregar: (producto) => set((estado) => reductorCarrito(estado, { tipo: 'agregado', producto })),
  quitar: (productoId) => set((estado) => reductorCarrito(estado, { tipo: 'quitado', productoId })),
  vaciar: () => set({ lineas: [] }),
}));
export function ContadorCarrito() {   // contexto: el selector se usa dentro de un componente
  const cantidad = useAlmacenCarrito((estado) => estado.lineas.length);   // solo se renderiza si cambia esto
  return <span>{cantidad}</span>;
}   // solo se renderiza si cambia esto

export { useAlmacenCarrito };
