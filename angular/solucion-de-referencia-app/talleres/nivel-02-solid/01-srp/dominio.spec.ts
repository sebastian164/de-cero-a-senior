// 3. Reglas de negocio probadas sin importar nada de Angular.
import { aplicarCupon } from './despues/dominio/cupones';
import { descuentoComercial, Factura, iva, sePuedeEnviar, subtotal, total } from './despues/dominio/factura';
import { facturaACsv } from './despues/exportacion/csv';

const factura = (tipoCliente: Factura['tipoCliente'], valorUnitario: number): Factura =>
  ({ id: 'X', cliente: 'C', tipoCliente, emitidaEn: new Date(0), lineas: [{ descripcion: 'a;b', cantidad: 2, valorUnitario }] });

describe('dominio de facturas (sin Angular)', () => {
  it('sin descuento por debajo de un millón', () => {
    const f = factura('mayorista', 100_000);
    expect(descuentoComercial(f)).toBe(0);
    expect(iva(f)).toBe(38_000);
    expect(total(f)).toBe(238_000);
  });
  it('5 % al minorista y 10 % al mayorista por encima de un millón; IVA sobre la base descontada', () => {
    expect(descuentoComercial(factura('minorista', 600_000))).toBe(60_000);
    const m = factura('mayorista', 600_000);
    expect(descuentoComercial(m)).toBe(120_000);
    expect(iva(m)).toBe(205_200);
    expect(total(m)).toBe(subtotal(m) - 120_000 + 205_200);
  });
  it('cupones con la fecha como parámetro', () => {
    expect(aplicarCupon('bienvenida', 200_000, new Date(2026, 3, 1))).toEqual({ valido: true, descuento: 20_000 });
    expect(aplicarCupon('NAVIDAD', 100_000, new Date(2026, 11, 24))).toEqual({ valido: true, descuento: 15_000 });
    expect(aplicarCupon('NAVIDAD', 100_000, new Date(2026, 5, 24))).toMatchObject({ valido: false });
  });
  it('envío y CSV', () => {
    expect(sePuedeEnviar(factura('minorista', 1), 'ana@correo.co')).toBe(true);
    expect(sePuedeEnviar(factura('minorista', 1), 'ana@correo')).toBe(false);
    expect(facturaACsv(factura('minorista', 10))).toBe('descripcion;cantidad;valor\na,b;2;20\ntotal;;24\n');
  });
});
