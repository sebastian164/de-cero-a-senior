// Los bloques «Antes» de las tres sesiones de TypeScript compilan (tsc) y hacen lo que el README dice que hacen mal.
import { afterEach, describe, expect, it, vi } from 'vitest';
import { total } from './01-typescript-esencial/antes/total-antes';
import { carrito } from './02-orientacion-a-objetos-en-typescript/antes/carrito-antes';
import { aplicarDescuento, cargarTablero, retardos } from './03-funciones-inmutabilidad-y-asincronia/antes/tablero-antes';

afterEach(() => vi.useRealTimers());

describe('TS 01 · antes: any deja pasar errores', () => {
  it("un ítem con 'cantidad' en lugar de 'cant' no avisa: da NaN", () => {
    expect(total([{ precio: 18000, cantidad: 2 }], { tipo: 'fijo', valor: 0 })).toBeNaN();
  });
  it('sin cupón revienta en ejecución (el tipo any no lo impidió)', () => {
    expect(() => total([{ precio: 1, cant: 1 }])).toThrow(TypeError);
  });
  it('con datos bien nombrados y cupón funciona', () => {
    expect(total([{ precio: 1000, cant: 2 }], { tipo: 'porcentaje', valor: 10 })).toBe(1800);
  });
});

describe('TS 02 · antes: la clase no protege sus reglas', () => {
  it('acepta cantidad negativa, un total inventado y pagado sin ítems válidos', () => {
    expect(carrito.items[0]?.cantidad).toBe(-3);
    expect(carrito.total).toBe(1);
    expect(carrito.pagado).toBe(true);
  });
});

describe('TS 03 · antes: muta el argumento y carga en serie', () => {
  it('aplicarDescuento modifica el pedido recibido y reordena su lista', () => {
    const pedido = { total: 100, items: [{ producto: 'B', precio: 30 }, { producto: 'A', precio: 10 }] };
    const resultado = aplicarDescuento(pedido, 10);
    expect(resultado).toBe(pedido);                   // la misma referencia
    expect(pedido.total).toBe(90);                    // modificó el argumento
    expect(pedido.items.map((i) => i.producto)).toEqual(['A', 'B']);   // reordenó la lista de otro
  });
  it('cargarTablero tarda la suma de los tres retardos (1 200 ms) y no tolera fallos', async () => {
    vi.useFakeTimers();
    let listo = false;
    const p = cargarTablero('7').then((r) => { listo = true; return r; });
    await vi.advanceTimersByTimeAsync(retardos.cliente + retardos.pedidos + retardos.facturas - 1);
    expect(listo).toBe(false);                        // a 1 199 ms todavía no termina
    await vi.advanceTimersByTimeAsync(1);
    expect(listo).toBe(true);
    expect(Object.keys(await p)).toEqual(['cliente', 'pedidos', 'facturas']);
    expect(retardos.cliente + retardos.pedidos + retardos.facturas).toBe(1200);
  });
});
