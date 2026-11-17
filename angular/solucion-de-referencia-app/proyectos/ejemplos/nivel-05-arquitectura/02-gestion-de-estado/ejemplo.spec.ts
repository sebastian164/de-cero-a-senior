import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Carrito, CarritoStore, Linea } from './carrito';
import { ListaPedidosPagina } from './lista-pedidos.pagina';

const l = (productoId: string, precio: number, cantidad: number): Linea => ({ productoId, precio, cantidad });

describe.each([
  ['servicio con señales', () => TestBed.inject(Carrito)],
  ['NgRx SignalStore', () => TestBed.inject(CarritoStore)],
] as const)('n5-02 ejemplo: carrito como %s', (_n, crear) => {
  it('suma, cuenta y quita', () => {
    const c = crear();
    c.agregar(l('a', 100, 2)); c.agregar(l('b', 50, 1));
    expect(c.total()).toBe(250);
    expect(c.cantidad()).toBe(3);
    c.quitar('a');
    expect(c.total()).toBe(50);
    expect(c.lineas()).toEqual([l('b', 50, 1)]);
  });
});

describe('n5-02 ejemplo: filtro en la URL', () => {
  it('los parámetros de consulta son las entradas', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: 'pedidos', component: ListaPedidosPagina }], withComponentInputBinding())] });
    const h = await RouterTestingHarness.create('/pedidos?estado=pagado&pagina=2');
    const http = TestBed.inject(HttpTestingController);
    const r = http.expectOne((x) => x.url === '/api/pedidos');
    expect(r.request.params.get('estado')).toBe('pagado');
    expect(r.request.params.get('pagina')).toBe('2');
    r.flush({ items: [{ id: 'p2', estado: 'pagado', cliente: 'Ana' }] });
    await h.fixture.whenStable();
    expect(h.routeNativeElement!.textContent).toContain('Filtro: pagado · página 2');
    expect(h.routeNativeElement!.textContent).toContain('p2 Ana');
  });

  it('sin parámetros en la URL las entradas valen sus valores por defecto (todos, página 1)', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: 'pedidos', component: ListaPedidosPagina }], withComponentInputBinding())] });
    const h = await RouterTestingHarness.create('/pedidos');
    const r = TestBed.inject(HttpTestingController).expectOne((x) => x.url === '/api/pedidos');
    expect([r.request.params.get('estado'), r.request.params.get('pagina')]).toEqual(['todos', '1']);
    r.flush({ items: [] });
    await h.fixture.whenStable();
    expect(h.routeNativeElement!.textContent).toContain('Filtro: todos · página 1');
  });
});
