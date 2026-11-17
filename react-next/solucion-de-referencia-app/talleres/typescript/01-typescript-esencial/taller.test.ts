import { describe, expect, it } from 'vitest';
import { comision, type MedioDePago } from './medios-de-pago';
import { agruparPor } from './agrupar';
import { validarPedido } from './validar-pedido';
import * as inv from './inventario';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

describe('TS 01 · medios de pago', () => {
  const casos: [MedioDePago, number][] = [
    [{ tipo: 'tarjeta', franquicia: 'visa', ultimos4: '4242', cuotas: 1 }, 2900],
    [{ tipo: 'tarjeta', franquicia: 'amex', ultimos4: '0005', cuotas: 3 }, 4500],
    [{ tipo: 'transferencia', banco: 'Banco X', cuenta: '123' }, 3500],
    [{ tipo: 'efectivo', puntoDePago: 'Baloto' }, 2000],
    [{ tipo: 'billetera', proveedor: 'nequi', celular: '3000000000' }, 1500],
  ];
  it.each(casos)('calcula la comisión de %o', (medio, esperada) => {
    expect(comision(100_000, medio)).toBe(esperada);
  });
  it('el efectivo cobra el 1 % cuando supera el mínimo', () => {
    expect(comision(1_000_000, { tipo: 'efectivo', puntoDePago: 'Efecty' })).toBe(10_000);
  });
});

describe('TS 01 · agruparPor', () => {
  it('agrupa conservando el tipo de la clave', () => {
    const personas = [
      { nombre: 'Ana', ciudad: 'Bogotá' },
      { nombre: 'Luis', ciudad: 'Cali' },
      { nombre: 'Eva', ciudad: 'Bogotá' },
    ];
    const grupos = agruparPor(personas, 'ciudad');
    expect([...grupos.keys()]).toEqual(['Bogotá', 'Cali']);
    expect(grupos.get('Bogotá')?.map((p) => p.nombre)).toEqual(['Ana', 'Eva']);
  });
  it('acepta claves numéricas', () => {
    const grupos = agruparPor([{ n: 1 }, { n: 2 }, { n: 1 }], 'n');
    expect(grupos.get(1)).toHaveLength(2);
  });
});

describe('TS 01 · validarPedido', () => {
  const valido = {
    id: 'p-1', cliente: 'Ana', estado: 'pagado', creadoEn: '2026-01-02T10:00:00Z',
    lineas: [{ producto: 'Café', cantidad: 2, precio: 18000 }],
  };
  it('devuelve un Pedido tipado si la respuesta es correcta', () => {
    const r = validarPedido(valido);
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.valor.creadoEn).toBeInstanceOf(Date);
      expect(r.valor.lineas[0]?.cantidad).toBe(2);
    }
  });
  it.each([
    [null, 'La respuesta no es un objeto'],
    [{ ...valido, estado: 'enviado' }, 'estado inválido: enviado'],
    [{ ...valido, lineas: 'x' }, 'lineas debe ser un arreglo'],
    [{ ...valido, lineas: [{ producto: 'Té', cantidad: 0, precio: 1 }] }, 'lineas[0].cantidad debe ser un entero positivo'],
    [{ ...valido, creadoEn: 'ayer' }, 'creadoEn debe ser una fecha ISO'],
  ])('rechaza %o con un error claro', (entrada, error) => {
    expect(validarPedido(entrada)).toEqual({ ok: false, error });
  });
});

describe('TS 01 · inventario convertido', () => {
  it('corrige los errores que el JavaScript escondía', () => {
    expect(inv.precioConIva(inv.PRODUCTOS, 'NO-EXISTE')).toBeNull();
    expect(inv.valorInventario(inv.PRODUCTOS)).toBe(18000 * 12 + 3500 * 40);
    expect(inv.agotados(inv.PRODUCTOS).map((p) => p.sku)).toEqual(['TE-02']);
    expect(inv.PRODUCTOS[1]?.existencias).toBe(0);
    expect(inv.resumenPorCategoria(inv.PRODUCTOS)).toEqual({ bebidas: 12, panaderia: 40 });
    expect(inv.etiqueta(inv.PRODUCTOS, 'PAN-03')).toBe('PAN - $3500');
  });
  it('descontar no modifica el inventario original', () => {
    const r = inv.descontar(inv.PRODUCTOS, 'CAF-01', 2);
    expect(r.ok && r.productos[0]?.existencias).toBe(10);
    expect(inv.PRODUCTOS[0]?.existencias).toBe(12);
    expect(inv.descontar(inv.PRODUCTOS, 'TE-02', 1)).toEqual({ ok: false, motivo: 'sin-existencias' });
  });
});

describe('TS 01 · lo que el JavaScript original hacía en ejecución (los errores que el compilador descubre)', () => {
  // Se ejecuta el archivo original tal cual, con un `module` propio, para observar cada defecto.
  const cargar = () => {
    const modulo: { exports: Record<string, (...a: unknown[]) => unknown> } = { exports: {} };
    new Function('module', readFileSync(join(__dirname, 'js-original', 'inventario.js'), 'utf8'))(modulo);
    return modulo.exports as Record<string, (...a: unknown[]) => unknown>;
  };
  it('precioConIva de un SKU inexistente revienta con TypeError (TS18048: producto posiblemente undefined)', () => {
    expect(() => cargar().precioConIva!('NO-EXISTE')).toThrow(TypeError);
  });
  it('valorInventario da NaN en silencio por el campo "existencia" que no existe (TS2551)', () => {
    expect(cargar().valorInventario!()).toBeNaN();
  });
  it('agotados() asigna en vez de comparar: devuelve [] y deja TODAS las existencias en 0 (TS2540 solo se ve con readonly)', () => {
    const m = cargar();
    expect(m.agotados!()).toEqual([]);
    expect(m.resumenPorCategoria!()).toEqual({ bebidas: 0, panaderia: 0 });
  });
  it('descontar("CAF-01") sin cantidad se ejecuta al cargar el módulo (TS2554 en TypeScript; en JS no avisa)', () => {
    expect(cargar().descontar2).toBe(true);
  });
});
