import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { iva, subtotal, total, Factura } from './dominio/factura';
import { FacturaComponent } from './factura.component';

const f: Factura = { id: 'F', cliente: 'Ana', fecha: new Date(0), lineas: [{ descripcion: 'a', valor: 100 }, { descripcion: 'b', valor: 50 }] };

describe('n2-01 SRP (después)', () => {
  it('las reglas se prueban sin Angular', () => {
    expect(subtotal(f)).toBe(150);
    expect(iva(f)).toBeCloseTo(28.5);
    expect(total(f)).toBeCloseTo(178.5);
  });

  it('el componente pide por id, traduce el DTO y muestra el total con la tubería date', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const fx = TestBed.createComponent(FacturaComponent);
    fx.componentRef.setInput('id', 'F-1');
    TestBed.tick();
    TestBed.inject(HttpTestingController).expectOne('/api/facturas/F-1')
      .flush({ id: 'F-1', cliente: 'Ana', fecha_emision: '2026-03-14T10:00:00Z', lineas: [{ descripcion: 'x', valor: 100000 }] });
    await fx.whenStable();
    expect(fx.nativeElement.textContent).toContain('Emitida el 14/03/2026');
    expect(fx.nativeElement.textContent).toMatch(/Total:\s*\$\s*119\.000/);
  });
});
