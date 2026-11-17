// ---- react-next/nivel-04-clean-code-y-testing/03-pruebas/README.md (bloque 1), copiado tal cual entre el contexto agregado ----
import { describe, expect, it } from 'vitest';
import { reductorCarrito } from '../n3-03-carrito/reductor';
import { unaLinea, unProducto } from './fabricas';

describe('reductorCarrito', () => {
  it('suma la cantidad cuando el producto ya está en el carrito', () => {
    const inicial = { lineas: [unaLinea({ productoId: 'p1', cantidad: 1 })] };
    const resultado = reductorCarrito(inicial, { tipo: 'agregado', producto: unProducto({ id: 'p1' }) });
    expect(resultado.lineas).toEqual([expect.objectContaining({ productoId: 'p1', cantidad: 2 })]);
  });

  it('no modifica el estado original', () => {
    const inicial = { lineas: [] };
    reductorCarrito(inicial, { tipo: 'agregado', producto: unProducto() });
    expect(inicial.lineas).toHaveLength(0);
  });
});
