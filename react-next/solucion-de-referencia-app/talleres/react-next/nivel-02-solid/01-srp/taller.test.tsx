import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { agregarLinea, descuento, quitarLinea, totales, type Factura } from './despues/dominio';
import { aCsv } from './despues/exportar';
import { aFactura } from './despues/datos';
import { FACTURAS } from './despues/facturas-servidor';
import { EditorFactura } from './despues/editor-factura';
import { EditorFacturaAntes } from './antes/editor-factura';

const guardadas: unknown[] = [];
const servidor = setupServer(
  http.get('*/api/n2-01/facturas/:id', ({ params }) => {
    const f = FACTURAS[String(params.id)];
    return f ? HttpResponse.json(f) : new HttpResponse(null, { status: 404 });
  }),
  http.put('*/api/n2-01/facturas/:id', async ({ request }) => { guardadas.push(await request.json()); return new HttpResponse(null, { status: 204 }); }),
);
beforeAll(() => servidor.listen({ onUnhandledRequest: 'error' }));
afterEach(() => { servidor.resetHandlers(); guardadas.length = 0; });
afterAll(() => servidor.close());

const factura = (tipo: Factura['tipoCliente'], ...valores: number[]): Factura => ({
  numero: 'F', cliente: 'C', tipoCliente: tipo, fecha: new Date(0), lineas: valores.map((v, i) => ({ descripcion: `l${i}`, valor: v })),
});

describe('nivel-02/01 · punto 3: reglas sin importar React', () => {
  it('descuentos comerciales', () => {
    expect(descuento('mayorista', 1_000_000)).toBe(0);
    expect(descuento('mayorista', 1_200_000)).toBe(60_000);
    expect(descuento('frecuente', 18_000)).toBe(360);
    expect(descuento('normal', 5_000_000)).toBe(0);
  });
  it('IVA sobre la base con descuento, y exentos', () => {
    expect(totales(factura('mayorista', 900_000, 300_000))).toEqual({ subtotal: 1_200_000, descuento: 60_000, iva: 216_600, total: 1_356_600 });
    expect(totales(factura('exento', 100_000)).iva).toBe(0);
  });
  it('validación de líneas', () => {
    expect(agregarLinea(factura('normal'), 'ab', '10')).toEqual({ ok: false, error: 'La descripción debe tener al menos 3 caracteres' });
    expect(agregarLinea(factura('normal'), 'Café', '-1')).toEqual({ ok: false, error: 'El valor debe ser un número positivo' });
    expect(agregarLinea(factura('normal', ...Array<number>(10).fill(1)), 'Café', '1')).toEqual({ ok: false, error: 'Una factura admite máximo 10 líneas' });
    const r = agregarLinea(factura('normal'), ' Café ', '18.000');
    expect(r.ok && r.factura.lineas).toEqual([{ descripcion: 'Café', valor: 18000 }]);
    expect(quitarLinea(factura('normal', 1, 2), 0).lineas).toEqual([{ descripcion: 'l1', valor: 2 }]);
  });
  it('adaptador y CSV', () => {
    const f = aFactura(FACTURAS['2']!);
    expect(f.lineas).toEqual([{ descripcion: 'Café', valor: 18000 }]);
    expect(aCsv(f)).toBe('descripcion;valor\nCafé;18000\nsubtotal;18000\ndescuento;360\niva;3352\ntotal;20992');
  });
});

describe('nivel-02/01 · la versión separada se comporta igual que el componente original', () => {
  for (const [nombre, Editor] of [['antes', EditorFacturaAntes], ['después', EditorFactura]] as const) {
    it(`${nombre}: muestra totales, valida, agrega, quita y guarda`, async () => {
      const u = userEvent.setup();
      render(<Editor id="1" />);
      expect(await screen.findByRole('heading', { name: 'Factura FE-001' })).toBeInTheDocument();
      expect(screen.getByText('Cliente: Tienda La 14 (mayorista) · Fecha: 5/3/2026')).toBeInTheDocument();
      const resumen = screen.getAllByRole('definition')[0]!.closest('dl')!;
      const valores = () => within(resumen).getAllByRole('definition').map((d) => d.textContent);
      // El original encadena efectos: hay renderizados intermedios con totales en $0 hasta que la cadena termina.
      await waitFor(() => expect(valores()).toEqual(['$1.200.000', '$60.000', '$216.600', '$1.356.600']));
      await u.click(screen.getByRole('button', { name: 'Agregar línea' }));
      expect(screen.getByRole('alert')).toHaveTextContent('La descripción debe tener al menos 3 caracteres');
      await u.type(screen.getByLabelText('Descripción'), 'Panela');
      await u.type(screen.getByLabelText('Valor'), '100.000');
      await u.click(screen.getByRole('button', { name: 'Agregar línea' }));
      await waitFor(() => expect(valores()).toEqual(['$1.300.000', '$65.000', '$234.650', '$1.469.650']));
      await u.click(screen.getAllByRole('button', { name: 'Quitar' })[2]!);
      await waitFor(() => expect(valores()[3]).toBe('$1.356.600'));
      await u.click(screen.getByRole('button', { name: 'Guardar' }));
      expect(await screen.findByRole('status')).toHaveTextContent('Factura guardada');
      expect(guardadas).toEqual([{ numero: 'FE-001', lineas: FACTURAS['1']!.lineas }]);
    });
  }
});
