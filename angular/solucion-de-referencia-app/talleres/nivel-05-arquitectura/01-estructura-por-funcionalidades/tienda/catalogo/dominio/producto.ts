export interface Producto { readonly id: string; readonly nombre: string; readonly precio: number; }
export const precioConIva = (p: Producto, iva = 0.19) => Math.round(p.precio * (1 + iva));
