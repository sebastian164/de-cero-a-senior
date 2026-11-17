import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { filtrarPorNombre, Pedido, ResumenComponent, sumarImportes } from './resumen';

const pedido: Pedido = { id: '1', items: [{ nombre: 'Teclado', p: 100, c: 2 }, { nombre: 'Monitor', p: 500, c: 1 }] };

describe('n4-01 ejemplo: ResumenComponent', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }));

  it('funciones puras', () => {
    expect(filtrarPorNombre(pedido.items, 'Mon')).toHaveLength(1);
    expect(sumarImportes(pedido.items)).toBe(700);
  });

  it('pasa de cargando a listo y el total sigue al filtro (estado mínimo)', async () => {
    const f = TestBed.createComponent(ResumenComponent);
    f.componentRef.setInput('pedidoId', '1');
    await f.whenStable();
    expect(f.nativeElement.textContent).toContain('Cargando');
    TestBed.inject(HttpTestingController).expectOne('/api/pedidos/1').flush(pedido);
    await f.whenStable();
    expect(f.nativeElement.textContent).toContain('Total: 700');
    const input: HTMLInputElement = f.nativeElement.querySelector('input');
    input.value = 'Mon'; input.dispatchEvent(new Event('input'));
    await f.whenStable();
    expect(f.nativeElement.textContent).toContain('Total: 500');
  });

  it('un error de la API es un estado, no una bandera suelta', async () => {
    const f = TestBed.createComponent(ResumenComponent);
    f.componentRef.setInput('pedidoId', '9');
    await f.whenStable();
    TestBed.inject(HttpTestingController).expectOne('/api/pedidos/9').flush('x', { status: 500, statusText: 'Error' });
    await f.whenStable();
    expect(f.nativeElement.querySelector('[role=alert]').textContent).toContain('No se pudo cargar');
  });
});
