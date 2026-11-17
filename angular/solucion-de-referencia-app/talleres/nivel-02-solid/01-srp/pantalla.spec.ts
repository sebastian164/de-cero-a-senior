import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { PantallaFacturaAntesComponent } from './antes/pantalla-factura.component';
import { FacturaDto } from './despues/datos/facturas.api';
import { Descargador } from './despues/exportacion/csv';
import { PantallaFacturaComponent } from './despues/pantalla-factura.component';
import { TestBed } from '@angular/core/testing';

const dto: FacturaDto = {
  id: 'F-2', fecha_emision: '2026-03-14T10:00:00Z', cliente: { nombre_completo: 'El Tornillo', tipo: 'mayorista' },
  lineas: [{ descripcion: 'Taladro', cantidad: 3, valor_unitario: 350000 }, { descripcion: 'Brocas', cantidad: 10, valor_unitario: 12000 }],
};

describe('pantalla de factura: antes y después muestran lo mismo', () => {
  beforeEach(() => localStorage.clear());

  it('antes (línea base)', async () => {
    const { fixture } = await render(PantallaFacturaAntesComponent, { providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(HttpTestingController).expectOne('/api/facturas/F-2').flush(dto);
    fixture.componentRef.changeDetectorRef.markForCheck(); await fixture.whenStable();   // sin señales ni zona, nadie avisa a Angular de que llegó la respuesta
    expect(await screen.findByText(/Total:/)).toHaveTextContent('Total: $ 1.253.070');
  });

  it('después: mismas cifras, envío y exportación con dobles', async () => {
    const descargar = vi.fn();
    await render(PantallaFacturaComponent, {
      inputs: { id: 'F-2' },
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: Descargador, useValue: { descargar } }],
    });
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/facturas/F-2').flush(dto);
    expect(await screen.findByText(/Total:/)).toHaveTextContent('Total: $ 1.253.070');
    const u = userEvent.setup();
    await u.type(screen.getByLabelText('Correo'), 'compras@tornillo.co');
    await u.click(screen.getByRole('button', { name: 'Enviar' }));
    http.expectOne('/api/correos').flush({});
    expect(await screen.findByRole('status')).toHaveTextContent('Enviada a compras@tornillo.co');
    await u.click(screen.getByRole('button', { name: 'Exportar CSV' }));
    expect(descargar).toHaveBeenCalledWith('factura-F-2.csv', expect.stringContaining('total;;1253070'), 'text/csv');
  });
});
