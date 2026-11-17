// ---- react-next/nivel-03-patrones/03-contexto-reductores-y-estado/README.md (bloque 1), copiado tal cual entre el contexto agregado ----
export interface Producto { readonly id: string; readonly nombre: string; readonly precio: number }
export interface Linea { readonly productoId: string; readonly nombre: string; readonly precio: number; readonly cantidad: number }

// carrito/reductor.ts — puro, sin React
export interface EstadoCarrito {
  readonly lineas: readonly Linea[];
}

export type AccionCarrito =
  | { tipo: 'agregado'; producto: Producto }
  | { tipo: 'cantidadCambiada'; productoId: string; cantidad: number }
  | { tipo: 'quitado'; productoId: string }
  | { tipo: 'vaciado' };

export function reductorCarrito(estado: EstadoCarrito, accion: AccionCarrito): EstadoCarrito {
  switch (accion.tipo) {
    case 'agregado': {
      const existente = estado.lineas.find((l) => l.productoId === accion.producto.id);
      return {
        lineas: existente
          ? estado.lineas.map((l) => (l === existente ? { ...l, cantidad: l.cantidad + 1 } : l))
          : [...estado.lineas, { productoId: accion.producto.id, nombre: accion.producto.nombre, precio: accion.producto.precio, cantidad: 1 }],
      };
    }
    case 'cantidadCambiada':
      return {
        lineas: accion.cantidad <= 0
          ? estado.lineas.filter((l) => l.productoId !== accion.productoId)
          : estado.lineas.map((l) => (l.productoId === accion.productoId ? { ...l, cantidad: accion.cantidad } : l)),
      };
    case 'quitado':
      return { lineas: estado.lineas.filter((l) => l.productoId !== accion.productoId) };
    case 'vaciado':
      return { lineas: [] };
  }
}

export const totalCarrito = (estado: EstadoCarrito) =>
  estado.lineas.reduce((suma, l) => suma + l.precio * l.cantidad, 0);
