// Punto 5: el archivo js-original/inventario.js convertido a TypeScript estricto, sin any, con los errores corregidos.
export interface Producto {
  readonly sku: string;
  readonly nombre: string;
  readonly precio: number;
  readonly existencias: number;
  readonly categoria: Categoria;
}
export type Categoria = 'bebidas' | 'panaderia';

export const PRODUCTOS: readonly Producto[] = [
  { sku: 'CAF-01', nombre: 'Café', precio: 18000, existencias: 12, categoria: 'bebidas' },
  { sku: 'TE-02', nombre: 'Té', precio: 9000, existencias: 0, categoria: 'bebidas' },
  { sku: 'PAN-03', nombre: 'Pan', precio: 3500, existencias: 40, categoria: 'panaderia' },
];

export const buscar = (productos: readonly Producto[], sku: string): Producto | undefined =>
  productos.find((p) => p.sku === sku);

export function precioConIva(productos: readonly Producto[], sku: string): number | null {
  const producto = buscar(productos, sku);
  return producto ? Math.round(producto.precio * 1.19) : null;
}

export const valorInventario = (productos: readonly Producto[]): number =>
  productos.reduce((total, p) => total + p.precio * p.existencias, 0);

export const agotados = (productos: readonly Producto[]): Producto[] =>
  productos.filter((p) => p.existencias === 0);

export type ResultadoDescuento =
  | { ok: true; productos: readonly Producto[] }
  | { ok: false; motivo: 'no-existe' | 'sin-existencias' };

/** Inmutable: devuelve el inventario nuevo en lugar de modificar el producto en el sitio. */
export function descontar(productos: readonly Producto[], sku: string, cantidad: number): ResultadoDescuento {
  const producto = buscar(productos, sku);
  if (!producto) return { ok: false, motivo: 'no-existe' };
  if (producto.existencias < cantidad) return { ok: false, motivo: 'sin-existencias' };
  return {
    ok: true,
    productos: productos.map((p) => (p === producto ? { ...p, existencias: p.existencias - cantidad } : p)),
  };
}

export function resumenPorCategoria(productos: readonly Producto[]): Record<Categoria, number> {
  const resumen: Record<Categoria, number> = { bebidas: 0, panaderia: 0 };
  for (const p of productos) resumen[p.categoria] += p.existencias;
  return resumen;
}

export function etiqueta(productos: readonly Producto[], sku: string): string | null {
  const producto = buscar(productos, sku);
  return producto ? `${producto.nombre.toUpperCase()} - $` + producto.precio.toFixed() : null;
}
