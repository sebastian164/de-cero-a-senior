// Reglas puras del carrito.
export interface Linea { readonly productoId: number; readonly nombre: string; readonly precio: number; readonly cantidad: number; }
export const TARIFA_IVA = 0.19;
export const subtotalDe = (lineas: readonly Linea[]) => lineas.reduce((s, l) => s + l.precio * l.cantidad, 0);
export const ivaDe = (subtotal: number) => Math.round(subtotal * TARIFA_IVA);
export const cantidadDe = (lineas: readonly Linea[]) => lineas.reduce((s, l) => s + l.cantidad, 0);
export const agregarLinea = (lineas: readonly Linea[], nueva: Omit<Linea, 'cantidad'>): readonly Linea[] =>
  lineas.some((l) => l.productoId === nueva.productoId)
    ? lineas.map((l) => (l.productoId === nueva.productoId ? { ...l, cantidad: l.cantidad + 1 } : l))
    : [...lineas, { ...nueva, cantidad: 1 }];
