// Archivo JavaScript original (50 líneas) que se convierte a TypeScript estricto en ../inventario.ts.
// Se conserva tal cual para que `tsc --checkJs --strict` muestre los errores reales que esconde.

const productos = [
  { sku: 'CAF-01', nombre: 'Café', precio: 18000, existencias: 12, categoria: 'bebidas' },
  { sku: 'TE-02', nombre: 'Té', precio: 9000, existencias: 0, categoria: 'bebidas' },
  { sku: 'PAN-03', nombre: 'Pan', precio: 3500, existencias: 40, categoria: 'panaderia' },
];

function buscar(sku) {
  return productos.find((p) => p.sku === sku);
}

function precioConIva(sku) {
  const producto = buscar(sku);
  return producto.precio * 1.19;              // si no existe: TypeError en ejecución
}

function valorInventario() {
  let total = 0;
  for (const p of productos) {
    total += p.precio * p.existencia;           // 'existencia' no existe: NaN silencioso
  }
  return total;
}

function agotados() {
  return productos.filter((p) => p.existencias = 0);   // asignación en lugar de comparación
}

function descontar(sku, cantidad) {
  const producto = buscar(sku);
  if (!producto) throw new Error('No existe ' + sku);
  if (producto.existencias < cantidad) return false;
  producto.existencias -= cantidad;
  return true;
}

function resumenPorCategoria() {
  const resumen = {};
  for (const p of productos) {
    resumen[p.categoria] = (resumen[p.categoria] || 0) + p.existencias;
  }
  return resumen;
}

function etiqueta(sku) {
  const producto = buscar(sku);
  return producto.nombre.toUpperCase() + ' - $' + producto.precio.toFixed();
}

module.exports = { buscar, precioConIva, valorInventario, agotados, descontar, resumenPorCategoria, etiqueta, descontar2: descontar('CAF-01') };
