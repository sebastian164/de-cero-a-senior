// ---- react-next/nivel-02-solid/05-dip/README.md (bloque 4), copiado tal cual entre el contexto agregado ----
import { expect, it } from 'vitest';
import { agregarAlCarrito, type LineaCarrito } from './carrito';

const unProducto = () => ({ id: 'p1', nombre: 'Café', precio: 18000 });

it('bloque de prueba del README: agrega sin navegador ni proveedores', () => {
// En una prueba: sin navegador ni proveedores
const guardados: LineaCarrito[][] = [];
const lineas = agregarAlCarrito(unProducto(), {
  almacen: { leer: () => [], guardar: (l) => guardados.push(l) },
  analitica: { productoAgregado: () => {} },
  ahora: () => 1_700_000_000_000,
});
expect(lineas).toHaveLength(1);
});
