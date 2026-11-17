// Primer paso de la conversión: solo se agregan tipos; la lógica es la del JavaScript original.
// Se compila con strict para listar los errores reales (ver ANALISIS.md). No debe compilar.
interface Producto {
  readonly sku: string;
  readonly nombre: string;
  readonly precio: number;
  readonly existencias: number;
  readonly categoria: 'bebidas' | 'panaderia';
}

const productos: Producto[] = [
  { sku: 'CAF-01', nombre: 'Café', precio: 18000, existencias: 12, categoria: 'bebidas' },
  { sku: 'TE-02', nombre: 'Té', precio: 9000, existencias: 0, categoria: 'bebidas' },
  { sku: 'PAN-03', nombre: 'Pan', precio: 3500, existencias: 40, categoria: 'panaderia' },
];

function buscar(sku: string) {
  return productos.find((p) => p.sku === sku);
}

export function precioConIva(sku: string): number {
  const producto = buscar(sku);
  return producto.precio * 1.19;
}

export function valorInventario(): number {
  let total = 0;
  for (const p of productos) {
    total += p.precio * p.existencia;
  }
  return total;
}

export function agotados(): Producto[] {
  return productos.filter((p) => p.existencias = 0);
}

export function descontar(sku: string, cantidad: number): boolean {
  const producto = buscar(sku);
  if (!producto) throw new Error('No existe ' + sku);
  if (producto.existencias < cantidad) return false;
  producto.existencias -= cantidad;
  return true;
}

export function resumenPorCategoria() {
  const resumen = {};
  for (const p of productos) {
    resumen[p.categoria] = (resumen[p.categoria] || 0) + p.existencias;
  }
  return resumen;
}

export function etiqueta(sku: string): string {
  const producto = buscar(sku);
  return producto.nombre.toUpperCase() + ' - $' + producto.precio.toFixed();
}

export const descontar2 = descontar('CAF-01');
