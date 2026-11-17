// Ejecuta los bloques "Después" de las tres sesiones de TypeScript y comprueba lo que afirman los README.
import { describe, expect, it } from 'vitest';
import { total, descuento } from './01-typescript-esencial/total';
import { Carrito } from './02-orientacion-a-objetos-en-typescript/carrito-clase';
import { agregar, total as totalInmutable } from './02-orientacion-a-objetos-en-typescript/carrito-inmutable';
import {
  conDescuento, cargarTablero, cargarTableroEnSerie, cargarTableroTolerante, simulacion, type Pedido,
} from './03-funciones-inmutabilidad-y-asincronia/tablero';

describe('TS 01 · total con cupón', () => {
  const items = [{ producto: 'Café', precio: 18000, cantidad: 2 }, { producto: 'Pan', precio: 3500, cantidad: 4 }];
  it('sin cupón suma precio por cantidad', () => expect(total(items)).toBe(50000));
  it('aplica cada caso de la unión', () => {
    expect(total(items, { tipo: 'porcentaje', porcentaje: 10 })).toBe(45000);
    expect(total(items, { tipo: 'fijo', valor: 80000 })).toBe(0);
    expect(total(items, { tipo: 'envioGratis' })).toBe(50000);
    expect(descuento(1000, { tipo: 'fijo', valor: 300 })).toBe(300);
  });
});

describe('TS 02 · Carrito', () => {
  it('protege sus reglas', () => {
    const c = new Carrito();
    expect(() => c.agregar('Café', 18000, -3)).toThrow('La cantidad debe ser mayor que cero');
    expect(() => c.pagar()).toThrow('No se puede pagar un carrito vacío');
    c.agregar('Café', 18000, 2);
    expect(c.total).toBe(36000);
    c.pagar();
    expect(() => c.agregar('Pan', 3500, 1)).toThrow('El carrito ya fue pagado');
  });
  it('la versión inmutable devuelve un estado nuevo', () => {
    const inicial = { lineas: [], pagado: false };
    const siguiente = agregar(inicial, { producto: 'Café', precio: 18000, cantidad: 1 });
    expect(inicial.lineas).toHaveLength(0);
    expect(totalInmutable(siguiente)).toBe(18000);
  });
});

describe('TS 03 · funciones puras y asincronía', () => {
  it('conDescuento no modifica el pedido original', () => {
    const pedido: Pedido = { id: '1', total: 1000, items: [{ producto: 'b', precio: 20 }, { producto: 'a', precio: 10 }] };
    const nuevo = conDescuento(pedido, 10);
    expect(nuevo.total).toBe(900);
    expect(nuevo.items.map((i) => i.precio)).toEqual([10, 20]);
    expect(pedido.items.map((i) => i.precio)).toEqual([20, 10]);
    expect(pedido.total).toBe(1000);
  });
  it('Promise.all tarda lo del más lento (≈500 ms) y en serie la suma (≈1.200 ms)', async () => {
    let inicio = performance.now();
    await cargarTableroEnSerie('1');
    const serie = performance.now() - inicio;
    inicio = performance.now();
    await cargarTablero('1');
    const paralelo = performance.now() - inicio;
    expect(serie).toBeGreaterThanOrEqual(1190);
    expect(paralelo).toBeGreaterThanOrEqual(490);
    expect(paralelo).toBeLessThan(800);
  });
  it('la versión tolerante devuelve [] si falla una consulta no crítica', async () => {
    simulacion.fallaPedidos = true;
    try {
      const t = await cargarTableroTolerante('1');
      expect(t.pedidos).toEqual([]);
      expect(t.facturas).toEqual(['factura-1']);
      await expect(cargarTablero('1')).rejects.toThrow('fallo simulado');
    } finally {
      simulacion.fallaPedidos = false;
    }
  });
});
